package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;

import net.minecraft.client.Minecraft;

public final class DungeonBrowserScreenController {
    private DungeonBrowserScreenController() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new DungeonBrowserScreen());
    }

    public static void receiveData(DungeonBrowserDataPayload payload) {
        if (Minecraft.getInstance().screen instanceof DungeonBrowserScreen screen) {
            screen.receiveData(payload);
        }
    }
}
