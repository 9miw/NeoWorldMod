package com.ambition.neoworld.client.gui.party;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Blank silver and teal PNGs shared by the party finder and room. */
final class PartyFinderTheme {
    private static final Sprite FRAME = new Sprite("title_v3_frame", 1575, 820, 30, 30);
    private static final Sprite[] ROWS = sprites("title_v3_row_", 1444, 180, 30, 24, "selected");
    private static final Sprite[] CONTROLS = sprites("title_button_", 1440, 200, 40, 40, "primary");

    private PartyFinderTheme() {}

    static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        FRAME.draw(graphics, x, y, width, height, 6);
    }

    static void row(GuiGraphics graphics, int x, int y, int width, int height,
                    boolean selected, boolean hovered, boolean available) {
        ROWS[selected ? 2 : !available ? 3 : hovered ? 1 : 0].draw(graphics, x, y, width, height, 3);
    }

    static void badge(GuiGraphics graphics, int x, int y, int width, int height, boolean accent) {
        CONTROLS[accent ? 2 : 0].draw(graphics, x, y, width, height, 3);
    }

    static void button(GuiGraphics graphics, NeoButton button) {
        int state = !button.active ? 3 : button.isSelected() ? 2 : button.isHoveredOrFocused() ? 1
                : button.getStyle() == NeoButton.Style.PRIMARY ? 2 : 0;
        CONTROLS[state].draw(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(), 4);
    }

    private static Sprite[] sprites(String prefix, int width, int height, int edgeX, int edgeY, String accent) {
        return new Sprite[] {
                new Sprite(prefix + "normal", width, height, edgeX, edgeY),
                new Sprite(prefix + "hover", width, height, edgeX, edgeY),
                new Sprite(prefix + accent, width, height, edgeX, edgeY),
                new Sprite(prefix + "disabled", width, height, edgeX, edgeY)
        };
    }

    private record Sprite(ResourceLocation texture, int width, int height, int edgeX, int edgeY) {
        private Sprite(String name, int width, int height, int edgeX, int edgeY) {
            this(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/title/" + name + ".png"),
                    width, height, edgeX, edgeY);
        }

        private void draw(GuiGraphics g, int x, int y, int w, int h, int corner) {
            if (w <= 0 || h <= 0) return;
            double scale = Math.min((double) corner / Math.max(edgeX, edgeY),
                    Math.min(w / (2.0 * edgeX), h / (2.0 * edgeY)));
            int dx = (int) Math.round(edgeX * scale);
            int dy = (int) Math.round(edgeY * scale);
            int[] xs = {x, x + dx, x + w - dx, x + w};
            int[] ys = {y, y + dy, y + h - dy, y + h};
            int[] us = {0, edgeX, width - edgeX, width};
            int[] vs = {0, edgeY, height - edgeY, height};
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    int dw = xs[column + 1] - xs[column];
                    int dh = ys[row + 1] - ys[row];
                    if (dw > 0 && dh > 0) g.blit(texture, xs[column], ys[row], dw, dh, us[column], vs[row],
                            us[column + 1] - us[column], vs[row + 1] - vs[row], width, height);
                }
            }
        }
    }
}
