package com.ambition.neoworld.client.gui.dialogue;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.DialogOption;

/** Reuses approved shared glyphs and the existing scroll artwork without registering duplicates. */
public final class DialogueOptionIcons {
    public static final int TEXT_INSET = 20;
    public static final int RIGHT_INSET = 10;
    private static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath(
            "neoworld", "textures/item/protection_scroll.png");

    private DialogueOptionIcons() {}

    public static DialogueOptionKind kindFor(DialogOption option) {
        DialogueOptionKind nativeKind = DialogueOptionKind.fromType(option.optionType);
        if (nativeKind != DialogueOptionKind.DIALOGUE || option.dialogId <= 0
                || DialogController.instance == null) return nativeKind;
        var target = option.getDialog();
        // The serialized quest binding is available on Dialog itself. hasQuest() also
        // requires a QuestController cache, which is not guaranteed on multiplayer clients.
        return DialogueOptionKind.fromType(option.optionType, target == null ? null : target.quest);
    }

    public static void render(GuiGraphics g, DialogueOptionKind kind, int x, int y, int size,
                              boolean enabled, boolean selected) {
        if (size <= 0) return;
        enabled = enabled && kind != DialogueOptionKind.DISABLED;
        if (kind == DialogueOptionKind.ROLE) {
            NeoSystemIcons.render(g, NeoSystemIcons.Icon.PROFILE, x, y, size, enabled);
            return;
        }
        if (kind == DialogueOptionKind.COMMAND) {
            RenderSystem.enableBlend();
            float tint = enabled ? 1F : 0.42F;
            g.setColor(tint, tint, tint, enabled ? 1F : 0.7F);
            try {
                g.blit(SCROLL, x, y, size, size, 0, 0, 64, 64, 64, 64);
            } finally { g.setColor(1F, 1F, 1F, 1F); }
            return;
        }
        Component glyph = switch (kind) {
            case CLOSE -> NeoGuiIcons.icon(NeoGuiIcons.LEAVE);
            case DISABLED -> NeoBitmapFonts.text("\uE015");
            case QUEST -> Component.literal("!");
            default -> NeoBitmapFonts.text("\uE009");
        };
        int height = kind == DialogueOptionKind.CLOSE ? 16 : 9;
        int ascent = kind == DialogueOptionKind.CLOSE ? 13 : kind == DialogueOptionKind.QUEST ? 7 : 8;
        int color = !enabled ? 0x697B82 : kind == DialogueOptionKind.QUEST
                ? selected ? 0xFFE29C : 0xF3C65E : selected ? 0x79F4EE : 0xCDDDE0;
        var coloredGlyph = glyph.copy().withStyle(style -> style.withColor(color));
        var font = Minecraft.getInstance().font;
        int inkWidth = Math.max(1, font.width(coloredGlyph) - 1);
        float scale = size / (float) Math.max(inkWidth, height);
        g.pose().pushPose();
        RenderSystem.enableBlend();
        g.setColor(1F, 1F, 1F, 1F);
        try {
            g.pose().translate(x + (size - inkWidth * scale) / 2F,
                    y + (size - height * scale) / 2F, 0);
            g.pose().scale(scale, scale, 1F);
            g.drawString(font, coloredGlyph, 0, ascent - 7, color, false);
        } finally {
            g.pose().popPose();
            g.setColor(1F, 1F, 1F, 1F);
        }
    }
}
