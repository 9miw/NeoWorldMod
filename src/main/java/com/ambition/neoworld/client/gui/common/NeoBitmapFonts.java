package com.ambition.neoworld.client.gui.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Named bitmap fonts; ordinary characters retain the default font. */
public final class NeoBitmapFonts {
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("neoworld", "icons");
    public static final ResourceLocation CURRENCY = ResourceLocation.fromNamespaceAndPath("neoworld", "currency");
    public static final ResourceLocation RANKS = ResourceLocation.fromNamespaceAndPath("neoworld", "ranks");
    public static final ResourceLocation FASHION_TOOLTIP = ResourceLocation.fromNamespaceAndPath("neoworld", "fashion_tooltip");
    private NeoBitmapFonts() {}

    /** Converts legacy formatted labels while assigning only bitmap glyphs their font. */
    public static MutableComponent text(String text) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        ResourceLocation currentFont = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                if (!run.isEmpty()) {
                    result.append(Component.literal(run.toString()).withStyle(style.withFont(currentFont)));
                    run.setLength(0);
                }
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(++i));
                if (format != null) style = style.applyLegacyFormat(format);
                continue;
            }
            ResourceLocation font = c >= '\uE046' && c <= '\uE048' ? CURRENCY
                    : c >= '\uE020' && c <= '\uE045' ? RANKS
                    : c >= '\uE001' && c <= '\uE01F' ? ICONS
                    : c >= '\uE200' && c <= '\uE20D' ? FASHION_TOOLTIP : null;
            if (!java.util.Objects.equals(font, currentFont) && !run.isEmpty()) {
                result.append(Component.literal(run.toString()).withStyle(style.withFont(currentFont)));
                run.setLength(0);
            }
            currentFont = font;
            run.append(c);
        }
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).withStyle(style.withFont(currentFont)));
        return result;
    }
}
