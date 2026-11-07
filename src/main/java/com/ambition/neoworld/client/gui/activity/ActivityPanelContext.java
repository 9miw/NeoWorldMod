package com.ambition.neoworld.client.gui.activity;

import java.util.List;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import com.ambition.neoworld.network.activity.ActivityStatePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Shared services only; each panel chooses its own layout and controls. */
public final class ActivityPanelContext {
    private final ActivityScreen host;
    ActivityPanelContext(ActivityScreen host) { this.host = host; }
    public int width() { return ActivityScreen.CONTENT_W; }
    public int height() { return ActivityScreen.CONTENT_H; }
    public Font font() { return host.panelFont(); }
    public ActivityStatePayload data() { return host.data(); }
    public List<ActivityStatePayload.Entry> entries() { return data() == null ? List.of() : data().entries(); }
    public boolean busy() { return !host.idle(); }
    /** Custom vanilla widgets use panel-local bounds, then receive the shell's scale and input handling. */
    public <T extends AbstractWidget> T widget(T widget) {
        if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width()
                || widget.getY() + widget.getHeight() > height())
            throw new IllegalArgumentException("Activity widget exceeds its content area");
        return host.panelWidget(widget);
    }
    public NeoButton button(String label, int x, int y, int width, int height, Runnable callback, boolean enabled) {
        if (x < 0 || y < 0 || x + width > width() || y + height > height())
            throw new IllegalArgumentException("Activity widget exceeds its content area");
        return host.panelButton(label, x, y, width, height, callback, enabled);
    }
    public void request(Action action, String key) { host.request(action, key); }
    public void page(int delta) { host.changePage(delta); }
    public void tooltip(String text) { host.tooltip(text); }
    public void rebuild() { host.rebuildPanel(); }
    public NeoButton claim(ActivityStatePayload.Entry entry, Action action, int x, int y, int width, int height) {
        var button = button(entry.status() == 0 ? "ยังไม่ครบ" : entry.status() == 2 ? "รับแล้ว" : "รับของ",
                x, y, width, height, () -> request(action, entry.key()), entry.status() == 1);
        button.style(entry.status() == 1 ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD);
        button.setTooltip(Tooltip.create(Component.literal(entry.reward()))); return button;
    }
    public void pagination(int y) {
        button("<", 0, y, 28, 22, () -> page(-1), data() != null && data().page() > 0);
        button(">", 112, y, 28, 22, () -> page(1), data() != null
                && (data().page() + 1) * ActivityStatePayload.PAGE_SIZE < data().total());
    }
    public void pageLabel(GuiGraphics g, int y) {
        if (data() != null) g.drawCenteredString(font(), (data().page() + 1) + " / "
                + Math.max(1, (data().total() + ActivityStatePayload.PAGE_SIZE - 1) / ActivityStatePayload.PAGE_SIZE),
                70, y, NeoGuiStyle.COLOR_TEXT_MUTED);
    }
    public void heading(GuiGraphics g, String title, String description) {
        ActivityTextures.icon(g, host.panelId(), 0, 0, 22);
        float headingScale = Math.min(1.75F, (width() - 28F) / Math.max(1, font().width(title)));
        g.pose().pushPose(); g.pose().translate(28, 3, 0); g.pose().scale(headingScale, headingScale, 1);
        g.drawString(font(), title, 0, 0, NeoGuiStyle.COLOR_GOLD, false); g.pose().popPose();
        g.drawString(font(), font().plainSubstrByWidth(description, width()), 0, 27, NeoGuiStyle.COLOR_TEXT_MUTED, false);
    }
    public void resetLabel(GuiGraphics g, int x, int y) {
        if (data() == null) return;
        long seconds = Math.max(0, data().nextReset() - System.currentTimeMillis() / 1000);
        g.drawString(font(), String.format("รีเซ็ต 04:00 ไทย • %02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                x, y, NeoGuiStyle.COLOR_TEXT_MUTED, false);
    }
}
