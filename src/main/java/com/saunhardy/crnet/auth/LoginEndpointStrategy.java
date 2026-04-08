package com.saunhardy.crnet.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.saunhardy.crnet.config.CrNetConfig;
import com.saunhardy.crnet.util.UrlUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates by POSTing player credentials to a login endpoint and caching
 * the server-issued JWT per player UUID.
 * <p>
 * Ported from the Currency mod's {@code MoneyCommands.getOrFetchToken()} pattern.
 * Tokens are cached for ~9 minutes (configurable) and evicted periodically.
 */
public class LoginEndpointStrategy implements AuthStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginEndpointStrategy.class);
    private static final Gson GSON = new Gson();

    private static final long TOKEN_TTL_MS = 9 * 60 * 1_000L;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1_000L;

    private final ConcurrentHashMap<UUID, String> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> tokenExpiration = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> tokenLocks = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = 0;

    private final HttpClient httpClient;

    public LoginEndpointStrategy() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(CrNetConfig.CONNECT_TIMEOUT_MS.get()))
                .build();
    }

    @Override
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        if (playerUuid == null) {
            throw new TokenException("LoginEndpointStrategy requires a non-null playerUuid");
        }

        evictExpiredTokens();
        Object lock = tokenLocks.computeIfAbsent(playerUuid, k -> new Object());

        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (tokenCache.containsKey(playerUuid) && tokenExpiration.getOrDefault(playerUuid, 0L) > now) {
                return tokenCache.get(playerUuid);
            }
            return fetchToken(playerUuid);
        }
    }

    @Override
    public void invalidate(@Nullable UUID playerUuid) {
        if (playerUuid == null) return;
        tokenCache.remove(playerUuid);
        tokenExpiration.remove(playerUuid);
        LOGGER.debug("Token invalidated for player {}", playerUuid);
    }

    private String fetchToken(UUID playerUuid) throws TokenException {
        String url = UrlUtils.safeJoin(CrNetConfig.BASE_URL.get(), CrNetConfig.LOGIN_ENDPOINT.get());

        try {
            String body = GSON.toJson(Map.of("uuid", playerUuid.toString()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(CrNetConfig.REQUEST_TIMEOUT_MS.get()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new TokenException("Login endpoint returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            String token = obj.get("token").getAsString();

            long now = System.currentTimeMillis();
            tokenCache.put(playerUuid, token);
            tokenExpiration.put(playerUuid, now + TOKEN_TTL_MS);

            return token;
        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenException("Failed to fetch token from login endpoint: " + e.getMessage(), e);
        }
    }

    private void evictExpiredTokens() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) return;
        lastCleanupTime = now;

        tokenExpiration.forEach((uuid, expiry) -> {
            if (expiry < now) {
                tokenCache.remove(uuid);
                tokenExpiration.remove(uuid);
                tokenLocks.remove(uuid);
            }
        });
    }
}
