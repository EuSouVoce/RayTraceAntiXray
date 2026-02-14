package com.vanillage.raytraceantixray.tasks;

import com.google.common.base.Stopwatch;
import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.PlayerData;
import com.vanillage.raytraceantixray.util.TimeFormatter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Schedules and synchronizes per-player ray-trace jobs.
 * <p>
 * This timer task only orchestrates work; heavy computation runs inside the worker pool.
 * It also contains shutdown guards so the timer can race safely with plugin disable.
 */
public final class RayTraceTimerTask extends TimerTask {

    private final RayTraceAntiXray plugin;
    private final Stopwatch watch = Stopwatch.createUnstarted();
    private long timerRuns;
    private Instant lastNotify = Instant.MIN;

    public RayTraceTimerTask(final RayTraceAntiXray plugin) {
        this.plugin = plugin;
    }

    /**
     * Dispatches one logical ray-trace tick to the worker pool.
     * <p>
     * The method snapshots callables from the concurrent player map and waits for completion, which
     * guarantees non-overlapping logical ticks.
     */
    @Override
    public void run() {
        try {
            if (!this.plugin.isRunning() || this.plugin.getExecutorService() == null) {
                return;
            }

            if (this.plugin.isTimingsEnabled()) {
                this.watch.start();
            } else if (this.watch.isRunning()) {
                this.watch.reset();
                this.timerRuns = 0;
                this.lastNotify = Instant.MIN;
            }

            final List<Callable<Void>> tasks = this.plugin.getPlayerData().values().stream()
                    .map(PlayerData::getCallable)
                    .filter(Objects::nonNull)
                    .toList();
            this.plugin.getExecutorService().invokeAll(tasks);

            if (this.watch.isRunning()) {
                this.watch.stop();
                this.timerRuns++;
                final long nanoTime = this.watch.elapsed(TimeUnit.NANOSECONDS);
                final String formatted = TimeFormatter.STANDARD.format(TimeUnit.NANOSECONDS, nanoTime / this.timerRuns,
                        TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS);
                // print every second
                if (this.lastNotify.isBefore(Instant.now())) {
                    this.plugin.getLogger().info(formatted + " avg per raytrace tick.");
                    this.lastNotify = Instant.now().plusSeconds(1);
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final RejectedExecutionException e) {

        } catch (final Throwable t) {
            this.plugin.getLogger().log(Level.SEVERE, "Error thrown while raytracing: ", t);
        }
    }
}
