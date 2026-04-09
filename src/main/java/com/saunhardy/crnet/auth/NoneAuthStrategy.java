package com.saunhardy.crnet.auth;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * No-op auth strategy that produces no token.
 * <p>
 * When this strategy is active, {@link com.saunhardy.crnet.http.BackendHttpClient}
 * skips the {@code Authorization} header entirely.
 */
public class NoneAuthStrategy implements AuthStrategy {

    @Override
    public @Nullable String getToken(@Nullable UUID playerUuid) {
        return null;
    }

    @Override
    public void invalidate(@Nullable UUID playerUuid) {
        // No-op
    }
}
