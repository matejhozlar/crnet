package com.saunhardy.crnet.queue;

import com.saunhardy.crnet.config.CrNetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shared, bounded request queue for all CrNet consumers.
 * <p>
 * Replaces the per-mod single-threaded executor pattern used in PresenceAPI and
 * the Currency mod. All mods share one executor so the total number of concurrent
 * backend connections stays bounded regardless of how many mods are loaded.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Single-thread executor (FIFO ordering, no per-mod priority)</li>
 *   <li>Bounded queue — capacity configured via {@link CrNetConfig#getQueueCapacity()}</li>
 *   <li>Rejection policy: log-and-drop (preferred over blocking the server thread)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CrNet.getRequestQueue().submit(() -> {
 *     client.post("/presence/update", body, Void.class);
 * });
 * }</pre>
 */
public class RequestQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestQueue.class);

    private final ExecutorService executor;

    public RequestQueue(CrNetConfig config) {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                queue,
                r -> {
                    Thread t = new Thread(r, "crnet-request-queue");
                    t.setDaemon(true);
                    return t;
                },
                (r, exec) -> LOGGER.warn("CrNet request queue full — dropping request")
        );
    }

    /**
     * Submits a task to the shared request queue.
     * If the queue is full the task is silently dropped and a warning is logged.
     *
     * @param task the request to execute (typically a lambda calling {@link com.saunhardy.crnet.http.BackendHttpClient})
     */
    public void submit(Runnable task) {
        executor.submit(task);
    }

    /**
     * Shuts down the executor gracefully, waiting up to 5 seconds for in-flight
     * requests to complete. Called automatically on server stop.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOGGER.warn("CrNet request queue did not drain cleanly within 5 s");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
