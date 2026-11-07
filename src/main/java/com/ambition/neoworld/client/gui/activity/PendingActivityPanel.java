package com.ambition.neoworld.client.gui.activity;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;

public final class PendingActivityPanel implements ActivityPanel {
    public int id() { return 3; }
    public String title() { return "รางวัลค้างรับ"; }
    public void init(ActivityPanelContext c) {
        for (int i = 0; i < c.entries().size(); i++) c.claim(c.entries().get(i), Action.CLAIM, c.width() - 80, 45 + i * 32, 76, 26);
        c.pagination(246);
    }
    public void render(ActivityPanelContext c, GuiGraphics g, double mx, double my, float partialTick) {
        c.heading(g, title(), "รางวัลที่ยังไม่ได้รับจะอยู่ที่นี่ แม้ข้ามวันหรือเดือน");
        for (int i = 0; i < c.entries().size(); i++) {
            var e = c.entries().get(i); int y = 42 + i * 32;
            g.fill(0, y, c.width(), y + 30, NeoGuiStyle.COLOR_SLOT);
            if (!e.icon().isEmpty()) g.renderItem(e.icon(), 5, y + 7);
            g.drawString(c.font(), c.font().plainSubstrByWidth(e.label(), 326), 27, y + 4, NeoGuiStyle.COLOR_ACCENT, false);
            g.drawString(c.font(), c.font().plainSubstrByWidth(e.reward(), 326), 27, y + 16, NeoGuiStyle.COLOR_TEXT_MUTED, false);
            if (mx >= 0 && mx < c.width() - 84 && my >= y && my < y + 30) c.tooltip(e.reward());
        }
        if (c.entries().isEmpty()) g.drawCenteredString(c.font(), c.data() == null ? "กำลังโหลด..." : "ไม่มีรางวัลค้างรับ", c.width() / 2, 123, NeoGuiStyle.COLOR_TEXT_MUTED);
        c.pageLabel(g, 253);
    }
    public boolean mouseScrolled(ActivityPanelContext c, double mx, double my, double delta) {
        if (delta != 0) c.page(delta > 0 ? -1 : 1); return true;
    }
}
