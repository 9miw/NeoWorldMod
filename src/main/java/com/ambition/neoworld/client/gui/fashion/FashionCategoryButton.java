package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

/** Fashion-specific PNG row art with vanilla button input and narration. */
final class FashionCategoryButton extends Button {
    private static final int SOURCE_WIDTH = 384;
    private static final int SOURCE_HEIGHT = 104;
    private static final int SOURCE_EDGE = 8;
    private static final ResourceLocation[] TEXTURES = {
            texture("normal"), texture("hover"), texture("selected"), texture("disabled")
    };
    private boolean selected;

    FashionCategoryButton(Builder builder) {
        super(builder);
    }

    void selected(boolean value) {
        selected = value;
    }

    private static ResourceLocation texture(String state) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,
                "textures/gui/fashion/fashion_category_row_" + state + ".png");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        int state = !active ? 3 : selected ? 2 : isHoveredOrFocused() ? 1 : 0;
        int corner = Math.min(Math.max(1, Math.round(getHeight() * SOURCE_EDGE / (float) SOURCE_HEIGHT)),
                Math.min(getWidth(), getHeight()) / 2);
        int[] widths = {corner, getWidth() - 2 * corner, corner};
        int[] heights = {corner, getHeight() - 2 * corner, corner};
        int[] sourceWidths = {SOURCE_EDGE, SOURCE_WIDTH - 2 * SOURCE_EDGE, SOURCE_EDGE};
        int[] sourceHeights = {SOURCE_EDGE, SOURCE_HEIGHT - 2 * SOURCE_EDGE, SOURCE_EDGE};
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, alpha);
        try {
            int targetY = getY();
            int sourceY = 0;
            for (int row = 0; row < 3; row++) {
                int targetX = getX();
                int sourceX = 0;
                for (int column = 0; column < 3; column++) {
                    if (widths[column] > 0 && heights[row] > 0) {
                        graphics.blit(TEXTURES[state], targetX, targetY, widths[column], heights[row],
                                sourceX, sourceY, sourceWidths[column], sourceHeights[row], SOURCE_WIDTH, SOURCE_HEIGHT);
                    }
                    targetX += widths[column];
                    sourceX += sourceWidths[column];
                }
                targetY += heights[row];
                sourceY += sourceHeights[row];
            }
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }
}
