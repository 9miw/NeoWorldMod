package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

/** Thin wardrobe bevels using the same PNG material as its search control. */
final class FashionActionButton extends Button {
    private static final ResourceLocation NORMAL = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/fashion/fashion_search_normal.png");
    private static final ResourceLocation HOVER = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/fashion/fashion_search_focus.png");
    private final boolean primary;

    FashionActionButton(Builder builder, boolean primary) {
        super(builder);
        this.primary = primary;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int edge = Math.min(3, Math.min(getWidth(), getHeight()) / 2);
        int[] widths = {edge, getWidth() - 2 * edge, edge};
        int[] heights = {edge, getHeight() - 2 * edge, edge};
        int[] sourceWidths = {8, 496, 8};
        int[] sourceHeights = {8, 32, 8};
        ResourceLocation texture = active && isHoveredOrFocused() ? HOVER : NORMAL;
        RenderSystem.enableBlend();
        float brightness = active ? 1F : 0.65F;
        graphics.setColor((primary ? 0.3F : 1F) * brightness,
                (primary ? 0.82F : 1F) * brightness, (primary ? 0.86F : 1F) * brightness, alpha);
        try {
            int targetY = getY();
            int sourceY = 0;
            for (int row = 0; row < 3; row++) {
                int targetX = getX();
                int sourceX = 0;
                for (int column = 0; column < 3; column++) {
                    graphics.blit(texture, targetX, targetY, widths[column], heights[row],
                            sourceX, sourceY, sourceWidths[column], sourceHeights[row], 512, 48);
                    targetX += widths[column];
                    sourceX += sourceWidths[column];
                }
                targetY += heights[row];
                sourceY += sourceHeights[row];
            }
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
        renderScrollingString(graphics, Minecraft.getInstance().font, edge + 1, active ? 0xFFE7ECEF : 0xFF88969C);
    }
}
