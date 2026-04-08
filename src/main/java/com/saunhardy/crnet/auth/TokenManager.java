package com.saunhardy.crnet.auth;

import com.saunhardy.crnet.config.CrNetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared JWT token manager.
 * <p>
 * Stores and reuses a token until it is near expiry, then re-authenticates
 * transparently. Thread-safe — multiple mods on the same server instance share
 * one {@code TokenManager} via {@link com.saunhardy.crnet.CrNet#getTokenManager()}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * String token = CrNet.getTokenManager().getToken();
 * }</pre>
 *
 * <h3>Token refresh</h3>
 * Tokens are proactively refreshed {@value #REFRESH_BUFFER_SECONDS} seconds
 * before they expire. If a request receives a 401 response the caller should
 * call {@link #invalidate()} and then retry — this forces an immediate refresh
 * on the next {@link #getToken()} call.
 */
public class TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);

    /** Refresh token this many seconds before the server-reported expiry. */
    private static final int REFRESH_BUFFER_SECONDS = 60;

    private final CrNetConfig config;
    private final ReentrantLock lock = new ReentrantLock();

    private String cachedToken;
    private Instant tokenExpiry;

    public TokenManager(CrNetConfig config) {
        this.config = config;
    }

    /**
     * Returns a valid JWT, fetching a new one if the cache is empty or near expiry.
     *
     * @return bearer token string (without the {@code "Bearer "} prefix)
     * @throws TokenException if authentication fails
     */
    public String getToken() throws TokenException {
        lock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            return authenticate();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears the cached token, forcing re-authentication on the next {@link #getToken()} call.
     * Call this when a downstream request receives a 401 response.
     */
    public void invalidate() {
        lock.lock();
        try {
            cachedToken = null;
            tokenExpiry = null;
            LOGGER.debug("Token invalidated — will re-authenticate on next request");
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (cachedToken == null || tokenExpiry == null) return false;
        return Instant.now().isBefore(tokenExpiry.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private String authenticate() throws TokenException {
        // TODO: POST to config.getBaseUrl() + "/auth/login" with JWT secret,
        //       parse response, populate cachedToken and tokenExpiry
        throw new UnsupportedOperationException("TokenManager#authenticate not yet implemented");
    }
}
