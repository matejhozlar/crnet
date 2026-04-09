package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fluent builder for configuring a periodic heartbeat.
 * <p>
 * The consumer supplies the endpoint, interval, and a payload builder function.
 * CRNet handles scheduling and HTTP execution.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * HeartbeatHandle handle = client.heartbeat()
 *         .endpoint("/api/presence/heartbeat")
 *         .interval(5, TimeUnit.MINUTES)
 *         .payload(() -> buildPayloadJson(server))
 *         .start();
 *
 * handle.stop(); // on server shutdown
 * }</pre>
 */
public class HeartbeatBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatBuilder.class);

    private final CRNetClient client;
    private String endpoint;
    private long intervalMs;
    private Supplier<String> payloadSupplier;

    HeartbeatBuilder(CRNetClient client) {
        this.client = client;
    }

    /**
     * Sets the heartbeat endpoint path.
     *
     * @param path endpoint path relative to the client's base URL
     */
    public HeartbeatBuilder endpoint(String path) {
        this.endpoint = path;
        return this;
    }

    /**
     * Sets the heartbeat interval.
     *
     * @param duration interval duration
     * @param unit     time unit
     */
    public HeartbeatBuilder interval(long duration, TimeUnit unit) {
        this.intervalMs = unit.toMillis(duration);
        return this;
    }

    /**
     * Sets the payload supplier. Called on each heartbeat tick to produce the
     * JSON body. The supplier may capture any context it needs (e.g. the
     * {@code MinecraftServer} instance).
     *
     * @param supplier a function returning the JSON payload string
     */
    public HeartbeatBuilder payload(Supplier<String> supplier) {
        this.payloadSupplier = supplier;
        return this;
    }

    /**
     * Starts the heartbeat on a new daemon thread.
     *
     * @return a handle that can be used to stop the heartbeat
     * @throws IllegalStateException if required fields are not set
     */
    public HeartbeatHandle start() {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Heartbeat endpoint must be set");
        }
        if (intervalMs <= 0) {
            throw new IllegalStateException("Heartbeat interval must be positive");
        }
        if (payloadSupplier == null) {
            throw new IllegalStateException("Heartbeat payload supplier must be set");
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crnet-heartbeat");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String payload = payloadSupplier.get();
                client.internalPostAsync(endpoint, payload);
            } catch (Exception e) {
                LOGGER.error("Failed to send heartbeat to {}: {}", endpoint, e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        LOGGER.info("Heartbeat started: endpoint={}, interval={}ms", endpoint, intervalMs);
        return new HeartbeatHandle(scheduler);
    }
}
