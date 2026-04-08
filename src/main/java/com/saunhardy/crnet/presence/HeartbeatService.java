package com.saunhardy.crnet.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.saunhardy.crnet.CrNet;
import com.saunhardy.crnet.config.CrNetConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodic heartbeat that syncs the full online player list to the backend.
 * <p>
 * Ported from PresenceAPI's {@code HeartbeatService}. Uses the shared
 * {@link com.saunhardy.crnet.http.BackendHttpClient} and
 * {@link com.saunhardy.crnet.queue.RequestQueue} instead of a dedicated executor.
 */
public class HeartbeatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatService.class);

    private final ScheduledExecutorService scheduler;
    private volatile MinecraftServer server;

    public HeartbeatService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crnet-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the heartbeat scheduler. Called on server start.
     *
     * @param server the Minecraft server instance
     */
    public void start(MinecraftServer server) {
        this.server = server;
        int intervalMinutes = CrNetConfig.HEARTBEAT_INTERVAL_MINUTES.get();
        if (intervalMinutes <= 0) {
            LOGGER.info("Heartbeat disabled (interval = 0)");
            return;
        }
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        LOGGER.info("Heartbeat scheduled every {} minute(s)", intervalMinutes);
    }

    private void sendHeartbeat() {
        if (server == null) return;
        try {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();

            JsonArray playersArray = new JsonArray();
            for (ServerPlayer player : players) {
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", player.getStringUUID());
                obj.addProperty("username", player.getGameProfile().getName());
                playersArray.add(obj);
            }

            JsonObject payload = new JsonObject();
            payload.add("players", playersArray);
            payload.addProperty("timestamp", System.currentTimeMillis());

            String serverId = CrNetConfig.SERVER_ID.get();
            if (serverId != null && !serverId.isEmpty()) {
                payload.addProperty("serverId", serverId);
            }

            String heartbeatPath = CrNetConfig.HEARTBEAT_PATH.get();
            CrNet.getRequestQueue().submit(() -> {
                try {
                    CrNet.getHttpClient().postFireAndForget(heartbeatPath, payload.toString());
                    LOGGER.debug("Heartbeat sent ({} players)", players.size());
                } catch (Exception e) {
                    LOGGER.error("Failed to send heartbeat: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to build heartbeat payload", e);
        }
    }

    /**
     * Shuts down the heartbeat scheduler. Called on server stop.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
