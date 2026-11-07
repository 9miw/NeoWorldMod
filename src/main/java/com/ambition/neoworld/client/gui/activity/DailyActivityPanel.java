package com.ambition.neoworld.client.gui.activity;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public final class DailyActivityPanel implements ActivityPanel {
    private static final int SLOT_Y = 115, SLOT_SIZE = 112;
    public int id() { return 0; }
    public String title() { return "เช็กอินรายวัน"; }
    public void init(ActivityPanelContext c) {
        if (!c.entries().isEmpty()) {
            var entry = c.entries().getFirst();
            var button = c.claim(entry, Action.CHECK_IN, (c.width() - 168) / 2, 245, 168, 36);
            button.setMessage(Component.literal(entry.status() == 2 ? "รับแล้ว" : "รับรางวัล"));
        }
    }
    public void render(ActivityPanelContext c, GuiGraphics g, double mx, double my, float partialTick) {
        int center = c.width() / 2;
        int headingWidth = Math.round(c.font().width(title()) * 2F) + 32;
        ActivityTextures.icon(g, id(), center - headingWidth / 2, 18, 24);
        g.pose().pushPose(); g.pose().translate(center - headingWidth / 2 + 32, 20, 0); g.pose().scale(2F, 2F, 1);
        g.drawString(c.font(), title(), 0, 0, NeoGuiStyle.COLOR_GOLD, false); g.pose().popPose();
        g.drawCenteredString(c.font(), "รับรางวัลได้วันละ 1 ครั้ง", center, 55, NeoGuiStyle.COLOR_TEXT);
        g.drawCenteredString(c.font(), "รางวัลประจำวัน", center, 95, NeoGuiStyle.COLOR_GOLD);
        g.drawCenteredString(c.font(), "รีเซ็ตทุกวัน 04:00 น. (เวลาไทย)", center, 295, NeoGuiStyle.COLOR_TEXT_MUTED);
        if (c.entries().isEmpty()) {
            g.drawCenteredString(c.font(), "กำลังโหลด...", center, 165, NeoGuiStyle.COLOR_TEXT_MUTED); return;
        }
        var entry = c.entries().getFirst();
        int slotX = center - SLOT_SIZE / 2;
        boolean hovered = mx >= slotX && mx < slotX + SLOT_SIZE && my >= SLOT_Y && my < SLOT_Y + SLOT_SIZE;
        ActivityTextures.slot(g, slotX, SLOT_Y, SLOT_SIZE, false, hovered, entry.status() == 2);
        if (!entry.icon().isEmpty()) {
            if (entry.icon().is(Items.CHEST)) ActivityTextures.icon(g, 3, center - 36, SLOT_Y + 20, 72);
            else {
                g.pose().pushPose(); g.pose().translate(center - 32, SLOT_Y + 24, 0); g.pose().scale(4, 4, 1);
                g.renderItem(entry.icon(), 0, 0); g.pose().popPose();
                g.renderItemDecorations(c.font(), entry.icon(), center + 19, SLOT_Y + 67);
            }
        }
        if (hovered) c.tooltip(entry.reward());
    }
}
