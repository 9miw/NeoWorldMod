package com.ambition.neoworld.client.gui.party;

import com.ambition.neoworld.network.PartyStatePayload;

import net.minecraft.client.Minecraft;

public final class PartyScreenController {
    private PartyScreenController() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PartyScreen());
    }

    public static void receiveState(PartyStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof PartyScreen screen) {
            screen.receiveState(payload);
        }
    }
}
