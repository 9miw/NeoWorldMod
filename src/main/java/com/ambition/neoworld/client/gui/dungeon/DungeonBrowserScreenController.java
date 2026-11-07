package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import com.ambition.neoworld.network.dungeon.DungeonBrowserQueryPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonBrowserScreenController {
    private static boolean opening;
    private static Screen openingFrom;
    private DungeonBrowserScreenController() {
    }

    public static void open() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || opening) return;
        opening = true;
        openingFrom = minecraft.screen;
        PacketDistributor.sendToServer(DungeonBrowserQueryPayload.INSTANCE);
    }

    public static void cancelOpen() { opening = false; openingFrom = null; }

    public static void receiveData(DungeonBrowserDataPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (opening) {
            boolean stillRequested = minecraft.screen == openingFrom;
            cancelOpen();
            if (!stillRequested) return;
            minecraft.setScreen(new DungeonBrowserScreen());
        }
        if (minecraft.screen instanceof DungeonBrowserScreen screen) {
            screen.receiveData(payload);
        }
    }
}
