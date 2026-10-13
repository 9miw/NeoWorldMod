package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.client.ClientPartyHudData;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public final class PartyOverlay implements LayeredDraw.Layer {
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 3;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        var members = ClientPartyHudData.members();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui || members.isEmpty()) return;
        int rowWidth = Math.min(180, graphics.guiWidth() / 2 - 16);
        int rowHeight = Math.min(20, (graphics.guiHeight() - 56) / members.size());
        if (rowWidth < BAR_WIDTH || rowHeight < 20) return;
        int x = 8;
        int y = 8;
        var font = minecraft.font;
        for (var member : members) {
            boolean available = member.maxHealth() > 0;
            String level = available ? "Lv." + member.level() : "Lv.-";
            int levelWidth = font.width(level);
            graphics.drawString(font, level, x, y, 0xFFE5C578, true);
            int nameX = x + levelWidth + font.width(" ");
            graphics.drawString(font, font.plainSubstrByWidth(member.name(), rowWidth - (nameX - x)),
                    nameX, y, 0xFFE5ECEF, true);
            int barY = y + font.lineHeight + 3;
            float ratio = available ? member.health() / member.maxHealth() : 0;
            graphics.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF252F34);
            int filled = Math.round(BAR_WIDTH * ratio);
            if (filled > 0) graphics.fill(x, barY, x + filled, barY + BAR_HEIGHT,
                    ratio > 0.3F ? 0xFF267F70 : 0xFF9D4146);
            y += rowHeight;
        }
    }
}
