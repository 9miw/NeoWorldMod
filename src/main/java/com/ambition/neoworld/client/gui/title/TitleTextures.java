package com.ambition.neoworld.client.gui.title;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Independent empty PNG frames and row states. Source dimensions match the exported sprites. */
final class TitleTextures {
    private static final ResourceLocation FRAME = texture("title_frame");
    private static final ResourceLocation LEFT = texture("title_left_panel");
    private static final ResourceLocation RIGHT = texture("title_right_panel");
    private static final ResourceLocation[] ROWS = {texture("title_row_normal"), texture("title_row_hover"),
            texture("title_row_selected"), texture("title_row_disabled")};
    private TitleTextures() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/title/" + name + ".png");
    }
    static void frame(GuiGraphics g, int width, int height) {
        g.blit(FRAME, 0, 0, width, height, 0, 0, 1482, 959, 1482, 959);
    }
    static void panels(GuiGraphics g) {
        nineSlice(g, LEFT, 10, 40, 294, 251, 1115, 1272, 80, 80, 8, 8);
        nineSlice(g, RIGHT, 306, 43, 164, 249, 921, 1515, 80, 80, 8, 8);
    }
    static void row(GuiGraphics g, int state, int width, int height) {
        nineSlice(g, ROWS[state], 0, 0, width, height, 1960, 144, 64, 26, 8, 5);
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
