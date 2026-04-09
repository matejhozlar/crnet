package com.saunhardy.crnet;

import com.saunhardy.crnet.config.CRNetConfig;
import com.saunhardy.crnet.queue.RequestQueue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * CRNet mod bootstrap.
 * <p>
 * Initialises the shared infrastructure (HTTP client, request queue) that all
 * {@link CRNetClient} instances use. Per-client configuration (base URL, auth
 * strategy, heartbeat) is handled by {@link CRNetClient.Builder}.
 */
@Mod(CRNet.MOD_ID)
public class CRNet {

    public static final String MOD_ID = "crnet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HttpClient sharedHttpClient;
    private static RequestQueue requestQueue;

    public CRNet(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CRNetConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        sharedHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // explicit — do not change without a version bump
                .connectTimeout(Duration.ofMillis(CRNetConfig.CONNECT_TIMEOUT_MS.get()))
                .build();

        requestQueue = new RequestQueue(CRNetConfig.QUEUE_CAPACITY.get());
        LOGGER.info("CRNet initialised");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping — shutting down CRNet");
        if (requestQueue != null) requestQueue.shutdown();
        if (sharedHttpClient != null) {
            sharedHttpClient.close();
            sharedHttpClient = null;
        }
    }

    /**
     * Returns the shared {@link HttpClient} (HTTP/1.1 enforced).
     * Package-private — used by {@link CRNetClient.Builder}.
     */
    static HttpClient getSharedHttpClient() {
        return sharedHttpClient;
    }

    /**
     * Returns the shared {@link RequestQueue}.
     * Package-private — used by {@link CRNetClient.Builder}.
     */
    static RequestQueue getRequestQueue() {
        return requestQueue;
    }
}
