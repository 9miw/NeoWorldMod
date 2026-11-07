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

/** Decorative heading only: PNG art and dynamic text, without button input or hover. */
public final class NeoTitlePlaque implements Renderable {
    public enum Style {
        SLIM(174, 129, 1188, 166, 66, 0, 0, 34),
        RAISED(201, 368, 1135, 251, 120, 0, 0, 44),
        CREST(129, 638, 1277, 295, 185, 386, 82, 34);

        final int u, v, width, height, cap, center, bodyTop, padding;
        Style(int u, int v, int width, int height, int cap, int center, int bodyTop, int padding) {
            this.u = u; this.v = v; this.width = width; this.height = height;
            this.cap = cap; this.center = center; this.bodyTop = bodyTop; this.padding = padding;
        }
    }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/title_plaques.png");
    private int x, y, width, height;
    private Component title;
    private Style style = Style.SLIM;
    private int textColor = 0xFFF4F6F7;
    private boolean shadow;
    private boolean visible = true;

    /** Bounds include the Crest ornament above the text panel. */
    public NeoTitlePlaque(int x, int y, int width, int height, Component title) {
        bounds(x, y, width, height);
        title(title);
    }

    public NeoTitlePlaque bounds(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = Math.max(0, width); this.height = Math.max(0, height);
        return this;
    }
    public NeoTitlePlaque title(Component title) { this.title = Objects.requireNonNull(title); return this; }
    public NeoTitlePlaque style(Style style) { this.style = Objects.requireNonNull(style); return this; }
    public NeoTitlePlaque textColor(int argb) { textColor = argb; return this; }
    public NeoTitlePlaque shadow(boolean shadow) { this.shadow = shadow; return this; }
    public NeoTitlePlaque visible(boolean visible) { this.visible = visible; return this; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, Minecraft.getInstance().font);
    }

    public void render(GuiGraphics graphics, Font font) {
        if (!visible || width <= 0 || height <= 0) return;
        int[] columns = columns(style, width, height);
        int remaining = style.width - style.cap * 2 - style.center;
        int left = style.center == 0 ? remaining : remaining / 2;
        int[] source = {style.cap, left, style.center, remaining - left, style.cap};
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, 1F);
        try {
            int dx = x, u = style.u;
            for (int column = 0; column < columns.length; column++) {
                if (columns[column] > 0 && source[column] > 0) {
                    graphics.blit(TEXTURE, dx, y, columns[column], height,
                            u, style.v, source[column], style.height, 1536, 1024);
                }
                dx += columns[column];
                u += source[column];
            }
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
        int textInset = columns[0] + Math.max(2, Math.round(style.padding * height / (float) style.height));
        int textWidth = width - 2 * textInset;
        int bodyTop = Math.round(style.bodyTop * height / (float) style.height);
        int verticalPadding = Math.max(1, Math.round(style.padding * height / (float) style.height));
        int bodyHeight = height - bodyTop;
        if (textWidth > 0 && bodyHeight - verticalPadding * 2 >= font.lineHeight) {
            var label = Language.getInstance().getVisualOrder(font.substrByWidth(title, textWidth));
            graphics.drawString(font, label, x + (width - font.width(label)) / 2,
                    y + bodyTop + (bodyHeight - font.lineHeight) / 2, textColor, shadow);
        }
    }

    /** Fixed end ornaments and the Crest center scale uniformly with height. */
    static int[] columns(Style style, int width, int height) {
        int cap = Math.max(0, Math.round(style.cap * height / (float) style.height));
        int center = Math.max(0, Math.round(style.center * height / (float) style.height));
        long fixed = (long) cap * 2 + center;
        if (fixed > width) {
            center = (int) Math.round(width * center / (double) fixed);
            cap = (width - center) / 2;
        }
        int stretch = width - cap * 2 - center;
        int left = style.center == 0 ? stretch : stretch / 2;
        return new int[] {cap, left, center, stretch - left, cap};
    }
}
