package com.ambition.neoworld.client.gui.widget;

import java.util.Objects;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Compact, noninteractive subsection heading with dynamic styled text. */
public final class NeoSectionHeading implements Renderable {
    public enum Style { TEXT_LINE, ACCENT_STRIP, SMALL_PLAQUE }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/section_heading.png");
    private int x, y, width, height;
    private Component title;
    private Style style = Style.TEXT_LINE;
    private int textColor = 0xFFE7ECEF;
    private int accentColor = 0xFF2FB5A7;
    private int backgroundColor = 0xFF252C32;
    private boolean visible = true;
    private boolean shadow;

    public NeoSectionHeading(int x, int y, int width, int height, Component title) {
        bounds(x, y, width, height);
        title(title);
    }

    public NeoSectionHeading bounds(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = Math.max(0, width); this.height = Math.max(0, height);
        return this;
    }
    public NeoSectionHeading title(Component title) { this.title = Objects.requireNonNull(title); return this; }
    public NeoSectionHeading style(Style style) { this.style = Objects.requireNonNull(style); return this; }
    public NeoSectionHeading textColor(int argb) { textColor = argb; return this; }
    public NeoSectionHeading accentColor(int argb) { accentColor = argb; return this; }
    public NeoSectionHeading backgroundColor(int argb) { backgroundColor = argb; return this; }
    public NeoSectionHeading visible(boolean value) { visible = value; return this; }
    public NeoSectionHeading shadow(boolean value) { shadow = value; return this; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, Minecraft.getInstance().font);
    }

    public void render(GuiGraphics graphics, Font font) {
        if (!visible || width <= 0 || height <= 0) return;
        int inset;
        int reserve = 0;
        switch (style) {
            case TEXT_LINE -> {
                inset = 0;
                reserve = Math.min(12, width / 4);
            }
            case ACCENT_STRIP -> {
                graphics.fill(x, y, x + width, y + height, backgroundColor);
                graphics.fill(x, y, x + Math.min(2, width), y + height, accentColor);
                inset = 8;
            }
            case SMALL_PLAQUE -> {
                renderPlaque(graphics);
                inset = Math.min(width / 2, Math.max(4, Math.round(height * 54F / 149F) + 2));
            }
            default -> throw new IllegalStateException("Unknown heading style");
        }
        int availableWidth = width - inset * 2 - reserve;
        int verticalInset = style == Style.SMALL_PLAQUE ? Math.max(1, Math.round(height * 24F / 149F)) : 0;
        if (availableWidth <= 0 || height - 2 * verticalInset < font.lineHeight) return;
        var label = Language.getInstance().getVisualOrder(font.substrByWidth(title, availableWidth));
        graphics.drawString(font, label, x + inset, y + (height - font.lineHeight) / 2, textColor, shadow);
        if (style == Style.TEXT_LINE) {
            int start = x + font.width(label) + 4;
            int lineY = y + height / 2;
            if (start < x + width) graphics.fill(start, lineY, x + width, lineY + 1, 0xFF899299);
        }
    }

    private void renderPlaque(GuiGraphics graphics) {
        int cap = Math.min(width / 2, Math.max(1, Math.round(height * 54F / 149F)));
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, 1F);
        try {
            if (cap > 0) {
                graphics.blit(TEXTURE, x, y, cap, height, 45, 438, 54, 149, 1536, 1024);
                graphics.blit(TEXTURE, x + width - cap, y, cap, height, 1437, 438, 54, 149, 1536, 1024);
            }
            if (width > 2 * cap) graphics.blit(TEXTURE, x + cap, y, width - 2 * cap, height,
                    99, 438, 1338, 149, 1536, 1024);
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }
}
