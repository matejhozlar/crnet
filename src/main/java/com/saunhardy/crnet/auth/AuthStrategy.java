package com.saunhardy.crnet.auth;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * Pluggable authentication strategy.
 * <p>
 * Use the static factory methods to obtain instances:
 * <ul>
 *   <li>{@link #selfSignedJwt(String)} — HS256 JWT, server-level auth</li>
 *   <li>{@link #selfSignedJwt(String, int)} — HS256 JWT with custom TTL</li>
 *   <li>{@link #selfSignedJwt(String, int, String)} — HS256 JWT with custom TTL and an {@code aud} claim</li>
 *   <li>{@link #selfSignedJwt(String, int, String, Function)} — HS256 JWT with custom TTL, audience, and a UUID→name resolver for per-player tokens</li>
 *   <li>{@link #none()} — no authentication</li>
 * </ul>
 */
public interface AuthStrategy {

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

    /**
     * Invalidates all cached tokens, forcing re-authentication on subsequent calls.
     */
    void invalidateAll();

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
        return selfSignedJwt(secret, ttlSeconds, null, null);
    }

    /**
     * HS256 JWT with a custom TTL and an {@code aud} claim.
     * <p>
     * The {@code audience} value is emitted as the standard JWT {@code aud} claim so
     * the backend can distinguish tokens intended for the mod surface from tokens
     * intended for the web surface even when they share a signing secret.
     * <p>
     * When a caller passes a non-null {@code playerUuid} to {@link #getToken(UUID)},
     * the token additionally carries a {@code uuid} claim. No {@code name} claim is
     * emitted — use {@link #selfSignedJwt(String, int, String, Function)} if the
     * backend also needs the player name.
     *
     * @param secret     the HMAC secret key
     * @param ttlSeconds token time-to-live in seconds
     * @param audience   the {@code aud} claim value, or {@code null} to omit
     */
    static AuthStrategy selfSignedJwt(String secret, int ttlSeconds, @Nullable String audience) {
        return selfSignedJwt(secret, ttlSeconds, audience, null);
    }

    /**
     * HS256 JWT with a custom TTL, an {@code aud} claim, and a UUID→name resolver
     * used to populate a {@code name} claim on per-player tokens.
     * <p>
     * The resolver is invoked only when {@link #getToken(UUID)} is called with a
     * non-null player UUID. If the resolver returns {@code null} or a blank string
     * the {@code name} claim is omitted.
     *
     * @param secret       the HMAC secret key
     * @param ttlSeconds   token time-to-live in seconds
     * @param audience     the {@code aud} claim value, or {@code null} to omit
     * @param nameResolver resolver from player UUID to display name, or {@code null} to omit the {@code name} claim
     */
    static AuthStrategy selfSignedJwt(String secret,
                                      int ttlSeconds,
                                      @Nullable String audience,
                                      @Nullable Function<UUID, String> nameResolver) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }
        return new SelfSignedJwtStrategy(secret, ttlSeconds, audience, nameResolver);
    }

    /**
     * No authentication — requests are sent without an {@code Authorization} header.
     */
    static AuthStrategy none() {
        return new NoneAuthStrategy();
    }
}
