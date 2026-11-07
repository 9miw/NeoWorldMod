package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Text roles leave translated/styled Components and Minecraft's font metrics intact. */
public final class NeoText {
    public enum Role {
        BODY(NeoGuiStyle.COLOR_TEXT), MUTED(NeoGuiStyle.COLOR_TEXT_MUTED),
        EMPHASIS(NeoGuiStyle.COLOR_GOLD), ERROR(NeoGuiStyle.COLOR_ERROR),
        SUCCESS(NeoGuiStyle.COLOR_ACCENT);
        public final int color;
        Role(int color) { this.color = color; }
    }
    private NeoText() {}
    /** Wrap text within both width and height. Returns occupied height. */
    public static int wrapped(GuiGraphics graphics, Font font, Component text, Role role,
                              int x, int y, int width, int height) {
        if (width <= 0 || height < font.lineHeight) return 0;
        int offset = 0;
        for (var line : font.split(text, width)) {
            if (offset + font.lineHeight > height) break;
            graphics.drawString(font, line, x, y + offset, role.color, false);
            offset += font.lineHeight + 3;
        }
        return Math.min(offset, height);
    }
}
