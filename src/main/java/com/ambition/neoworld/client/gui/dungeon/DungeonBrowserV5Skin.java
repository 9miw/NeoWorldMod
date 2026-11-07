package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** V5 artwork only; gameplay, widgets and packet ownership remain unchanged. */
final class DungeonBrowserV5Skin {
    private DungeonBrowserV5Skin() {}

    static ResourceLocation asset(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/dungeon/browser/" + name + ".png");
    }

    private static void part(GuiGraphics g, String name, int x, int y, int w, int h,
            int u, int v, int rw, int rh, int sw, int sh) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, 1);
        g.blit(asset(name), x, y, w, h, u, v, rw, rh, sw, sh);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        // The full lantern/title band retains its aspect ratio; only plain edges extend vertically.
        int top = Math.round(w * 120F / 1468F);
        int bottom = Math.round(w * 64F / 1468F);
        int side = Math.max(1, Math.round(w * 32F / 1468F));
        int corner = Math.max(1, Math.round(w * 64F / 1468F));
        int middle = h - top - bottom;
        part(g, "shell", x, y, w, top, 0, 0, 1468, 120, 1468, 834);
        part(g, "shell", x, y + top, side, middle, 0, 120, 32, 650, 1468, 834);
        part(g, "shell", x + w - side, y + top, side, middle, 1436, 120, 32, 650, 1468, 834);
        part(g, "shell", x + side, y + top, w - side * 2, middle, 32, 120, 1404, 650, 1468, 834);
        part(g, "shell", x, y + h - bottom, corner, bottom, 0, 770, 64, 64, 1468, 834);
        part(g, "shell", x + corner, y + h - bottom, w - corner * 2, bottom, 64, 770, 1340, 64, 1468, 834);
        part(g, "shell", x + w - corner, y + h - bottom, corner, bottom, 1404, 770, 64, 64, 1468, 834);
    }

    static void socket(GuiGraphics g, String name, int x, int y, int w, int h,
            int sw, int sh, int sb, int border) {
        int b = Math.min(border, Math.min(w, h) / 2);
        int[] sx = {0, sb, sw-sb, sw}, sy = {0, sb, sh-sb, sh};
        int[] dx = {x, x+b, x+w-b, x+w}, dy = {y, y+b, y+h-b, y+h};
        for (int j=0; j<3; j++) for (int i=0; i<3; i++)
            part(g, name, dx[i], dy[j], dx[i+1]-dx[i], dy[j+1]-dy[j],
                    sx[i], sy[j], sx[i+1]-sx[i], sy[j+1]-sy[j], sw, sh);
    }

    static void entrance(GuiGraphics g, boolean route, int x, int y, int size) {
        int sw = route ? 70 : 82, sh = route ? 67 : 85;
        int w = Math.round(size * sw / (float)Math.max(sw, sh));
        int h = Math.round(size * sh / (float)Math.max(sw, sh));
        part(g, route ? "route_entrance" : "entrance", x+(size-w)/2, y+(size-h)/2,
                w, h, 0, 0, sw, sh, sw, sh);
    }

    static void bossFrame(GuiGraphics g, int x, int y, int w, int h) {
        var texture = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/item_tooltip_frame.png");
        int[] sx = {0, 24, 836, 860}, sy = {0, 24, 420, 444};
        int[] dx = {x, x+4, x+w-4, x+w}, dy = {y, y+4, y+h-4, y+h};
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, 1);
        for (int j=0; j<3; j++) for (int i=0; i<3; i++)
            g.blit(texture, dx[i], dy[j], dx[i+1]-dx[i], dy[j+1]-dy[j],
                    sx[i], sy[j], sx[i+1]-sx[i], sy[j+1]-sy[j], 860, 444);
    }
}
