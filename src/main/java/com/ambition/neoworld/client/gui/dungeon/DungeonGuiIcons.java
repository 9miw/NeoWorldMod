package com.ambition.neoworld.client.gui.dungeon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class DungeonGuiIcons {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/browser/icons.png");
    private static final int[][] REGIONS = {{110, 73, 539, 562}, {824, 86, 525, 527}, {1558, 104, 499, 524}};
    private DungeonGuiIcons() {}

    static void render(GuiGraphics graphics, int index, int x, int y, int size) {
        int[] source = REGIONS[index];
        float scale = size / (float) Math.max(source[2], source[3]);
        int w = Math.max(1, Math.round(source[2] * scale)), h = Math.max(1, Math.round(source[3] * scale));
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, 1);
        graphics.blit(ATLAS, x + (size - w) / 2, y + (size - h) / 2, w, h,
                source[0], source[1], source[2], source[3], 2172, 724);
    }
}
