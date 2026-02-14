package com.vanillage.raytraceantixray.data;

import com.vanillage.raytraceantixray.net.DuplexPacketHandler;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Mutable per-player state shared across packet thread, async workers and sync update task.
 * <p>
 * Threading notes:
 * <ul>
 * <li>{@code chunks} and {@code results} are lock-free concurrent structures.</li>
 * <li>{@code locations} is volatile and can be swapped atomically.</li>
 * <li>{@code callable} is intentionally nullable to allow safe no-op behavior during rebind.</li>
 * </ul>
 */
public final class PlayerData implements Callable<Void> {

    private final ConcurrentMap<LongWrapper, ChunkBlocks> chunks = new ConcurrentHashMap<>();
    private final Queue<Result> results = new ConcurrentLinkedQueue<>();
    private Callable<Void> callable;
    private DuplexPacketHandler packetHandler;
    private volatile VectorialLocation[] locations;
    private volatile int rayTraceIntervalTicks = 4;

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

    public Callable<Void> getCallable() {
        return this.callable;
    }

    public void setCallable(final Callable<Void> callable) {
        this.callable = callable;
    }

    public DuplexPacketHandler getPacketHandler() {
        return this.packetHandler;
    }

    public void setPacketHandler(final DuplexPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    public int getRayTraceIntervalTicks() {
        return this.rayTraceIntervalTicks;
    }

    public void setRayTraceIntervalTicks(final int rayTraceIntervalTicks) {
        this.rayTraceIntervalTicks = Math.max(rayTraceIntervalTicks, 4);
    }

    /**
     * Delegates execution to the currently bound player callable.
     * <p>
     * Returning {@code null} on a temporarily missing callable avoids hard failures during
     * player-data replacement races.
     */
    @Override
    public Void call() throws Exception {
        if (this.callable == null) {
            return null;
        }
        return this.callable.call();
    }

}
