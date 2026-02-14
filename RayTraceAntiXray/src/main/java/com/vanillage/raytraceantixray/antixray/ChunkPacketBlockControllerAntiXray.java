package com.vanillage.raytraceantixray.antixray;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.ChunkBlocks;
import io.papermc.paper.antixray.BitStorageReader;
import io.papermc.paper.antixray.BitStorageWriter;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import io.papermc.paper.antixray.ChunkPacketInfo;
import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.EngineMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

public final class ChunkPacketBlockControllerAntiXray extends ChunkPacketBlockController {

    public static final Palette<BlockState> GLOBAL_BLOCKSTATE_PALETTE = new GlobalPalette<>(Block.BLOCK_STATE_REGISTRY);
    private static final PaletteResize<BlockState> RESIZE_RETURNS_NEGATIVE_ONE = (idx, state) -> -1;
    private static final LevelChunkSection EMPTY_SECTION = null;
    private final RayTraceAntiXray plugin;
    private final ChunkPacketBlockController oldController;
    private final Executor executor;
    private final EngineMode engineMode;
    private final int maxBlockHeight;
    private final int updateRadius;
    private final boolean usePermission;
    public final boolean rayTraceThirdPerson;
    public final double rayTraceDistance;
    public final boolean rehideBlocks;
    public final double rehideDistance;
    private final int maxRayTraceBlockCountPerChunk;
    private final BlockState[] presetBlockStates;
    private final BlockState[] presetBlockStatesFull;
    private final BlockState[] presetBlockStatesStone;
    private final BlockState[] presetBlockStatesDeepslate;
    private final BlockState[] presetBlockStatesNetherrack;
    private final BlockState[] presetBlockStatesEndStone;
    private final int[] presetBlockStateBitsGlobal;
    private final int[] presetBlockStateBitsStoneGlobal;
    private final int[] presetBlockStateBitsDeepslateGlobal;
    private final int[] presetBlockStateBitsNetherrackGlobal;
    private final int[] presetBlockStateBitsEndStoneGlobal;
    public final boolean[] solidGlobal = new boolean[Block.BLOCK_STATE_REGISTRY.size()];
    public final Set<Block> bypassRehideBlocks;
    private final boolean[] obfuscateGlobal = new boolean[Block.BLOCK_STATE_REGISTRY.size()];
    private final boolean[] traceGlobal;
    private final boolean[] blockEntityGlobal = new boolean[Block.BLOCK_STATE_REGISTRY.size()];
    private final LevelChunkSection[] emptyNearbyChunkSections = { ChunkPacketBlockControllerAntiXray.EMPTY_SECTION, ChunkPacketBlockControllerAntiXray.EMPTY_SECTION, ChunkPacketBlockControllerAntiXray.EMPTY_SECTION,
            ChunkPacketBlockControllerAntiXray.EMPTY_SECTION };
    private final int maxBlockHeightUpdatePosition;

    public ChunkPacketBlockControllerAntiXray(
            final RayTraceAntiXray plugin,
            final ChunkPacketBlockController oldController,
            final boolean rayTraceThirdPerson,
            final double rayTraceDistance,
            final boolean rehideBlocks,
            final double rehideDistance,
            final int maxRayTraceBlockCountPerChunk,
            final Iterable<? extends String> toTrace,
            final Iterable<? extends String> bypassRehideBlocks,
            final Level level,
            final Executor executor) {
        this.plugin = plugin;
        this.oldController = oldController;
        this.executor = executor;
        final WorldConfiguration.Anticheat.AntiXray paperWorldConfig = level.paperConfig().anticheat.antiXray;
        this.engineMode = paperWorldConfig.engineMode;
        this.maxBlockHeight = paperWorldConfig.maxBlockHeight >> 4 << 4;
        this.updateRadius = paperWorldConfig.updateRadius;
        this.usePermission = paperWorldConfig.usePermission;
        this.rayTraceThirdPerson = rayTraceThirdPerson;
        this.rayTraceDistance = rayTraceDistance;
        this.rehideBlocks = rehideBlocks;
        this.rehideDistance = rehideDistance;
        this.maxRayTraceBlockCountPerChunk = maxRayTraceBlockCountPerChunk;
        List<Block> toObfuscate;

        if (this.engineMode == EngineMode.HIDE) {
            toObfuscate = paperWorldConfig.hiddenBlocks;
            this.presetBlockStates = null;
            this.presetBlockStatesFull = null;
            this.presetBlockStatesStone = new BlockState[] { Blocks.STONE.defaultBlockState() };
            this.presetBlockStatesDeepslate = new BlockState[] { Blocks.DEEPSLATE.defaultBlockState() };
            this.presetBlockStatesNetherrack = new BlockState[] { Blocks.NETHERRACK.defaultBlockState() };
            this.presetBlockStatesEndStone = new BlockState[] { Blocks.END_STONE.defaultBlockState() };
            this.presetBlockStateBitsGlobal = null;
            this.presetBlockStateBitsStoneGlobal = new int[] { ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                    .idFor(Blocks.STONE.defaultBlockState(), PaletteResize.noResizeExpected()) };
            this.presetBlockStateBitsDeepslateGlobal = new int[] { ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                    .idFor(Blocks.DEEPSLATE.defaultBlockState(), PaletteResize.noResizeExpected()) };
            this.presetBlockStateBitsNetherrackGlobal = new int[] { ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                    .idFor(Blocks.NETHERRACK.defaultBlockState(), PaletteResize.noResizeExpected()) };
            this.presetBlockStateBitsEndStoneGlobal = new int[] { ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE
                    .idFor(Blocks.END_STONE.defaultBlockState(), PaletteResize.noResizeExpected()) };
        } else {
            toObfuscate = new ArrayList<>(paperWorldConfig.replacementBlocks);
            final List<BlockState> presetBlockStateList = new LinkedList<>();

            for (final Block block : paperWorldConfig.hiddenBlocks) {

                if (!(block instanceof EntityBlock)) {
                    toObfuscate.add(block);
                    presetBlockStateList.add(block.defaultBlockState());
                }
            }

            // The doc of the LinkedHashSet(Collection<? extends E>) constructor doesn't
            // specify that the insertion order is the predictable iteration order of the
            // specified Collection, although it is in the implementation
            final Set<BlockState> presetBlockStateSet = new LinkedHashSet<>();
            // Therefore addAll(Collection<? extends E>) is used, which guarantees this
            // order in the doc
            presetBlockStateSet.addAll(presetBlockStateList);
            this.presetBlockStates = presetBlockStateSet.isEmpty()
                    ? new BlockState[] { Blocks.DIAMOND_ORE.defaultBlockState() }
                    : presetBlockStateSet.toArray(new BlockState[0]);
            this.presetBlockStatesFull = presetBlockStateSet.isEmpty()
                    ? new BlockState[] { Blocks.DIAMOND_ORE.defaultBlockState() }
                    : presetBlockStateList.toArray(new BlockState[0]);
            this.presetBlockStatesStone = null;
            this.presetBlockStatesDeepslate = null;
            this.presetBlockStatesNetherrack = null;
            this.presetBlockStatesEndStone = null;
            this.presetBlockStateBitsGlobal = new int[this.presetBlockStatesFull.length];

            for (int i = 0; i < this.presetBlockStatesFull.length; i++) {
                this.presetBlockStateBitsGlobal[i] = ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(this.presetBlockStatesFull[i],
                        PaletteResize.noResizeExpected());
            }

            this.presetBlockStateBitsStoneGlobal = null;
            this.presetBlockStateBitsDeepslateGlobal = null;
            this.presetBlockStateBitsNetherrackGlobal = null;
            this.presetBlockStateBitsEndStoneGlobal = null;
        }

        for (final Block block : toObfuscate) {

            // Don't obfuscate air because air causes unnecessary block updates and causes
            // block updates to fail in the void
            if (block != null && !block.defaultBlockState().isAir()) {
                // Replace all block states of a specified block
                for (final BlockState blockState : block.getStateDefinition().getPossibleStates()) {
                    this.obfuscateGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(blockState,
                            PaletteResize.noResizeExpected())] = true;
                }
            }
        }

