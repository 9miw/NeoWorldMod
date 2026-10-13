package com.ambition.neoworld.client.gui.widget;

import java.util.Objects;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared NeoWorld button. Vanilla handles input, focus, sound, tooltips and narration.
 * Construct with {@code new NeoButton(Button.builder(label, action).bounds(x, y, w, h))}
 * and register with {@code addRenderableWidget}.
 */
public final class NeoButton extends Button {
    public enum Style { STANDARD, PRIMARY, DESTRUCTIVE }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/buttons.png");
    private static final int TEXTURE_WIDTH = 1536;
    private static final int TEXTURE_HEIGHT = 1024;
    // Columns: normal, hover/focus, selected, disabled. Rows follow Style.
    private static final int[] SOURCE_X = {28, 407, 784, 1162};
    private static final int[] SOURCE_Y = {168, 441, 711};
    private static final int[] SOURCE_WIDTH = {350, 347, 350, 348};
    private static final int SOURCE_HEIGHT = 144;
    private static final int SOURCE_EDGE = 44;
    private Style style = Style.STANDARD;
    private boolean selected;
    private boolean textScrolling = true;

    public NeoButton(Button.Builder builder) {
        super(builder);
    }

    public NeoButton style(Style style) {
        this.style = Objects.requireNonNull(style);
        return this;
    }

    public Style getStyle() {
        return style;
    }

    public NeoButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    /** Disabling scrolling centers the full label without trimming, allowing overflow. */
    public NeoButton textScrolling(boolean enabled) {
        textScrolling = enabled;
        return this;
    }

    public boolean isTextScrolling() {
        return textScrolling;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        RenderSystem.enableBlend();
        graphics.setColor(1F, 1F, 1F, alpha);
        try {
            renderFrame(graphics);
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
        Font font = Minecraft.getInstance().font;
        int padding = cornerSize() + 2;
        int textColor = color(active ? 0xE7ECEF : 0x646D72);
        if (textScrolling) {
            renderScrollingString(graphics, font, padding, textColor);
        } else {
            FormattedCharSequence label = getMessage().getVisualOrderText();
            graphics.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2, textColor, false);
        }
    }

    private int color(int rgb) {
        return (Math.round(Math.clamp(alpha, 0F, 1F) * 255F) << 24) | rgb;
    }

    private void renderFrame(GuiGraphics graphics) {
        int state = !active ? 3 : selected ? 2 : isHoveredOrFocused() ? 1 : 0;
        int corner = cornerSize();
        int[] widths = {corner, getWidth() - 2 * corner, corner};
        int[] heights = {corner, getHeight() - 2 * corner, corner};
        int[] sourceWidths = {SOURCE_EDGE, SOURCE_WIDTH[state] - 2 * SOURCE_EDGE, SOURCE_EDGE};
        int[] sourceHeights = {SOURCE_EDGE, SOURCE_HEIGHT - 2 * SOURCE_EDGE, SOURCE_EDGE};
        int y = getY();
        int v = SOURCE_Y[style.ordinal()];
        for (int row = 0; row < 3; row++) {
            int x = getX();
            int u = SOURCE_X[state];
            for (int column = 0; column < 3; column++) {
                if (widths[column] > 0 && heights[row] > 0) {
                    graphics.blit(TEXTURE, x, y, widths[column], heights[row],
                            u, v, sourceWidths[column], sourceHeights[row], TEXTURE_WIDTH, TEXTURE_HEIGHT);
                }
                x += widths[column];
                u += sourceWidths[column];
            }
            y += heights[row];
            v += sourceHeights[row];
        }
    }

    private int cornerSize() {
        return Math.min(Math.max(1, Math.round(getHeight() * SOURCE_EDGE / (float) SOURCE_HEIGHT)),
                Math.min(getWidth(), getHeight()) / 2);
    }
}
