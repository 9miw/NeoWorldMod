package com.ambition.neoworld.client.gui.title;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.title.TitleGuiPagePayload;
import com.ambition.neoworld.network.title.TitleGuiRequestPayload;
import com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Display server snapshots only. All hitboxes derive from the same logical layout. */
public final class TitleScreen extends Screen {
    private static final int W = 480, H = 320;
    private static final String[] FILTERS = {"ทั้งหมด", "ทั่วไป", "หายาก", "เชิงขำ"};
    private TitleGuiPagePayload data;
    private UUID pending;
    private Action pendingAction;
    private String selected = "", search = "", status = "กำลังโหลดฉายา...";
    private int filter, page, debounce, waiting;
    private int x, y;
    private float scale;
    private boolean ready;
    private EditBox searchBox;
    private NeoButton equip, clear, visibility, previous, next, refresh;
    private final List<NeoButton> tabs = new ArrayList<>();
    private final List<TitleRowWidget> rows = new ArrayList<>();

    public TitleScreen() { super(Component.literal("จัดการฉายา")); }
    @Override protected void init() {
        tabs.clear(); rows.clear();
        scale = Math.max(0.1F, Math.min(1.25F, Math.min((width - 12F) / W, (height - 12F) / H)));
        x = (width - size(W)) / 2; y = (height - size(H)) / 2;
        button("X", 448, 27, 20, 18, () -> onClose()).style(NeoButton.Style.DESTRUCTIVE)
                .setTooltip(Tooltip.create(Component.literal("ปิดหน้าจัดการฉายา")));
        for (int i = 0; i < FILTERS.length; i++) {
            final int index = i;
            var tab = button(FILTERS[i], 14 + i * 71, 45, 67, 22, () -> {
                filter = index; page = 0; selected = ""; request(Action.QUERY);
            }).selected(i == filter);
            tab.active = pending == null;
            tabs.add(tab);
        }
        searchBox = new EditBox(font, px(16), py(72), size(229), size(15), Component.literal("ค้นหาฉายา"));
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.literal("ค้นหาชื่อหรือ ID..."));
        searchBox.setValue(search);
        searchBox.setResponder(value -> { search = value; page = 0; debounce = 8; ready = false; controls(); });
        addRenderableWidget(searchBox);
        refresh = button("โหลดใหม่", 252, 70, 45, 19, () -> request(Action.QUERY));
        refresh.setTooltip(Tooltip.create(Component.literal("โหลดข้อมูลล่าสุดจาก Server")));
        var entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var row = addRenderableWidget(new TitleRowWidget(px(18), py(94 + i * 34), size(278), size(30), entry,
                    () -> entry.id().equals(selected), () -> data != null && data.equipped().equals(entry.id()),
                    () -> { selected = entry.id(); rebuildWidgets(); }));
            rows.add(row);
        }
        previous = button("<", 14, 267, 26, 20, () -> { page--; request(Action.QUERY); });
        next = button(">", 270, 267, 26, 20, () -> { page++; request(Action.QUERY); });
        visibility = button(data != null && data.showTitle() ? "เหนือหัว: เปิด" : "เหนือหัว: ปิด",
                314, 224, 151, 21, () -> request(Action.SETTINGS));
        visibility.setTooltip(Tooltip.create(Component.literal("เปิดหรือปิดการแสดงฉายาของคุณเหนือหัว")));
        button("เอฟเฟกต์: ยังไม่พร้อม", 314, 248, 151, 16, () -> {}).active = false;
        equip = button("ใส่ฉายา", 314, 268, 91, 22, () -> request(Action.EQUIP)).style(NeoButton.Style.PRIMARY);
        clear = button("ถอด", 410, 268, 55, 22, () -> request(Action.CLEAR));
        controls();
        if (data == null && pending == null && ready == false && status.equals("กำลังโหลดฉายา...")) request(Action.QUERY);
    }
    private NeoButton button(String label, int bx, int by, int bw, int bh, Runnable action) {
        return addRenderableWidget(new NeoButton(Button.builder(Component.literal(label), b -> action.run())
                .bounds(px(bx), py(by), size(bw), size(bh))));
    }
    private int px(int logical) { return x + Math.round(logical * scale); }
    private int py(int logical) { return y + Math.round(logical * scale); }
    private int size(int logical) { return Math.max(1, Math.round(logical * scale)); }
    private List<TitleGuiPagePayload.Entry> entries() { return data == null ? List.of() : data.entries(); }
    private TitleGuiPagePayload.Entry selectedEntry() {
        return entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
    }
    private void controls() {
        if (equip == null) return;
        boolean idle = pending == null && ready && debounce == 0;
        var entry = selectedEntry();
        equip.active = idle && entry != null && entry.unlocked() && !entry.id().equals(data.equipped());
        clear.active = idle && data != null && !data.equipped().isEmpty();
        visibility.active = idle && data != null;
        previous.active = idle && page > 0;
        next.active = idle && data != null && (page + 1) * TitleGuiPagePayload.PAGE_SIZE < data.filteredCount();
        refresh.active = pending == null;
        searchBox.setEditable(pending == null);
        searchBox.setTextColor(pending == null ? NeoGuiStyle.COLOR_TEXT : NeoGuiStyle.COLOR_DISABLED);
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).active = pending == null;
            tabs.get(i).selected(i == filter);
        }
        rows.forEach(row -> row.active = pending == null);
    }
    private void request(Action action) {
        if (pending != null) return;
        var entry = selectedEntry();
        if (action == Action.EQUIP && entry == null) return;
        pending = UUID.randomUUID(); pendingAction = action; waiting = 0; debounce = 0;
        status = action == Action.QUERY ? "กำลังโหลด..." : "กำลังบันทึก...";
        PacketDistributor.sendToServer(new TitleGuiRequestPayload(pending, action,
                action == Action.EQUIP ? entry.id() : "", filter, search, page,
                data != null && (action == Action.SETTINGS ? !data.showTitle() : data.showTitle()),
                data != null && data.showEffects()));
        controls();
    }
    public void receive(TitleGuiPagePayload response) {
        if (!response.requestId().equals(pending)) return;
        var action = pendingAction;
        pending = null; waiting = 0;
        if (!response.error().isEmpty()) {
            ready = false; status = response.error();
        } else {
            data = response; ready = true; page = response.page();
            if (selectedEntry() == null) selected = entries().isEmpty() ? "" : entries().getFirst().id();
            status = action == Action.QUERY ? "" : "บันทึกแล้ว";
        }
        rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if (pending != null && ++waiting > 200) {
            pending = null; ready = false; status = "หมดเวลารอ กดโหลดใหม่เพื่อตรวจข้อมูลล่าสุด"; rebuildWidgets();
        }
        if (debounce > 0 && pending == null && --debounce == 0) request(Action.QUERY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (ready && pending == null && mouseX >= px(14) && mouseX < px(296) && mouseY >= py(94) && mouseY < py(264)) {
            if (deltaY < 0 && next.active) { page++; request(Action.QUERY); }
            else if (deltaY > 0 && previous.active) { page--; request(Action.QUERY); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        TitleTextures.frame(g, W, H);
        TitleTextures.panels(g);
        g.drawCenteredString(font, "จัดการฉายา", W / 2, 16, NeoGuiStyle.COLOR_TEXT);
        if (entries().isEmpty()) g.drawCenteredString(font, pending != null ? "กำลังโหลด..." : "ไม่พบฉายา", 155, 157, NeoGuiStyle.COLOR_TEXT_MUTED);
        if (data != null) {
            int pages = Math.max(1, (data.filteredCount() + TitleGuiPagePayload.PAGE_SIZE - 1) / TitleGuiPagePayload.PAGE_SIZE);
            g.drawCenteredString(font, "หน้า " + (page + 1) + " / " + pages + " • ปลดล็อก "
                    + data.unlockedCount() + " / " + data.totalCount(), 155, 274, NeoGuiStyle.COLOR_TEXT_MUTED);
        }
        renderDetails(g, (mouseX - x) / scale, (mouseY - y) / scale);
        g.drawString(font, font.plainSubstrByWidth(status, 440), 16, 299,
                ready || pending != null ? NeoGuiStyle.COLOR_TEXT_MUTED : NeoGuiStyle.COLOR_ERROR, false);
        g.pose().popPose();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
    }
    private void renderDetails(GuiGraphics g, float mouseX, float mouseY) {
        var entry = selectedEntry();
        if (entry == null) {
            g.drawCenteredString(font, "เลือกฉายาเพื่อดูรายละเอียด", 386, 110, NeoGuiStyle.COLOR_TEXT_MUTED);
            return;
        }
        int color = 0xFF000000 | entry.color();
        g.drawCenteredString(font, font.plainSubstrByWidth(entry.display(), 144), 389, 52, color);
        if (minecraft.player != null) {
            g.drawCenteredString(font, minecraft.player.getName(), 389, 65, NeoGuiStyle.COLOR_TEXT);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, 342, 77, 437, 135, 27, 0.0625F,
                    mouseX, mouseY, minecraft.player);
        }
        g.drawCenteredString(font, entry.rare() ? "หายาก" : entry.category() == 1 ? "เชิงขำ" : "ทั่วไป",
                389, 139, entry.rare() ? NeoGuiStyle.COLOR_GOLD : NeoGuiStyle.COLOR_ACCENT);
        lines(g, entry.description(), 316, 153, 147, 2, NeoGuiStyle.COLOR_TEXT_MUTED);
        lines(g, entry.requirement(), 316, 176, 147, 3, NeoGuiStyle.COLOR_TEXT);
        g.drawString(font, entry.unlocked() ? "ปลดล็อกแล้ว" : "ยังไม่ปลดล็อก", 316, 212,
                entry.unlocked() ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_DISABLED, false);
    }
    private void lines(GuiGraphics g, String text, int tx, int ty, int lineWidth, int limit, int color) {
        var lines = font.split(Component.literal(text), lineWidth);
        for (int i = 0; i < Math.min(limit, lines.size()); i++) g.drawString(font, lines.get(i), tx, ty + i * 10, color, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
