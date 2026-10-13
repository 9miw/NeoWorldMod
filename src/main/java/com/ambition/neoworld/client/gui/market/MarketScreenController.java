package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.network.market.MarketActionResultPayload;
import com.ambition.neoworld.network.market.MarketPagePayload;
import com.ambition.neoworld.network.market.MarketManagementPagePayload;

import net.minecraft.client.Minecraft;

public final class MarketScreenController {
    private MarketScreenController() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new MarketScreen());
    }

    public static void receivePage(MarketPagePayload payload) {
        MarketScreen screen = NeoPopupScreen.findScreen(MarketScreen.class);
        if (screen != null) screen.receivePage(payload);
    }

    public static void receiveAction(MarketActionResultPayload payload) {
        MarketSellScreen sell = NeoPopupScreen.findScreen(MarketSellScreen.class);
        if (sell != null) {
            sell.receiveAction(payload);
            return;
        }
        MarketManagementScreen management = NeoPopupScreen.findScreen(MarketManagementScreen.class);
        if (management != null) {
            management.receiveAction(payload);
            return;
        }
        MarketScreen screen = NeoPopupScreen.findScreen(MarketScreen.class);
        if (screen != null) screen.receiveAction(payload);
    }

    public static void receiveManagementPage(MarketManagementPagePayload payload) {
        MarketManagementScreen screen = NeoPopupScreen.findScreen(MarketManagementScreen.class);
        if (screen != null) screen.receivePage(payload);
    }
}
