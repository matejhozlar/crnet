package com.saunhardy.crnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * <h3>Restart-persistent scheduling</h3>
 * By default the first tick fires one interval after {@link #start()}. When a
 * persistence file is configured via {@link #persistLastSentTo(Path)}, the
 * builder reads the timestamp of the last successful send from that file and
 * schedules the next tick for {@code last + interval}. This lets a long-period
 * heartbeat (e.g. daily) survive server restarts without re-firing on every
 * boot.
 */
public class HeartbeatBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatBuilder.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private final CRNetClient client;
    private String endpoint;
    private long intervalMs;
    private Supplier<String> payloadSupplier;
    private Executor payloadExecutor;
    private Path persistencePath;

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
        this.payloadExecutor = Objects.requireNonNull(payloadExecutor, "payloadExecutor");
        this.payloadSupplier = Objects.requireNonNull(supplier, "supplier");
        return this;
    }

    /**
     * Enables restart-persistent scheduling. The timestamp of each successful
     * send is written to {@code path}; at {@link #start()} the file is read
     * and the next tick is scheduled for {@code last + interval} (or
     * immediately, if that moment has already passed).
     * <p>
     * The file stores a single ASCII line containing epoch millis. Missing,
     * empty, or unparseable files are treated as "never sent" and the
     * heartbeat fires at {@code now + interval} as if persistence were off.
     * Parent directories are created on demand.
     *
     * @param path file that stores the last-sent timestamp
     */
    public HeartbeatBuilder persistLastSentTo(Path path) {
        this.persistencePath = Objects.requireNonNull(path, "path");
        return this;
    }

    /**
     * Starts the heartbeat on a new daemon thread.
     *
     * @return a handle that can be used to stop or manually trigger the heartbeat
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

        HeartbeatTask task = new HeartbeatTask(
                client, endpoint, intervalMs, payloadSupplier, payloadExecutor, persistencePath);

        long initialDelayMs = task.computeInitialDelayMs();
        scheduler.scheduleAtFixedRate(task, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);

        LOGGER.info("Heartbeat started: endpoint={}, interval={}ms, initialDelay={}ms",
                endpoint, intervalMs, initialDelayMs);
        return new HeartbeatHandle(scheduler, task);
    }

    /**
     * Captures the tick/send logic and its configuration. Held by
     * {@link HeartbeatHandle} so {@code triggerNow()} can re-enter it.
     */
    static final class HeartbeatTask implements Runnable {

        private final CRNetClient client;
        private final String endpoint;
        private final long intervalMs;
        private final Supplier<String> payloadSupplier;
        private final Executor payloadExecutor;
        private final Path persistencePath;
        private final AtomicBoolean buildInFlight = new AtomicBoolean(false);

        HeartbeatTask(CRNetClient client, String endpoint, long intervalMs,
                      Supplier<String> payloadSupplier, Executor payloadExecutor,
                      Path persistencePath) {
            this.client = client;
            this.endpoint = endpoint;
            this.intervalMs = intervalMs;
            this.payloadSupplier = payloadSupplier;
            this.payloadExecutor = payloadExecutor;
            this.persistencePath = persistencePath;
        }

        long computeInitialDelayMs() {
            if (persistencePath == null) {
                return intervalMs;
            }
            long lastSent = readLastSent();
            if (lastSent <= 0L) {
                return intervalMs;
            }
            long elapsed = System.currentTimeMillis() - lastSent;
            if (elapsed >= intervalMs) {
                return 0L;
            }
            return intervalMs - elapsed;
        }

        @Override
        public void run() {
            if (payloadExecutor == null) {
                // Scheduler is single-threaded, so inline execution is naturally self-limiting.
                buildAndSend();
                return;
            }

            // When dispatching to a foreign executor, the scheduler returns immediately
            // and the next tick can fire before the previous payload finished building.
            // Skip overlapping ticks to avoid concurrent POSTs and redundant load on
            // the payload executor (typically the MC server thread).
            if (!buildInFlight.compareAndSet(false, true)) {
                LOGGER.debug("Skipping heartbeat tick for {} — previous build still in flight", endpoint);
                return;
            }

            try {
                payloadExecutor.execute(() -> {
                    try {
                        buildAndSend();
                    } finally {
                        buildInFlight.set(false);
                    }
                });
            } catch (Exception e) {
                buildInFlight.set(false);
                LOGGER.error("Failed to dispatch heartbeat payload build for {}: {}", endpoint, e.getMessage());
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
                } else if (response == null) {
                    // postAsync should not complete with both response and exception null, but
                    // guard against a future contract change masquerading as a success.
                    LOGGER.warn("Heartbeat to {} completed with no response and no exception — not persisting timestamp", endpoint);
                } else if (!response.isSuccess()) {
                    LOGGER.warn("Heartbeat to {} returned HTTP {}: {}", endpoint,
                            response.getStatusCode(),
                            response.getMessage() != null ? response.getMessage() : response.getError());
                } else {
                    writeLastSent(System.currentTimeMillis());
                }
            });
        }

        private long readLastSent() {
            try {
                if (!Files.exists(persistencePath)) {
                    return 0L;
                }
                String content = Files.readString(persistencePath).trim();
                if (content.isEmpty()) {
                    return 0L;
                }
                return Long.parseLong(content);
            } catch (IOException | NumberFormatException e) {
                LOGGER.warn("Could not read heartbeat timestamp from {}: {}", persistencePath, e.getMessage());
                return 0L;
            }
        }

        private void writeLastSent(long timestampMs) {
            if (persistencePath == null) {
                return;
            }
            Path tmp = persistencePath.resolveSibling(persistencePath.getFileName() + ".tmp");
            try {
                Path parent = persistencePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(tmp, Long.toString(timestampMs));
                Files.move(tmp, persistencePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.warn("Could not persist heartbeat timestamp to {}: {}", persistencePath, e.getMessage());
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException cleanupEx) {
                    LOGGER.debug("Could not clean up heartbeat tmp file {}: {}", tmp, cleanupEx.getMessage());
                }
            }
        }
    }
}
