package com.vanillage.raytraceantixray.tasks;

import com.google.common.base.Stopwatch;
import com.vanillage.raytraceantixray.RayTraceAntiXray;
import com.vanillage.raytraceantixray.data.PlayerData;
import com.vanillage.raytraceantixray.util.TimeFormatter;

import java.time.Instant;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class RayTraceTimerTask extends TimerTask {

    private final RayTraceAntiXray plugin;
    private final Stopwatch watch = Stopwatch.createUnstarted();
    private long timerRuns;
    private Instant lastNotify = Instant.MIN;

    public RayTraceTimerTask(final RayTraceAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            if (this.plugin.isTimingsEnabled()) {
                this.watch.start();
            } else if (this.watch.isRunning()) {
                this.watch.reset();
                this.timerRuns = 0;
                this.lastNotify = Instant.MIN;
            }

            this.plugin.getExecutorService()
                    .invokeAll(this.plugin.getPlayerData().values().stream().map(PlayerData::getCallable).toList());

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
