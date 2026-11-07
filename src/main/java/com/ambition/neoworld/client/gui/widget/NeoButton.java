package com.ambition.neoworld.client.gui.widget;

import java.util.Objects;
import java.util.function.BiConsumer;

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
    public enum Skin { CLASSIC, FLOATING }

    private static final ResourceLocation FLOATING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/menu/floating_button.png");

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
    private Skin skin = Skin.CLASSIC;
    private int skinCornerSize = 8;
    private boolean selected;
    private boolean textScrolling = true;
    private float textScale = 1F;
    private int textOffsetY;

    /** Optical label alignment; leaves the frame and input bounds unchanged. */
    public NeoButton textOffsetY(int offset) {
        textOffsetY = offset;
        return this;
    }
    private boolean uniformTextScale;

    /** Opt-in label scale; the button frame and hitbox keep their existing size. */
    public NeoButton textScale(float scale) {
        uniformTextScale = false;
        textScale = Float.isFinite(scale) ? Math.clamp(scale, 0.5F, 3F) : 1F;
        return this;
    }

    /** Opt-in for screens that scale their complete layout, including labels, uniformly. */
    public NeoButton uniformTextScale(float scale) {
        uniformTextScale = true;
        textScale = Float.isFinite(scale) && scale > 0F ? scale : 1F;
        return this;
    }

    static float resolveTextScale(float requested, int height, int lineHeight, boolean uniform) {
        if (uniform) return requested;
        return Math.min(Math.clamp(requested, 0.5F, 3F), Math.max(0.5F, (height - 4F) / lineHeight));
    }
    private BiConsumer<GuiGraphics, NeoButton> frameRenderer;

    /** Optional feature skin; ordinary NeoButtons retain the shared atlas and input behavior. */
    public NeoButton frameRenderer(BiConsumer<GuiGraphics, NeoButton> renderer) {
        frameRenderer = renderer; return this;
    }

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

    public NeoButton skin(Skin skin) {
        this.skin = Objects.requireNonNull(skin);
        return this;
    }

    public Skin getSkin() { return skin; }

    /** Floating-skin corner size in logical GUI pixels; does not resize the hitbox. */
    public NeoButton skinCornerSize(int size) {
        skinCornerSize = Math.max(1, size);
        return this;
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
        if (textScale != 1F) {
            float scale = resolveTextScale(textScale, getHeight(), font.lineHeight, uniformTextScale);
            int availableWidth = Math.max(1, (int) ((getWidth() - 2 * padding) / scale));
            int availableHeight = Math.max(1, (int) (getHeight() / scale));
            graphics.enableScissor(getX() + padding, getY(), getRight() - padding, getBottom());
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2F, getY() + getHeight() / 2F + textOffsetY, 0);
            graphics.pose().scale(scale, scale, 1);
            if (textScrolling) renderScrollingString(graphics, font, getMessage(), -availableWidth / 2,
                    -availableHeight / 2, availableWidth - availableWidth / 2, availableHeight - availableHeight / 2, textColor);
            else graphics.drawString(font, getMessage(), -font.width(getMessage()) / 2, -font.lineHeight / 2, textColor, false);
            graphics.pose().popPose(); graphics.disableScissor();
            return;
        }
        if (textScrolling) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, textOffsetY, 0);
            renderScrollingString(graphics, font, padding, textColor);
            graphics.pose().popPose();
        } else {
            FormattedCharSequence label = getMessage().getVisualOrderText();
            graphics.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + textOffsetY, textColor, false);
        }
    }

    private int color(int rgb) {
        return (Math.round(Math.clamp(alpha, 0F, 1F) * 255F) << 24) | rgb;
    }

    private void renderFrame(GuiGraphics graphics) {
        if (frameRenderer != null) { frameRenderer.accept(graphics, this); return; }
        if (skin == Skin.FLOATING) { renderFloatingFrame(graphics); return; }
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

    private void renderFloatingFrame(GuiGraphics graphics) {
        boolean highlighted = active && isHoveredOrFocused();
        float red = 1F, green = 1F, blue = 1F;
        if (!active) {
            red = green = blue = 0.45F;
        } else if (style == Style.DESTRUCTIVE) {
            green = blue = highlighted || selected ? 0.3F : 0.55F;
        } else if (selected) {
            green = 0.82F; blue = 0.35F;
        } else if (highlighted) {
            red = 0.35F;
        } else if (style == Style.PRIMARY) {
            red = 0.55F; green = 0.9F;
        }
        int corner = Math.min(skinCornerSize, Math.min(getWidth(), getHeight()) / 2);
        int[] widths = {corner, getWidth() - 2 * corner, corner};
        int[] heights = {corner, getHeight() - 2 * corner, corner};
        int[] sourceWidths = {120, 1078, 120};
        int[] sourceHeights = {120, 596, 120};
        int y = getY(), v = 140;
        for (int row = 0; row < 3; row++) {
            int x = getX(), u = 34;
            for (int column = 0; column < 3; column++) {
                graphics.setColor(red, green, blue, alpha * (row == 1 && column == 1 ? 0.62F : 1F));
                if (widths[column] > 0 && heights[row] > 0) {
                    graphics.blit(FLOATING_TEXTURE, x, y, widths[column], heights[row],
                            u, v, sourceWidths[column], sourceHeights[row], 1386, 1135);
                }
                x += widths[column]; u += sourceWidths[column];
            }
            y += heights[row]; v += sourceHeights[row];
        }
    }

    private int cornerSize() {
        return Math.min(Math.max(1, Math.round(getHeight() * SOURCE_EDGE / (float) SOURCE_HEIGHT)),
                Math.min(getWidth(), getHeight()) / 2);
    }
}
