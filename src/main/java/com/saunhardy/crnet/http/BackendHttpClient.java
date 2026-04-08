package com.saunhardy.crnet.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.saunhardy.crnet.auth.TokenException;
import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.config.CrNetConfig;
import com.saunhardy.crnet.util.UrlUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Standardised HTTP client for communicating with the Createrington backend.
 * <p>
 * Uses {@link java.net.http.HttpClient} with <strong>HTTP/1.1 explicitly
 * enforced</strong> — HTTP/2 upgrade must be treated as a deliberate breaking
 * change (see PresenceAPI v1.1.0 regression).
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Automatic JWT bearer token injection via {@link TokenManager}</li>
 *   <li>Exponential backoff retry on transient failures</li>
 *   <li>Transparent 401 handling: invalidates token and retries once</li>
 *   <li>Generic response deserialisation via GSON</li>
 * </ul>
 */
public class BackendHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendHttpClient.class);
    private static final Gson GSON = new Gson();

    private final TokenManager tokenManager;
    private final HttpClient httpClient;

    public BackendHttpClient(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // explicit — do not change without a version bump
                .connectTimeout(Duration.ofMillis(CrNetConfig.CONNECT_TIMEOUT_MS.get()))
                .build();
    }

    // ── POST ─────────────────────────────────────────────────────────────

    /**
     * Sends a POST request with a JSON body (server-level auth).
     */
    public <T> ApiResponse<T> post(String path, String jsonBody, Class<T> responseType) throws BackendException {
        return post(path, jsonBody, responseType, null);
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param path         path relative to the configured base URL
     * @param jsonBody     serialised JSON string
     * @param responseType class to deserialise the response body into
     * @param playerUuid   player UUID for per-player auth, or {@code null}
     * @return parsed API response
     * @throws BackendException on HTTP error, parse failure, or auth failure
     */
    public <T> ApiResponse<T> post(String path, String jsonBody, Class<T> responseType,
                                   @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("POST", path, jsonBody, playerUuid);
        return parseResponse(response, responseType);
    }

    /**
     * Fire-and-forget POST (server-level auth). Does not parse the response body.
     */
    public void postFireAndForget(String path, String jsonBody) throws BackendException {
        postFireAndForget(path, jsonBody, null);
    }

    /**
     * Fire-and-forget POST. Does not parse the response body.
     */
    public void postFireAndForget(String path, String jsonBody, @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("POST", path, jsonBody, playerUuid);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BackendException("POST " + path + " returned HTTP " + response.statusCode() + ": " + response.body(),
                    response.statusCode());
        }
    }

    // ── GET ──────────────────────────────────────────────────────────────

    /**
     * Sends a GET request (server-level auth).
     */
    public <T> ApiResponse<T> get(String path, Class<T> responseType) throws BackendException {
        return get(path, responseType, null);
    }

    /**
     * Sends a GET request.
     *
     * @param path         path relative to the configured base URL
     * @param responseType class to deserialise the response body into
     * @param playerUuid   player UUID for per-player auth, or {@code null}
     * @return parsed API response
     * @throws BackendException on HTTP error, parse failure, or auth failure
     */
    public <T> ApiResponse<T> get(String path, Class<T> responseType,
                                  @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("GET", path, null, playerUuid);
        return parseResponse(response, responseType);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private HttpResponse<String> sendWithRetry(String method, String path,
                                               @Nullable String body,
                                               @Nullable UUID playerUuid) throws BackendException {
        int maxRetries = CrNetConfig.MAX_RETRIES.get();
        boolean authRetried = false;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest request;
            try {
                request = buildRequest(path, method, body, playerUuid);
            } catch (TokenException e) {
                throw new BackendException("Failed to obtain auth token: " + e.getMessage(), e);
            }

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new BackendException("Request interrupted", e);
                }
                if (attempt < maxRetries) {
                    LOGGER.warn("{} {} failed (attempt {}/{}): {}", method, path, attempt + 1, maxRetries, e.getMessage());
                    sleepBackoff(attempt);
                    continue;
                }
                throw new BackendException("I/O error after " + (maxRetries + 1) + " attempts: " + e.getMessage(), e);
            }

            int status = response.statusCode();

            // 401: invalidate token and retry once
            if (status == 401 && !authRetried) {
                authRetried = true;
                tokenManager.invalidate(playerUuid);
                LOGGER.debug("Got 401, invalidated token and retrying (playerUuid={})", playerUuid);
                continue;
            }

            // 5xx: transient server error — retry with backoff
            if (status >= 500 && attempt < maxRetries) {
                LOGGER.warn("{} {} returned {} (attempt {}/{})", method, path, status, attempt + 1, maxRetries);
                sleepBackoff(attempt);
                continue;
            }

            return response;
        }

        // Should not reach here, but just in case
        throw new BackendException("Request failed after all retry attempts");
    }

    private HttpRequest buildRequest(String path, String method, @Nullable String body,
                                     @Nullable UUID playerUuid) throws TokenException {
        String url = UrlUtils.safeJoin(CrNetConfig.BASE_URL.get(), path);
        String token = tokenManager.getToken(playerUuid);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(CrNetConfig.REQUEST_TIMEOUT_MS.get()));

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
        } else {
            builder.GET();
        }

        return builder.build();
    }

    private <T> ApiResponse<T> parseResponse(HttpResponse<String> response, Class<T> responseType) throws BackendException {
        int status = response.statusCode();
        String rawBody = response.body();

        if (status < 200 || status >= 300) {
            return new ApiResponse<>(status, rawBody, null, rawBody);
        }

        if (responseType == Void.class || responseType == void.class) {
            return new ApiResponse<>(status, rawBody, null, null);
        }

        try {
            T data = GSON.fromJson(rawBody, responseType);
            return new ApiResponse<>(status, rawBody, data, null);
        } catch (JsonSyntaxException e) {
            throw new BackendException("Failed to parse response body: " + e.getMessage(), e);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1_000L * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
