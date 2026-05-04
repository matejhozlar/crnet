package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean stopped = new AtomicBoolean(false);

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
     * heartbeat to complete. Idempotent — subsequent calls are no-ops.
     * <p>
     * On server shutdown CRNet calls this automatically as a safety net for
     * heartbeats whose owning mod forgot to stop them. Consumers should still
     * call {@code stop()} themselves (e.g. on {@code ServerStoppingEvent}).
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                LOGGER.warn("Heartbeat scheduler did not terminate cleanly within 5 s");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            CRNet.unregisterHeartbeat(this);
        }
    }

    /**
     * @return {@code true} once {@link #stop()} has been called on this handle.
     */
    public boolean isStopped() {
        return stopped.get();
    }
}
