package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handle for a running heartbeat schedule.
 * <p>
 * Returned by {@link HeartbeatBuilder#start()}. Call {@link #stop()} on server
 * shutdown to cleanly terminate the heartbeat thread.
 */
public class HeartbeatHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatHandle.class);

    private final ScheduledExecutorService scheduler;

    HeartbeatHandle(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
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
