package com.ambition.neoworld.client.gui;

import com.ambition.neoworld.client.ClientEconomyData;
import com.ambition.neoworld.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// AbstractWidget สำหรับแสดงยอดเงิน (Silver, Gold, Diamond) ภายใน GUI เช่น CustomNPCs Trader
public class EconomyBalanceWidget extends AbstractWidget {

    private ItemStack silverStack;
    private ItemStack goldStack;
    private ItemStack diamondStack;

    public EconomyBalanceWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Economy Balance"));
        if (ModItems.SILVER_COIN.isBound()) {
            silverStack = new ItemStack(ModItems.SILVER_COIN.get());
        }
        if (ModItems.GOLD_COIN.isBound()) {
            goldStack = new ItemStack(ModItems.GOLD_COIN.get());
        }
        if (ModItems.DIAMOND_COIN.isBound()) {
            diamondStack = new ItemStack(ModItems.DIAMOND_COIN.get());
        }
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int bgX = this.getX();
        int bgY = this.getY();
        int bgW = this.getWidth();
        int bgH = this.getHeight();


        int textY = bgY + (bgH - 8) / 2;

        int curX = guiGraphics.guiWidth()-5;
        int y = 5; // ตำแหน่งอยู่เหนือ LevelOverlay เล็กน้อย

        String silverStr = "\uE019§f" + ClientEconomyData.getFormattedSilver();
        String goldStr = " \uE018§6" + ClientEconomyData.getFormattedGold();
        String diamondStr = " \uE017§b" + ClientEconomyData.getFormattedDiamond();
        guiGraphics.drawString(font, silverStr+goldStr+diamondStr, curX - font.width(silverStr+goldStr+diamondStr), y, 0xFFFFFF);

    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
