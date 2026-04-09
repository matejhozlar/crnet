package com.saunhardy.crnet.auth;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Shared JWT token manager.
 * <p>
 * Delegates to the provided {@link AuthStrategy}. Each {@link com.saunhardy.crnet.CRNetClient}
 * owns its own {@code TokenManager} instance.
 */
public class TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);

    private final AuthStrategy strategy;

    public TokenManager(AuthStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Returns a valid JWT for server-level auth (playerUuid = null).
     *
     * @return bearer token string (without the {@code "Bearer "} prefix), or {@code null} for no-auth
     * @throws TokenException if authentication fails
     */
    @Nullable
    public String getToken() throws TokenException {
        return getToken(null);
    }

    /**
     * Returns a valid JWT, optionally scoped to a specific player.
     *
     * @param playerUuid player UUID for per-player auth, or {@code null} for server-level auth
     * @return bearer token string (without the {@code "Bearer "} prefix), or {@code null} for no-auth
     * @throws TokenException if authentication fails
     */
    @Nullable
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        return strategy.getToken(playerUuid);
    }

    /**
     * Clears cached tokens, forcing re-authentication on the next call.
     */
    public void invalidate() {
        invalidate(null);
    }

    /**
     * Clears cached tokens for a specific player (or server-level if null).
     *
     * @param playerUuid player UUID to invalidate, or {@code null} for server-level
     */
    public void invalidate(@Nullable UUID playerUuid) {
        strategy.invalidate(playerUuid);
        LOGGER.debug("Token invalidated (playerUuid={})", playerUuid);
    }

    /**
     * Clears all cached tokens, forcing re-authentication on subsequent calls.
     */
    public void invalidateAll() {
        strategy.invalidateAll();
        LOGGER.debug("All tokens invalidated");
    }
}
