package com.saunhardy.crnet.http;

import com.saunhardy.crnet.auth.TokenException;
import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.config.CrNetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * BackendHttpClient client = new BackendHttpClient(config, tokenManager);
 * ApiResponse<PlayerBalance> resp = client.get("/balance/" + uuid, PlayerBalance.class);
 * }</pre>
 */
public class BackendHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendHttpClient.class);

    private final CrNetConfig config;
    private final TokenManager tokenManager;
    private final HttpClient httpClient;

    public BackendHttpClient(CrNetConfig config, TokenManager tokenManager) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // explicit — do not change without a version bump
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .build();
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param path        path relative to the configured base URL (e.g. {@code "/presence/update"})
     * @param jsonBody    serialised JSON string
     * @param responseType class to deserialise the response body into
     * @return parsed response object
     * @throws BackendException on HTTP error, parse failure, or auth failure
     */
    public <T> T post(String path, String jsonBody, Class<T> responseType) throws BackendException {
        // TODO: implement — build request, call sendWithRetry, deserialise with GSON
        throw new UnsupportedOperationException("BackendHttpClient#post not yet implemented");
    }

    /**
     * Sends a GET request.
     *
     * @param path        path relative to the configured base URL
     * @param responseType class to deserialise the response body into
     * @return parsed response object
     * @throws BackendException on HTTP error, parse failure, or auth failure
     */
    public <T> T get(String path, Class<T> responseType) throws BackendException {
        // TODO: implement
        throw new UnsupportedOperationException("BackendHttpClient#get not yet implemented");
    }

    /**
     * Sends a request with exponential backoff retry logic.
     * On a 401 response, invalidates the token and retries once before giving up.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest.Builder requestBuilder)
            throws BackendException {
        // TODO: implement retry loop (up to config.getMaxRetries()),
        //       handle 401 via tokenManager.invalidate() + single retry
        throw new UnsupportedOperationException("BackendHttpClient#sendWithRetry not yet implemented");
    }

    private HttpRequest buildRequest(String path, String method, String body)
            throws TokenException {
        // TODO: attach Authorization: Bearer <token> header
        throw new UnsupportedOperationException("BackendHttpClient#buildRequest not yet implemented");
    }
}
