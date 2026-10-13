package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.network.fashion.FashionActionResultPayload;

import net.minecraft.client.Minecraft;

public final class FashionScreenController {
    private FashionScreenController() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FashionScreen());
    }

    public static void receiveAction(FashionActionResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof FashionScreen screen) {
            screen.receiveAction(payload);
        }
    }
}
