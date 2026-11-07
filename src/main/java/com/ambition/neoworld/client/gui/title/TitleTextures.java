package com.ambition.neoworld.client.gui.title;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.ambition.neoworld.client.gui.widget.NeoButton;

/** Independent empty PNG frames and row states. Source dimensions match the exported sprites. */
final class TitleTextures {
    private static final ResourceLocation FRAME = texture("title_v3_frame");
    private static final ResourceLocation LEFT = texture("title_left_panel");
    private static final ResourceLocation RIGHT = texture("title_right_panel");
    private static final ResourceLocation SCENE = texture("title_preview_scene");
    private static final ResourceLocation[] BUTTONS = {texture("title_button_normal"), texture("title_button_hover"),
            texture("title_button_primary"), texture("title_button_disabled")};
    private static final ResourceLocation[] ROWS = {texture("title_v3_row_normal"), texture("title_v3_row_hover"),
            texture("title_v3_row_selected"), texture("title_v3_row_disabled")};
    private TitleTextures() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/title/" + name + ".png");
    }
    static void frame(GuiGraphics g, int width, int height) {
        nineSlice(g, FRAME, 0, 0, width, height, 1575, 820, 30, 30, 4, 4);
    }
    static void panels(GuiGraphics g) {
        g.blit(FRAME, 3, 30, 594, 2, 30, 0, 1515, 20, 1575, 820);
        g.blit(FRAME, 332, 32, 3, 313, 0, 30, 30, 760, 1575, 820);
        nineSlice(g, ROWS[0], 340, 34, 248, 196, 1444, 180, 30, 24, 3, 3);
        g.blit(SCENE, 343, 37, 242, 190, 0, 400, 1024, 804, 1024, 1536);
        g.fillGradient(343, 37, 585, 227, 0x33080D12, 0x44080D12);
        nineSlice(g, ROWS[0], 340, 234, 248, 54, 1444, 180, 30, 24, 3, 3);
    }
    static void button(GuiGraphics g, NeoButton button) {
        int state = !button.active ? 3 : button.isHoveredOrFocused() ? 1
                : button.isSelected() || button.getStyle() == NeoButton.Style.PRIMARY ? 2 : 0;
        int corner = Math.min(4, Math.min(button.getWidth(), button.getHeight()) / 2);
        nineSlice(g, BUTTONS[state], button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                1440, 200, 40, 40, corner, corner);
    }
    static void control(GuiGraphics g, int state, int x, int y, int width, int height) {
        nineSlice(g, BUTTONS[state], x, y, width, height, 1440, 200, 40, 40, 3, 3);
    }
    static void row(GuiGraphics g, int state, int width, int height) {
        nineSlice(g, ROWS[state], 0, 0, width, height, 1444, 180, 30, 24, 3, 3);
    }
    private static void nineSlice(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height,
            int sourceWidth, int sourceHeight, int sourceEdgeX, int sourceEdgeY, int edgeX, int edgeY) {
        int[] widths = {edgeX, width - edgeX * 2, edgeX};
        int[] heights = {edgeY, height - edgeY * 2, edgeY};
        int[] sourceWidths = {sourceEdgeX, sourceWidth - sourceEdgeX * 2, sourceEdgeX};
        int[] sourceHeights = {sourceEdgeY, sourceHeight - sourceEdgeY * 2, sourceEdgeY};
        int dy = y, v = 0;
        for (int row = 0; row < 3; row++) {
            int dx = x, u = 0;
            for (int column = 0; column < 3; column++) {
                if (widths[column] > 0 && heights[row] > 0) g.blit(texture, dx, dy, widths[column], heights[row],
                        u, v, sourceWidths[column], sourceHeights[row], sourceWidth, sourceHeight);
                dx += widths[column]; u += sourceWidths[column];
            }
            dy += heights[row]; v += sourceHeights[row];
        }
    }
}
