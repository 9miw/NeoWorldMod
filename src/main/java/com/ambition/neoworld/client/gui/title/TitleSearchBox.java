package com.ambition.neoworld.client.gui.title;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Vanilla editing with render, cursor hit testing and text layout at one scale. */
final class TitleSearchBox extends EditBox {
    private final int logicalWidth, logicalHeight;
    TitleSearchBox(Font font, int x, int y, int width, int height, int logicalWidth, int logicalHeight) {
        super(font, x, y, width, height, Component.literal("ค้นหาฉายา"));
        this.logicalWidth = logicalWidth; this.logicalHeight = logicalHeight;
        setBordered(false); setMaxLength(48); setHint(Component.literal("ค้นหาฉายา..."));
    }
    private boolean logical(BooleanSupplier action) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        setX(0); setY(0); setWidth(logicalWidth); setHeight(logicalHeight);
        try { return action.getAsBoolean(); }
        finally { setX(x); setY(y); setWidth(w); setHeight(h); }
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        float sx = getWidth() / (float) logicalWidth, sy = getHeight() / (float) logicalHeight;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(sx, sy, 1);
        try { logical(() -> { super.renderWidget(g, (int) ((mouseX - x) / sx), (int) ((mouseY - y) / sy), partialTick); return true; }); }
        finally { g.pose().popPose(); }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = (x - getX()) * logicalWidth / getWidth(), my = (y - getY()) * logicalHeight / getHeight();
        return logical(() -> super.mouseClicked(mx, my, button));
    }
    @Override public boolean charTyped(char c, int modifiers) { return logical(() -> super.charTyped(c, modifiers)); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return logical(() -> super.keyPressed(key, scan, modifiers)); }
}
