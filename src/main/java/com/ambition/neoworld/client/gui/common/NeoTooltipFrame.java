package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Native Deepslate pixel panel with premium-only atlas animations. */
public final class NeoTooltipFrame {
    public static final int OUTER_MARGIN = 16;
    private static final ResourceLocation EPIC_PULSE = sprite("epic_pulse");
    private static final ResourceLocation GOLD_PULSE = sprite("legendary_pulse");
    private static final ResourceLocation SPARK = sprite("cross_spark");
    private static final ResourceLocation GOLD_FLOW = sprite("gold_flow");
    private NeoTooltipFrame() {}

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "tooltip/deepslate/" + name);
    }

    public static void render(GuiGraphics graphics, NeoTooltipRarity rarity, int textX, int textY, int textWidth, int textHeight) {
        int n = rarity.cornerSize(), padding = rarity.padding();
        int x = textX - padding, y = textY - padding;
        int width = textWidth + padding * 2, height = textHeight + padding * 2;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 399);
            RenderSystem.enableBlend();
            // Opaque center color is sampled from the real BG PNG, not a second generic backing.
            part(graphics, rarity, x + n, y + n, width - n * 2, height - n * 2, 127, 63, 1, 1);
            part(graphics, rarity, x, y, n, n, 0, 0, n, n);
            part(graphics, rarity, x + width - n, y, n, n, 16, 0, n, n);
            part(graphics, rarity, x, y + height - n, n, n, 0, 16, n, n);
            part(graphics, rarity, x + width - n, y + height - n, n, n, 16, 16, n, n);
            horizontal(graphics, rarity, x + n, y, width - n * 2, n, 32);
            horizontal(graphics, rarity, x + n, y + height - n, width - n * 2, n, 48);
            vertical(graphics, rarity, x, y + n, height - n * 2, n, 32);
            vertical(graphics, rarity, x + width - n, y + n, height - n * 2, n, 48);
            if (rarity.premium()) {
                tinted(graphics, rarity, rarity == NeoTooltipRarity.EPIC ? EPIC_PULSE : GOLD_PULSE,
                        x, y, width, height, 0.65f);
                tinted(graphics, rarity, SPARK, x - 6, y - 6, width + 12, height + 12,
                        rarity == NeoTooltipRarity.LEGENDARY ? 0.85f : 0.6f);
            }
            if (rarity == NeoTooltipRarity.LEGENDARY) {
                int rx = rarity.railOffset(), ry = rarity.railOffsetY();
                flow(graphics, rarity, x + n, y + ry, width - n * 2, 0);
                flow(graphics, rarity, x + width - rx, y + n, height - n * 2, 90);
                flow(graphics, rarity, x + width - n, y + height - ry, width - n * 2, 180);
                flow(graphics, rarity, x + rx, y + height - n, height - n * 2, 270);
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void horizontal(GuiGraphics g, NeoTooltipRarity r, int x, int y, int length, int height, int u) {
        for (int offset = 0; offset < length; offset += 12) {
            int count = Math.min(12, length - offset);
            part(g, r, x + offset, y, count, height, u, 0, count, height);
        }
        // The end of a partial tile must still meet the corner's exact attachment profile.
        part(g, r, x + length - 1, y, 1, height, u + 11, 0, 1, height);
    }

    private static void vertical(GuiGraphics g, NeoTooltipRarity r, int x, int y, int length, int width, int u) {
        for (int offset = 0; offset < length; offset += 12) {
            int count = Math.min(12, length - offset);
            part(g, r, x, y + offset, width, count, u, 16, width, count);
        }
        part(g, r, x, y + length - 1, width, 1, u, 27, width, 1);
    }

    private static void tinted(GuiGraphics g, NeoTooltipRarity rarity, ResourceLocation sprite,
                               int x, int y, int width, int height, float opacity) {
        int rgb = rarity.color;
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, opacity);
        try { g.blitSprite(sprite, x, y, width, height); }
        finally { g.setColor(1, 1, 1, 1); }
    }

    private static void flow(GuiGraphics g, NeoTooltipRarity rarity, int x, int y, int length, int angle) {
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
            tinted(g, rarity, GOLD_FLOW, 0, -2, length, 4, 0.8f);
        } finally { g.pose().popPose(); }
    }

    private static void part(GuiGraphics g, NeoTooltipRarity rarity, int x, int y, int width, int height,
                             int u, int v, int sourceWidth, int sourceHeight) {
        g.blit(rarity.partsTexture, x, y, width, height, u, v, sourceWidth, sourceHeight, 128, 64);
    }
}
