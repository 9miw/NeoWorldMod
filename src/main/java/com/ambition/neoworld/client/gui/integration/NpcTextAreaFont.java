package com.ambition.neoworld.client.gui.integration;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import noppes.npcs.shared.client.util.TrueTypeFont;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/** Supplies vanilla font metrics to Custom NPCs' existing text layout. */
public final class NpcTextAreaFont extends TrueTypeFont {
    public NpcTextAreaFont() {
        super(new Font(Font.DIALOG, Font.PLAIN, 9), 1.0F);
    }

    @Override
    public int width(String text) {
        return Minecraft.getInstance().font.width(format(text));
    }

    @Override
    public int height(String text) {
        return Minecraft.getInstance().font.lineHeight;
    }

    public static FormattedCharSequence format(String text) {
        // The editor uses FFFF for syntax colors; ordinary section signs stay editable text.
        List<FormattedCharSequence> parts = new ArrayList<>();
        Style style = Style.EMPTY;
        int start = 0;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\uFFFF') continue;
            ChatFormatting code = ChatFormatting.getByCode(text.charAt(i + 1));
            if (code == null) continue;
            parts.add(FormattedCharSequence.forward(text.substring(start, i), style));
            style = code == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(code);
            i++;
            start = i + 1;
        }
        parts.add(FormattedCharSequence.forward(text.substring(start), style));
        return FormattedCharSequence.composite(parts);
    }
}
