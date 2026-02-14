package com.vanillage.raytraceantixray.tasks;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.antixray.ChunkPacketBlockControllerAntiXray;
import com.vanillage.raytraceantixray.data.*;
import com.vanillage.raytraceantixray.util.BlockIterator;
import com.vanillage.raytraceantixray.util.BlockOcclusionCulling;
import com.vanillage.raytraceantixray.util.BlockOcclusionCulling.BlockOcclusionGetter;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.MissingPaletteEntryException;
import net.minecraft.world.level.chunk.PaletteResize;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Per-player async ray-trace job.
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Reads player-local chunk cache snapshots.</li>
 * <li>Computes visibility transitions for tracked obfuscated blocks.</li>
 * <li>Pushes compact visibility deltas to the sync update queue.</li>
 * </ul>
 * <p>
 * Thread-safety strategy:
 * <ul>
 * <li>Never mutates server world state.</li>
 * <li>Treats missing/stale references as unloaded and exits gracefully.</li>
 * <li>Uses conservative guards so async failures degrade to "skip this tick".</li>
 * </ul>
 */
public final class RayTraceCallable implements Callable<Void> {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final RayTraceAntiXray plugin;
    private final PlayerData playerData;
    private final CachedSectionBlockOcclusionGetter cachedSectionBlockOcclusionGetter;
    private final BlockOcclusionCulling blockOcclusionCulling;
    private final Collection<ChunkBlocks> chunks;
    private final double rayTraceDistance;
    private final double rayTraceDistanceSquared;
    private final boolean rehideBlocks;
    private final double rehideDistanceSquared;
    private final Set<Block> bypassRehideBlocks;

    private volatile VectorialLocation[] tracedLocations = null;

    /**
     * Builds a callable bound to the player's current anti-xray controller context.
     * <p>
     * If initialization inputs are incomplete (null world/location/controller), this instance
     * becomes a no-op callable instead of throwing.
     */
    public RayTraceCallable(final RayTraceAntiXray plugin, final PlayerData playerData) {
        this.plugin = plugin;
        final VectorialLocation[] initialLocations = playerData.getLocations();
        if (initialLocations == null || initialLocations.length == 0 || initialLocations[0] == null
                || initialLocations[0].getWorld() == null) {
            this.playerData = null;
            this.cachedSectionBlockOcclusionGetter = null;
            this.blockOcclusionCulling = null;
            this.chunks = null;
            this.rayTraceDistance = 0.;
            this.rayTraceDistanceSquared = 0.;
            this.rehideBlocks = false;
            this.rehideDistanceSquared = 0.;
            this.bypassRehideBlocks = null;
            return;
        }

        final ChunkPacketBlockController chunkPacketBlockController = ((CraftWorld) initialLocations[0].getWorld())
                .getHandle().chunkPacketBlockController;

        if (!(chunkPacketBlockController instanceof final ChunkPacketBlockControllerAntiXray chunkPacketBlockControllerAntiXray)) {
            this.playerData = null;
            this.cachedSectionBlockOcclusionGetter = null;
            this.blockOcclusionCulling = null;
            this.chunks = null;
            this.rayTraceDistance = 0.;
            this.rayTraceDistanceSquared = 0.;
            this.rehideBlocks = false;
            this.rehideDistanceSquared = 0.;
            this.bypassRehideBlocks = null;
            return;
        }

        this.playerData = playerData;
        final MutableLongWrapper mutableLongWrapper = new MutableLongWrapper(0L);
        final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        final boolean[] solidGlobal = chunkPacketBlockControllerAntiXray.solidGlobal;
        this.cachedSectionBlockOcclusionGetter = new CachedSectionBlockOcclusionGetter() {
            private static final boolean UNLOADED_OCCLUDING = true;
            private LevelChunk chunk;
            private LevelChunkSection section;
            private int chunkX;
            private int sectionY;
            private int chunkZ;

            @Override
            public boolean isOccluding(final int x, final int y, final int z) {
                final int chunkX = x >> 4;
                final int chunkZ = z >> 4;

                if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
                    mutableLongWrapper.setValue(ChunkPos.asLong(chunkX, chunkZ));
                    final ChunkBlocks chunkBlocks = chunks.get(mutableLongWrapper);

                    if (chunkBlocks == null) {
                        return UNLOADED_OCCLUDING;
                    }

                    final LevelChunk chunk = chunkBlocks.getChunk();

                    if (chunk == null) {
                        return UNLOADED_OCCLUDING;
                    }

                    final int sectionY = y >> 4;
                    final int minSectionY = chunk.getMinSectionY();

                    if (sectionY < minSectionY || sectionY >= chunk.getMaxSectionY()) {
                        return false;
                    }

                    final LevelChunkSection section = chunk.getSections()[sectionY - minSectionY];
                    return section != null && !section.hasOnlyAir()
                            && solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                                    .idFor(RayTraceCallable.getBlockState(section, x, y, z), PaletteResize.noResizeExpected())]; // Sections
                                                                                                                // aren't
                                                                                                                // null
                                                                                                                // anymore.
                                                                                                                // Unfortunately,
                                                                                                                // LevelChunkSection#recalcBlockCounts()
                                                                                                                // temporarily
                                                                                                                // resets
                                                                                                                // #nonEmptyBlockCount
                                                                                                                // to 0
                                                                                                                // due
                                                                                                                // to a
                                                                                                                // Paper
                                                                                                                // optimization.
                }

                final int sectionY = y >> 4;

                if (this.sectionY != sectionY) {
                    if (this.chunk == null) {
                        return UNLOADED_OCCLUDING;
                    }

                    final int minSectionY = this.chunk.getMinSectionY();

                    if (sectionY < minSectionY || sectionY >= this.chunk.getMaxSectionY()) {
                        return false;
                    }

                    final LevelChunkSection section = this.chunk.getSections()[sectionY - minSectionY];
                    return section != null && !section.hasOnlyAir()
                            && solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                                    .idFor(RayTraceCallable.getBlockState(section, x, y, z), PaletteResize.noResizeExpected())]; // Sections
                                                                                                                // aren't
                                                                                                                // null
                                                                                                                // anymore.
                                                                                                                // Unfortunately,
                                                                                                                // LevelChunkSection#recalcBlockCounts()
                                                                                                                // temporarily
                                                                                                                // resets
                                                                                                                // #nonEmptyBlockCount
                                                                                                                // to 0
                                                                                                                // due
                                                                                                                // to a
                                                                                                                // Paper
                                                                                                                // optimization.
                }

