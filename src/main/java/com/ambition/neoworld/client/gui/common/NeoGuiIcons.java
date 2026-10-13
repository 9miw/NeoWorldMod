package com.ambition.neoworld.client.gui.common;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NeoGuiIcons {
    public static final String LEADER = "\uE100";
    public static final String MEMBER = "\uE101";
    public static final String INVITE = "\uE102";
    public static final String KICK = "\uE103";
    public static final String READY = "\uE104";
    public static final String LEAVE = "\uE105";
    public static final String DISBAND = "\uE106";
    public static final String REFRESH = "\uE107";
    private static final ResourceLocation ICON_FONT = ResourceLocation.fromNamespaceAndPath("neoworld", "gui_icons");
    private static final ResourceLocation CONTROL_FONT = ResourceLocation.fromNamespaceAndPath("neoworld", "gui_controls");

    private NeoGuiIcons() {}

    public static Component icon(String glyph) {
        return Component.literal(glyph).withStyle(style -> style.withFont(ICON_FONT).withColor(0xFFFFFF));
    }

    public static Component label(String glyph, String label) {
        return Component.empty().append(Component.literal(glyph)
                .withStyle(style -> style.withFont(CONTROL_FONT)))
                .append(Component.literal(" " + label));
    }
}
