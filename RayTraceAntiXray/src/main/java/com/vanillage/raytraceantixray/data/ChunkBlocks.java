package com.vanillage.raytraceantixray.data;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ChunkBlocks {
    private final Reference<LevelChunk> chunk;
    private final LongWrapper key;
    private final Map<BlockPos, Boolean> blocks;

    public ChunkBlocks(final LevelChunk chunk, final Map<BlockPos, Boolean> blocks) {
        this.chunk = new WeakReference<>(chunk);
        this.key = new LongWrapper(chunk.getPos().toLong());
        this.blocks = blocks;
    }

    public LevelChunk getChunk() {
        return this.chunk.get();
    }

    public LongWrapper getKey() {
        return this.key;
    }

    public Map<BlockPos, Boolean> getBlocks() {
        return this.blocks;
    }
}
