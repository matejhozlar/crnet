package com.saunhardy.crnet.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Shared, bounded request queue for all CRNet consumers.
 * <p>
 * All {@link com.saunhardy.crnet.CRNetClient} instances share one executor so the
 * total number of concurrent backend connections stays bounded regardless of how
 * many clients are active.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Single-thread executor (FIFO ordering, no per-client priority)</li>
 *   <li>Bounded queue — capacity provided at construction time</li>
 *   <li>Rejection policy: log-and-drop (preferred over blocking the server thread)</li>
 * </ul>
 */
public class RequestQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestQueue.class);

    private final ExecutorService executor;

    public RequestQueue(int capacity) {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);
        this.executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                queue,
                r -> {
                    Thread t = new Thread(r, "crnet-request-queue");
                    t.setDaemon(true);
                    return t;
                },
                (r, exec) -> LOGGER.warn("CRNet request queue full — dropping request")
        );
    }

    /**
     * Submits a task that returns a result.
     * <p>
     * The returned {@link CompletableFuture} completes normally with the result,
     * or completes exceptionally if the task throws. If the queue is full, the
     * future completes exceptionally with a message indicating the rejection.
     *
     * @param task the callable to execute
     * @param <T>  the result type
     * @return a future that completes with the task result
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.submit(() -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            LOGGER.warn("CRNet request queue full — rejecting callable task");
            future.completeExceptionally(new RuntimeException("Request queue full — task rejected"));
        }
        return future;
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
                LOGGER.warn("CRNet request queue did not drain cleanly within 5 s");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
