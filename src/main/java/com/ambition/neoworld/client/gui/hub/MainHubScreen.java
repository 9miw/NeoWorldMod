package com.ambition.neoworld.client.gui.hub;

import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.navigation.NeoScreenDestination;
import com.ambition.neoworld.client.gui.navigation.NeoScreenRegistry;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MainHubScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int CARD_WIDTH = 112;
    private static final int CARD_HEIGHT = 48;
    private static final int CARD_GAP = 8;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int gridX;
    private int gridY;
    private int closeX;
    private int closeY;

    public MainHubScreen() {
        super(Component.literal("NeoWorld Hub"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(404, width - 8);
        panelHeight = Math.min(232, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        closeX = panelX + panelWidth - 29;
        closeY = panelY + scaleY(109);
        gridX = panelX + (panelWidth - (COLUMNS * CARD_WIDTH + (COLUMNS - 1) * CARD_GAP)) / 2;
        gridY = panelY + scaleY(244);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        NeoGuiRender.frame(graphics, panelX, panelY, panelWidth, panelHeight);
        renderTitle(graphics);
        renderCloseButton(graphics, mouseX, mouseY);
        renderHeader(graphics);
        renderDestinations(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics graphics) {
        int plateWidth = scaleX(620);
        int x = panelX + (panelWidth - plateWidth) / 2;
        int y = panelY + scaleY(34);
        graphics.pose().pushPose();
        graphics.pose().translate(x + plateWidth / 2.0F, y, 0);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.drawString(font, title, -font.width(title) / 2, 0, 0xFFF4F6F7, false);
        graphics.pose().popPose();
        graphics.drawString(font, "+", x + 10, y + 1, NeoGuiStyle.COLOR_GOLD, false);
        graphics.drawString(font, "+", x + plateWidth - 15, y + 1, NeoGuiStyle.COLOR_GOLD, false);
    }

    private void renderCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = NeoGuiRender.inside(mouseX, mouseY, closeX, closeY, 18, 18);
        if (hovered) {
            NeoGuiRender.widget(graphics, closeX, closeY, 18, 18, 1214, 440, 216, 206);
        } else {
            NeoGuiRender.widget(graphics, closeX, closeY, 18, 18, 108, 440, 200, 204);
        }
        NeoGuiRender.centered(graphics, font, "X", closeX, 18, closeY + 5,
                hovered ? 0xFFFFFFFF : 0xFFD2D7DA);
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = panelX + scaleX(41);
        int y = panelY + scaleY(137);
        int width = panelWidth - scaleX(82);
        graphics.drawString(font, "ระบบหลัก", x + 4, y, NeoGuiStyle.COLOR_GOLD, false);
        graphics.drawString(font, "เลือกหน้าที่ต้องการเปิด", x + 4, y + 13, NeoGuiStyle.COLOR_TEXT_MUTED, false);
        NeoGuiRender.separator(graphics, x, x + width, y + 29);
    }

    private void renderDestinations(GuiGraphics graphics, int mouseX, int mouseY) {
        List<NeoScreenDestination> destinations = NeoScreenRegistry.destinations();
        for (int index = 0; index < destinations.size(); index++) {
            NeoScreenDestination destination = destinations.get(index);
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            int x = gridX + column * (CARD_WIDTH + CARD_GAP);
            int y = gridY + row * (CARD_HEIGHT + CARD_GAP);
            renderDestination(graphics, destination, x, y, mouseX, mouseY);
        }
    }

    private void renderDestination(GuiGraphics graphics, NeoScreenDestination destination,
                                   int x, int y, int mouseX, int mouseY) {
        boolean hovered = destination.enabled() && NeoGuiRender.inside(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
        if (!destination.enabled()) {
            NeoGuiRender.widget(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, 783, 752, 336, 147);
        } else if (hovered) {
            NeoGuiRender.widget(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, 438, 78, 290, 284);
        } else {
            NeoGuiRender.widget(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, 44, 80, 314, 279);
        }

        ItemStack icon = destination.icon().get();
        graphics.renderItem(icon, x + 8, y + 8);
        int textX = x + 30;
        int textWidth = CARD_WIDTH - 36;
        int titleColor = destination.enabled() ? NeoGuiStyle.COLOR_TEXT : NeoGuiStyle.COLOR_DISABLED;
        int descriptionColor = destination.enabled() ? NeoGuiStyle.COLOR_TEXT_MUTED : 0xFF737D84;
        graphics.drawString(font, NeoGuiRender.trim(font, destination.title().getString(), textWidth),
                textX, y + 9, titleColor, false);
        graphics.drawString(font, NeoGuiRender.trim(font, destination.description().getString(), textWidth),
                textX, y + 22, descriptionColor, false);
        graphics.drawString(font, destination.enabled() ? "OPEN" : "SOON",
                x + CARD_WIDTH - 29, y + CARD_HEIGHT - 12,
                destination.enabled() ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_GOLD, false);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        NeoScreenDestination hovered = destinationAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.enabled()
                    ? Component.literal("เปิด " + hovered.title().getString())
                    : Component.literal("ยังไม่มี GUI สำหรับ " + hovered.title().getString()), mouseX, mouseY);
        } else if (NeoGuiRender.inside(mouseX, mouseY, closeX, closeY, 18, 18)) {
            graphics.renderTooltip(font, Component.literal("Close"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (NeoGuiRender.inside(mouseX, mouseY, closeX, closeY, 18, 18)) {
            onClose();
            return true;
        }
        NeoScreenDestination destination = destinationAt(mouseX, mouseY);
        if (destination != null && destination.enabled()) {
            destination.opener().run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private NeoScreenDestination destinationAt(double mouseX, double mouseY) {
        List<NeoScreenDestination> destinations = NeoScreenRegistry.destinations();
        for (int index = 0; index < destinations.size(); index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            int x = gridX + column * (CARD_WIDTH + CARD_GAP);
            int y = gridY + row * (CARD_HEIGHT + CARD_GAP);
            if (NeoGuiRender.inside(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT)) {
                return destinations.get(index);
            }
        }
        return null;
    }

    private int scaleX(int textureX) {
        return Math.round(textureX * panelWidth / (float) NeoGuiStyle.FRAME_TEXTURE_WIDTH);
    }

    private int scaleY(int textureY) {
        return Math.round(textureY * panelHeight / (float) NeoGuiStyle.FRAME_TEXTURE_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
