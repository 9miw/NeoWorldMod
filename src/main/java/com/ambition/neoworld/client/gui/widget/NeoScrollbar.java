package com.ambition.neoworld.client.gui.widget;

import java.util.Objects;
import java.util.function.DoubleConsumer;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Slim track/thumb; values and content sizes use the same logical units. */
public final class NeoScrollbar extends AbstractWidget {
    public enum Orientation { VERTICAL, HORIZONTAL }
    private Orientation orientation = Orientation.VERTICAL;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/scrollbar_slim.png");
    private static final int TEXTURE_SIZE = 1254;
    private double viewport, content, value;
    private double scrollStep = 12;
    private double grabOffset;
    private boolean dragging;
    private DoubleConsumer onChange = ignored -> {};

    public NeoScrollbar(int x, int y, int width, int height, Component label) {
        super(x, y, Math.max(3, width), Math.max(4, height), Objects.requireNonNull(label));
    }

    public NeoScrollbar onChange(DoubleConsumer listener) {
        onChange = Objects.requireNonNull(listener);
        return this;
    }

    public NeoScrollbar orientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation);
        dragging = false;
        return this;
    }

    private int length() { return orientation == Orientation.VERTICAL ? height : width; }
    private int thickness() { return orientation == Orientation.VERTICAL ? width : height; }
    private int start() { return orientation == Orientation.VERTICAL ? getY() : getX(); }
    private double axis(double x, double y) { return orientation == Orientation.VERTICAL ? y : x; }

    public NeoScrollbar range(double viewport, double content) {
        this.viewport = finiteNonnegative(viewport);
        this.content = finiteNonnegative(content);
        dragging = false;
        return value(value);
    }

    public NeoScrollbar value(double value) {
        double next = Math.clamp(finiteNonnegative(value), 0D, getMaxValue());
        if (Double.compare(next, this.value) != 0) {
            this.value = next;
            onChange.accept(next);
        }
        return this;
    }

    public NeoScrollbar scrollStep(double step) {
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Scroll step must be positive");
        scrollStep = step;
        return this;
    }

    public double getValue() { return value; }
    public double getMaxValue() { return Math.max(0D, content - viewport); }
    public boolean isScrollable() { return viewport > 0 && getMaxValue() > 0 && length() > 3; }

    public int getThumbHeight() {
        int track = Math.max(0, length() - 2);
        if (!isScrollable()) return track;
        int minimum = Math.min(track, Math.max(8, thickness() * 2));
        // Reserve travel even when overflow is smaller than a pixel.
        return Math.min(track - 1, Math.max(minimum, (int) Math.round(track * viewport / content)));
    }

    public int getThumbY() {
        int travel = Math.max(0, length() - 2 - getThumbHeight());
        return start() + 1 + (isScrollable() ? (int) Math.round(travel * value / getMaxValue()) : 0);
    }

    /** Forward wheel input here when the pointer is over the associated content viewport. */
    public boolean scroll(double wheelDelta) {
        if (!active || !visible || !isScrollable() || !Double.isFinite(wheelDelta) || wheelDelta == 0) return false;
        double previous = value;
        value(value - wheelDelta * scrollStep);
        return previous != value;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return isMouseOver(mouseX, mouseY) && scroll(scrollY != 0 ? scrollY : scrollX);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isScrollable() && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!isScrollable()) return;
        int thumbY = getThumbY();
        int thumbHeight = getThumbHeight();
        double pointer = axis(mouseX, mouseY);
        if (pointer < thumbY || pointer >= thumbY + thumbHeight) {
            value(value + (pointer < thumbY ? -viewport : viewport));
            dragging = false;
        } else {
            grabOffset = pointer - thumbY;
            dragging = true;
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (!dragging || !active || !visible || !isScrollable()) return;
        int travel = length() - 2 - getThumbHeight();
        if (travel > 0) value((axis(mouseX, mouseY) - start() - 1 - grabOffset) / travel * getMaxValue());
    }

    @Override
    public void onRelease(double mouseX, double mouseY) { dragging = false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused() || !isScrollable()) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> value(value - scrollStep);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> value(value + scrollStep);
            case GLFW.GLFW_KEY_PAGE_UP -> value(value - viewport);
            case GLFW.GLFW_KEY_PAGE_DOWN -> value(value + viewport);
            case GLFW.GLFW_KEY_HOME -> value(0);
            case GLFW.GLFW_KEY_END -> value(getMaxValue());
            default -> { return false; }
        }
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) dragging = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (width <= 0 || height <= 0) return;
        boolean available = active && isScrollable();
        if (!available) dragging = false;
        RenderSystem.enableBlend();
        graphics.setColor(available ? 1F : 0.5F, available ? 1F : 0.5F, available ? 1F : 0.5F, alpha);
        try {
            drawSprite(graphics, 0, thickness(), length(), 217, 109, 81, 1045, 48);
            if (isScrollable()) {
                boolean hover = isHoveredOrFocused() && axis(mouseX, mouseY) >= getThumbY()
                        && axis(mouseX, mouseY) < getThumbY() + getThumbHeight();
                int state = available && dragging ? 2 : available && (hover || isFocused()) ? 1 : 0;
                int[] sourceX = {451, 691, 939};
                int[] sourceWidth = {93, 95, 96};
                drawSprite(graphics, getThumbY() - start(), Math.max(1, thickness() - 2), getThumbHeight(),
                        sourceX[state], 429, sourceWidth[state], 429, 56);
            }
        } finally {
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }

    private void drawSprite(GuiGraphics g, int offset, int breadth, int extent,
                            int u, int v, int sw, int sh, int cap) {
        int inset = (thickness() - breadth) / 2;
        if (orientation == Orientation.VERTICAL) {
            sprite(g, getX() + inset, getY() + offset, breadth, extent, u, v, sw, sh, cap);
        } else {
            g.pose().pushPose();
            g.pose().translate(getX() + offset, getY() + height - inset, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
            sprite(g, 0, 0, breadth, extent, u, v, sw, sh, cap);
            g.pose().popPose();
        }
    }

    private static void sprite(GuiGraphics graphics, int x, int y, int width, int height,
                               int u, int v, int sourceWidth, int sourceHeight, int sourceCap) {
        int cap = Math.min(Math.max(1, Math.round(width * sourceCap / (float) sourceWidth)), height / 2);
        if (cap > 0) {
            graphics.blit(TEXTURE, x, y, width, cap, u, v, sourceWidth, sourceCap, TEXTURE_SIZE, TEXTURE_SIZE);
            graphics.blit(TEXTURE, x, y + height - cap, width, cap,
                    u, v + sourceHeight - sourceCap, sourceWidth, sourceCap, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        if (height > cap * 2) graphics.blit(TEXTURE, x, y + cap, width, height - cap * 2,
                u, v + sourceCap, sourceWidth, sourceHeight - sourceCap * 2, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        int percent = isScrollable() ? (int) Math.round(value / getMaxValue() * 100) : 0;
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(" " + percent + "%"));
    }

    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }
}
