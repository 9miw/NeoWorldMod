package com.ambition.neoworld.client.event;

import com.ambition.neoworld.client.ClientPartyHudData;
import com.ambition.neoworld.client.ClientTitleData;
import com.ambition.neoworld.client.state.ClientEconomyData;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.client.state.ClientLevelData;

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
        com.ambition.neoworld.client.gui.integration.DungeonDeathGui.reset();
        ClientLevelData.reset();
        ClientEconomyData.reset();
        ClientFashionData.reset();
        ClientPartyHudData.reset();
        com.ambition.neoworld.client.gui.party.PartyScreenController.reset();
        ClientTitleData.reset();
    }
}
