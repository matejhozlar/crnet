package com.saunhardy.crnet.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Generates an HS256 JWT locally on every call — no server round-trip required.
 * <p>
 * Token generation is cheap so no caching is needed. The {@code playerUuid}
 * parameter is ignored because this strategy produces server-level tokens.
 */
public class SelfSignedJwtStrategy implements AuthStrategy {

    private final String secret;
    private final int ttlSeconds;

    public SelfSignedJwtStrategy(String secret, int ttlSeconds) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        if (secret == null || secret.isBlank()) {
            throw new TokenException("JWT secret is not configured");
        }

        long nowMs = System.currentTimeMillis();

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.builder()
                    .issuedAt(new Date(nowMs))
                    .expiration(new Date(nowMs + ttlSeconds * 1_000L))
                    .signWith(key)
                    .compact();
        } catch (Exception e) {
            throw new TokenException("Failed to generate self-signed JWT", e);
        }
    }

    @Override
    public void invalidate(@Nullable UUID playerUuid) {
        // No-op — tokens are generated fresh each time.
    }
}
