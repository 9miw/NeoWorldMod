package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;

import com.ambition.neoworld.client.state.ClientEconomyData;

import net.minecraft.network.chat.Component;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

// คลาสเรนเดอร์ยอดเงินระบบ Economy (Silver, Gold, Diamond) บน HUD บริเวณกึ่งกลางด้านบนหน้าจอ
public class EconomyOverlay implements LayeredDraw.Layer {

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        Font font = mc.font;
        float scale = 0.5f;

        float curX = (guiGraphics.guiWidth() / 2.0f) / scale;
        float y = 5.0f / scale;

        String silverStr = "§r\uE046§f" + ClientEconomyData.getFormattedSilver();
        String goldStr = " §r\uE047§6" + ClientEconomyData.getFormattedGold();
        String diamondStr = " §r\uE048§b" + ClientEconomyData.getFormattedDiamond();
        Component fullText = NeoBitmapFonts.text( silverStr + goldStr + diamondStr);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawCenteredString(font, fullText, (int) curX, (int) y, 0xFFFFFF);
        guiGraphics.pose().popPose();
    }
}
