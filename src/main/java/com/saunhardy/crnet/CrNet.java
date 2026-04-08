package com.saunhardy.crnet;

import com.saunhardy.crnet.auth.TokenManager;
import com.saunhardy.crnet.config.CrNetConfig;
import com.saunhardy.crnet.queue.RequestQueue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CrNet.MOD_ID)
public class CrNet {

    public static final String MOD_ID = "crnet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TokenManager tokenManager;
    private static RequestQueue requestQueue;

    public CrNet(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        CrNetConfig config = CrNetConfig.load();
        tokenManager = new TokenManager(config);
        requestQueue = new RequestQueue(config);
        LOGGER.info("CrNet initialised (baseUrl={})", config.getBaseUrl());
    }

    public static TokenManager getTokenManager() {
        return tokenManager;
    }

    public static RequestQueue getRequestQueue() {
        return requestQueue;
    }
}