                if (this.section == null) {
                    return this.chunk == null && UNLOADED_OCCLUDING;
                }

                return solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                        .idFor(RayTraceCallable.getBlockState(this.section, x, y, z), PaletteResize.noResizeExpected())];
            }

            @Override
            public boolean isOccludingRay(final int x, final int y, final int z) {
                final int chunkX = x >> 4;
                final int sectionY = y >> 4;
                final int chunkZ = z >> 4;

                if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
                    this.chunkX = chunkX;
                    this.sectionY = sectionY;
                    this.chunkZ = chunkZ;
                    mutableLongWrapper.setValue(ChunkPos.asLong(chunkX, chunkZ));
                    final ChunkBlocks chunkBlocks = chunks.get(mutableLongWrapper);

                    if (chunkBlocks == null) {
                        this.chunk = null;
                        this.section = null;
                        return UNLOADED_OCCLUDING;
                    }

                    this.chunk = chunkBlocks.getChunk();

                    if (this.chunk == null) {
                        this.section = null;
                        return UNLOADED_OCCLUDING;
                    }

                    final int minSectionY = this.chunk.getMinSectionY();

                    if (sectionY < minSectionY || sectionY >= this.chunk.getMaxSectionY()) {
                        this.section = null;
                        return false;
                    }

                    this.section = this.chunk.getSections()[sectionY - minSectionY];

                    if (this.section == null) { // Sections aren't null anymore.
                        return false;
                    }

                    if (this.section.hasOnlyAir()) { // Unfortunately, LevelChunkSection#recalcBlockCounts() temporarily
                                                // resets #nonEmptyBlockCount to 0 due to a Paper optimization.
                        this.section = null;
                        return false;
                    }

                    return solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                            .idFor(RayTraceCallable.getBlockState(this.section, x, y, z), PaletteResize.noResizeExpected())];
                }

                if (this.sectionY != sectionY) {
                    this.sectionY = sectionY;

                    if (this.chunk == null) {
                        // section = null;
                        return UNLOADED_OCCLUDING;
                    }

                    final int minSectionY = this.chunk.getMinSectionY();

                    if (sectionY < minSectionY || sectionY >= this.chunk.getMaxSectionY()) {
                        this.section = null;
                        return false;
                    }

                    this.section = this.chunk.getSections()[sectionY - minSectionY];

                    if (this.section == null) { // Sections aren't null anymore.
                        return false;
                    }

                    if (this.section.hasOnlyAir()) { // Unfortunately, LevelChunkSection#recalcBlockCounts() temporarily
                                                // resets #nonEmptyBlockCount to 0 due to a Paper optimization.
                        this.section = null;
                        return false;
                    }

                    return solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                            .idFor(RayTraceCallable.getBlockState(this.section, x, y, z), PaletteResize.noResizeExpected())];
                }

                if (this.section == null) {
                    return this.chunk == null && UNLOADED_OCCLUDING;
                }

                return solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                        .idFor(RayTraceCallable.getBlockState(this.section, x, y, z), PaletteResize.noResizeExpected())];
            }

            @Override
            public void initializeCache(final LevelChunk chunk, final int chunkX, final int sectionY, final int chunkZ) {
                this.chunk = chunk;
                final int sectionIndex = sectionY - chunk.getMinSectionY();
                final LevelChunkSection[] sections = chunk.getSections();
                this.section = sectionIndex >= 0 && sectionIndex < sections.length ? sections[sectionIndex] : null;
                this.chunkX = chunkX;
                this.sectionY = sectionY;
                this.chunkZ = chunkZ;
            }

            @Override
            public void clearCache() {
                this.chunk = null;
                this.section = null;
            }
        };
        this.blockOcclusionCulling = new BlockOcclusionCulling(
                new BlockIterator(0., 0., 0., 0., 0., 0.)::initializeNormalized, this.cachedSectionBlockOcclusionGetter,
                true);
        this.chunks = chunks.values();
        this.rayTraceDistance = chunkPacketBlockControllerAntiXray.rayTraceDistance;
        this.rayTraceDistanceSquared = this.rayTraceDistance * this.rayTraceDistance;
        this.rehideBlocks = chunkPacketBlockControllerAntiXray.rehideBlocks;
        final double rehideDistance = chunkPacketBlockControllerAntiXray.rehideDistance;
        this.rehideDistanceSquared = rehideDistance * rehideDistance;
        this.bypassRehideBlocks = chunkPacketBlockControllerAntiXray.bypassRehideBlocks;
    }

    /**
     * Executes one ray-trace pass when player camera locations changed.
     * <p>
     * Duplicate location arrays are skipped to reduce needless CPU work.
     */
    @Override
    public Void call() {
        if (this.playerData == null || this.blockOcclusionCulling == null) {
            return null;
        }

        final VectorialLocation[] locations = this.playerData.getLocations();
        if (locations == null || locations.length == 0 || locations[0] == null || locations[0].getWorld() == null) {
            return null;
        }

        if (Arrays.equals(this.tracedLocations, locations)) {
            // we already did raytracing for these locations
            return null;
        }

        // set before rayTrace to avoid constantly trying if error thrown.
        this.tracedLocations = locations;

        try {
            this.rayTrace();
        } catch (final Throwable t) {
            this.plugin.getLogger().log(Level.SEVERE, "An error occured on the RayTraceAntiXray tick thread", t);
            throw t;
        }

        return null;
    }

    /**
     * Core visibility computation loop.
     * <p>
     * The method is intentionally tolerant to concurrent invalidation of chunks/sections. It avoids
     * raising exceptions for transient unload races and simply discards now-invalid work items.
     */
    private void rayTrace() {
        if (this.blockOcclusionCulling == null) {
            return;
        }

        final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = this.playerData.getChunks();
        final VectorialLocation[] locations = this.playerData.getLocations();
        if (locations == null || locations.length == 0 || locations[0] == null || locations[0].getVector() == null) {
            return;
        }

        final Vector playerVector = locations[0].getVector();
        final double playerX = playerVector.getX();
        final double playerY = playerVector.getY();
        final double playerZ = playerVector.getZ();
        final int chunkXMin = ((int) Math.floor(playerX - this.rayTraceDistance)) >> 4;
        final int chunkZMin = ((int) Math.floor(playerZ - this.rayTraceDistance)) >> 4;
        final int chunkXMax = ((int) Math.floor(playerX + this.rayTraceDistance)) >> 4;
        final int chunkZMax = ((int) Math.floor(playerZ + this.rayTraceDistance)) >> 4;
        final Queue<Result> results = this.playerData.getResults();

        for (final ChunkBlocks chunkBlocks : this.chunks) {
            if (chunkBlocks == null) {
                continue;
            }

            final LevelChunk chunk = chunkBlocks.getChunk();

            if (chunk == null) {
                chunks.remove(chunkBlocks.getKey(), chunkBlocks);
                continue;
            }

            final ChunkPos chunkPos = chunk.getPos();
            final int chunkX = chunkPos.x;

            if (chunkX < chunkXMin || chunkX > chunkXMax) {
                continue;
            }

            final int chunkZ = chunkPos.z;

            if (chunkZ < chunkZMin || chunkZ > chunkZMax) {
                continue;
            }

            final Iterator<Entry<BlockPos, Boolean>> iterator = chunkBlocks.getBlocks().entrySet().iterator();

            while (iterator.hasNext()) {
                final Entry<BlockPos, Boolean> blockHidden = iterator.next();
                final BlockPos block = blockHidden.getKey();
                final int x = block.getX();
                final int y = block.getY();
                final int z = block.getZ();
                final double centerX = x + 0.5;
                final double centerY = y + 0.5;
                final double centerZ = z + 0.5;
                final double differenceX = playerX - centerX;
                final double differenceY = playerY - centerY;
                final double differenceZ = playerZ - centerZ;
                final double distanceSquared = differenceX * differenceX + differenceY * differenceY
                        + differenceZ * differenceZ;

                if (!(distanceSquared <= this.rayTraceDistanceSquared)) {
                    continue;
                }

                boolean visible = false;

                if (distanceSquared < this.rehideDistanceSquared) {
                    final int sectionY = y >> 4;

                    for (int i = 0; i < locations.length; i++) {
                        final VectorialLocation location = locations[i];
                        final Vector direction = location.getDirection();
                        final double directionX = direction.getX();
                        final double directionY = direction.getY();
                        final double directionZ = direction.getZ();
                        this.cachedSectionBlockOcclusionGetter.initializeCache(chunk, chunkX, sectionY, chunkZ);

                        if (i == 0) {
                            if (this.blockOcclusionCulling.isVisible(x, y, z, centerX, centerY, centerZ, differenceX,
                                    differenceY, differenceZ, distanceSquared, directionX, directionY, directionZ)) {
                                visible = true;
                                break;
                            }
                        } else {
                            final Vector vector = location.getVector();
                            final double vectorDifferenceX = vector.getX() - centerX;
                            final double vectorDifferenceY = vector.getY() - centerY;
                            final double vectorDifferenceZ = vector.getZ() - centerZ;

                            if (this.blockOcclusionCulling.isVisible(x, y, z, centerX, centerY, centerZ, vectorDifferenceX,
                                    vectorDifferenceY, vectorDifferenceZ,
                                    vectorDifferenceX * vectorDifferenceX + vectorDifferenceY * vectorDifferenceY
                                            + vectorDifferenceZ * vectorDifferenceZ,
                                    directionX, directionY, directionZ)) {
                                visible = true;
                                break;
                            }
                        }
                    }
                }

                final boolean hidden = blockHidden.getValue();

                if (visible) {
                    if (hidden) {
                        results.add(new Result(chunkBlocks, block, true));

                        if (this.rehideBlocks) {
                            boolean bypass = false;

                            if (this.bypassRehideBlocks != null) {
                                final int sectionIndex = (y >> 4) - chunk.getMinSectionY();
                                final LevelChunkSection[] sections = chunk.getSections();
                                final LevelChunkSection section = sectionIndex >= 0 && sectionIndex < sections.length
                                        ? sections[sectionIndex]
                                        : null;

                                if (section != null && !section.hasOnlyAir()
                                        && this.bypassRehideBlocks.contains(RayTraceCallable.getBlockState(section, x, y, z).getBlock())) {
                                    bypass = true;
                                }
                            }

                            if (bypass) {
                                iterator.remove();
                            } else {
                                blockHidden.setValue(false);
                            }
                        } else {
                            iterator.remove();
                        }
                    }
                } else if (!hidden) {
                    results.add(new Result(chunkBlocks, block, false));
                    blockHidden.setValue(true);
                }
            }
        }

        this.cachedSectionBlockOcclusionGetter.clearCache();
    }

    /**
     * Reads a block state from a section and falls back to air on palette desync.
     * <p>
     * This protects async readers against transient palette resize windows in upstream internals.
     */
    private static BlockState getBlockState(final LevelChunkSection section, final int x, final int y, final int z) {
        // synchronized (section.getStates()) {
        // try {
        // section.getStates().acquire();
        try {
            return section.getBlockState(x & 15, y & 15, z & 15);
        } catch (final MissingPaletteEntryException e) {
            return RayTraceCallable.AIR;
        }
        // } finally {
        // section.getStates().release();
        // }
        // }
    }

    /**
     * Small cache contract used by {@link BlockOcclusionCulling} to avoid repeating chunk/section
     * lookups for neighboring ray steps.
     */
    private interface CachedSectionBlockOcclusionGetter extends BlockOcclusionGetter {
        void initializeCache(LevelChunk chunk, int chunkX, int sectionY, int chunkZ);

        void clearCache();
    }
}
