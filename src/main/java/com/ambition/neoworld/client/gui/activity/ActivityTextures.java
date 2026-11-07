package com.ambition.neoworld.client.gui.activity;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoBitmapIcon;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Source sizes are verified by docs/gui/activity/export-assets.py and asset-manifest.json. */
public final class ActivityTextures {
    private static final String[] STATES = {"normal", "hover", "selected", "disabled"};
    private static final String[] ICONS = {"daily", "monthly", "online", "pending"};
    private static final NeoBitmapIcon[] PANEL_ICONS = {NeoBitmapIcon.ACTIVITY_ICONS_ICON_DAILY,
            NeoBitmapIcon.ACTIVITY_ICONS_ICON_MONTHLY, NeoBitmapIcon.ACTIVITY_ICONS_ICON_ONLINE,
            NeoBitmapIcon.ACTIVITY_ICONS_ICON_PENDING};
    private ActivityTextures() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/activity/" + name + ".png");
    }
    public static Component label(ActivityPanel panel) {
        if (panel.id() < 0 || panel.id() >= ICONS.length) return Component.literal(panel.title());
        return Component.empty().append(PANEL_ICONS[panel.id()].component()).append(" " + panel.title());
    }
    public static void frame(GuiGraphics g, int width, int height) { image(g, "frame", 0, 0, width, height, 1280, 840); }
    public static void icon(GuiGraphics g, int id, int x, int y, int size) {
        if (id >= 0 && id < ICONS.length) image(g, "icon_" + ICONS[id], x, y, size, size, 32, 32);
    }
    public static void silver(GuiGraphics g, int x, int y, int size) { image(g, "icon_silver", x, y, size, size, 32, 32); }
    public static void slot(GuiGraphics g, int x, int y, int size, boolean today, boolean hover, boolean disabled) {
        int state = today ? 2 : disabled ? 3 : hover ? 1 : 0;
        image(g, "slot_" + STATES[state], x, y, size, size, 96, 96);
    }
    /** Tall card frame: preserve six-pixel corners instead of stretching a square slot. */
    public static void card(GuiGraphics g, int x, int y, int width, int height, boolean claimable) {
        nine(g, claimable ? "slot_selected" : "slot_normal", x, y, width, height, 96, 96, 16, 6);
    }
    public static NeoButton skin(NeoButton button, boolean navigation) {
        return button.frameRenderer((g, b) -> {
            int state = !b.active ? 3 : b.isSelected() ? 2 : b.isHoveredOrFocused() ? 1 : 0;
            boolean nav = navigation || b.getStyle() == NeoButton.Style.DESTRUCTIVE;
            boolean red = b.getStyle() == NeoButton.Style.DESTRUCTIVE && b.active && b.isHoveredOrFocused();
            if (red) g.setColor(1F, 0.35F, 0.35F, 1F);
            nine(g, (nav ? "nav_" : "button_") + STATES[state], b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                    nav ? 320 : 280, nav ? 128 : 100, 24);
            if (red) g.setColor(1F, 1F, 1F, 1F);
        });
    }
    public static void scrollbar(GuiGraphics g, int x, int y, int width, int height, int thumbY, int thumbHeight) {
        image(g, "scroll_track", x, y, width, height, 16, 160);
        nine(g, "scroll_thumb", x, thumbY, width, thumbHeight, 32, 128, 8);
    }
    private static void image(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(texture(name), x, y, w, h, 0, 0, sw, sh, sw, sh);
    }
    private static void nine(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh, int edge) {
        int corner = Math.min(Math.max(1, h * edge / sh), Math.min(w, h) / 2);
        nine(g, name, x, y, w, h, sw, sh, edge, corner);
    }
    private static void nine(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh, int edge, int corner) {
        int[] dw = {corner, w - 2 * corner, corner}, dh = {corner, h - 2 * corner, corner};
        int[] widths = {edge, sw - 2 * edge, edge}, heights = {edge, sh - 2 * edge, edge};
        int dy = y, v = 0;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int row = 0; row < 3; row++) {
            int dx = x, u = 0;
            for (int col = 0; col < 3; col++) {
                if (dw[col] > 0 && dh[row] > 0) g.blit(texture(name), dx, dy, dw[col], dh[row], u, v, widths[col], heights[row], sw, sh);
                dx += dw[col]; u += widths[col];
            }
            dy += dh[row]; v += heights[row];
        }
    }
}
