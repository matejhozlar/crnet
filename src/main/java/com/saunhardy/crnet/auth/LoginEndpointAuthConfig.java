package com.saunhardy.crnet.auth;

import java.net.http.HttpClient;

/**
 * Deferred configuration for {@link LoginEndpointStrategy}.
 * <p>
 * Created by {@link AuthStrategy#loginEndpoint(String)} and materialised into a real
 * {@link LoginEndpointStrategy} by {@link com.saunhardy.crnet.CRNetClient.Builder#build()},
 * which supplies the {@code HttpClient} and {@code baseUrl}.
 */
public final class LoginEndpointAuthConfig implements AuthConfig {

    private final String loginPath;

    LoginEndpointAuthConfig(String loginPath) {
        this.loginPath = loginPath;
    }

    public String getLoginPath() {
        return loginPath;
    }

    /**
     * Creates the real auth strategy once the client's base URL and HTTP client are known.
     */
    public AuthStrategy create(HttpClient httpClient, String baseUrl) {
        return new LoginEndpointStrategy(httpClient, baseUrl, loginPath);
    }
}
