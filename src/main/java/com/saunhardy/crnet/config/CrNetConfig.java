package com.saunhardy.crnet.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Shared configuration for all CrNet components.
 * <p>
 * Registered as a {@code SERVER} config so values are read from
 * {@code crnet-server.toml} and are per-world on dedicated servers.
 */
public class CrNetConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Network ──────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.IntValue CONNECT_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_MS;
    public static final ModConfigSpec.IntValue MAX_RETRIES;
    public static final ModConfigSpec.IntValue QUEUE_CAPACITY;

    // ── Auth ─────────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> AUTH_MODE;
    public static final ModConfigSpec.ConfigValue<String> JWT_SECRET;
    public static final ModConfigSpec.ConfigValue<String> LOGIN_ENDPOINT;
    public static final ModConfigSpec.IntValue TOKEN_TTL_SECONDS;

    // ── Heartbeat ────────────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue HEARTBEAT_INTERVAL_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> HEARTBEAT_PATH;
    public static final ModConfigSpec.ConfigValue<String> SERVER_ID;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("CrNet — shared backend HTTP client configuration").push("network");

        BASE_URL = BUILDER
                .comment("Base URL of the Createrington backend API")
                .define("baseUrl", "http://localhost:5001");

        CONNECT_TIMEOUT_MS = BUILDER
                .comment("TCP connect timeout in milliseconds")
                .defineInRange("connectTimeoutMs", 5_000, 500, 60_000);

        REQUEST_TIMEOUT_MS = BUILDER
                .comment("Per-request timeout in milliseconds")
                .defineInRange("requestTimeoutMs", 10_000, 1_000, 120_000);

        MAX_RETRIES = BUILDER
                .comment("Maximum retry attempts for transient failures (5xx / I/O errors)")
                .defineInRange("maxRetries", 3, 0, 10);

        QUEUE_CAPACITY = BUILDER
                .comment("Bounded request queue capacity (log-and-drop on overflow)")
                .defineInRange("queueCapacity", 256, 16, 4096);

        BUILDER.pop();

        BUILDER.comment("Authentication settings").push("auth");

        AUTH_MODE = BUILDER
                .comment("Auth strategy: 'self_signed' (PresenceAPI-style HS256 JWT) or 'login_endpoint' (server-issued token)")
                .define("authMode", "self_signed");

        JWT_SECRET = BUILDER
                .comment("Secret key for self-signed JWTs (used when authMode = self_signed)")
                .define("jwtSecret", "");

        LOGIN_ENDPOINT = BUILDER
                .comment("Login path appended to baseUrl (used when authMode = login_endpoint)")
                .define("loginEndpoint", "/auth/login");

        TOKEN_TTL_SECONDS = BUILDER
                .comment("Token TTL in seconds for self-signed JWTs")
                .defineInRange("tokenTtlSeconds", 60, 10, 3600);

        BUILDER.pop();

        BUILDER.comment("Heartbeat settings").push("heartbeat");

        HEARTBEAT_INTERVAL_MINUTES = BUILDER
                .comment("Interval in minutes between heartbeat syncs (0 = disabled)")
                .defineInRange("heartbeatIntervalMinutes", 5, 0, 60);

        HEARTBEAT_PATH = BUILDER
                .comment("Heartbeat endpoint path appended to baseUrl")
                .define("heartbeatPath", "/presence/heartbeat");

        SERVER_ID = BUILDER
                .comment("Server identifier included in heartbeat payloads (empty = omit)")
                .define("serverId", "");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CrNetConfig() {}
}
