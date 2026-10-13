package com.ambition.neoworld.client.gui.widget;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.resources.ResourceLocation;

/** Noninteractive, reusable PNG Soft Slim indicator. Progress is normalized to 0..1. */
public final class NeoProgressBar implements Renderable {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/progress_soft_slim.png");
    private int x, y, width, height;
    private float progress;
    private int fillColor = 0xFF2FB5A7;
    private boolean loading, indeterminate;
    private boolean enabled = true;
    private boolean showPercentage = true;

    public NeoProgressBar() { this(0, 0, 0, 0); }

    public NeoProgressBar(int x, int y, int width, int height) {
        bounds(x, y, width, height);
    }

    public NeoProgressBar bounds(int x, int y, int width, int height) {
        this.x = x; this.y = y;
        this.width = Math.max(0, width); this.height = Math.max(0, height);
        return this;
    }

    public NeoProgressBar progress(float value) {
        progress = Float.isFinite(value) ? Math.clamp(value, 0F, 1F) : 0F;
        return this;
    }

    public NeoProgressBar progress(double value) {
        return progress(Double.isFinite(value) ? (float) Math.clamp(value, 0D, 1D) : 0F);
    }

    /** ARGB fill tint, independent of the silver frame. */
    public NeoProgressBar color(int argb) { fillColor = argb; return this; }
    public NeoProgressBar loading(boolean value) { loading = value; return this; }
    public NeoProgressBar indeterminate(boolean value) { indeterminate = value; return this; }
    public NeoProgressBar enabled(boolean value) { enabled = value; return this; }
    public NeoProgressBar showPercentage(boolean value) { showPercentage = value; return this; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, Minecraft.getInstance().font);
    }

    public void render(GuiGraphics graphics, Font font) {
        if (width < 3 || height < 3) return;
        RenderSystem.enableBlend();
        try {
            graphics.setColor(enabled ? 1F : 0.55F, enabled ? 1F : 0.55F, enabled ? 1F : 0.55F, 1F);
            renderFrame(graphics);
            int border = Math.min(Math.max(1, Math.round(height * 24F / 140F)), (height - 1) / 2);
            int innerWidth = width - border * 2;
            int innerHeight = height - border * 2;
            if (innerWidth <= 0 || innerHeight <= 0) return;
            float phase = (Util.getMillis() % 1800L) / 1800F;
            int start = indeterminate ? Math.round((innerWidth + innerWidth / 3F) * phase) - innerWidth / 3 : 0;
            int end = indeterminate ? start + Math.max(1, innerWidth / 3) : Math.round(innerWidth * progress);
            int tint = enabled ? fillColor : 0xFF70777E;
            graphics.setColor(((tint >>> 16) & 255) / 255F, ((tint >>> 8) & 255) / 255F,
                    (tint & 255) / 255F, ((tint >>> 24) & 255) / 255F);
            // Row bounds follow the chamfer; no global scissor state is changed.
            int chamfer = Math.min(Math.max(1, height / 8), (innerHeight - 1) / 2);
            for (int row = 0; row < innerHeight; row++) {
                int inset = Math.max(0, chamfer - Math.min(row, innerHeight - 1 - row));
                int left = Math.max(inset, start);
                int right = Math.min(innerWidth - inset, end);
                if (right <= left) continue;
                int u = 130 + Math.round(left * 1270F / innerWidth);
                int sourceWidth = Math.max(1, Math.round((right - left) * 1270F / innerWidth));
                int v = 624 + Math.min(67, row * 68 / innerHeight);
                graphics.blit(TEXTURE, x + border + left, y + border + row, right - left, 1,
                        u, v, sourceWidth, 1, 1536, 1024);
            }
            graphics.setColor(1F, 1F, 1F, 1F);
            if (enabled && (loading || indeterminate)) {
                int sweepWidth = Math.max(2, innerWidth / 12);
                for (int row = 0; row < innerHeight; row++) {
                    int inset = Math.max(0, chamfer - Math.min(row, innerHeight - 1 - row));
                    int sweep = Math.round(phase * (innerWidth + sweepWidth + innerHeight)) - sweepWidth - row;
                    int left = Math.max(Math.max(inset, start), sweep);
                    int right = Math.min(Math.min(innerWidth - inset, end), sweep + sweepWidth);
                    if (right > left) graphics.fill(x + border + left, y + border + row,
                            x + border + right, y + border + row + 1, 0x35FFFFFF);
                }
            }
            String label = Math.round(progress * 100F) + "%";
            if (showPercentage && !indeterminate && innerHeight >= font.lineHeight + 2
                    && innerWidth >= font.width(label) + 4) {
                graphics.drawString(font, label, x + (width - font.width(label)) / 2,
                        y + (height - font.lineHeight) / 2, enabled ? 0xFFFFFFFF : 0xFFABB4BA, true);
            }
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }

    private void renderFrame(GuiGraphics graphics) {
        int cap = Math.min(Math.max(1, Math.round(height * 52F / 140F)), width / 2);
        int edge = Math.min(Math.max(1, Math.round(height * 24F / 140F)), height / 2);
        int[] dw = {cap, width - 2 * cap, cap};
        int[] dh = {edge, height - 2 * edge, edge};
        int[] sw = {52, 1278, 52};
        int[] sh = {24, 92, 24};
        int dy = y, v = 264;
        for (int row = 0; row < 3; row++) {
            int dx = x, u = 77;
            for (int col = 0; col < 3; col++) {
                if (dw[col] > 0 && dh[row] > 0) graphics.blit(TEXTURE, dx, dy, dw[col], dh[row],
                        u, v, sw[col], sh[row], 1536, 1024);
                dx += dw[col]; u += sw[col];
            }
            dy += dh[row]; v += sh[row];
        }
    }
}
