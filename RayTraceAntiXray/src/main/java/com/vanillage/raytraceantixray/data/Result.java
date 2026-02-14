package com.vanillage.raytraceantixray.data;

import net.minecraft.core.BlockPos;

public final class Result {
    private final ChunkBlocks chunkBlocks;
    private final BlockPos block;
    private final boolean visible;

    public Result(final ChunkBlocks chunkBlocks, final BlockPos block, final boolean visible) {
        this.chunkBlocks = chunkBlocks;
        this.block = block;
        this.visible = visible;
    }

    public ChunkBlocks getChunkBlocks() {
        return this.chunkBlocks;
    }

    public BlockPos getBlock() {
        return this.block;
    }

    public boolean isVisible() {
        return this.visible;
    }
}
