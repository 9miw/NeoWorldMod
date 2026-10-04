package com.ambition.neoworld.client.gui.widget;

import com.ambition.neoworld.client.ClientEconomyData;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

// AbstractWidget สำหรับแสดงยอดเงิน (Silver, Gold, Diamond) ภายใน GUI เช่น CustomNPCs Trader
public class EconomyBalanceWidget extends AbstractWidget {
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private static final int HORIZONTAL_PADDING = 4;
    private final Alignment alignment;

    public EconomyBalanceWidget(int x, int y, int width, int height) {
        this(x, y, width, height, Alignment.CENTER);
    }

    public EconomyBalanceWidget(int x, int y, int width, int height, Alignment alignment) {
        super(x, y, width, height, Component.literal("Economy Balance"));
        this.alignment = alignment;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int bgX = this.getX();
        int bgY = this.getY();
        int bgW = this.getWidth();
        int bgH = this.getHeight();

        String silverStr = "§r\uE046§f" + ClientEconomyData.getFormattedSilver();
        String goldStr = " §r\uE047§6" + ClientEconomyData.getFormattedGold();
        String diamondStr = " §r\uE048§b" + ClientEconomyData.getFormattedDiamond();
        String fullText = diamondStr + goldStr + silverStr;

        int textWidth = font.width(fullText);
        int textX = switch (alignment) {
            case LEFT -> bgX + HORIZONTAL_PADDING;
            case CENTER -> bgX + (bgW - textWidth) / 2;
            case RIGHT -> bgX + bgW - textWidth - HORIZONTAL_PADDING;
        };
        int textY = bgY + (bgH - font.lineHeight) / 2;

        guiGraphics.drawString(font, fullText, textX, textY, 0xFFFFFF);

        if (this.isHovered()) {
            List<Component> tooltip = List.of(
                    Component.literal("§7ยอดเงินคงเหลือ:"),
                    Component.literal("§f\uE046 §fSilver: " + ClientEconomyData.getFullSilver()),
                    Component.literal("§f\uE047 §6Gold: " + ClientEconomyData.getFullGold()),
                    Component.literal("§f\uE048 §bDiamond: " + ClientEconomyData.getFullDiamond())
            );
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
