package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Feature-local crystal artwork; shared widgets retain their input and sound behavior. */
final class DungeonCrystalSkin {
    private DungeonCrystalSkin() {}

    static ResourceLocation asset(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,
                "textures/gui/dungeon/crystal/" + name + ".png");
    }

    static void image(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh) {
        RenderSystem.enableBlend();
        if (w > 0 && h > 0) g.blit(asset(name), x, y, w, h, 0, 0, sw, sh, sw, sh);
    }

    static void icon(GuiGraphics g, String name, int x, int y, int size) {
        image(g, name, x + (size - 16) / 2, y + (size - 16) / 2, 16, 16, 16, 16);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        socket(g, "frame", x, y, w, h, 72, 72, 24, 24);
    }

    static void socket(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh, int sourceBorder, int border) {
        RenderSystem.enableBlend();
        int b = Math.min(border, Math.min(w, h) / 2);
        int[] xs = {0, sourceBorder, sw - sourceBorder, sw};
        int[] ys = {0, sourceBorder, sh - sourceBorder, sh};
        int[] dx = {x, x + b, x + w - b, x + w};
        int[] dy = {y, y + b, y + h - b, y + h};
        for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
            if (dx[i + 1] > dx[i] && dy[j + 1] > dy[j])
                g.blit(asset(name), dx[i], dy[j], dx[i + 1] - dx[i], dy[j + 1] - dy[j],
                        xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j], sw, sh);
        }
    }
}
