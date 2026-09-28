package com.ambition.neoworld.client.gui;

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

    public EconomyBalanceWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Economy Balance"));
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int bgX = this.getX();
        int bgY = this.getY();
        int bgW = this.getWidth();
        int bgH = this.getHeight();

        int textY = bgY + (bgH - font.lineHeight) / 2;

        String silverStr = "\uE019§f" + ClientEconomyData.getFormattedSilver();
        String goldStr = " \uE018§6" + ClientEconomyData.getFormattedGold();
        String diamondStr = " \uE017§b" + ClientEconomyData.getFormattedDiamond();
        String fullText = silverStr + goldStr + diamondStr;

        int textX = bgX + (bgW - font.width(fullText)) / 2;
        guiGraphics.drawString(font, fullText, textX, textY, 0xFFFFFF);

        if (this.isHovered()) {
            List<Component> tooltip = List.of(
                    Component.literal("§7ยอดเงินคงเหลือ:"),
                    Component.literal("\uE019 §fSilver: " + ClientEconomyData.getFullSilver()),
                    Component.literal("\uE018 §6Gold: " + ClientEconomyData.getFullGold()),
                    Component.literal("\uE017 §bDiamond: " + ClientEconomyData.getFullDiamond())
            );
            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
