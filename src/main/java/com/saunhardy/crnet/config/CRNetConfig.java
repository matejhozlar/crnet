package com.saunhardy.crnet.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Global infrastructure configuration for CRNet.
 * <p>
 * Contains only shared settings that apply to all clients (timeouts, queue capacity,
 * retry policy). Per-client settings (base URL, auth strategy, heartbeat) are
 * provided via {@link com.saunhardy.crnet.CRNetClient.Builder}.
 * <p>
 * Registered as a {@code SERVER} config so values are read from
 * {@code crnet-server.toml} and are per-world on dedicated servers.
 */
public class CRNetConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue QUEUE_CAPACITY;
    public static final ModConfigSpec.IntValue CONNECT_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue MAX_RETRIES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("CRNet — shared backend HTTP client configuration").push("network");

        QUEUE_CAPACITY = BUILDER
                .comment("Bounded request queue capacity (log-and-drop on overflow)")
                .defineInRange("queueCapacity", 256, 16, 4096);

        CONNECT_TIMEOUT_MS = BUILDER
                .comment("TCP connect timeout in milliseconds")
                .defineInRange("connectTimeoutMs", 5_000, 500, 60_000);

        REQUEST_TIMEOUT_MS = BUILDER
                .comment("Per-request timeout in milliseconds")
                .defineInRange("requestTimeoutMs", 10_000, 1_000, 120_000);

        MAX_RETRIES = BUILDER
                .comment("Maximum retry attempts for transient failures (5xx / I/O errors)")
                .defineInRange("maxRetries", 3, 0, 10);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CRNetConfig() {}
}
