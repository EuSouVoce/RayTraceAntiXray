package com.vanillage.raytraceantixray.net;

import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.ChunkBlocks;
import com.vanillage.raytraceantixray.data.LongWrapper;
import com.vanillage.raytraceantixray.data.PlayerData;
import com.vanillage.raytraceantixray.util.DuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.HashMap;

/**
 * Netty duplex handler responsible for tracking chunk packet lifecycle per player.
 * <p>
 * Threading model:
 * <ul>
 * <li>Runs on the Netty channel thread.</li>
 * <li>Must avoid expensive world lookups and Bukkit state mutations that require main-thread affinity.</li>
 * <li>Only touches lock-free player-local caches ({@link PlayerData#getChunks()}).</li>
 * </ul>
 * <p>
 * The defensive checks in this class intentionally prefer dropping stale packet information over
 * trying to recover aggressively from a possibly inconsistent state. This prevents hard crashes and
 * avoids leaking world A data into world B after teleports/respawns.
 */
public class DuplexPacketHandler extends DuplexHandler {

    public static final String NAME = "com.vanillage.raytraceantixray:duplex_handler";

    private final RayTraceAntiXray plugin;
    private final Player player;

    public DuplexPacketHandler(final RayTraceAntiXray plugin, final Player player) {
        super(DuplexPacketHandler.NAME);
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (this.handle(ctx, msg, promise)) {
            super.write(ctx, msg, promise);
        }
    }

    /**
     * Processes outgoing packet events and updates player-local ray-trace chunk cache.
     * <p>
     * The method is intentionally non-blocking and conservative:
     * <ul>
     * <li>Returns early on missing/stale references.</li>
     * <li>Never forces chunk/world loads from Netty thread.</li>
     * <li>Drops stale context on world mismatch to prevent cross-world contamination.</li>
     * </ul>
     *
     * @return always {@code true} to keep packet pipeline flow uninterrupted.
     */
    public boolean handle(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        if (msg instanceof final ClientboundLevelChunkWithLightPacket packet) {
            // A player data instance is always bound to a world and defines what is to be
            // calculated.
            // Apart from the join and quit event, this is the only place that defines the
            // world of a player by renewing the player data instance.
            // In principle, we could (additionally) renew the player data instance anywhere
            // else if we detect a world change (e.g. move event, changed world event, ...).
            // However, Anti-Xray specifies what is to be calculated via the chunk packet.
            // Since Anti-Xray is async, chunk packets can be delayed.
            // (The packet order of all packets is still being preserved and consistent.)
            // This could for example lead to the following order of events:
            // (1) Chunk packet event of world A.
            // (2) Changed world event from world A to B.
            // (3) Chunk packet event of world A.
            // (4) Chunk packet event of world B.
            // This would lead to unnecessarily renewing the player data instance multiple
            // times in the worst case.
            // Therefore, we only renew the player data instance here.
            // For similar reasons, we also handle chunk unloads via packet events below.
            // Everywhere else we have to check if the player's world still matches the
            // world of the player data instance before we use it.
            // (See for example the move event.)
            // Get the result from Anti-Xray for the current chunk packet.
            // We can't remove the entry because the same chunk packet can be sent to
            // multiple players.
            // The garbage collector will remove the entry later since we're using a weak
            // key map.
            ChunkBlocks chunkBlocks = this.plugin.getPacketChunkBlocksCache().get(packet);

            if (chunkBlocks == null) {
                // RayTraceAntiXray is probably not enabled in this world (or other plugins
                // bypass Anti-Xray). Avoid touching Bukkit world/location APIs on Netty thread.
                return true;
            }

            // Get chunk from weak reference.
            final LevelChunk chunk = chunkBlocks.getChunk();

            if (chunk == null) {
                // The chunk has already been unloaded and garbage collected.
                // A chunk unload packet will probably follow.
                // We can ignore this chunk packet.
                return true;
            }

            final CraftWorld world = chunk.getLevel().getWorld();
            final PlayerData playerData = this.plugin.getPlayerData().get(this.player.getUniqueId());
            if (playerData == null) {
                return true;
            }

            final var locations = playerData.getLocations();
            if (locations == null || locations.length == 0 || locations[0] == null) {
                return true;
            }

            if (!world.equals(locations[0].getWorld())) {
                // Stale packet from previous world context; keep state consistent.
                playerData.getChunks().clear();
                return true;
            }

            // We need to copy the chunk blocks because the same chunk packet could have
            // been sent to multiple players.
            chunkBlocks = new ChunkBlocks(chunk, new HashMap<>(chunkBlocks.getBlocks()));
            playerData.getChunks().put(chunkBlocks.getKey(), chunkBlocks);
        } else if (msg instanceof final ClientboundForgetLevelChunkPacket packet) {
            // Note that chunk unload packets aren't sent on world change and on respawn.
            // World changes are already handled above.
            // Technically removing chunks isn't necessary since we're using a weak
            // reference to the chunk.
            final PlayerData playerData = this.plugin.getPlayerData().get(this.player.getUniqueId());
            if (playerData != null) {
                playerData.getChunks().remove(new LongWrapper(packet.pos().toLong()));
            }
        } else if (msg instanceof ClientboundRespawnPacket) {
            // As with world changes, chunk unload packets aren't sent on respawn.
            // All required chunks are (re)sent afterwards.
            // Thus we clear the chunks.
            // If respawning involves a world change, it will be handled in the next chunk
            // packet event.
            final PlayerData playerData = this.plugin.getPlayerData().get(this.player.getUniqueId());
            if (playerData != null) {
                playerData.getChunks().clear();
            }
        }
        return true;
    }

}
