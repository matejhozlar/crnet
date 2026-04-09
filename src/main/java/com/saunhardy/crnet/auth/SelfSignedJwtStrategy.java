package com.saunhardy.crnet.auth;

import com.saunhardy.crnet.config.CRNetConfig;
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
 * Ported from PresenceAPI's {@code ApiClient.generateJWT()}. Token generation is
 * cheap so no caching is needed. The {@code playerUuid} parameter is ignored
 * because this strategy produces server-level tokens.
 */
public class SelfSignedJwtStrategy implements AuthStrategy {

    @Override
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        String secret = CRNetConfig.JWT_SECRET.get();
        if (secret == null || secret.isBlank()) {
            throw new TokenException("JWT secret is not configured (crnet-server.toml → auth.jwtSecret)");
        }

        int ttlSeconds = CRNetConfig.TOKEN_TTL_SECONDS.get();
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
