package com.ambition.neoworld.client.gui.activity;

import net.minecraft.client.gui.GuiGraphics;

/** Coordinates start at (0,0) inside the right-hand content area. */
public interface ActivityPanel {
    int id();
    String title();
    void init(ActivityPanelContext context);
    void render(ActivityPanelContext context, GuiGraphics graphics, double mouseX, double mouseY, float partialTick);
    default void tick(ActivityPanelContext context) {}
    default boolean mouseClicked(ActivityPanelContext context, double x, double y, int button) { return false; }
    default boolean mouseDragged(ActivityPanelContext context, double x, double y, int button) { return false; }
    default boolean mouseReleased(ActivityPanelContext context, int button) { return false; }
    default boolean mouseScrolled(ActivityPanelContext context, double mouseX, double mouseY, double delta) { return false; }
}
