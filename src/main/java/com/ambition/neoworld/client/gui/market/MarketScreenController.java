package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.network.MarketActionResultPayload;
import com.ambition.neoworld.network.MarketPagePayload;

import net.minecraft.client.Minecraft;

public final class MarketScreenController {
    private MarketScreenController() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new MarketScreen());
    }

    public static void receivePage(MarketPagePayload payload) {
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) screen.receivePage(payload);
    }

    public static void receiveAction(MarketActionResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof MarketScreen screen) screen.receiveAction(payload);
    }
}
