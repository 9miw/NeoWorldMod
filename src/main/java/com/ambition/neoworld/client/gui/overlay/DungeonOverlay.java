package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.client.state.ClientDungeonHudData;
import com.ambition.neoworld.client.gui.common.NeoBitmapIcon;
import com.ambition.neoworld.network.dungeon.DungeonHudPayload;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Server-owned dungeon status, anchored to the vertical center of the right edge. */
public final class DungeonOverlay implements LayeredDraw.Layer {
    private static final int W = 240, H = 100, WHITE = 0xFFE7ECEF, GOLD = 0xFFF2C14E, MUTED = 0xFFA7B4BA;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        var state = ClientDungeonHudData.get();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !state.visible()
                || !mc.player.level().dimension().equals(ModDimensionRegistries.DUNGEON_LEVEL)) return;
        // Reduce the complete HUD to 60% while retaining the compact viewport fit.
        float scale = 0.6F * Math.min(1F, Math.min((g.guiWidth() * 0.45F - 8F) / W, (g.guiHeight() - 16F) / H));
        if (scale <= 0) return;
        int x = Math.round(g.guiWidth() - 8 - W * scale), y = Math.round((g.guiHeight() - H * scale) / 2F);
        Font font = mc.font;
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1F);
        g.setColor(1F, 1F, 1F, 1F);
        image(g, "minimal_strip", 0, 0, W, H, 440, 224);
        g.fill(7, 8, W-2, H-8, 0xF2192025);
        var view = DungeonHudPresentation.from(state);
        label(g, font, state.title(), 18, 13, 112, GOLD);
        right(g, font, view.status(), W-14, 13, 86, state.phase() == DungeonHudPayload.Phase.BOSS ? 0xFFFF646D
                : state.phase() == DungeonHudPayload.Phase.REWARD ? 0xFF53E58E : 0xFF69DCD6);
        g.fill(18, 28, W-14, 29, 0xFF3C484D);
        int remainingWidth = Math.min(104, font.width(view.remaining()));
        label(g, font, view.objective(), 18, 36, W-32-(remainingWidth == 0 ? 0 : remainingWidth+8), WHITE);
        right(g, font, view.remaining(), W-14, 36, remainingWidth, WHITE);
        int trackWidth = W-32;
        image(g, "minimal_track", 18, 52, trackWidth, 3, 376, 4);
        int fill = Math.round(trackWidth * view.progress());
        if (fill > 0) {
            // Clip UVs and destination together so the fill is revealed, not squeezed.
            boolean boss = state.phase() == DungeonHudPayload.Phase.BOSS;
            int sourceWidth = boss ? 384 : 376, sourceHeight = boss ? 16 : 4;
            g.blit(texture(boss ? "boss_fill" : "minimal_fill"),
                    18, 52, fill, 3, 0, 0, Math.round(sourceWidth * fill / (float)trackWidth), sourceHeight, sourceWidth, sourceHeight);
        }
        label(g, font, "เวลาเหลือ", 18, 63, 96, MUTED);
        label(g, font, "ผู้รอดชีวิต", 134, 63, 92, MUTED);
        icon(g, font, NeoBitmapIcon.DUNGEON_HUD_CLOCK, 18, 77, GOLD);
        label(g, font, view.time(), 36, 78, 78, !state.unlimited() && state.secondsRemaining() <= 60 ? 0xFFFF646D : GOLD);
        icon(g, font, NeoBitmapIcon.DUNGEON_HUD_MEMBER, 134, 77, 0xFF69DCD6);
        right(g, font, view.survivors(), W-14, 78, 68, 0xFF53E58E);
        g.pose().popPose();
    }

    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/hud/" + name + ".png"); }
    private static void image(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh) { g.blit(texture(name), x, y, w, h, 0, 0, sw, sh, sw, sh); }
    private static String fit(Font font, String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "..."; }
    private static void label(GuiGraphics g, Font font, String text, int x, int y, int width, int color) { g.drawString(font, fit(font, text, width), x, y, color, false); }
    private static void right(GuiGraphics g, Font font, String text, int x, int y, int width, int color) { String fitted = fit(font, text, width); g.drawString(font, fitted, x - font.width(fitted), y, color, false); }
    private static void icon(GuiGraphics g, Font font, NeoBitmapIcon icon, int x, int y, int color) { g.drawString(font, icon.component().withStyle(s -> s.withColor(color & 0xFFFFFF)), x, y, color, false); }
}
