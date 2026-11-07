package com.ambition.neoworld.client.gui.activity;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Thirty calendar slots, six columns and three visible rows, independently scrolled. */
public final class MonthlyActivityPanel implements ActivityPanel {
    private int offset;
    private boolean dragging;
    public int id() { return 1; }
    public String title() { return "เช็กอินรายเดือน"; }
    public void init(ActivityPanelContext c) {
        for (int cell = 0; cell < 18; cell++) {
            int index = offset * 6 + cell;
            if (index >= c.entries().size()) break;
            var entry = c.entries().get(index);
            var button = c.button(entry.status() == 2 ? "รับแล้ว" : "รับ", (cell % 6) * 70 + 4,
                    102 + (cell / 6) * 84, 62, 20, () -> c.request(Action.CLAIM, entry.key()), entry.status() == 1);
            if (entry.status() == 1) button.style(com.ambition.neoworld.client.gui.widget.NeoButton.Style.PRIMARY);
            button.setTooltip(Tooltip.create(Component.literal(entry.reward() + (entry.status() == 3 ? "\nหมดสิทธิ์รับแล้ว" : ""))));
        }
        var up = c.button("^", 430, 42, 14, 18, () -> scroll(c, -1), offset > 0);
        up.setTooltip(Tooltip.create(Component.literal("เลื่อนรางวัลขึ้น")));
        var down = c.button("v", 430, 276, 14, 18, () -> scroll(c, 1), offset < 2);
        down.setTooltip(Tooltip.create(Component.literal("เลื่อนรางวัลลง")));
    }
    public void render(ActivityPanelContext c, GuiGraphics g, double mx, double my, float partialTick) {
        c.heading(g, title(), "รับได้เฉพาะวันนี้ • วันที่ผ่านมาแล้วรับย้อนหลังไม่ได้");
        for (int cell = 0; cell < 18; cell++) {
            int index = offset * 6 + cell;
            if (index >= c.entries().size()) break;
            var e = c.entries().get(index); int x = (cell % 6) * 70, y = 42 + (cell / 6) * 84;
            boolean today = c.data() != null && c.data().currentMonthDay() == index + 1;
            g.drawCenteredString(c.font(), "วันที่ " + (index + 1), x + 35, y, today ? NeoGuiStyle.COLOR_GOLD : NeoGuiStyle.COLOR_TEXT_MUTED);
            boolean hover = mx >= x + 9 && mx < x + 57 && my >= y + 10 && my < y + 58;
            ActivityTextures.slot(g, x + 9, y + 10, 48, today, hover, e.status() != 1);
            if (!e.icon().isEmpty()) {
                if (e.icon().is(net.minecraft.world.item.Items.CHEST)) ActivityTextures.icon(g, 3, x + 18, y + 19, 30);
                else {
                    g.pose().pushPose(); g.pose().translate(x + 17, y + 18, 0); g.pose().scale(2, 2, 1);
                    g.renderItem(e.icon(), 0, 0); g.pose().popPose();
                    g.renderItemDecorations(c.font(), e.icon(), x + 33, y + 34);
                }
            } else {
                ActivityTextures.silver(g, x + 23, y + 17, 22);
                g.drawCenteredString(c.font(), c.font().plainSubstrByWidth(e.reward().replace(" Silver", ""), 34), x + 33, y + 43, NeoGuiStyle.COLOR_TEXT);
            }
            if (hover)
                c.tooltip(e.reward() + (e.status() == 3 ? " • หมดสิทธิ์รับแล้ว" : ""));
        }
        int top = 62 + offset * 43;
        ActivityTextures.scrollbar(g, 432, 62, 10, 212, top, 126);
        c.resetLabel(g, 0, 314);
    }
    public boolean mouseScrolled(ActivityPanelContext c, double mx, double my, double delta) {
        if (delta != 0) scroll(c, delta > 0 ? -1 : 1); return true;
    }
    private void scroll(ActivityPanelContext c, int delta) {
        int next = Math.max(0, Math.min(2, offset + delta));
        if (next != offset) { offset = next; c.rebuild(); }
    }
    private void drag(ActivityPanelContext c, double y) {
        int next = Math.max(0, Math.min(2, (int) Math.round((y - 62 - 63) / 43)));
        if (next != offset) { offset = next; c.rebuild(); }
    }
    public boolean mouseClicked(ActivityPanelContext c, double x, double y, int button) {
        if (button != 0 || x < 430 || x >= 444 || y < 62 || y >= 274) return false;
        dragging = true; drag(c, y); return true;
    }
    public boolean mouseDragged(ActivityPanelContext c, double x, double y, int button) {
        if (!dragging || button != 0) return false;
        drag(c, y); return true;
    }
    public boolean mouseReleased(ActivityPanelContext c, int button) {
        boolean handled = dragging; dragging = false; return handled;
    }
}
