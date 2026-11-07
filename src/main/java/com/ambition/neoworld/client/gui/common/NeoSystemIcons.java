package com.ambition.neoworld.client.gui.common;

import java.util.Objects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Shared system/action glyphs extracted from the approved menu artwork. */
public final class NeoSystemIcons {
    public enum Icon {
        PROFILE("\uE300"), TITLES("\uE301"), MARKET("\uE004", 9, 8),
        FASHION("\uE200", NeoBitmapFonts.FASHION_TOOLTIP, 10, 8),
        PARTY("\uE302"), DUNGEON("\uE303"), RESUME("\uE304"),
        SETTINGS("\uE006", 9, 8), MODS("\uE305"), ADVANCEMENTS("\uE306"),
        STATISTICS("\uE307"), LAN("\uE308"), EXIT("\uE309");

        public final String glyph;
        public final ResourceLocation font;
        public final int height, ascent;
        Icon(String glyph) { this(glyph, NeoBitmapFonts.ICONS, 16, 13); }
        Icon(String glyph, int height, int ascent) { this(glyph, NeoBitmapFonts.ICONS, height, ascent); }
        Icon(String glyph, ResourceLocation font, int height, int ascent) {
            this.glyph = glyph; this.font = font; this.height = height; this.ascent = ascent;
        }
    }

    private NeoSystemIcons() {}

    public static MutableComponent icon(Icon icon) {
        Objects.requireNonNull(icon);
        return Component.literal(icon.glyph).withStyle(style -> style.withFont(icon.font).withColor(0xFFFFFF));
    }

    public static MutableComponent label(Icon icon, Component label) {
        return Component.empty().append(icon(icon)).append(Component.literal(" ")).append(label);
    }

    public static Icon forSystem(String id) {
        return switch (id) {
            case "profile" -> Icon.PROFILE;
            case "titles" -> Icon.TITLES;
            case "market" -> Icon.MARKET;
            case "fashion" -> Icon.FASHION;
            case "party" -> Icon.PARTY;
            case "dungeon" -> Icon.DUNGEON;
            default -> null;
        };
    }

    /** Fits a single glyph inside the supplied square; no item-stack or atlas rendering. */
    public static void render(GuiGraphics graphics, Icon icon, int x, int y, int size, boolean enabled) {
        if (size <= 0) return;
        var font = Minecraft.getInstance().font;
        var glyph = icon(icon).withStyle(style -> style.withColor(enabled ? 0xFFFFFF : 0x737373));
        // Bitmap advance has an extra logical pixel; vertical bounds start at 7-ascent.
        int inkWidth = Math.max(1, font.width(glyph) - 1);
        float scale = size / (float) Math.max(inkWidth, icon.height);
        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, 1);
        try {
            graphics.pose().translate(x + (size - inkWidth * scale) / 2F,
                    y + (size - icon.height * scale) / 2F, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, glyph, 0, icon.ascent - 7,
                    enabled ? 0xFFFFFFFF : 0xA6FFFFFF, false);
        } finally {
            graphics.pose().popPose();
            graphics.setColor(1, 1, 1, 1);
        }
    }
}
