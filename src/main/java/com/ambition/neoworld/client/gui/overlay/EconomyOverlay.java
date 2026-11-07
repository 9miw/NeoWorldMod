package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.client.gui.common.NeoEconomyText;

import com.ambition.neoworld.client.state.ClientEconomyData;

import net.minecraft.network.chat.Component;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

// คลาสเรนเดอร์ยอดเงินระบบ Economy (Silver, Diamond) บน HUD บริเวณกึ่งกลางด้านบนหน้าจอ
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

        Component fullText = NeoEconomyText.balances(ClientEconomyData.getFormattedSilver(),
                ClientEconomyData.getFormattedDiamond(), NeoEconomyText.Order.SILVER_FIRST);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawCenteredString(font, fullText, (int) curX, (int) y, 0xFFFFFF);
        guiGraphics.pose().popPose();
    }
}
