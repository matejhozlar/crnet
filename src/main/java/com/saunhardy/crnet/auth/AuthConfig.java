package com.saunhardy.crnet.auth;

/**
 * Sealed marker interface for authentication configuration.
 * <p>
 * Used by {@link com.saunhardy.crnet.CRNetClient.Builder} to accept either a
 * ready-to-use {@link AuthStrategy} or a deferred {@link LoginEndpointAuthConfig}.
 */
public sealed interface AuthConfig permits AuthStrategy, LoginEndpointAuthConfig {
}
