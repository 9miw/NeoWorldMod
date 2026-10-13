package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

final class FashionWindowFrame {
    private static final ResourceLocation SHELL = texture("shell");
    private static final ResourceLocation PLAQUE = texture("title_plaque");
    private static final ResourceLocation CLOSE_NORMAL = texture("close_normal");
    private static final ResourceLocation CLOSE_HOVER = texture("close_hover");

    private FashionWindowFrame() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/fashion/fashion_" + name + ".png");
    }

    static void render(GuiGraphics graphics, int x, int y, int width, int height) {
        int edge = Math.min(6, Math.min(width, height) / 2);
        // The interior underlay also fills any alpha remaining in the generated shell.
        graphics.fill(x + edge, y + edge, x + width - edge, y + height - edge, 0xFF191F23);
        int[] widths = {edge, width - edge * 2, edge};
        int[] heights = {edge, height - edge * 2, edge};
        int[] sourceWidths = {24, 812, 24};
        int[] sourceHeights = {24, 396, 24};
        RenderSystem.enableBlend();
        int ty = y;
        int v = 0;
        for (int row = 0; row < 3; row++) {
            int tx = x;
            int u = 0;
            for (int col = 0; col < 3; col++) {
                if (row != 1 || col != 1) {
                    graphics.blit(SHELL, tx, ty, widths[col], heights[row], u, v,
                            sourceWidths[col], sourceHeights[row], 860, 444);
                }
                tx += widths[col];
                u += sourceWidths[col];
            }
            ty += heights[row];
            v += sourceHeights[row];
        }
    }

    static void title(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(PLAQUE, x, y, width, height, 0, 0, 480, 112, 480, 112);
    }

    static final class CloseButton extends Button {
        CloseButton(Builder builder) { super(builder); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableBlend();
            graphics.blit(isHoveredOrFocused() ? CLOSE_HOVER : CLOSE_NORMAL,
                    getX(), getY(), getWidth(), getHeight(), 0, 0, 64, 64, 64, 64);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, "X", getX() + (getWidth() - font.width("X")) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, 0xFFE7ECEF, false);
        }
    }
}
