package com.ambition.neoworld.client.gui.pvp;

import net.minecraft.client.Minecraft;
import com.ambition.neoworld.network.pvp.PvpGuiDataPayload;
public final class PvpScreenController {
    private PvpScreenController() { }
    public static void open() { if(!PvpMatchScreenController.openCurrent()) Minecraft.getInstance().setScreen(new PvpSelectionScreen()); }
    public static void receive(PvpGuiDataPayload data) {
        if(Minecraft.getInstance().screen instanceof PvpSelectionScreen screen) screen.receive(data);
    }
}
