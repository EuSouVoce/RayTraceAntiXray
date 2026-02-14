package com.vanillage.raytraceantixray.data;

import com.vanillage.raytraceantixray.net.DuplexPacketHandler;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public final class PlayerData implements Callable<Object> {

    private final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = new ConcurrentHashMap<>();
    private final Queue<Result> results = new ConcurrentLinkedQueue<>();
    private Callable<?> callable;
    private DuplexPacketHandler packetHandler;
    private volatile VectorialLocation[] locations;

    public PlayerData(final VectorialLocation[] locations) {
        this.locations = locations;
    }

    public VectorialLocation[] getLocations() {
        return this.locations;
    }

    public void setLocations(final VectorialLocation[] locations) {
        this.locations = locations;
    }

    public ConcurrentMap<LongWrapper, ChunkBlocks> getChunks() {
        return this.chunks;
    }

    public Queue<Result> getResults() {
        return this.results;
    }

    public Callable<?> getCallable() {
        return this.callable;
    }

    public void setCallable(final Callable<?> callable) {
        this.callable = callable;
    }

    public DuplexPacketHandler getPacketHandler() {
        return this.packetHandler;
    }

    public void setPacketHandler(final DuplexPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Override
    public Object call() throws Exception {
        return this.callable.call();
    }

}
