package com.ambition.neoworld.client.gui.hub;

import net.minecraft.client.Minecraft;

public final class MainHubScreenController {
    private MainHubScreenController() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new MainHubScreen());
    }
}
