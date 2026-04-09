package com.saunhardy.crnet;

import com.saunhardy.crnet.auth.AuthConfig;
import com.saunhardy.crnet.auth.AuthStrategy;
import com.saunhardy.crnet.auth.LoginEndpointAuthConfig;
import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.http.ApiResponse;
import com.saunhardy.crnet.http.BackendHttpClient;
import com.saunhardy.crnet.queue.RequestQueue;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpClient;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API entry point for CRNet consumers.
 * <p>
 * Each consuming mod constructs its own {@code CRNetClient} via the {@link Builder},
 * providing a base URL and auth strategy. All clients share the global
 * {@link RequestQueue} managed by the {@link CRNet} mod class.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CRNetClient client = new CRNetClient.Builder()
 *         .baseUrl("http://localhost:5001")
 *         .auth(AuthStrategy.selfSignedJwt(secret))
 *         .build();
 *
 * // Fire-and-forget
 * client.postAsync("/presence/update", json);
 *
 * // Async with typed response
 * CompletableFuture<ApiResponse<Balance>> future =
 *         client.get("/api/balance", Balance.class, playerUuid);
 *
 * // Synchronous (blocks caller — do NOT call on the main server thread)
 * ApiResponse<Balance> result = future.join();
 * }</pre>
 */
public class CRNetClient {

    private final BackendHttpClient httpClient;
    private final TokenManager tokenManager;
    private final RequestQueue requestQueue;

    private CRNetClient(BackendHttpClient httpClient, TokenManager tokenManager, RequestQueue requestQueue) {
        this.httpClient = httpClient;
        this.tokenManager = tokenManager;
        this.requestQueue = requestQueue;
    }

    // ── Async fire-and-forget ───────────────────────────────────────────

    /**
     * Submits a fire-and-forget POST (server-level auth).
     *
     * @param path     endpoint path relative to the base URL
     * @param jsonBody serialised JSON body
     * @return a future that completes when the request finishes (or exceptionally on error)
     */
    public CompletableFuture<Void> postAsync(String path, String jsonBody) {
        return postAsync(path, jsonBody, null);
    }

    /**
     * Submits a fire-and-forget POST with optional per-player auth.
     *
     * @param path       endpoint path relative to the base URL
     * @param jsonBody   serialised JSON body
     * @param playerUuid player UUID for per-player auth, or {@code null}
     * @return a future that completes when the request finishes (or exceptionally on error)
     */
    public CompletableFuture<Void> postAsync(String path, String jsonBody, @Nullable UUID playerUuid) {
        return requestQueue.submit(() -> {
            httpClient.postFireAndForget(path, jsonBody, playerUuid);
            return null;
        });
    }

    // ── Async with typed response ───────────────────────────────────────

    /**
     * Sends a POST and returns the typed response (server-level auth).
     */
    public <T> CompletableFuture<ApiResponse<T>> post(String path, String jsonBody, Class<T> responseType) {
        return post(path, jsonBody, responseType, null);
    }

    /**
     * Sends a POST and returns the typed response.
     *
     * @param path         endpoint path relative to the base URL
     * @param jsonBody     serialised JSON body
     * @param responseType class to deserialise the response into
     * @param playerUuid   player UUID for per-player auth, or {@code null}
     * @return a future completing with the parsed response
     */
    public <T> CompletableFuture<ApiResponse<T>> post(String path, String jsonBody, Class<T> responseType,
                                                      @Nullable UUID playerUuid) {
        return requestQueue.submit(() -> httpClient.post(path, jsonBody, responseType, playerUuid));
    }

    /**
     * Sends a GET and returns the typed response (server-level auth).
     */
    public <T> CompletableFuture<ApiResponse<T>> get(String path, Class<T> responseType) {
        return get(path, responseType, null);
    }

    /**
     * Sends a GET and returns the typed response.
     *
     * @param path         endpoint path relative to the base URL
     * @param responseType class to deserialise the response into
     * @param playerUuid   player UUID for per-player auth, or {@code null}
     * @return a future completing with the parsed response
     */
    public <T> CompletableFuture<ApiResponse<T>> get(String path, Class<T> responseType,
                                                     @Nullable UUID playerUuid) {
        return requestQueue.submit(() -> httpClient.get(path, responseType, playerUuid));
    }

    // ── Token management ────────────────────────────────────────────────

    /**
     * Invalidates the cached auth token for a player.
     * Call this on player logout to clean up per-player token caches.
     *
     * @param playerUuid the player whose token to invalidate
     */
    public void invalidateToken(UUID playerUuid) {
        tokenManager.invalidate(playerUuid);
    }

    /**
     * Releases this client's resources (token cache, etc.).
     * The client must not be used after calling this method.
     */
    public void close() {
        tokenManager.invalidateAll();
    }

    // ── Heartbeat ───────────────────────────────────────────────────────

    /**
     * Returns a new heartbeat builder for configuring periodic requests.
     *
     * @return a fluent heartbeat builder
     */
    public HeartbeatBuilder heartbeat() {
        return new HeartbeatBuilder(this);
    }

    // ── Builder ─────────────────────────────────────────────────────────

    public static class Builder {

        private String baseUrl;
        private AuthConfig authConfig = AuthStrategy.none();

        /**
         * Sets the base URL for all requests made by this client.
         *
         * @param baseUrl the backend API base URL (e.g. {@code "http://localhost:5001"})
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the auth configuration for this client.
         * <p>
         * Accepts either an {@link AuthStrategy} (from static factories like
         * {@link AuthStrategy#selfSignedJwt(String)}) or a {@link LoginEndpointAuthConfig}
         * (from {@link AuthStrategy#loginEndpoint(String)}).
         *
         * @param config an {@link AuthConfig} instance
         */
        public Builder auth(AuthConfig config) {
            this.authConfig = config;
            return this;
        }

        /**
         * Builds the client. Requires {@link #baseUrl(String)} to have been called.
         *
         * @return a new {@link CRNetClient} ready for use
         * @throws IllegalStateException if base URL is not set or CRNet is not initialised
         */
        public CRNetClient build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("baseUrl must be set");
            }

            HttpClient sharedHttpClient = CRNet.getSharedHttpClient();
            RequestQueue requestQueue = CRNet.getRequestQueue();
            if (sharedHttpClient == null || requestQueue == null) {
                throw new IllegalStateException("CRNet has not been initialised — ensure the mod is loaded and commonSetup has completed");
            }

            AuthStrategy strategy;
            if (authConfig instanceof LoginEndpointAuthConfig loginConfig) {
                strategy = loginConfig.create(sharedHttpClient, baseUrl);
            } else if (authConfig instanceof AuthStrategy authStrategy) {
                strategy = authStrategy;
            }  else {
                // Unreachable — AuthConfig is sealed to AuthStrategy and LoginEndpointAuthConfig
                throw new AssertionError("Unknown AuthConfig type: " + authConfig.getClass());
            }

            TokenManager tokenManager = new TokenManager(strategy);
            BackendHttpClient httpClient = new BackendHttpClient(baseUrl, tokenManager, sharedHttpClient);

            return new CRNetClient(httpClient, tokenManager, requestQueue);
        }
    }
}
