package com.saunhardy.crnet.auth;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pluggable authentication strategy.
 * <p>
 * Two implementations exist:
 * <ul>
 *   <li>{@link SelfSignedJwtStrategy} — generates an HS256 JWT locally (no server round-trip)</li>
 *   <li>{@link LoginEndpointStrategy} — POSTs credentials to a login endpoint and caches the server-issued token</li>
 * </ul>
 */
public interface AuthStrategy {

    /**
     * Returns a valid bearer token string (without the {@code "Bearer "} prefix).
     *
     * @param playerUuid player UUID for per-player auth, or {@code null} for server-level auth
     * @throws TokenException if a token cannot be obtained
     */
    String getToken(@Nullable UUID playerUuid) throws TokenException;

    /**
     * Invalidates cached tokens so the next {@link #getToken} call forces a refresh.
     *
     * @param playerUuid player whose token to invalidate, or {@code null} for server-level tokens
     */
    void invalidate(@Nullable UUID playerUuid);
}
