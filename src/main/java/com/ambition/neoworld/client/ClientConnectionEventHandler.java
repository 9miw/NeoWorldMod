package com.ambition.neoworld.client;

import com.ambition.neoworld.NeoWorld;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class ClientConnectionEventHandler {
    private ClientConnectionEventHandler() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLevelData.reset();
        ClientEconomyData.reset();
    }
}
