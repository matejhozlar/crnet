package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * <h3>Thread-safe payload assembly</h3>
 * By default the payload supplier runs on CRNet's own scheduled executor, which
 * is not the Minecraft server thread. If the payload reads state that is only
 * safe to access from a specific thread (OPAC claim managers, party streams,
 * world data, etc.), provide an {@link Executor} via {@link #payloadOn}:
 * <pre>{@code
 * // MinecraftServer implements Executor, so it can be passed directly
 * HeartbeatHandle handle = client.heartbeat()
 *         .endpoint("/api/forceloads/sync")
 *         .interval(5, TimeUnit.MINUTES)
 *         .payloadOn(server, () -> buildPayloadJson(server))
 *         .start();
 * }</pre>
 */
public class HeartbeatBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatBuilder.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final CRNetClient client;
    private String endpoint;
    private long intervalMs;
    private Supplier<String> payloadSupplier;
    private Executor payloadExecutor;

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
     * <p>
     * The supplier runs on CRNet's scheduled executor thread. If the payload
     * reads thread-unsafe state, use {@link #payloadOn(Executor, Supplier)}
     * instead.
     *
     * @param supplier a function returning the JSON payload string
     */
    public HeartbeatBuilder payload(Supplier<String> supplier) {
        this.payloadSupplier = supplier;
        this.payloadExecutor = null;
        return this;
    }

    /**
     * Sets the payload supplier and the executor it must run on. On each
     * heartbeat tick the supplier is dispatched to {@code payloadExecutor};
     * once it produces the JSON body the POST is submitted through CRNet's
     * normal request queue.
     * <p>
     * Intended for callers whose payload assembly reads thread-confined state
     * (e.g. Minecraft/OPAC APIs on the server thread). {@code MinecraftServer}
     * implements {@link Executor}, so it can be passed directly.
     *
     * @param payloadExecutor executor the supplier is dispatched to
     * @param supplier        a function returning the JSON payload string
     */
    public HeartbeatBuilder payloadOn(Executor payloadExecutor, Supplier<String> supplier) {
        this.payloadExecutor = payloadExecutor;
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
            Thread t = new Thread(r, "crnet-heartbeat-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        LOGGER.info("Heartbeat started: endpoint={}, interval={}ms", endpoint, intervalMs);
        return new HeartbeatHandle(scheduler);
    }

    private void tick() {
        if (payloadExecutor != null) {
            try {
                payloadExecutor.execute(this::buildAndSend);
            } catch (Exception e) {
                LOGGER.error("Failed to dispatch heartbeat payload build for {}: {}", endpoint, e.getMessage());
            }
        } else {
            buildAndSend();
        }
    }

    private void buildAndSend() {
        final String payload;
        try {
            payload = payloadSupplier.get();
        } catch (Exception e) {
            LOGGER.error("Failed to build heartbeat payload for {}: {}", endpoint, e.getMessage());
            return;
        }
        client.postAsync(endpoint, payload).whenComplete((response, ex) -> {
            if (ex != null) {
                LOGGER.error("Heartbeat to {} failed: {}", endpoint, ex.getMessage());
            } else if (!response.isSuccess()) {
                LOGGER.warn("Heartbeat to {} returned HTTP {}: {}", endpoint,
                        response.getStatusCode(),
                        response.getMessage() != null ? response.getMessage() : response.getError());
            }
        });
    }
}
