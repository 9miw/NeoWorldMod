package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoProgressBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Developer-facing catalogue; never requests or changes server data. */
public final class NeoComponentShowcaseScreen extends NeoWindowScreen {
    public NeoComponentShowcaseScreen(Screen parent) {
        super(Component.literal("ส่วนประกอบ GUI กลาง"), parent, 360, 240);
    }
    @Override protected void initContent() {
        if (window.contentWidth() < 80 || window.contentHeight() < 120) return;
        int gap = 6, buttonWidth = (window.contentWidth() - gap * 2) / 3;
        var styles = NeoButton.Style.values();
        String[] labels = {"ทั่วไป", "ยืนยัน", "ลบ"};
        for (int i = 0; i < styles.length; i++) {
            var button = new NeoButton(Button.builder(Component.literal(labels[i]), ignored ->
                    NeoPopupScreen.showAlert(Component.literal("ตัวอย่าง"), Component.literal("ปุ่มกลางทำงานแล้ว"), null))
                    .bounds(window.contentX() + i * (buttonWidth + gap), window.contentY() + 78, buttonWidth, 20))
                    .style(styles[i]);
            addRenderableWidget(button);
        }
    }
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = window.contentX(), y = window.contentY(), w = window.contentWidth();
        NeoText.wrapped(graphics, font, Component.literal("ข้อความหลัก — ใช้ฟอนต์เกมและรองรับภาษาไทย"), NeoText.Role.BODY, x,y,w,24);
        NeoText.wrapped(graphics, font, Component.literal("คำอธิบายประกอบ"), NeoText.Role.MUTED, x,y+28,w,12);
        NeoText.wrapped(graphics, font, Component.literal("ค่าสำคัญ 1,250"), NeoText.Role.EMPHASIS, x,y+44,w,12);
        NeoText.wrapped(graphics, font, Component.literal("ตัวอย่างข้อความผิดพลาด"), NeoText.Role.ERROR, x,y+60,w,12);
        if (window.contentHeight() >= 140)
            new NeoProgressBar(x, y+112, w, 14).progress(0.65F).showPercentage(true).render(graphics,mouseX,mouseY,partialTick);
    }
}
