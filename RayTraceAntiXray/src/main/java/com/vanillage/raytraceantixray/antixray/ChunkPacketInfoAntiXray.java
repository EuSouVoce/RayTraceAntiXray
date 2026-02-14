package com.vanillage.raytraceantixray.antixray;

import io.papermc.paper.antixray.ChunkPacketInfo;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkPacketInfoAntiXray extends ChunkPacketInfo<BlockState> implements Runnable {

    private final ChunkPacketBlockControllerAntiXray chunkPacketBlockControllerAntiXray;
    private LevelChunk[] nearbyChunks;

    public ChunkPacketInfoAntiXray(final ClientboundLevelChunkWithLightPacket chunkPacket, final LevelChunk chunk,
            final ChunkPacketBlockControllerAntiXray chunkPacketBlockControllerAntiXray) {
        super(chunkPacket, chunk);
        this.chunkPacketBlockControllerAntiXray = chunkPacketBlockControllerAntiXray;
    }

    public LevelChunk[] getNearbyChunks() {
        return this.nearbyChunks;
    }

    public void setNearbyChunks(final LevelChunk... nearbyChunks) {
        this.nearbyChunks = nearbyChunks;
    }

    @Override
    public void run() {
        this.chunkPacketBlockControllerAntiXray.obfuscate(this);
    }
}
