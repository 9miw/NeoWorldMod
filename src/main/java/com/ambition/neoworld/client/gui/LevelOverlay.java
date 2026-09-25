package com.ambition.neoworld.client.gui;

import com.ambition.neoworld.client.ClientLevelData;
import com.ambition.neoworld.level.LevelFormula;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

// คลาสเรนเดอร์แถบเลเวล [Lv. X] และหลอด EXP สไตล์ RPG ที่มุมซ้ายบนของหน้าจอ
public class LevelOverlay implements LayeredDraw.Layer {

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        int level = ClientLevelData.getLevel();
        long exp = ClientLevelData.getExp();
        long maxExp = ClientLevelData.getMaxExp();
        float ratio = ClientLevelData.getProgressRatio();

        Font font = mc.font;
        int x = 2 ;
        int y = guiGraphics.guiHeight()-18;
        int barWidth = 100;
        int barHeight = 5;

        // พื้นหลังกล่องสีดำโปร่งแสง

        // ข้อความแสดงเลเวล
        String levelText = "§6§lLv. " + level ;
        guiGraphics.drawString(font, levelText, x, y, 0xFFFFFF, true);

        // ข้อความแสดงจำนวน EXP
        String expText = level >= LevelFormula.MAX_LEVEL ? "§eMAX" : "§7" + String.format("%.2f", ratio*100) + "%";
        int expTextWidth = font.width(expText);
        guiGraphics.drawString(font, expText, x + barWidth - expTextWidth, y, 0xAAAAAA, false);

        // หลอดพื้นหลังสีเทาเข้ม
        int barY = y + 10;
        guiGraphics.fill(x, barY, x + barWidth, barY + barHeight, 0xFF1E293B);

        // หลอดแสดงเปอร์เซ็นต์ EXP (สีฟ้าสดใส)
        int filledWidth = (int) (barWidth * ratio);
        if (filledWidth > 0) {
            guiGraphics.fill(x, barY, x + filledWidth, barY + barHeight, 0xFFF8A606);
        }

        // เส้นขอบของหลอด
        guiGraphics.renderOutline(x, barY, barWidth, barHeight, 0xFF334155);
    }
}
