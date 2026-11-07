package com.ambition.neoworld.client.gui.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Vanilla input with feature PNGs and text scaled from the same logical bounds. */
final class TitleButton extends Button {
    private final int logicalWidth, logicalHeight;
    private boolean primary, selected;
    TitleButton(Component label, int x, int y, int width, int height, int logicalWidth, int logicalHeight, Runnable action) {
        super(Button.builder(label, b -> action.run()).bounds(x, y, width, height));
        this.logicalWidth = logicalWidth; this.logicalHeight = logicalHeight;
    }
    TitleButton primary() { primary = true; return this; }
    TitleButton selected(boolean value) { selected = value; return this; }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose(); g.pose().translate(getX(), getY(), 0);
        g.pose().scale(getWidth() / (float) logicalWidth, getHeight() / (float) logicalHeight, 1);
        int state = !active ? 3 : isHoveredOrFocused() ? 1 : primary || selected ? 2 : 0;
        TitleTextures.control(g, state, 0, 0, logicalWidth, logicalHeight);
        var font = Minecraft.getInstance().font;
        var label = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), logicalWidth - 8));
        g.drawString(font, label, (logicalWidth - font.width(label)) / 2, (logicalHeight - 9) / 2,
                active ? 0xFFF0F4F6 : 0xFF79838B, false);
        g.pose().popPose();
    }
}
