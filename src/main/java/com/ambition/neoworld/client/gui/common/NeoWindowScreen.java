package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.client.gui.widget.NeoCloseButton;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opt-in shell: existing feature screens keep their current layout and lifecycle. */
public abstract class NeoWindowScreen extends Screen {
    private final Screen parent;
    private final int desiredWidth, desiredHeight;
    protected NeoWindowLayout window;

    protected NeoWindowScreen(Component title, Screen parent, int width, int height) {
        super(title);
        this.parent = parent;
        desiredWidth = width;
        desiredHeight = height;
    }

    @Override
    protected final void init() {
        window = NeoWindowLayout.centered(width, height, desiredWidth, desiredHeight);
        initContent();
        addRenderableWidget(NeoCloseButton.create(window.closeX(), window.closeY(), window.closeSize(), this::onClose));
    }

    protected abstract void initContent();
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        NeoPopupTextures.panel(graphics, window.x(), window.y(), window.width(), window.height());
        int titleWidth = Math.max(0, window.width() - 72);
        new NeoTitlePlaque(window.x() + 12, window.y() + 6, titleWidth, 24, title).render(graphics, font);
        if (window.contentWidth() > 0 && window.contentHeight() > 0) {
            graphics.enableScissor(window.contentX(), window.contentY(),
                    window.contentX() + window.contentWidth(), window.contentY() + window.contentHeight());
            try { renderContent(graphics, mouseX, mouseY, partialTick); }
            finally { graphics.disableScissor(); }
        }
        // Screen.render calls our no-op background hook before widgets/tooltips.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background is drawn explicitly before the shell in render().
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
