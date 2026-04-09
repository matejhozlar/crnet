package com.saunhardy.crnet.auth;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pluggable authentication strategy.
 * <p>
 * Use the static factory methods to obtain instances:
 * <ul>
 *   <li>{@link #selfSignedJwt(String)} — HS256 JWT, server-level auth</li>
 *   <li>{@link #selfSignedJwt(String, int)} — HS256 JWT with custom TTL</li>
 *   <li>{@link #loginEndpoint(String)} — per-player auth via login endpoint</li>
 *   <li>{@link #none()} — no authentication</li>
 * </ul>
 */
public non-sealed interface AuthStrategy extends AuthConfig {

    /**
     * Returns a valid bearer token string (without the {@code "Bearer "} prefix).
     *
     * @param playerUuid player UUID for per-player auth, or {@code null} for server-level auth
     * @return token string, or {@code null} if this strategy produces no tokens
     * @throws TokenException if a token cannot be obtained
     */
    @Nullable
    String getToken(@Nullable UUID playerUuid) throws TokenException;

    /**
     * Invalidates cached tokens so the next {@link #getToken} call forces a refresh.
     *
     * @param playerUuid player whose token to invalidate, or {@code null} for server-level tokens
     */
    void invalidate(@Nullable UUID playerUuid);

    // ── Static factories ────────────────────────────────────────────────

    /**
     * Server-level HS256 JWT with a default TTL of 60 seconds.
     *
     * @param secret the HMAC secret key
     */
    static AuthStrategy selfSignedJwt(String secret) {
        return selfSignedJwt(secret, 60);
    }

    /**
     * Server-level HS256 JWT with a custom TTL.
     *
     * @param secret     the HMAC secret key
     * @param ttlSeconds token time-to-live in seconds
     */
    static AuthStrategy selfSignedJwt(String secret, int ttlSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }
        return new SelfSignedJwtStrategy(secret, ttlSeconds);
    }

    /**
     * Per-player auth via a login endpoint. The returned config object is finalised
     * by {@link com.saunhardy.crnet.CRNetClient.Builder#build()}, which injects the
     * {@code HttpClient} and {@code baseUrl}.
     *
     * @param path the login endpoint path (e.g. {@code "/auth/login"})
     * @return a deferred auth config that the client builder will materialise
     */
    static LoginEndpointAuthConfig loginEndpoint(String path) {
        return new LoginEndpointAuthConfig(path);
    }

    /**
     * No authentication — requests are sent without an {@code Authorization} header.
     */
    static AuthStrategy none() {
        return new NoneAuthStrategy();
    }
}
