package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.client.gui.widget.NeoProgressBar;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import com.ambition.neoworld.network.dungeon.DungeonLoadingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DungeonLoadingScreen extends Screen {
    private final int roomId;
    private final NeoProgressBar progress = new NeoProgressBar().indeterminate(true).showPercentage(false);

    private DungeonLoadingScreen(DungeonLoadingPayload payload) {
        super(Component.literal(payload.title()));
        roomId = payload.roomId();
    }

    public static void receive(DungeonLoadingPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.visible()) {
            client.setScreen(new DungeonLoadingScreen(payload));
        } else if (client.screen instanceof DungeonLoadingScreen screen && screen.roomId == payload.roomId()) {
            client.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xED10171A);
        int panelWidth = Math.min(280, Math.max(1, width - 24));
        int left = (width - panelWidth) / 2;
        int top = height / 2 - 42;
        new NeoTitlePlaque(left, top, panelWidth, 30,
                Component.literal(font.plainSubstrByWidth(title.getString(), Math.max(1, panelWidth - 32))))
                .render(graphics, font);
        graphics.drawCenteredString(font, "กำลังเตรียมดันเจี้ยน…", width / 2, top + 44, 0xE7ECEF);
        progress.bounds(left + 12, top + 65, Math.max(1, panelWidth - 24), 12).render(graphics, font);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
