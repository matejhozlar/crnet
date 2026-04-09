package com.saunhardy.crnet;

import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.config.CRNetConfig;
import com.saunhardy.crnet.http.BackendHttpClient;
import com.saunhardy.crnet.presence.HeartbeatService;
import com.saunhardy.crnet.queue.RequestQueue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Mod(CRNet.MOD_ID)
public class CRNet {

    public static final String MOD_ID = "crnet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TokenManager tokenManager;
    private static BackendHttpClient httpClient;
    private static RequestQueue requestQueue;
    private static HeartbeatService heartbeatService;

    public CRNet(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CRNetConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        HttpClient sharedHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // explicit — do not change without a version bump
                .connectTimeout(Duration.ofMillis(CRNetConfig.CONNECT_TIMEOUT_MS.get()))
                .build();

        tokenManager = new TokenManager(sharedHttpClient);
        httpClient = new BackendHttpClient(tokenManager, sharedHttpClient);
        requestQueue = new RequestQueue();
        heartbeatService = new HeartbeatService(requestQueue, httpClient);
        LOGGER.info("CRNet initialised (baseUrl={}, authMode={})",
                CRNetConfig.BASE_URL.get(), CRNetConfig.AUTH_MODE.get());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (heartbeatService != null) {
            heartbeatService.start(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping — shutting down CRNet");
        if (heartbeatService != null) heartbeatService.shutdown();
        if (requestQueue != null) requestQueue.shutdown();
    }

    public static TokenManager getTokenManager() {
        return tokenManager;
    }

    public static BackendHttpClient getHttpClient() {
        return httpClient;
    }

    public static RequestQueue getRequestQueue() {
        return requestQueue;
    }

    public static HeartbeatService getHeartbeatService() {
        return heartbeatService;
    }
}
