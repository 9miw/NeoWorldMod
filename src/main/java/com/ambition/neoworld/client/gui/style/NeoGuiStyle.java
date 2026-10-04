package com.ambition.neoworld.client.gui.style;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.resources.ResourceLocation;

public final class NeoGuiStyle {
    public static final ResourceLocation MARKET_FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_frame.png");
    public static final ResourceLocation MARKET_WIDGET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_widgets.png");
    public static final ResourceLocation SILVER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/silver_coin.png");

    public static final int FRAME_TEXTURE_WIDTH = 1420;
    public static final int FRAME_TEXTURE_HEIGHT = 924;
    public static final int WIDGET_TEXTURE_WIDTH = 1536;
    public static final int WIDGET_TEXTURE_HEIGHT = 1024;

    public static final int COLOR_PANEL = 0xF5161B1E;
    public static final int COLOR_PANEL_DARK = 0xF00E1215;
    public static final int COLOR_FRAME = 0xFF4B535A;
    public static final int COLOR_FRAME_LIGHT = 0xFF7B858C;
    public static final int COLOR_SLOT = 0xFF252B2F;
    public static final int COLOR_SLOT_HOVER = 0xFF374348;
    public static final int COLOR_ACCENT = 0xFF2FB5A7;
    public static final int COLOR_ACCENT_DARK = 0xFF176B65;
    public static final int COLOR_GOLD = 0xFFF2C14E;
    public static final int COLOR_TEXT = 0xFFE7ECEF;
    public static final int COLOR_TEXT_MUTED = 0xFFABB4BA;
    public static final int COLOR_DISABLED = 0xFF646D72;
    public static final int COLOR_ERROR = 0xFFFF7B72;

    private NeoGuiStyle() {
    }
}