        if (toTrace == null) {
            this.traceGlobal = this.obfuscateGlobal;
        } else {
            this.traceGlobal = new boolean[Block.BLOCK_STATE_REGISTRY.size()];

            for (final String id : toTrace) {
                Block block = null;
                try {
                    block = ChunkPacketBlockControllerAntiXray.getBlock(id);
                } catch (final Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to parse ray-trace-block: " + id,
                            e);
                }

                // Don't obfuscate air because air causes unnecessary block updates and causes
                // block updates to fail in the void
                if (block != null && !block.defaultBlockState().isAir()) {
                    // Replace all block states of a specified block
                    for (final BlockState blockState : block.getStateDefinition().getPossibleStates()) {
                        final int blockStateId = ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(blockState,
                                PaletteResize.noResizeExpected());
                        this.traceGlobal[blockStateId] = true;
                        this.obfuscateGlobal[blockStateId] = true;
                    }
                }
            }
        }

        if (bypassRehideBlocks == null) {
            this.bypassRehideBlocks = null;
        } else {
            this.bypassRehideBlocks = new HashSet<>();

            for (final String id : bypassRehideBlocks) {
                try {
                    final Block block = ChunkPacketBlockControllerAntiXray.getBlock(id);
                    if (block != null) {
                        this.bypassRehideBlocks.add(block);
                    }
                } catch (final Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Failed to parse bypass-rehide-block: " + id, e);
                }
            }
        }

        final EmptyLevelChunk emptyChunk = new EmptyLevelChunk(level,
                new ChunkPos(0, 0),
                MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        final BlockPos zeroPos = new BlockPos(0, 0, 0);

        for (int i = 0; i < this.solidGlobal.length; i++) {
            final BlockState blockState = ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.valueFor(i);

            if (blockState != null) {
                this.blockEntityGlobal[i] = blockState.hasBlockEntity();
                this.solidGlobal[i] = blockState.isRedstoneConductor(emptyChunk, zeroPos)
                        && !blockState.is(Blocks.SPAWNER) && !blockState.is(Blocks.BARRIER)
                        && !blockState.is(Blocks.SHULKER_BOX) && !blockState.is(Blocks.SLIME_BLOCK)
                        && !blockState.is(Blocks.MANGROVE_ROOTS)
                        || paperWorldConfig.lavaObscures && blockState == Blocks.LAVA.defaultBlockState();
                // Comparing blockState == Blocks.LAVA.defaultBlockState() instead of
                // blockState.is(Blocks.LAVA) ensures that only "stationary lava" is used
                // shulker box checks TE.
            }
        }

        this.maxBlockHeightUpdatePosition = this.maxBlockHeight + this.updateRadius - 1;
    }

    public ChunkPacketBlockController getOldController() {
        return this.oldController;
    }

    private int getPresetBlockStatesFullLength() {
        return this.engineMode == EngineMode.HIDE ? 1 : this.presetBlockStatesFull.length;
    }

    @Override
    public BlockState[] getPresetBlockStates(final Level level, final ChunkPos chunkPos, final int chunkSectionY) {
        // Return the block states to be added to the paletted containers so that they
        // can be used for obfuscation
        final int bottomBlockY = chunkSectionY << 4;

        if (bottomBlockY < this.maxBlockHeight) {
            if (this.engineMode == EngineMode.HIDE) {
                return switch (level.getWorld().getEnvironment()) {
                    case NETHER -> this.presetBlockStatesNetherrack;
                    case THE_END -> this.presetBlockStatesEndStone;
                    default -> bottomBlockY < 0 ? this.presetBlockStatesDeepslate : this.presetBlockStatesStone;
                };
            }

            return this.presetBlockStates;
        }

        return null;
    }

    @Override
    public boolean shouldModify(final ServerPlayer player, final LevelChunk chunk) {
        return !this.usePermission || !player.getBukkitEntity().hasPermission("paper.antixray.bypass");
    }

    @Override
    public ChunkPacketInfoAntiXray getChunkPacketInfo(final ClientboundLevelChunkWithLightPacket chunkPacket,
            final LevelChunk chunk) {
        // Return a new instance to collect data and objects in the right state while
        // creating the chunk packet for thread safe access later
        return new ChunkPacketInfoAntiXray(chunkPacket, chunk, this);
    }

    @Override
    public void modifyBlocks(final ClientboundLevelChunkWithLightPacket chunkPacket,
            final ChunkPacketInfo<BlockState> chunkPacketInfo) {
        if (!(chunkPacketInfo instanceof ChunkPacketInfoAntiXray)) {
            chunkPacket.setReady(true);
            return;
        }

        final LevelChunk chunk = chunkPacketInfo.getChunk();
        final int x = chunk.getPos().x;
        final int z = chunk.getPos().z;

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getRegionScheduler().execute(
                    this.plugin,
                    chunk.getLevel().getWorld(),
                    x, z,
                    () -> this.modifyBlocks(chunkPacket, chunkPacketInfo));
            return;
        }

        final Level level = chunk.getLevel();
        ((ChunkPacketInfoAntiXray) chunkPacketInfo).setNearbyChunks(level.getChunkIfLoaded(x - 1, z),
                level.getChunkIfLoaded(x + 1, z), level.getChunkIfLoaded(x, z - 1), level.getChunkIfLoaded(x, z + 1));
        this.executor.execute((Runnable) chunkPacketInfo);
    }

    // Actually these fields should be variables inside the obfuscate method but in
    // sync mode or with SingleThreadExecutor in async mode it's okay (even without
    // ThreadLocal)
    // If an ExecutorService with multiple threads is used, ThreadLocal must be used
    // here
    private final ThreadLocal<int[]> presetBlockStateBits = ThreadLocal
            .withInitial(() -> new int[this.getPresetBlockStatesFullLength()]);
    private static final ThreadLocal<boolean[]> SOLID = ThreadLocal
            .withInitial(() -> new boolean[Block.BLOCK_STATE_REGISTRY.size()]);
    private static final ThreadLocal<boolean[]> OBFUSCATE = ThreadLocal
            .withInitial(() -> new boolean[Block.BLOCK_STATE_REGISTRY.size()]);
    private static final ThreadLocal<boolean[]> TRACE = ThreadLocal
            .withInitial(() -> new boolean[Block.BLOCK_STATE_REGISTRY.size()]);
    private static final ThreadLocal<boolean[]> BLOCK_ENTITY = ThreadLocal
            .withInitial(() -> new boolean[Block.BLOCK_STATE_REGISTRY.size()]);
    // These boolean arrays represent chunk layers, true means don't obfuscate,
    // false means obfuscate
    private static final ThreadLocal<boolean[][]> CURRENT = ThreadLocal.withInitial(() -> new boolean[16][16]);
    private static final ThreadLocal<boolean[][]> NEXT = ThreadLocal.withInitial(() -> new boolean[16][16]);
    private static final ThreadLocal<boolean[][]> NEXT_NEXT = ThreadLocal.withInitial(() -> new boolean[16][16]);
    private static final ThreadLocal<boolean[][]> TRACE_CACHE = ThreadLocal.withInitial(() -> new boolean[16][16]);
    private static final ThreadLocal<boolean[][]> BLOCK_ENTITY_CACHE = ThreadLocal
            .withInitial(() -> new boolean[16][16]);
    private static final Field BLOCK_ENTITIES_DATA_FIELD;
    private static final Field PACKED_X_Z_FIELD;
    private static final Field Y_FIELD;

    static {
        try {
            BLOCK_ENTITIES_DATA_FIELD = ClientboundLevelChunkPacketData.class.getDeclaredField("blockEntitiesData");
            ChunkPacketBlockControllerAntiXray.BLOCK_ENTITIES_DATA_FIELD.setAccessible(true);
            final Class<?> blockEntityInfoClass = Class
                    .forName("net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo");
            PACKED_X_Z_FIELD = blockEntityInfoClass.getDeclaredField("packedXZ");
            ChunkPacketBlockControllerAntiXray.PACKED_X_Z_FIELD.setAccessible(true);
            Y_FIELD = blockEntityInfoClass.getDeclaredField("y");
            ChunkPacketBlockControllerAntiXray.Y_FIELD.setAccessible(true);
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void obfuscate(final ChunkPacketInfoAntiXray chunkPacketInfoAntiXray) {
        // Early validation - skip obfuscation if chunk data is invalid
        // This can happen with other plugins that create chunk packets
        // with incomplete chunk data
        final LevelChunk chunk = chunkPacketInfoAntiXray.getChunk();
        if (chunk == null || chunk.getSections() == null || chunk.getSections().length == 0) {
            chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
            return;
        }

        try {
            // Verify chunk level is accessible
            final Level level = chunk.getLevel();
            if (level == null) {
                chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
                return;
            }
        } catch (final Exception e) {
            // Chunk in invalid state
            chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
            return;
        }

        final int[] presetBlockStateBits = this.presetBlockStateBits.get();
        final boolean[] solid = ChunkPacketBlockControllerAntiXray.SOLID.get();
        final boolean[] obfuscate = ChunkPacketBlockControllerAntiXray.OBFUSCATE.get();
        final boolean[] trace = this.traceGlobal == this.obfuscateGlobal ? obfuscate : ChunkPacketBlockControllerAntiXray.TRACE.get();
        final boolean[] blockEntity = ChunkPacketBlockControllerAntiXray.BLOCK_ENTITY.get();
        boolean[][] current = ChunkPacketBlockControllerAntiXray.CURRENT.get();
        boolean[][] next = ChunkPacketBlockControllerAntiXray.NEXT.get();
        boolean[][] nextNext = ChunkPacketBlockControllerAntiXray.NEXT_NEXT.get();
        final boolean[][] traceCache = ChunkPacketBlockControllerAntiXray.TRACE_CACHE.get();
        final boolean[][] blockEntityCache = ChunkPacketBlockControllerAntiXray.BLOCK_ENTITY_CACHE.get();
        // bitStorageReader, bitStorageWriter and nearbyChunkSections could also be
        // reused (with ThreadLocal if necessary) but it's not worth it
        final BitStorageReader bitStorageReader = new BitStorageReader();
        final BitStorageWriter bitStorageWriter = new BitStorageWriter();
        final LevelChunkSection[] nearbyChunkSections = new LevelChunkSection[4];
        final Level level = chunk.getLevel();
        final int maxChunkSectionIndex = Math.min((this.maxBlockHeight >> 4) - chunk.getMinSectionY(), chunk.getSectionsCount())
                - 1;

        // Validate buffer - Other plugins may provide incomplete data
        final byte[] buffer = chunkPacketInfoAntiXray.getBuffer();
        if (buffer == null || buffer.length == 0) {
            chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
            return;
        }

        boolean[] solidTemp = null;
        boolean[] obfuscateTemp = null;
        boolean[] traceTemp = null;
        boolean[] blockEntityTemp = null;
        bitStorageReader.setBuffer(buffer);
        bitStorageWriter.setBuffer(buffer);
        final int numberOfBlocks = presetBlockStateBits.length;
        // Keep the lambda expressions as simple as possible. They are used very
        // frequently.
        final LayeredIntSupplier random = numberOfBlocks == 1 ? (() -> 0)
                : this.engineMode == EngineMode.OBFUSCATE_LAYER ? new LayeredIntSupplier() {
                    // engine-mode: 3
                    private int state;
                    private int next;

                    {
                        while ((this.state = ThreadLocalRandom.current().nextInt()) == 0)
                            ;
                    }

                    @Override
                    public void nextLayer() {
                        // https://en.wikipedia.org/wiki/Xorshift
                        this.state ^= this.state << 13;
                        this.state ^= this.state >>> 17;
                        this.state ^= this.state << 5;
                        // https://www.pcg-random.org/posts/bounded-rands.html
                        this.next = (int) ((Integer.toUnsignedLong(this.state) * numberOfBlocks) >>> 32);
                    }

                    @Override
                    public int getAsInt() {
                        return this.next;
                    }
                } : new LayeredIntSupplier() {
                    // engine-mode: 2
                    private int state;

                    {
                        while ((this.state = ThreadLocalRandom.current().nextInt()) == 0)
                            ;
                    }

                    @Override
                    public int getAsInt() {
                        // https://en.wikipedia.org/wiki/Xorshift
                        this.state ^= this.state << 13;
                        this.state ^= this.state >>> 17;
                        this.state ^= this.state << 5;
                        // https://www.pcg-random.org/posts/bounded-rands.html
                        return (int) ((Integer.toUnsignedLong(this.state) * numberOfBlocks) >>> 32);
                    }
                };
        final HashMap<BlockPos, Boolean> blocks = new HashMap<>();
        final HashSet<BlockPos> blockEntities = new HashSet<>();

        try {
            for (int chunkSectionIndex = 0; chunkSectionIndex <= maxChunkSectionIndex; chunkSectionIndex++) {
                if (chunkPacketInfoAntiXray.isWritten(chunkSectionIndex)
                        && chunkPacketInfoAntiXray.getPresetValues(chunkSectionIndex) != null) {
                    int[] presetBlockStateBitsTemp;

                    if (chunkPacketInfoAntiXray.getPalette(chunkSectionIndex) instanceof GlobalPalette) {
                        if (this.engineMode == EngineMode.HIDE) {
                            presetBlockStateBitsTemp = switch (level.getWorld().getEnvironment()) {
                                case NETHER -> this.presetBlockStateBitsNetherrackGlobal;
                                case THE_END -> this.presetBlockStateBitsEndStoneGlobal;
                                default -> chunkSectionIndex + chunk.getMinSectionY() < 0
                                                                            ? this.presetBlockStateBitsDeepslateGlobal
                                                                            : this.presetBlockStateBitsStoneGlobal;
                            };
                        } else {
                            presetBlockStateBitsTemp = this.presetBlockStateBitsGlobal;
                        }
                    } else {
                        // If it's presetBlockStates, use this.presetBlockStatesFull instead
                        final BlockState[] presetBlockStatesFull = chunkPacketInfoAntiXray
                                .getPresetValues(chunkSectionIndex) == this.presetBlockStates ? this.presetBlockStatesFull
                                        : chunkPacketInfoAntiXray.getPresetValues(chunkSectionIndex);
                        presetBlockStateBitsTemp = presetBlockStateBits;

                        final Palette<BlockState> palette = chunkPacketInfoAntiXray.getPalette(chunkSectionIndex);
                        boolean hasMissingPresetStates = false;
                        int lastFoundPresetState = -1;
                        for (int i = 0; i < presetBlockStateBitsTemp.length; i++) {
                            int id;
                            try {
                                // if another plugin has modified the palette, it is possible for one or more of
                                // the expected preset states to be missing.
                                id = palette.idFor(presetBlockStatesFull[i], ChunkPacketBlockControllerAntiXray.RESIZE_RETURNS_NEGATIVE_ONE);
                            } catch (final Exception e) {
                                // if this happens, someone is using a custom palette and ignoring the resize
                                // handler
                                if (this.plugin.handleNag(e)) {
                                    this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                                            """
                                                    Unexpected error thrown while reading palette for preset block states for: state=%s section=%d chunk=%s level=%s"""
                                                    .formatted(presetBlockStatesFull[i], chunkSectionIndex,
                                                            chunk.getPos(), level.getWorld().getName()),
                                            e);
                                    this.plugin.logDebugNagReminder();
                                }
                                // skip this chunk as it's likely all palettes will raise the same error
                                chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
                                return;
                            }

                            if (id == -1) {
                                hasMissingPresetStates = true;
                            } else {
                                lastFoundPresetState = id;
                            }
                            presetBlockStateBitsTemp[i] = id;
                        }

                        // replace all missing states with the last found state or skip this section
                        if (hasMissingPresetStates) {
                            if (lastFoundPresetState == -1)
                                continue;
                            for (int i = 0; i < presetBlockStateBitsTemp.length; i++) {
                                if (presetBlockStateBitsTemp[i] == -1)
                                    presetBlockStateBitsTemp[i] = lastFoundPresetState;
                                // set to last known good state for the next missing entry
                                lastFoundPresetState = presetBlockStateBitsTemp[i];
                            }
                        }
                    }

                    bitStorageWriter.setIndex(chunkPacketInfoAntiXray.getIndex(chunkSectionIndex));

                    // Check if the chunk section below was not obfuscated
                    if (chunkSectionIndex == 0 || !chunkPacketInfoAntiXray.isWritten(chunkSectionIndex - 1)
                            || chunkPacketInfoAntiXray.getPresetValues(chunkSectionIndex - 1) == null) {
                        // If so, initialize some stuff
                        bitStorageReader.setBits(chunkPacketInfoAntiXray.getBits(chunkSectionIndex));
                        bitStorageReader.setIndex(chunkPacketInfoAntiXray.getIndex(chunkSectionIndex));
                        solidTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex), solid,
                                this.solidGlobal);
                        obfuscateTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex), obfuscate,
                                this.obfuscateGlobal);
                        traceTemp = trace == obfuscate ? obfuscateTemp
                                : this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex), trace,
                                        this.traceGlobal);
                        blockEntityTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex),
                                blockEntity, this.blockEntityGlobal);
                        // Read the blocks of the upper layer of the chunk section below if it exists
                        LevelChunkSection belowChunkSection = null;
                        final boolean skipFirstLayer = chunkSectionIndex == 0 || (belowChunkSection = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunk,
                                chunkSectionIndex - 1)) == ChunkPacketBlockControllerAntiXray.EMPTY_SECTION;

                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                current[z][x] = true;
                                next[z][x] = skipFirstLayer || this.isTransparent(belowChunkSection, x, 15, z);
                                traceCache[z][x] = false;
                            }
                        }

                        // Abuse the obfuscateLayer method to read the blocks of the first layer of the
                        // current chunk section
                        bitStorageWriter.setBits(0);
                        this.obfuscateLayer(chunk.getPos(), chunk.getMinSectionY(), chunkSectionIndex, -1, bitStorageReader,
                                bitStorageWriter, solidTemp, obfuscateTemp, traceTemp, blockEntityTemp,
                                presetBlockStateBitsTemp, current, next, nextNext, traceCache, blockEntityCache,
                                this.emptyNearbyChunkSections, random, blocks, blockEntities);
                    }

                    bitStorageWriter.setBits(chunkPacketInfoAntiXray.getBits(chunkSectionIndex));
                    nearbyChunkSections[0] = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunkPacketInfoAntiXray.getNearbyChunks()[0],
                            chunkSectionIndex);
                    nearbyChunkSections[1] = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunkPacketInfoAntiXray.getNearbyChunks()[1],
                            chunkSectionIndex);
                    nearbyChunkSections[2] = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunkPacketInfoAntiXray.getNearbyChunks()[2],
                            chunkSectionIndex);
                    nearbyChunkSections[3] = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunkPacketInfoAntiXray.getNearbyChunks()[3],
                            chunkSectionIndex);

                    // Obfuscate all layers of the current chunk section except the upper one
                    for (int y = 0; y < 15; y++) {
                        final boolean[][] temp = current;
                        current = next;
                        next = nextNext;
                        nextNext = temp;
                        random.nextLayer();
                        this.obfuscateLayer(chunk.getPos(), chunk.getMinSectionY(), chunkSectionIndex, y, bitStorageReader,
                                bitStorageWriter, solidTemp, obfuscateTemp, traceTemp, blockEntityTemp,
                                presetBlockStateBitsTemp, current, next, nextNext, traceCache, blockEntityCache,
                                nearbyChunkSections, random, blocks, blockEntities);
                    }

                    // Check if the chunk section above doesn't need obfuscation
                    if (chunkSectionIndex == maxChunkSectionIndex
                            || !chunkPacketInfoAntiXray.isWritten(chunkSectionIndex + 1)
                            || chunkPacketInfoAntiXray.getPresetValues(chunkSectionIndex + 1) == null) {
                        // If so, obfuscate the upper layer of the current chunk section by reading
                        // blocks of the first layer from the chunk section above if it exists
                        final LevelChunkSection aboveChunkSection = ChunkPacketBlockControllerAntiXray.getSectionSafely(chunk, chunkSectionIndex + 1);
                        final boolean aboveChunkSectionEmpty = aboveChunkSection == ChunkPacketBlockControllerAntiXray.EMPTY_SECTION;
                        final boolean[][] temp = current;
                        current = next;
                        next = nextNext;
                        nextNext = temp;

                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (aboveChunkSectionEmpty || this.isTransparent(aboveChunkSection, x, 0, z)) {
                                    current[z][x] = true;
                                }
                            }
                        }

                        // There is nothing to read anymore
                        bitStorageReader.setBits(0);
                        solid[0] = true;
                        random.nextLayer();
                        this.obfuscateLayer(chunk.getPos(), chunk.getMinSectionY(), chunkSectionIndex, 15, bitStorageReader,
                                bitStorageWriter, solid, obfuscateTemp, traceTemp, blockEntityTemp,
                                presetBlockStateBitsTemp, current, next, nextNext, traceCache, blockEntityCache,
                                nearbyChunkSections, random, blocks, blockEntities);
                    } else {
                        // If not, initialize the reader and other stuff for the chunk section above to
                        // obfuscate the upper layer of the current chunk section
                        bitStorageReader.setBits(chunkPacketInfoAntiXray.getBits(chunkSectionIndex + 1));
                        bitStorageReader.setIndex(chunkPacketInfoAntiXray.getIndex(chunkSectionIndex + 1));
                        solidTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex + 1), solid,
                                this.solidGlobal);
                        obfuscateTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex + 1),
                                obfuscate, this.obfuscateGlobal);
                        traceTemp = trace == obfuscate ? obfuscateTemp
                                : this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex + 1), trace,
                                        this.traceGlobal);
                        blockEntityTemp = this.readPalette(chunkPacketInfoAntiXray.getPalette(chunkSectionIndex + 1),
                                blockEntity, this.blockEntityGlobal);
                        final boolean[][] temp = current;
                        current = next;
                        next = nextNext;
                        nextNext = temp;
                        random.nextLayer();
                        this.obfuscateLayer(chunk.getPos(), chunk.getMinSectionY(), chunkSectionIndex, 15, bitStorageReader,
                                bitStorageWriter, solidTemp, obfuscateTemp, traceTemp, blockEntityTemp,
                                presetBlockStateBitsTemp, current, next, nextNext, traceCache, blockEntityCache,
                                nearbyChunkSections, random, blocks, blockEntities);
                    }

                    bitStorageWriter.flush();
                }
            }
        } catch (final Exception e) {
            // Catch any unexpected exceptions during chunk obfuscation
            // This can happen with FartherViewDistance or other plugins that create chunks
            if (this.plugin.handleNag(e)) {
                this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Failed to obfuscate chunk: chunk=%s level=%s".formatted(chunk.getPos(), level), e);
                this.plugin.logDebugNagReminder();
            }
            // Fall through to set the packet as ready anyway
        }

        if (this.plugin.isRunning()) {
            this.plugin.getPacketChunkBlocksCache().put(chunkPacketInfoAntiXray.getChunkPacket(),
                    new ChunkBlocks(chunkPacketInfoAntiXray.getChunk(), blocks));
        }

        if (blockEntities != null && !blockEntities.isEmpty()) {
            try {
                List<?> blockEntitiesData = (List<?>) ChunkPacketBlockControllerAntiXray.BLOCK_ENTITIES_DATA_FIELD
                        .get(chunkPacketInfoAntiXray.getChunkPacket().getChunkData());
                // Skip if blockEntitiesData is null or empty - may happen with
                // FartherViewDistance chunks
                if (blockEntitiesData != null && !blockEntitiesData.isEmpty()) {
                    // ensure mutable and replace when filtered
                    blockEntitiesData = new ArrayList<>(blockEntitiesData);

                    final ChunkPos chunkPos = chunk.getPos();
                    final int minX = chunkPos.getMinBlockX();
                    final int minZ = chunkPos.getMinBlockZ();
                    final MutableBlockPos mutableBlockPos = new MutableBlockPos();

                    blockEntitiesData.removeIf(blockEntityData -> {
                        try {
                            final int packedXZ = ChunkPacketBlockControllerAntiXray.PACKED_X_Z_FIELD.getInt(blockEntityData);
                            return blockEntities.contains(mutableBlockPos.set(minX + (packedXZ >>> 4),
                                    ChunkPacketBlockControllerAntiXray.Y_FIELD.getInt(blockEntityData), minZ + (packedXZ & 15)));
                        } catch (final IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    ChunkPacketBlockControllerAntiXray.BLOCK_ENTITIES_DATA_FIELD.set(chunkPacketInfoAntiXray.getChunkPacket().getChunkData(),
                            blockEntitiesData);
                }
                // TODO: Also remove from
                // chunkPacketInfoAntiXray.getChunkPacket().getExtraPackets(), however, it's
                // unlikely that it contains anything.
            } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (final Exception e) {
                if (this.plugin.handleNag(e)) {
                    this.plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Failed to remove obfuscated block entities from chunk packet for chunk=%s level=%s"
                                    .formatted(chunk.getPos(), level),
                            e);
                    this.plugin.logDebugNagReminder();
                }
            }
        }

        chunkPacketInfoAntiXray.getChunkPacket().setReady(true);
    }

    private void obfuscateLayer(final ChunkPos chunkPos, final int minSectionY, final int chunkSectionIndex, final int y,
            final BitStorageReader bitStorageReader, final BitStorageWriter bitStorageWriter, final boolean[] solid, final boolean[] obfuscate,
            final boolean[] trace, final boolean[] blockEntity, final int[] presetBlockStateBits, final boolean[][] current, final boolean[][] next,
            final boolean[][] nextNext, final boolean[][] traceCache, final boolean[][] blockEntityCache,
            final LevelChunkSection[] nearbyChunkSections, final IntSupplier random, final Map<? super BlockPos, ? super Boolean> blocks,
            final Set<? super BlockPos> blockEntities) {
        final int minX = chunkPos.getMinBlockX();
        final int minZ = chunkPos.getMinBlockZ();
        final int realY = (chunkSectionIndex + minSectionY << 4) + y;
        // First block of first line
        int bits = bitStorageReader.read();

        if (nextNext[0][0] = !solid[bits]) {
            if (traceCache[0][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                final BlockPos block = new BlockPos(minX + 0, realY, minZ + 0);
                blocks.put(block, true);

                if (blockEntityCache[0][0]) {
                    blockEntities.add(block);
                }
            } else {
                bitStorageWriter.skip();
            }

            next[0][1] = true;
            next[1][0] = true;
        } else {
            if (current[0][0] || this.isTransparent(nearbyChunkSections[2], 0, y, 15)
                    || this.isTransparent(nearbyChunkSections[0], 15, y, 0)) {
                if (traceCache[0][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 0, realY, minZ + 0);
                    blocks.put(block, true);

                    if (blockEntityCache[0][0]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }
            } else {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                if (blockEntityCache[0][0]) {
                    blockEntities.add(new BlockPos(minX + 0, realY, minZ + 0));
                }
            }
        }

        if (trace[bits]) {
            traceCache[0][0] = true;
            blockEntityCache[0][0] = blockEntity[bits];
        } else {
            traceCache[0][0] = false;

            if (!obfuscate[bits]) {
                next[0][0] = true;
            } else {
                blockEntityCache[0][0] = blockEntity[bits];
            }
        }

        // First line
        for (int x = 1; x < 15; x++) {
            bits = bitStorageReader.read();

            if (nextNext[0][x] = !solid[bits]) {
                if (traceCache[0][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + x, realY, minZ + 0);
                    blocks.put(block, true);

                    if (blockEntityCache[0][x]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }

                next[0][x - 1] = true;
                next[0][x + 1] = true;
                next[1][x] = true;
            } else {
                if (current[0][x] || this.isTransparent(nearbyChunkSections[2], x, y, 15)) {
                    if (traceCache[0][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                        final BlockPos block = new BlockPos(minX + x, realY, minZ + 0);
                        blocks.put(block, true);

                        if (blockEntityCache[0][x]) {
                            blockEntities.add(block);
                        }
                    } else {
                        bitStorageWriter.skip();
                    }
                } else {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                    if (blockEntityCache[0][x]) {
                        blockEntities.add(new BlockPos(minX + x, realY, minZ + 0));
                    }
                }
            }

            if (trace[bits]) {
                traceCache[0][x] = true;
                blockEntityCache[0][x] = blockEntity[bits];
            } else {
                traceCache[0][x] = false;

                if (!obfuscate[bits]) {
                    next[0][x] = true;
                } else {
                    blockEntityCache[0][x] = blockEntity[bits];
                }
            }
        }

        // Last block of first line
        bits = bitStorageReader.read();

        if (nextNext[0][15] = !solid[bits]) {
            if (traceCache[0][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                final BlockPos block = new BlockPos(minX + 15, realY, minZ + 0);
                blocks.put(block, true);

                if (blockEntityCache[0][15]) {
                    blockEntities.add(block);
                }
            } else {
                bitStorageWriter.skip();
            }

            next[0][14] = true;
            next[1][15] = true;
        } else {
            if (current[0][15] || this.isTransparent(nearbyChunkSections[2], 15, y, 15)
                    || this.isTransparent(nearbyChunkSections[1], 0, y, 0)) {
                if (traceCache[0][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 15, realY, minZ + 0);
                    blocks.put(block, true);

                    if (blockEntityCache[0][15]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }
            } else {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                if (blockEntityCache[0][15]) {
                    blockEntities.add(new BlockPos(minX + 15, realY, minZ + 0));
                }
            }
        }

        if (trace[bits]) {
            traceCache[0][15] = true;
            blockEntityCache[0][15] = blockEntity[bits];
        } else {
            traceCache[0][15] = false;

            if (!obfuscate[bits]) {
                next[0][15] = true;
            } else {
                blockEntityCache[0][15] = blockEntity[bits];
            }
        }

        // All inner lines
        for (int z = 1; z < 15; z++) {
            // First block
            bits = bitStorageReader.read();

            if (nextNext[z][0] = !solid[bits]) {
                if (traceCache[z][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 0, realY, minZ + z);
                    blocks.put(block, true);

                    if (blockEntityCache[z][0]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }

                next[z][1] = true;
                next[z - 1][0] = true;
                next[z + 1][0] = true;
            } else {
                if (current[z][0] || this.isTransparent(nearbyChunkSections[0], 15, y, z)) {
                    if (traceCache[z][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                        final BlockPos block = new BlockPos(minX + 0, realY, minZ + z);
                        blocks.put(block, true);

                        if (blockEntityCache[z][0]) {
                            blockEntities.add(block);
                        }
                    } else {
                        bitStorageWriter.skip();
                    }
                } else {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                    if (blockEntityCache[z][0]) {
                        blockEntities.add(new BlockPos(minX + 0, realY, minZ + z));
                    }
                }
            }

            if (trace[bits]) {
                traceCache[z][0] = true;
                blockEntityCache[z][0] = blockEntity[bits];
            } else {
                traceCache[z][0] = false;

                if (!obfuscate[bits]) {
                    next[z][0] = true;
                } else {
                    blockEntityCache[z][0] = blockEntity[bits];
                }
            }

            // All inner blocks
            for (int x = 1; x < 15; x++) {
                bits = bitStorageReader.read();

                if (nextNext[z][x] = !solid[bits]) {
                    if (traceCache[z][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                        final BlockPos block = new BlockPos(minX + x, realY, minZ + z);
                        blocks.put(block, true);

                        if (blockEntityCache[z][x]) {
                            blockEntities.add(block);
                        }
                    } else {
                        bitStorageWriter.skip();
                    }

                    next[z][x - 1] = true;
                    next[z][x + 1] = true;
                    next[z - 1][x] = true;
                    next[z + 1][x] = true;
                } else {
                    if (current[z][x]) {
                        if (traceCache[z][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                            bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                            final BlockPos block = new BlockPos(minX + x, realY, minZ + z);
                            blocks.put(block, true);

                            if (blockEntityCache[z][x]) {
                                blockEntities.add(block);
                            }
                        } else {
                            bitStorageWriter.skip();
                        }
                    } else {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                        if (blockEntityCache[z][x]) {
                            blockEntities.add(new BlockPos(minX + x, realY, minZ + z));
                        }
                    }
                }

                if (trace[bits]) {
                    traceCache[z][x] = true;
                    blockEntityCache[z][x] = blockEntity[bits];
                } else {
                    traceCache[z][x] = false;

                    if (!obfuscate[bits]) {
                        next[z][x] = true;
                    } else {
                        blockEntityCache[z][x] = blockEntity[bits];
                    }
                }
            }

            // Last block
            bits = bitStorageReader.read();

            if (nextNext[z][15] = !solid[bits]) {
                if (traceCache[z][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 15, realY, minZ + z);
                    blocks.put(block, true);

                    if (blockEntityCache[z][15]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }

                next[z][14] = true;
                next[z - 1][15] = true;
                next[z + 1][15] = true;
            } else {
                if (current[z][15] || this.isTransparent(nearbyChunkSections[1], 0, y, z)) {
                    if (traceCache[z][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                        final BlockPos block = new BlockPos(minX + 15, realY, minZ + z);
                        blocks.put(block, true);

                        if (blockEntityCache[z][15]) {
                            blockEntities.add(block);
                        }
                    } else {
                        bitStorageWriter.skip();
                    }
                } else {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                    if (blockEntityCache[z][15]) {
                        blockEntities.add(new BlockPos(minX + 15, realY, minZ + z));
                    }
                }
            }

            if (trace[bits]) {
                traceCache[z][15] = true;
                blockEntityCache[z][15] = blockEntity[bits];
            } else {
                traceCache[z][15] = false;

                if (!obfuscate[bits]) {
                    next[z][15] = true;
                } else {
                    blockEntityCache[z][15] = blockEntity[bits];
                }
            }
        }

        // First block of last line
        bits = bitStorageReader.read();

        if (nextNext[15][0] = !solid[bits]) {
            if (traceCache[15][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                final BlockPos block = new BlockPos(minX + 0, realY, minZ + 15);
                blocks.put(block, true);

                if (blockEntityCache[15][0]) {
                    blockEntities.add(block);
                }
            } else {
                bitStorageWriter.skip();
            }

            next[15][1] = true;
            next[14][0] = true;
        } else {
            if (current[15][0] || this.isTransparent(nearbyChunkSections[3], 0, y, 0)
                    || this.isTransparent(nearbyChunkSections[0], 15, y, 15)) {
                if (traceCache[15][0] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 0, realY, minZ + 15);
                    blocks.put(block, true);

                    if (blockEntityCache[15][0]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }
            } else {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                if (blockEntityCache[15][0]) {
                    blockEntities.add(new BlockPos(minX + 0, realY, minZ + 15));
                }
            }
        }

        if (trace[bits]) {
            traceCache[15][0] = true;
            blockEntityCache[15][0] = blockEntity[bits];
        } else {
            traceCache[15][0] = false;

            if (!obfuscate[bits]) {
                next[15][0] = true;
            } else {
                blockEntityCache[15][0] = blockEntity[bits];
            }
        }

        // Last line
        for (int x = 1; x < 15; x++) {
            bits = bitStorageReader.read();

            if (nextNext[15][x] = !solid[bits]) {
                if (traceCache[15][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + x, realY, minZ + 15);
                    blocks.put(block, true);

                    if (blockEntityCache[15][x]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }

                next[15][x - 1] = true;
                next[15][x + 1] = true;
                next[14][x] = true;
            } else {
                if (current[15][x] || this.isTransparent(nearbyChunkSections[3], x, y, 0)) {
                    if (traceCache[15][x] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                        bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                        final BlockPos block = new BlockPos(minX + x, realY, minZ + 15);
                        blocks.put(block, true);

                        if (blockEntityCache[15][x]) {
                            blockEntities.add(block);
                        }
                    } else {
                        bitStorageWriter.skip();
                    }
                } else {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                    if (blockEntityCache[15][x]) {
                        blockEntities.add(new BlockPos(minX + x, realY, minZ + 15));
                    }
                }
            }

            if (trace[bits]) {
                traceCache[15][x] = true;
                blockEntityCache[15][x] = blockEntity[bits];
            } else {
                traceCache[15][x] = false;

                if (!obfuscate[bits]) {
                    next[15][x] = true;
                } else {
                    blockEntityCache[15][x] = blockEntity[bits];
                }
            }
        }

        // Last block of last line
        bits = bitStorageReader.read();

        if (nextNext[15][15] = !solid[bits]) {
            if (traceCache[15][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                final BlockPos block = new BlockPos(minX + 15, realY, minZ + 15);
                blocks.put(block, true);

                if (blockEntityCache[15][15]) {
                    blockEntities.add(block);
                }
            } else {
                bitStorageWriter.skip();
            }

            next[15][14] = true;
            next[14][15] = true;
        } else {
            if (current[15][15] || this.isTransparent(nearbyChunkSections[3], 15, y, 0)
                    || this.isTransparent(nearbyChunkSections[1], 0, y, 15)) {
                if (traceCache[15][15] && blocks.size() < this.maxRayTraceBlockCountPerChunk) {
                    bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Exposed to air
                    final BlockPos block = new BlockPos(minX + 15, realY, minZ + 15);
                    blocks.put(block, true);

                    if (blockEntityCache[15][15]) {
                        blockEntities.add(block);
                    }
                } else {
                    bitStorageWriter.skip();
                }
            } else {
                bitStorageWriter.write(presetBlockStateBits[random.getAsInt()]); // Not exposed to air

                if (blockEntityCache[15][15]) {
                    blockEntities.add(new BlockPos(minX + 15, realY, minZ + 15));
                }
            }
        }

        if (trace[bits]) {
            traceCache[15][15] = true;
            blockEntityCache[15][15] = blockEntity[bits];
        } else {
            traceCache[15][15] = false;

            if (!obfuscate[bits]) {
                next[15][15] = true;
            } else {
                blockEntityCache[15][15] = blockEntity[bits];
            }
        }
    }

    /**
     * Safely get a chunk section from a chunk, returning EMPTY_SECTION if the chunk
     * is null,
     * if the sections array is null, or if the index is out of bounds.
     * This prevents crashes when nearby chunks have different section counts,
     * which can happen with FartherViewDistance or when chunks are partially
     * loaded.
     */
    private static LevelChunkSection getSectionSafely(final LevelChunk chunk, final int sectionIndex) {
        if (chunk == null) {
            return ChunkPacketBlockControllerAntiXray.EMPTY_SECTION;
        }
        final LevelChunkSection[] sections = chunk.getSections();
        if (sections == null || sectionIndex < 0 || sectionIndex >= sections.length) {
            return ChunkPacketBlockControllerAntiXray.EMPTY_SECTION;
        }
        return sections[sectionIndex];
    }

    private boolean isTransparent(final LevelChunkSection chunkSection, final int x, final int y, final int z) {
        if (chunkSection == ChunkPacketBlockControllerAntiXray.EMPTY_SECTION) {
            return true;
        }

        try {
            return !this.solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(chunkSection.getBlockState(x, y, z),
                    PaletteResize.noResizeExpected())];
        } catch (final MissingPaletteEntryException e) {
            // Race condition / visibility issue / no happens-before relationship
            // We don't care and treat the block as transparent
            // Internal implementation details of PalettedContainer, LinearPalette,
            // HashMapPalette, CrudeIncrementalIntIdentityHashBiMap, ... guarantee us that
            // no (other) exceptions will occur
            return true;
        }
    }

    private boolean[] readPalette(final Palette<BlockState> palette, final boolean[] temp, final boolean[] global) {
        if (palette instanceof GlobalPalette) {
            return global;
        }

        try {
            for (int i = 0; i < palette.getSize(); i++) {
                temp[i] = global[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(palette.valueFor(i),
                        PaletteResize.noResizeExpected())];
            }
        } catch (final MissingPaletteEntryException e) {
            // Race condition / visibility issue / no happens-before relationship
            // We don't care because we at least see the state as it was when the chunk
            // packet was created
            // Internal implementation details of PalettedContainer, LinearPalette,
            // HashMapPalette, CrudeIncrementalIntIdentityHashBiMap, ... guarantee us that
            // no (other) exceptions will occur until we have all the data that we need here
            // Since all palettes have a fixed initial maximum size and there is no internal
            // restructuring and no values are removed from palettes, we are also guaranteed
            // to see the data
        }

        return temp;
    }

    @Override
    public void onBlockChange(final Level level, final BlockPos blockPos, final BlockState newBlockState, final BlockState oldBlockState,
            @Block.UpdateFlags final int flags, final int maxUpdateDepth) {
        if (oldBlockState != null
                && this.solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(oldBlockState, PaletteResize.noResizeExpected())]
                && !this.solidGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(newBlockState, PaletteResize.noResizeExpected())]
                && blockPos.getY() <= this.maxBlockHeightUpdatePosition) {
            this.updateNearbyBlocks(level, blockPos);
        }
    }

    @Override
    public void onPlayerLeftClickBlock(final ServerPlayerGameMode serverPlayerGameMode, final BlockPos blockPos,
            final ServerboundPlayerActionPacket.Action action, final Direction direction, final int worldHeight, final int sequence) {
        if (blockPos.getY() <= this.maxBlockHeightUpdatePosition) {
            this.updateNearbyBlocks(serverPlayerGameMode.level, blockPos);
        }
    }

    private void updateNearbyBlocks(final Level level, final BlockPos blockPos) {
        if (this.updateRadius >= 2) {
            BlockPos temp = blockPos.west();
            this.updateBlock(level, temp);
            this.updateBlock(level, temp.west());
            this.updateBlock(level, temp.below());
            this.updateBlock(level, temp.above());
            this.updateBlock(level, temp.north());
            this.updateBlock(level, temp.south());
            this.updateBlock(level, temp = blockPos.east());
            this.updateBlock(level, temp.east());
            this.updateBlock(level, temp.below());
            this.updateBlock(level, temp.above());
            this.updateBlock(level, temp.north());
            this.updateBlock(level, temp.south());
            this.updateBlock(level, temp = blockPos.below());
            this.updateBlock(level, temp.below());
            this.updateBlock(level, temp.north());
            this.updateBlock(level, temp.south());
            this.updateBlock(level, temp = blockPos.above());
            this.updateBlock(level, temp.above());
            this.updateBlock(level, temp.north());
            this.updateBlock(level, temp.south());
            this.updateBlock(level, temp = blockPos.north());
            this.updateBlock(level, temp.north());
            this.updateBlock(level, temp = blockPos.south());
            this.updateBlock(level, temp.south());
        } else if (this.updateRadius == 1) {
            this.updateBlock(level, blockPos.west());
            this.updateBlock(level, blockPos.east());
            this.updateBlock(level, blockPos.below());
            this.updateBlock(level, blockPos.above());
            this.updateBlock(level, blockPos.north());
            this.updateBlock(level, blockPos.south());
        } else {
            // Do nothing if updateRadius <= 0 (test mode)
        }
    }

    private void updateBlock(final Level level, final BlockPos blockPos) {
        final BlockState blockState = level.getBlockStateIfLoaded(blockPos);

        if (blockState != null
                && this.obfuscateGlobal[ChunkPacketBlockControllerAntiXray.GLOBAL_BLOCKSTATE_PALETTE.idFor(blockState, PaletteResize.noResizeExpected())]) {
            ((ServerLevel) level).getChunkSource().blockChanged(blockPos);
        }
    }

    @FunctionalInterface
    private interface LayeredIntSupplier extends IntSupplier {
        default void nextLayer() {

        }
    }

    private static Block getBlock(final String id) throws Exception {
        Class<?> keyClass;
        try {
            keyClass = Class.forName("net.minecraft.resources.ResourceLocation");
        } catch (final ClassNotFoundException e) {
            keyClass = Class.forName("net.minecraft.resources.Identifier");
        }

        Object key;
        try {
            final java.lang.reflect.Method parseMethod = keyClass.getMethod("parse", String.class);
            key = parseMethod.invoke(null, id);
        } catch (final NoSuchMethodException e) {
            final java.lang.reflect.Constructor<?> ctor = keyClass.getConstructor(String.class);
            key = ctor.newInstance(id);
        }

        final java.lang.reflect.Method getOptionalMethod = BuiltInRegistries.BLOCK.getClass().getMethod("getOptional",
                keyClass);
        final java.util.Optional<?> optional = (java.util.Optional<?>) getOptionalMethod.invoke(BuiltInRegistries.BLOCK, key);

        return (Block) optional.orElse(null);
    }
}
