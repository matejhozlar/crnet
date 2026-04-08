package com.saunhardy.crnet.config;

/**
 * Shared configuration primitives consumed by all CrNet components.
 * <p>
 * Loaded once during {@code FMLCommonSetupEvent} and passed into each
 * sub-system (HTTP client, token manager, request queue) at construction time.
 * Values are read from the NeoForge config file {@code crnet-server.toml}.
 */
public class CrNetConfig {

    // TODO: wire up NeoForge config system (ForgeConfigSpec / ModConfigEvent)

    private final String baseUrl;
    private final String jwtSecret;
    private final int connectTimeoutMs;
    private final int requestTimeoutMs;
    private final int maxRetries;
    private final int queueCapacity;

    private CrNetConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.jwtSecret = builder.jwtSecret;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.requestTimeoutMs = builder.requestTimeoutMs;
        this.maxRetries = builder.maxRetries;
        this.queueCapacity = builder.queueCapacity;
    }

    /** Load config from the NeoForge config file. */
    public static CrNetConfig load() {
        // TODO: read from ForgeConfigSpec values
        return new Builder().build();
    }

    public String getBaseUrl() { return baseUrl; }
    public String getJwtSecret() { return jwtSecret; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public int getQueueCapacity() { return queueCapacity; }

    public static class Builder {
        private String baseUrl = "http://localhost:5001";
        private String jwtSecret = "";
        private int connectTimeoutMs = 5_000;
        private int requestTimeoutMs = 10_000;
        private int maxRetries = 3;
        private int queueCapacity = 256;

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder jwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; return this; }
        public Builder connectTimeoutMs(int ms) { this.connectTimeoutMs = ms; return this; }
        public Builder requestTimeoutMs(int ms) { this.requestTimeoutMs = ms; return this; }
        public Builder maxRetries(int retries) { this.maxRetries = retries; return this; }
        public Builder queueCapacity(int capacity) { this.queueCapacity = capacity; return this; }

        public CrNetConfig build() { return new CrNetConfig(this); }
    }
}
