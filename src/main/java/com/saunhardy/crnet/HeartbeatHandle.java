package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handle for a running heartbeat schedule.
 * <p>
 * Returned by {@link HeartbeatBuilder#start()}. Call {@link #stop()} on server
 * shutdown to cleanly terminate the heartbeat thread, or {@link #triggerNow()}
 * to fire an ad-hoc send outside the regular cadence (for example from an RCON
 * command).
 */
public class HeartbeatHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatHandle.class);

    private final ScheduledExecutorService scheduler;
    private final HeartbeatBuilder.HeartbeatTask task;

    HeartbeatHandle(ScheduledExecutorService scheduler, HeartbeatBuilder.HeartbeatTask task) {
        this.scheduler = scheduler;
        this.task = task;
    }

    /**
     * Submits an out-of-band heartbeat tick to the scheduler. Honours the same
     * overlap guard as the regular cadence: if a previous build is still
     * in flight, the trigger is a no-op. Intended for operator-invoked pushes
     * (e.g. an RCON command) that need an immediate send without waiting for
     * the next scheduled tick.
     * <p>
     * Does <em>not</em> reset the fixed-rate schedule — the next scheduled tick
     * still fires at its originally-computed time. If persistence is enabled
     * ({@link HeartbeatBuilder#persistLastSentTo}), a successful manual send
     * updates the persisted timestamp, so a restart after {@code triggerNow()}
     * will reschedule relative to the manual send.
     *
     * @return {@code true} if the tick was submitted, {@code false} if the
     *         scheduler is shut down and the request was dropped
     */
    public boolean triggerNow() {
        try {
            scheduler.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Heartbeat triggerNow rejected — scheduler is shut down");
            return false;
        }
    }

    /**
     * Stops the heartbeat scheduler, waiting up to 5 seconds for any in-flight
     * heartbeat to complete.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LOGGER.warn("Heartbeat scheduler did not terminate cleanly within 5 s");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
