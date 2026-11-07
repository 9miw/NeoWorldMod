package com.ambition.neoworld.client.gui.activity;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import com.ambition.neoworld.client.gui.widget.NeoProgressBar;

public final class OnlineActivityPanel implements ActivityPanel {
    private static final int[] MINUTES = {15, 30, 60, 120};
    private static final int CARD_Y = 98, CARD_WIDTH = 104, CARD_HEIGHT = 194, PITCH = 112;
    private final NeoProgressBar[] progressBars = new NeoProgressBar[4];
    public OnlineActivityPanel() {
        for (int i = 0; i < progressBars.length; i++) progressBars[i] = new NeoProgressBar(
                12 + i * PITCH, CARD_Y + 122, 84, 10).showPercentage(false);
    }
    public int id() { return 2; }
    public String title() { return "รางวัลออนไลน์"; }
    public void init(ActivityPanelContext c) {
        for (int i = 0; i < Math.min(4, c.entries().size()); i++) {
            var entry = c.entries().get(i);
            var button = c.claim(entry, Action.CLAIM, 10 + i * PITCH, CARD_Y + 160, 88, 26);
            button.setMessage(Component.literal(entry.status() == 2 ? "รับแล้ว" : entry.status() == 1 ? "รับรางวัล" : "ยังไม่ครบ"));
        }
    }
    public void render(ActivityPanelContext c, GuiGraphics g, double mx, double my, float partialTick) {
        int seconds = c.data() == null ? 0 : c.data().onlineSeconds();
        int center = c.width() / 2, headingWidth = Math.round(c.font().width(title()) * 2F) + 36;
        ActivityTextures.icon(g, id(), center - headingWidth / 2, 12, 28);
        g.pose().pushPose(); g.pose().translate(center - headingWidth / 2 + 36, 17, 0); g.pose().scale(2F, 2F, 1);
        g.drawString(c.font(), title(), 0, 0, NeoGuiStyle.COLOR_GOLD, false); g.pose().popPose();
        g.drawCenteredString(c.font(), "เวลาออนไลน์สะสมวันนี้ " + seconds / 60 + " นาที", center, 50, NeoGuiStyle.COLOR_TEXT);
        g.drawCenteredString(c.font(), (c.data() != null && c.data().afk() ? "AFK: หยุดนับเวลา" : "ไม่นับช่วง AFK")
                + " • รีเซ็ต 04:00 น. (เวลาไทย)", center, 70, NeoGuiStyle.COLOR_TEXT_MUTED);
        for (int i = 0; i < Math.min(4, c.entries().size()); i++) {
            var e = c.entries().get(i); int x = 2 + i * PITCH;
            ActivityTextures.card(g, x, CARD_Y, CARD_WIDTH, CARD_HEIGHT, e.status() == 1);
            g.drawCenteredString(c.font(), MINUTES[i] + " นาที", x + CARD_WIDTH / 2, CARD_Y + 12, NeoGuiStyle.COLOR_GOLD);
            boolean hovered = mx >= x + 14 && mx < x + 90 && my >= CARD_Y + 34 && my < CARD_Y + 110;
            ActivityTextures.slot(g, x + 14, CARD_Y + 34, 76, false, hovered, false);
            if (e.icon().is(Items.CHEST)) ActivityTextures.icon(g, 3, x + 28, CARD_Y + 48, 48);
            else if (!e.icon().isEmpty()) {
                g.pose().pushPose(); g.pose().translate(x + 28, CARD_Y + 48, 0); g.pose().scale(3, 3, 1);
                g.renderItem(e.icon(), 0, 0); g.pose().popPose();
                g.renderItemDecorations(c.font(), e.icon(), x + 53, CARD_Y + 75);
            }
            progressBars[i].progress(seconds / (double) (MINUTES[i] * 60)).render(g, c.font());
            g.drawCenteredString(c.font(), Math.min(seconds / 60, MINUTES[i]) + "/" + MINUTES[i] + " นาที",
                    x + CARD_WIDTH / 2, CARD_Y + 139, NeoGuiStyle.COLOR_TEXT);
            if (hovered) c.tooltip(e.reward());
        }
        if (c.data() == null) g.drawCenteredString(c.font(), "กำลังโหลด...", center, 180, NeoGuiStyle.COLOR_TEXT_MUTED);
        g.drawCenteredString(c.font(), "ชี้ที่ไอเทมเพื่อดูรายละเอียดรางวัล", center, 315, NeoGuiStyle.COLOR_TEXT_MUTED);
    }
}
