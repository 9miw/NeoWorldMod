package com.ambition.neoworld.client.gui.style;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class NeoGuiRender {
    private NeoGuiRender() {
    }

    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(NeoGuiStyle.MARKET_FRAME_TEXTURE, x, y, width, height, 0, 0,
                NeoGuiStyle.FRAME_TEXTURE_WIDTH, NeoGuiStyle.FRAME_TEXTURE_HEIGHT,
                NeoGuiStyle.FRAME_TEXTURE_WIDTH, NeoGuiStyle.FRAME_TEXTURE_HEIGHT);
    }

    public static void widget(GuiGraphics graphics, int x, int y, int width, int height,
                              int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(NeoGuiStyle.MARKET_WIDGET_TEXTURE, x, y, width, height, u, v,
                sourceWidth, sourceHeight, NeoGuiStyle.WIDGET_TEXTURE_WIDTH, NeoGuiStyle.WIDGET_TEXTURE_HEIGHT);
    }

    public static void separator(GuiGraphics graphics, int startX, int endX, int y) {
        graphics.fill(startX, y, endX, y + 1, 0xFF394147);
        graphics.fill(startX + 2, y + 1, endX - 2, y + 2, 0xFF171B1E);
    }

    public static void centered(GuiGraphics graphics, Font font, String text, int x, int width, int y, int color) {
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }

    public static void right(GuiGraphics graphics, Font font, String text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    public static String trim(Font font, String text, int maxWidth) {
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
