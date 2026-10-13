package com.ambition.neoworld.client.gui.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.activity.ActivityRequestPayload;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import com.ambition.neoworld.network.activity.ActivityStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only item previews; every reward action is validated by the server. */
public final class ActivityScreen extends Screen {
    private static final int W = 520, H = 350;
    private static final String[] TABS = {"รายวัน", "รายเดือน", "ออนไลน์", "ค้างรับ"};
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/title/title_frame.png");
    private ActivityStatePayload data;
    private UUID pending;
    private Action pendingAction;
    private int tab, page, x, y, waiting, refreshTicks, cooldown;
    private float scale;
    private String status = "กำลังโหลด...";
    private final List<NeoButton> actionButtons = new ArrayList<>();
    private NeoButton all, previous, next;
    public ActivityScreen() { super(Component.literal("กิจกรรม")); }
    public static void open() { Minecraft.getInstance().setScreen(new ActivityScreen()); }
    public static void receive(ActivityStatePayload response) {
        if (Minecraft.getInstance().screen instanceof ActivityScreen screen) screen.update(response);
    }
    @Override protected void init() {
        actionButtons.clear();
        scale = Math.max(0.1F, Math.min(1.25F, Math.min((width - 12F) / W, (height - 12F) / H)));
        x = (width - size(W)) / 2; y = (height - size(H)) / 2;
        button("X", 482, 27, 20, 18, this::onClose).style(NeoButton.Style.DESTRUCTIVE)
                .setTooltip(Tooltip.create(Component.literal("ปิดกิจกรรม")));
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            var button = button(TABS[i], 20 + i * 121, 50, 116, 22, () -> {
                tab = index; page = 0; data = null; request(Action.QUERY, "");
            }).selected(tab == i);
            button.active = pending == null;
            actionButtons.add(button);
        }
        if (data != null) {
            for (int i = 0; i < data.entries().size(); i++) {
                var entry = data.entries().get(i);
                var claim = button(entry.status() == 0 ? "ยังไม่ครบ" : entry.status() == 2 ? "รับแล้ว" : "รับของ",
                        420, 113 + i * 30, 78, 24, () -> request(tab == 0 ? Action.CHECK_IN : Action.CLAIM, entry.key()));
                claim.active = pending == null && entry.status() == 1;
                claim.style(entry.status() == 1 ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD);
                claim.setTooltip(Tooltip.create(Component.literal(entry.reward())));
                actionButtons.add(claim);
            }
        }
        previous = button("<", 20, 303, 28, 22, () -> { page--; request(Action.QUERY, ""); });
        next = button(">", 145, 303, 28, 22, () -> { page++; request(Action.QUERY, ""); });
        previous.active = pending == null && data != null && page > 0;
        next.active = pending == null && data != null && (page + 1) * ActivityStatePayload.PAGE_SIZE < data.total();
        actionButtons.add(previous); actionButtons.add(next);
        var refresh = button("โหลดใหม่", 180, 303, 80, 22, () -> request(Action.QUERY, ""));
        refresh.active = pending == null; actionButtons.add(refresh);
        all = button("รับทั้งหมด", 346, 303, 152, 22, () -> request(Action.CLAIM_ALL, "")).style(NeoButton.Style.PRIMARY);
        all.active = pending == null && data != null && (!data.checkedIn() || data.pendingCount() > 0); actionButtons.add(all);
        if (cooldown > 0) actionButtons.forEach(button -> button.active = false);
        if (data == null && pending == null) request(Action.QUERY, "");
    }
    private NeoButton button(String label, int bx, int by, int bw, int bh, Runnable action) {
        return addRenderableWidget(new NeoButton(Button.builder(Component.literal(label), b -> action.run())
                .bounds(px(bx), py(by), size(bw), size(bh))));
    }
    private int px(int value) { return x + Math.round(value * scale); }
    private int py(int value) { return y + Math.round(value * scale); }
    private int size(int value) { return Math.max(1, Math.round(value * scale)); }
    private void request(Action action, String key) {
        if (pending != null) return;
        pending = UUID.randomUUID(); pendingAction = action; waiting = 0; refreshTicks = 0; cooldown = 4;
        if (action != Action.QUERY) status = "กำลังรับรางวัล...";
        PacketDistributor.sendToServer(new ActivityRequestPayload(pending, action, key, tab, page));
        actionButtons.forEach(button -> button.active = false);
    }
    private void update(ActivityStatePayload response) {
        if (!response.requestId().equals(pending)) return;
        boolean action = pendingAction != Action.QUERY;
        pending = null; waiting = 0; data = response; tab = response.tab(); page = response.page();
        if (action || status.equals("กำลังโหลด...") || status.startsWith("หมดเวลารอ")) status = response.message();
        rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if (cooldown > 0 && --cooldown == 0 && pending == null) rebuildWidgets();
        if (pending != null && ++waiting > 200) {
            pending = null; status = "หมดเวลารอ กดโหลดใหม่เพื่อตรวจสถานะ";
            rebuildWidgets();
        }
        if (pending == null && ++refreshTicks >= 100) request(Action.QUERY, "");
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (data != null && pending == null && mouseX >= px(20) && mouseX < px(498) && mouseY >= py(110) && mouseY < py(290)) {
            if (deltaY < 0 && next.active) { page++; request(Action.QUERY, ""); }
            else if (deltaY > 0 && previous.active) { page--; request(Action.QUERY, ""); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.blit(FRAME, 0, 0, W, H, 0, 0, 1482, 959, 1482, 959);
        g.fill(14, 47, W - 14, H - 18, NeoGuiStyle.COLOR_PANEL_DARK);
        g.drawCenteredString(font, title, W / 2, 16, NeoGuiStyle.COLOR_TEXT);
        if (data != null) {
            g.drawString(font, "เช็กอิน " + data.monthDays() + "/28 วัน • ออนไลน์ " + data.onlineSeconds() / 60
                    + " นาที • ค้างรับ " + data.pendingCount(), 22, 80, NeoGuiStyle.COLOR_GOLD, false);
            long remaining = Math.max(0, data.nextReset() - System.currentTimeMillis() / 1000);
            String timer = String.format("%02d:%02d:%02d", remaining / 3600, remaining / 60 % 60, remaining % 60);
            g.drawString(font, "รีเซ็ต 04:00 ไทย • อีก " + timer + (data.afk() ? " • AFK: หยุดนับเวลา" : ""),
                    22, 96, data.afk() ? NeoGuiStyle.COLOR_ERROR : NeoGuiStyle.COLOR_TEXT_MUTED, false);
            for (int i = 0; i < data.entries().size(); i++) {
                var entry = data.entries().get(i); int rowY = 111 + i * 30;
                g.fill(20, rowY, 498, rowY + 28, NeoGuiStyle.COLOR_SLOT);
                if (!entry.icon().isEmpty()) g.renderItem(entry.icon(), 24, rowY + 5);
                g.drawString(font, entry.label(), 45, rowY + 3,
                        entry.status() == 1 ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(entry.reward(), 366), 45, rowY + 15,
                        NeoGuiStyle.COLOR_TEXT_MUTED, false);
            }
            if (data.entries().isEmpty()) g.drawCenteredString(font, "ไม่มีรางวัลค้างรับ", W / 2, 180, NeoGuiStyle.COLOR_TEXT_MUTED);
            int pages = Math.max(1, (data.total() + ActivityStatePayload.PAGE_SIZE - 1) / ActivityStatePayload.PAGE_SIZE);
            g.drawCenteredString(font, (page + 1) + " / " + pages, 96, 310, NeoGuiStyle.COLOR_TEXT_MUTED);
        }
        g.drawString(font, font.plainSubstrByWidth(status, 472), 22, 333, NeoGuiStyle.COLOR_TEXT_MUTED, false);
        g.pose().popPose();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        if (data != null) {
            for (int i = 0; i < data.entries().size(); i++) {
                if (mouseX >= px(20) && mouseX < px(416) && mouseY >= py(111 + i * 30) && mouseY < py(139 + i * 30))
                    g.renderTooltip(font, font.split(Component.literal(data.entries().get(i).reward()), 280), mouseX, mouseY);
            }
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
