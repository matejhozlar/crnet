package com.saunhardy.crnet.auth;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Generates an HS256 JWT locally on every call — no server round-trip required.
 * <p>
 * Token generation is cheap so no caching is needed. When {@code playerUuid}
 * is non-null, the token carries {@code uuid} (and optionally {@code name},
 * if a name resolver was supplied) claims so the backend can identify the
 * acting player. When {@code audience} is non-null, it is emitted as the
 * standard {@code aud} claim so the backend can distinguish mod-audience
 * tokens from web-audience tokens.
 */
class SelfSignedJwtStrategy implements AuthStrategy {

    private final SecretKey signingKey;
    private final int ttlSeconds;
    private final @Nullable String audience;
    private final @Nullable Function<UUID, String> nameResolver;

    SelfSignedJwtStrategy(String secret,
                          int ttlSeconds,
                          @Nullable String audience,
                          @Nullable Function<UUID, String> nameResolver) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
        this.audience = audience;
        this.nameResolver = nameResolver;
    }

    @Override
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        long nowMs = System.currentTimeMillis();

        try {
            JwtBuilder builder = Jwts.builder()
                    .issuedAt(new Date(nowMs))
                    .expiration(new Date(nowMs + ttlSeconds * 1_000L));

            if (audience != null) {
                builder.audience().add(audience).and();
            }

            if (playerUuid != null) {
                builder.claim("uuid", playerUuid.toString());
                String name = resolveName(playerUuid);
                if (name != null && !name.isBlank()) {
                    builder.claim("name", name);
                }
            }

            return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenException("Failed to generate self-signed JWT", e);
        }
    }

    private @Nullable String resolveName(UUID playerUuid) throws TokenException {
        if (nameResolver == null) return null;
        try {
            return nameResolver.apply(playerUuid);
        } catch (Exception e) {
            throw new TokenException("Name resolver failed for player " + playerUuid, e);
        }
    }

    @Override
    public void invalidate(@Nullable UUID playerUuid) {
        // No-op — tokens are generated fresh each time.
    }

    @Override
    public void invalidateAll() {
        // No-op — tokens are generated fresh each time.
    }
}
