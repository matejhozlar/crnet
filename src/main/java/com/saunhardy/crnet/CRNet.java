package com.saunhardy.crnet;

import com.saunhardy.crnet.config.CRNetConfig;
import com.saunhardy.crnet.queue.RequestQueue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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
        modContainer.registerConfig(ModConfig.Type.COMMON, CRNetConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Shared infrastructure is mod-lifetime: it is created once here and never torn down,
        // so consumers can build CRNetClients across multiple server sessions in the same JVM.
        sharedHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // explicit — do not change without a version bump
                .connectTimeout(Duration.ofMillis(CRNetConfig.CONNECT_TIMEOUT_MS.get()))
                .build();

        requestQueue = new RequestQueue(CRNetConfig.THREAD_POOL_SIZE.get(), CRNetConfig.QUEUE_CAPACITY.get());
        LOGGER.info("CRNet initialised");
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
