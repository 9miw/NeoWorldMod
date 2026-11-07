package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Measured PNG regions for the approved Teal Smoke modal, with dynamic opacity. */
public final class NeoPopupTextures {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "neoworld", "textures/gui/common/popup_teal_smoke.png");
    private NeoPopupTextures() {}

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        try {
            // Interior texture only: omit the backing sprite's decorative corners.
            graphics.setColor(1, 1, 1, 0.55F);
            if (width > 4 && height > 4) graphics.blit(TEXTURE, x + 2, y + 2, width - 4, height - 4,
                    100, 160, 590, 260, 1536, 1024);
            graphics.setColor(1, 1, 1, 1);
            slices(graphics, x, y, width, height, 795, 106, 695, 374, 40, 4, 1F);
        } finally { graphics.setColor(1, 1, 1, 1); }
    }

    static void plaque(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, 1);
        try { slices(graphics, x, y, width, height, 460, 560, 617, 105, 30, 4, 0.8F); }
        finally { graphics.setColor(1, 1, 1, 1); }
    }

    public static void button(GuiGraphics graphics, NeoButton button) {
        boolean highlighted = button.active && button.isHoveredOrFocused();
        float shade = !button.active ? 0.45F : highlighted ? 1F : 0.88F;
        graphics.setColor(shade, shade, shade, 1);
        try {
            int sourceY = button.getStyle() == NeoButton.Style.PRIMARY ? 701 : 845;
            if (button.active && button.getStyle() == NeoButton.Style.DESTRUCTIVE)
                graphics.setColor(shade, shade * 0.45F, shade * 0.45F, 1);
            else if (button.active && button.isSelected()) graphics.setColor(shade, shade * 0.82F, shade * 0.4F, 1);
            else if (highlighted && sourceY == 845) graphics.setColor(0.65F, 1, 1, 1);
            slices(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                    460, sourceY, 617, 108, 32, 4, 0.9F);
        } finally { graphics.setColor(1, 1, 1, 1); }
    }

    private static void slices(GuiGraphics graphics, int x, int y, int width, int height,
                               int u, int v, int sourceWidth, int sourceHeight,
                               int sourceEdge, int edge, float centerAlpha) {
        int corner = Math.min(edge, Math.min(width, height) / 2);
        int[] dw = {corner, width - 2 * corner, corner};
        int[] dh = {corner, height - 2 * corner, corner};
        int[] sw = {sourceEdge, sourceWidth - 2 * sourceEdge, sourceEdge};
        int[] sh = {sourceEdge, sourceHeight - 2 * sourceEdge, sourceEdge};
        // Capture caller tint and restore it after varying the center opacity.
        float[] tint = RenderSystem.getShaderColor().clone();
        int dy = y, sv = v;
        for (int row = 0; row < 3; row++) {
            int dx = x, su = u;
            for (int col = 0; col < 3; col++) {
                graphics.setColor(tint[0], tint[1], tint[2], tint[3] * (row == 1 && col == 1 ? centerAlpha : 1F));
                if (dw[col] > 0 && dh[row] > 0) graphics.blit(TEXTURE, dx, dy, dw[col], dh[row],
                        su, sv, sw[col], sh[row], 1536, 1024);
                dx += dw[col]; su += sw[col];
            }
            dy += dh[row]; sv += sh[row];
        }
        graphics.setColor(tint[0], tint[1], tint[2], tint[3]);
    }
}
