package com.ambition.neoworld.client.gui.title;

import net.minecraft.client.Minecraft;
import com.ambition.neoworld.network.title.TitleGuiPagePayload;

public final class TitleScreenController {
    private TitleScreenController() {}
    public static void open() { Minecraft.getInstance().setScreen(new TitleScreen()); }
    public static void receive(TitleGuiPagePayload page) {
        if (Minecraft.getInstance().screen instanceof TitleScreen screen) screen.receive(page);
    }
}
