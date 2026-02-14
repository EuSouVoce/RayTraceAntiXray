package com.vanillage.raytraceantixray.tasks;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.*;
import io.netty.channel.Channel;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Queue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Main-thread update dispatcher that transforms async ray-trace results into packet updates.
 * <p>
 * This runnable acts as a synchronization boundary between:
 * <ul>
 * <li>async ray-trace computation queues, and</li>
 * <li>main-thread world/chunk access and packet emission.</li>
 * </ul>
 * <p>
 * Defensive checks are intentionally redundant: they absorb race windows where the player changes
 * world, chunks get unloaded, or player data is replaced between enqueue and flush.
 */
public final class UpdateBukkitRunnable extends BukkitRunnable implements Consumer<ScheduledTask> {
    private final RayTraceAntiXray plugin;
    private final Player player;

    public UpdateBukkitRunnable(final RayTraceAntiXray plugin) {
        this(plugin, null);
    }

    public UpdateBukkitRunnable(final RayTraceAntiXray plugin, final Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void run() {
        if (this.player == null) {
            for (final Player p : this.plugin.getServer().getOnlinePlayers()) {
                this.update(p);
            }
        } else {
            this.update(this.player);
        }
    }

    @Override
    public void accept(final ScheduledTask t) {
        this.run();
    }

    /**
     * Flushes pending block visibility results for a specific player.
     * <p>
     * Why this method is defensive:
     * <ul>
     * <li>Player data can be replaced while this runnable is iterating.</li>
     * <li>Chunks referenced by queued results may already be stale or unloaded.</li>
     * <li>World transitions may happen between async calculation and sync send.</li>
     * </ul>
     * In all such cases, the method exits or skips safely without throwing.
     */
    public void update(final Player player) {
        final PlayerData playerData = this.plugin.getPlayerData().get(player.getUniqueId());
        if (playerData == null)
            return; // NPCs don't get added to the playerdata map.

        final VectorialLocation[] locations = playerData.getLocations();
        if (locations == null || locations.length == 0 || locations[0] == null) {
            return;
        }

        final World world = locations[0].getWorld();
        if (world == null) {
            return;
        }

        if (!player.getWorld().equals(world)) {
            this.plugin.createPlayerDataFor(player, player.getEyeLocation());
            return;
        }

        final Location loc = player.getLocation();
        final Vector vec = loc.toVector();
        vec.setY(vec.getY() + player.getEyeHeight());
        final VectorialLocation vecLoc = new VectorialLocation(world, vec, loc.getDirection());
        playerData.setLocations(RayTraceAntiXray.getLocations(player, vecLoc));

        final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = playerData.getChunks();
        final ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        final Environment environment = world.getEnvironment();
        final Queue<Result> results = playerData.getResults();
        Result result;

        while ((result = results.poll()) != null) {
            if (result.getChunkBlocks() == null || result.getBlock() == null) {
                continue;
            }

            final ChunkBlocks chunkBlocks = result.getChunkBlocks();

            // Check if the client still has the chunk loaded and if it wasn't resent in the
            // meantime.
            // Note that even if this check passes, the server could have already unloaded
            // or resent the chunk but the corresponding packet is still in the packet
            // queue.
            // Technically the null check isn't necessary but we don't need to send an
            // update packet because the client will unload the chunk.
            if (chunkBlocks.getChunk() == null || chunks.get(chunkBlocks.getKey()) != chunkBlocks) {
                continue;
            }

            final BlockPos block = result.getBlock();

            // Similar to the null check above, this check isn't actually necessary.
            // However, we don't need to send an update packet because the client will
            // unload the chunk.
            // Thus we can avoid loading the chunk just for the update packet.
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }

            BlockState blockState;
            BlockEntity blockEntity = null;

            if (result.isVisible()) {
                blockState = serverLevel.getBlockState(block);

                if (blockState.hasBlockEntity()) {
                    blockEntity = serverLevel.getBlockEntity(block);
                }
            } else if (environment == Environment.NETHER) {
                blockState = Blocks.NETHERRACK.defaultBlockState();
            } else if (environment == Environment.THE_END) {
                blockState = Blocks.END_STONE.defaultBlockState();
            } else if (block.getY() < 0) {
                blockState = Blocks.DEEPSLATE.defaultBlockState();
            } else {
                blockState = Blocks.STONE.defaultBlockState();
            }

            // We can't send the packet normally (through the packet queue).
            // We bypass the packet queue since our calculations are based on the packet
            // state (not the server state) as seen by the packet listener.
            // As described above, the packet queue could for example already contain a
            // chunk unload packet.
            // Thus we send our packet immediately before that.
            UpdateBukkitRunnable.sendPacketImmediately(player, new ClientboundBlockUpdatePacket(block, blockState));

            if (blockEntity != null) {
                final Packet<ClientGamePacketListener> packet = blockEntity.getUpdatePacket();

                if (packet != null) {
                    UpdateBukkitRunnable.sendPacketImmediately(player, packet);
                }
            }
        }
    }

    /**
     * Sends a packet directly through the channel to preserve ordering assumptions relative to
     * already queued chunk packets.
     * <p>
     * Returns {@code false} if the connection/channel is no longer valid.
     */
    private static boolean sendPacketImmediately(final Player player, final Object packet) {
        final ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;

        if (connection == null || connection.processedDisconnect) {
            return false;
        }

        final Channel channel = connection.connection.channel;

        if (channel == null || !channel.isOpen()) {
            return false;
        }

        channel.writeAndFlush(packet);
        return true;
    }
}
