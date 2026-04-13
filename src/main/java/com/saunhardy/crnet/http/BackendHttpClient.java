package com.saunhardy.crnet.http;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.saunhardy.crnet.auth.TokenException;
import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.config.CRNetConfig;
import com.saunhardy.crnet.util.UrlUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Standardised HTTP client for communicating with a backend API.
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

    private final String baseUrl;
    private final TokenManager tokenManager;
    private final HttpClient httpClient;

    public BackendHttpClient(String baseUrl, TokenManager tokenManager, HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.tokenManager = tokenManager;
        this.httpClient = httpClient;
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
        return post(path, jsonBody, (Type) responseType, playerUuid);
    }

    /**
     * Server-level auth shorthand for {@link #post(String, String, Type, UUID)}.
     */
    public <T> ApiResponse<T> post(String path, String jsonBody, Type responseType) throws BackendException {
        return post(path, jsonBody, responseType, null);
    }

    /**
     * Sends a POST request with a JSON body, accepting a generic {@link Type}
     * for parameterised response shapes (e.g. {@code List<TopEntry>}).
     *
     * <p>Use {@link TypeToken#getParameterized(Type, Type...)} to construct
     * a {@code Type} for a generic class:
     * <pre>{@code
     * Type listOfEntries = TypeToken.getParameterized(List.class, TopEntry.class).getType();
     * ApiResponse<List<TopEntry>> r = client.post("/x", body, listOfEntries, uuid);
     * }</pre>
     */
    public <T> ApiResponse<T> post(String path, String jsonBody, Type responseType,
                                   @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("POST", path, jsonBody, playerUuid);
        return parseResponse(response, responseType);
    }

    /**
     * POST that returns the response envelope without deserialising a typed body (server-level auth).
     */
    public ApiResponse<Void> postFireAndForget(String path, String jsonBody) throws BackendException {
        return postFireAndForget(path, jsonBody, null);
    }

    /**
     * POST that returns the response envelope without deserialising a typed body.
     */
    public ApiResponse<Void> postFireAndForget(String path, String jsonBody, @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("POST", path, jsonBody, playerUuid);
        return parseResponse(response, Void.class);
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
        return get(path, (Type) responseType, playerUuid);
    }

    /**
     * Server-level auth shorthand for {@link #get(String, Type, UUID)}.
     */
    public <T> ApiResponse<T> get(String path, Type responseType) throws BackendException {
        return get(path, responseType, null);
    }

    /**
     * Sends a GET request, accepting a generic {@link Type} for parameterised
     * response shapes (e.g. {@code List<TopEntry>}).
     *
     * <p>Use {@link TypeToken#getParameterized(Type, Type...)} to construct
     * a {@code Type} for a generic class.
     */
    public <T> ApiResponse<T> get(String path, Type responseType,
                                  @Nullable UUID playerUuid) throws BackendException {
        HttpResponse<String> response = sendWithRetry("GET", path, null, playerUuid);
        return parseResponse(response, responseType);
    }

    /**
     * Server-level auth shorthand for {@link #getList(String, Class, UUID)}.
     */
    public <T> ApiResponse<List<T>> getList(String path, Class<T> elementType) throws BackendException {
        return getList(path, elementType, null);
    }

    /**
     * Convenience wrapper for endpoints that return an array as the typed
     * payload (e.g. {@code GET /api/currency/top}). Equivalent to calling
     * {@link #get(String, Type, UUID)} with a parameterised {@code List<T>}.
     */
    public <T> ApiResponse<List<T>> getList(String path, Class<T> elementType,
                                            @Nullable UUID playerUuid) throws BackendException {
        Type listType = TypeToken.getParameterized(List.class, elementType).getType();
        return get(path, listType, playerUuid);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private HttpResponse<String> sendWithRetry(String method, String path,
                                               @Nullable String body,
                                               @Nullable UUID playerUuid) throws BackendException {
        int maxRetries = CRNetConfig.MAX_RETRIES.get();
        boolean authRetried = false;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest request;
            try {
                request = buildRequest(path, method, body, playerUuid);
            } catch (TokenException e) {
                throw new BackendException("Failed to obtain auth token: " + e.getMessage(), e);
            }

            if (CRNetConfig.LOG_REQUESTS.get()) {
                LOGGER.info(">> {} {}", method, UrlUtils.safeJoin(baseUrl, path));
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

            if (CRNetConfig.LOG_REQUESTS.get()) {
                LOGGER.info("<< {} {} — HTTP {}", method, path, status);
            }

            // 401: invalidate token and retry once (does not consume the normal retry budget)
            if (status == 401 && !authRetried) {
                authRetried = true;
                tokenManager.invalidate(playerUuid);
                LOGGER.debug("Got 401, invalidated token and retrying (playerUuid={})", playerUuid);
                attempt--;
                continue;
            }

            // 5xx: transient server error — retry with backoff
            if (status >= 500 && attempt < maxRetries) {
                LOGGER.warn("{} {} returned {} (attempt {}/{})", method, path, status, attempt + 1, maxRetries);
                sleepBackoff(attempt);
                continue;
            }

            if (status < 200 || status >= 300) {
                LOGGER.warn("{} {} returned HTTP {}: {}", method, path, status, response.body());
            }

            return response;
        }

        // Should not reach here, but just in case
        throw new BackendException("Request failed after all retry attempts");
    }

    private HttpRequest buildRequest(String path, String method, @Nullable String body,
                                     @Nullable UUID playerUuid) throws TokenException {
        String url = UrlUtils.safeJoin(baseUrl, path);
        String token = tokenManager.getToken(playerUuid);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(CRNetConfig.REQUEST_TIMEOUT_MS.get()));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
        } else {
            builder.GET();
        }

        return builder.build();
    }

    private <T> ApiResponse<T> parseResponse(HttpResponse<String> response, Type responseType) throws BackendException {
        int status = response.statusCode();
        String rawBody = response.body();

        // Pre-parse once so we can both extract envelope metadata and
        // (when present) the typed `data` payload without re-parsing.
        JsonObject root = null;
        try {
            JsonElement parsed = GSON.fromJson(rawBody, JsonElement.class);
            if (parsed != null && parsed.isJsonObject()) {
                root = parsed.getAsJsonObject();
            }
        } catch (JsonSyntaxException ignored) {
            // Not JSON — leave root null; envelope fields stay null.
        }

        String message = readStringField(root, "message");
        String playerMessage = readStringField(root, "playerMessage");

        if (status < 200 || status >= 300) {
            return new ApiResponse<>(status, rawBody, null, rawBody, message, playerMessage);
        }

        if (responseType == Void.class || responseType == void.class) {
            return new ApiResponse<>(status, rawBody, null, null, message, playerMessage);
        }

        try {
            // Envelope-aware: if the body is the standard envelope
            // ({ success, message, playerMessage?, data? }), deserialise
            // `data` as T. Both `success` and `data` must be present so we
            // don't accidentally unwrap legacy responses that happen to
            // expose a domain field called `data`.
            T data;
            JsonElement dataEl = (root != null && root.has("success")) ? root.get("data") : null;
            if (dataEl != null && !dataEl.isJsonNull()) {
                data = GSON.fromJson(dataEl, responseType);
            } else {
                data = GSON.fromJson(rawBody, responseType);
            }
            return new ApiResponse<>(status, rawBody, data, null, message, playerMessage);
        } catch (JsonSyntaxException e) {
            throw new BackendException("Failed to parse response body: " + e.getMessage(), e);
        }
    }

    private static String readStringField(@Nullable JsonObject root, String key) {
        if (root == null) return null;
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1_000L * (1L << attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
