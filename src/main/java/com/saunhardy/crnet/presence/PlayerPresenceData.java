package com.saunhardy.crnet.presence;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable data object representing a player's presence state.
 * <p>
 * Ported from PresenceAPI. Unlike the original, this builder does <strong>not</strong>
 * read config flags — the consuming mod decides what data to include.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PlayerPresenceData data = PlayerPresenceData.fromPlayer(player, "joined")
 *         .displayName(player.getDisplayName().getString())
 *         .gamemode(player.gameMode.getGameModeForPlayer())
 *         .build();
 * }</pre>
 */
public class PlayerPresenceData {

    private final String minecraftUsername;
    private final String uuid;
    private final String state;
    private final long timestamp;

    @Nullable private final String serverId;
    @Nullable private final String displayName;
    @Nullable private final String gamemode;
    @Nullable private final String dimension;
    @Nullable private final Double x;
    @Nullable private final Double y;
    @Nullable private final Double z;
    @Nullable private final Float health;
    @Nullable private final Integer experienceLevel;
    @Nullable private final String ipAddress;

    private PlayerPresenceData(Builder builder) {
        this.minecraftUsername = builder.minecraftUsername;
        this.uuid = builder.uuid;
        this.state = builder.state;
        this.timestamp = builder.timestamp;
        this.serverId = builder.serverId;
        this.displayName = builder.displayName;
        this.gamemode = builder.gamemode;
        this.dimension = builder.dimension;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.health = builder.health;
        this.experienceLevel = builder.experienceLevel;
        this.ipAddress = builder.ipAddress;
    }

    /**
     * Serialises this presence data to a JSON object.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("minecraftUsername", minecraftUsername);
        json.addProperty("uuid", uuid);
        json.addProperty("state", state);
        json.addProperty("timestamp", timestamp);

        if (serverId != null) json.addProperty("serverId", serverId);
        if (displayName != null) json.addProperty("displayName", displayName);
        if (gamemode != null) json.addProperty("gamemode", gamemode);
        if (dimension != null) json.addProperty("dimension", dimension);
        if (ipAddress != null) json.addProperty("ipAddress", ipAddress);
        if (experienceLevel != null) json.addProperty("experienceLevel", experienceLevel);
        if (health != null) json.addProperty("health", health);

        if (x != null && y != null && z != null) {
            JsonObject position = new JsonObject();
            position.addProperty("x", x);
            position.addProperty("y", y);
            position.addProperty("z", z);
            json.add("position", position);
        }

        return json;
    }

    /**
     * Creates a new builder pre-populated with the player's username and UUID.
     *
     * @param player the server player
     * @param state  event state (e.g. {@code "joined"}, {@code "left"})
     * @return a new builder
     */
    public static Builder fromPlayer(ServerPlayer player, String state) {
        return new Builder()
                .minecraftUsername(player.getGameProfile().getName())
                .uuid(player.getStringUUID())
                .state(state);
    }

    public static class Builder {
        private String minecraftUsername;
        private String uuid;
        private String state;
        private long timestamp = System.currentTimeMillis();

        @Nullable private String serverId;
        @Nullable private String displayName;
        @Nullable private String gamemode;
        @Nullable private String dimension;
        @Nullable private Double x;
        @Nullable private Double y;
        @Nullable private Double z;
        @Nullable private Float health;
        @Nullable private Integer experienceLevel;
        @Nullable private String ipAddress;

        public Builder minecraftUsername(String minecraftUsername) { this.minecraftUsername = minecraftUsername; return this; }
        public Builder uuid(String uuid) { this.uuid = uuid; return this; }
        public Builder state(String state) { this.state = state; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder serverId(@Nullable String serverId) { this.serverId = serverId; return this; }

        public Builder displayName(@Nullable String displayName) { this.displayName = displayName; return this; }

        public Builder gamemode(@Nullable GameType gameType) {
            this.gamemode = gameType != null ? gameType.getName() : null;
            return this;
        }

        public Builder dimension(@Nullable String dimension) { this.dimension = dimension; return this; }

        public Builder position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder health(float health) { this.health = health; return this; }
        public Builder experienceLevel(int level) { this.experienceLevel = level; return this; }
        public Builder ipAddress(@Nullable String ipAddress) { this.ipAddress = ipAddress; return this; }

        public PlayerPresenceData build() {
            if (minecraftUsername == null) throw new IllegalStateException("minecraftUsername is required");
            if (uuid == null) throw new IllegalStateException("uuid is required");
            if (state == null) throw new IllegalStateException("state is required");
            return new PlayerPresenceData(this);
        }
    }
}
