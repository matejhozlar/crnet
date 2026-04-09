package com.saunhardy.crnet.auth;

import com.saunhardy.crnet.config.CrNetConfig;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

/**
 * Shared JWT token manager.
 * <p>
 * Delegates to the configured {@link AuthStrategy} (selected by the
 * {@code auth.authMode} config value). Thread-safe — multiple mods on the same
 * server instance share one {@code TokenManager} via
 * {@link com.saunhardy.crnet.CrNet#getTokenManager()}.
 *
 * <h3>Auth modes</h3>
 * <ul>
 *   <li>{@code self_signed} — generates an HS256 JWT locally per request (PresenceAPI pattern)</li>
 *   <li>{@code login_endpoint} — fetches a server-issued token per player (Currency mod pattern)</li>
 * </ul>
 */
public class TokenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);

    private final AuthStrategy strategy;

    private static final List<String> VALID_AUTH_MODES = List.of("self_signed", "login_endpoint");

    public TokenManager(HttpClient httpClient) {
        String mode = CrNetConfig.AUTH_MODE.get();
        if (!VALID_AUTH_MODES.contains(mode)) {
            LOGGER.warn("Unknown auth mode '{}', falling back to 'self_signed'. Valid values: {}", mode, VALID_AUTH_MODES);
        }
        if ("login_endpoint".equals(mode)) {
            this.strategy = new LoginEndpointStrategy(httpClient);
            LOGGER.info("TokenManager using login_endpoint auth strategy");
        } else {
            this.strategy = new SelfSignedJwtStrategy();
            LOGGER.info("TokenManager using self_signed auth strategy");
        }
    }

    /**
     * Returns a valid JWT for server-level auth (playerUuid = null).
     *
     * @return bearer token string (without the {@code "Bearer "} prefix)
     * @throws TokenException if authentication fails
     */
    public String getToken() throws TokenException {
        return getToken(null);
    }

    /**
     * Returns a valid JWT, optionally scoped to a specific player.
     *
     * @param playerUuid player UUID for per-player auth, or {@code null} for server-level auth
     * @return bearer token string (without the {@code "Bearer "} prefix)
     * @throws TokenException if authentication fails
     */
    public String getToken(@Nullable UUID playerUuid) throws TokenException {
        return strategy.getToken(playerUuid);
    }

    /**
     * Clears cached tokens, forcing re-authentication on the next call.
     * Call this when a downstream request receives a 401 response.
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
}
