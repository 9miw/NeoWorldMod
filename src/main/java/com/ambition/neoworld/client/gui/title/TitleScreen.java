package com.ambition.neoworld.client.gui.title;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import com.ambition.neoworld.network.title.TitleGuiPagePayload;
import com.ambition.neoworld.network.title.TitleGuiRequestPayload;
import com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Server-owned title management; selection previews without equipping. */
public final class TitleScreen extends Screen {
    private static final int W = 600, H = 350;
    private static final String[] OWNERSHIP = {"ทั้งหมด", "ปลดล็อกแล้ว", "ยังไม่ปลดล็อก"};
    private static final String[] CATEGORIES = {"ทั้งหมด", "ทั่วไป", "หายาก", "เชิงขำ"};
    private static final String[] SORTS = {"ลำดับเดิม", "ความหายาก", "ชื่อฉายา"};
    private TitleGuiPagePayload data;
    private UUID pending;
    private Action pendingAction;
    private String selected = "", search = "", status = "กำลังโหลดฉายา...";
    private int category, ownership, sort, page, debounce, waiting, x, y, dropdown = -1, menuIndex;
    private float scale, previewYaw;
    private boolean ready, dragging;
    private TitleSearchBox searchBox;
    private TitleButton equip, clear, visibility, previous, next, refresh, categoryButton, sortButton;
    private final List<TitleButton> tabs = new ArrayList<>();
    private final List<TitleRowWidget> rows = new ArrayList<>();
    public TitleScreen() { super(Component.literal("จัดการฉายา")); }
    @Override protected void init() {
        tabs.clear(); rows.clear();
        scale = Math.max(0.1F, Math.min((width - 24F) / W, (height - 16F) / H));
        x = (width - size(W)) / 2; y = (height - size(H)) / 2;
        button("×", 574, 5, 20, 20, this::onClose).setTooltip(Tooltip.create(Component.literal("ปิด (Esc)")));
        searchBox = new TitleSearchBox(font, px(26), py(48), size(253), size(12), 253, 12);
        searchBox.setValue(search);
        searchBox.setResponder(value -> { search = value; page = 0; debounce = 8; ready = false; controls(); });
        addRenderableWidget(searchBox);
        refresh = button("", 294, 40, 32, 24, () -> request(Action.QUERY)).primary();
        refresh.setMessage(NeoGuiIcons.icon(NeoGuiIcons.REFRESH));
        refresh.setTooltip(Tooltip.create(Component.literal("โหลดข้อมูลล่าสุด")));
        for (int i = 0; i < OWNERSHIP.length; i++) {
            final int index = i;
            tabs.add(button(OWNERSHIP[i], 12 + i * 106, 72, 102, 24, () -> {
                ownership = index; filtersChanged();
            }).selected(ownership == i));
        }
        categoryButton = button("ประเภท: " + CATEGORIES[category] + " ▾", 12, 104, 152, 20, () -> openDropdown(0));
        sortButton = button("เรียง: " + SORTS[sort] + " ▾", 174, 104, 152, 20, () -> openDropdown(1));
        var entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            rows.add(addRenderableWidget(new TitleRowWidget(px(12), py(132 + i * 36), size(314), size(32), entry,
                    () -> entry.id().equals(selected), () -> data != null && data.equipped().equals(entry.id()),
                    () -> { selected = entry.id(); previewYaw = 0; controls(); })));
        }
        previous = button("‹", 104, 316, 25, 20, () -> { page--; request(Action.QUERY); });
        next = button("›", 197, 316, 25, 20, () -> { page++; request(Action.QUERY); });
        visibility = button("", 558, 292, 28, 16, () -> request(Action.SETTINGS));
        visibility.setTooltip(Tooltip.create(Component.literal("เปิดหรือปิดฉายาเหนือหัวของคุณ")));
        equip = button("ใส่ฉายานี้", 340, 314, 148, 24, () -> request(Action.EQUIP)).primary();
        clear = button("ถอดฉายา", 494, 314, 94, 24, () -> request(Action.CLEAR));
        controls();
        if (data == null && pending == null && !ready && status.equals("กำลังโหลดฉายา...")) request(Action.QUERY);
    }
    private TitleButton button(String label, int bx, int by, int bw, int bh, Runnable action) {
        return addRenderableWidget(new TitleButton(Component.literal(label), px(bx), py(by), size(bw), size(bh), bw, bh, action));
    }
    private int px(int value) { return x + Math.round(value * scale); }
    private int py(int value) { return y + Math.round(value * scale); }
    private int size(int value) { return Math.max(1, Math.round(value * scale)); }
    private double lx(double value) { return (value - x) / scale; }
    private double ly(double value) { return (value - y) / scale; }
    private List<TitleGuiPagePayload.Entry> entries() { return data == null ? List.of() : data.entries(); }
    private TitleGuiPagePayload.Entry selectedEntry() { return entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private void filtersChanged() { page = 0; dropdown = -1; request(Action.QUERY); }
    private void controls() {
        if (equip == null) return;
        boolean idle = pending == null && ready && debounce == 0;
        var entry = selectedEntry();
        boolean equipped = entry != null && data != null && entry.id().equals(data.equipped());
        equip.setMessage(Component.literal(entry == null ? "เลือกฉายา" : equipped ? "กำลังใส่อยู่" : entry.unlocked() ? "ใส่ฉายานี้" : "ยังไม่ปลดล็อก"));
        equip.active = idle && entry != null && entry.unlocked() && !equipped;
        clear.active = idle && data != null && !data.equipped().isEmpty();
        visibility.active = idle && data != null;
        visibility.setMessage(Component.literal(data != null && data.showTitle() ? "●" : "○"));
        visibility.selected(data != null && data.showTitle());
        previous.active = idle && page > 0;
        next.active = idle && data != null && (page + 1) * TitleGuiPagePayload.PAGE_SIZE < data.filteredCount();
        refresh.active = pending == null;
        categoryButton.active = sortButton.active = pending == null;
        searchBox.setEditable(pending == null);
        searchBox.setTextColor(pending == null ? NeoGuiStyle.COLOR_TEXT : NeoGuiStyle.COLOR_DISABLED);
        for (int i = 0; i < tabs.size(); i++) { tabs.get(i).active = pending == null; tabs.get(i).selected(i == ownership); }
        rows.forEach(row -> row.active = idle);
    }
    private void request(Action action) {
        if (pending != null) return;
        var entry = selectedEntry();
        if (action == Action.EQUIP && entry == null) return;
        pending = UUID.randomUUID(); pendingAction = action; waiting = 0; debounce = 0;
        status = action == Action.QUERY ? "กำลังโหลด..." : "กำลังบันทึก...";
        PacketDistributor.sendToServer(new TitleGuiRequestPayload(pending, action, action == Action.EQUIP ? entry.id() : "",
                category, search, page, data != null && (action == Action.SETTINGS ? !data.showTitle() : data.showTitle()),
                data != null && data.showEffects(), ownership, sort));
        controls();
    }
    public void receive(TitleGuiPagePayload response) {
        if (!response.requestId().equals(pending)) return;
        var action = pendingAction; pending = null; waiting = 0;
        if (!response.error().isEmpty()) { ready = false; status = response.error(); }
        else {
            data = response; ready = true; page = response.page();
            if (selectedEntry() == null) selected = entries().isEmpty() ? "" : entries().getFirst().id();
            status = action == Action.QUERY ? "" : "บันทึกแล้ว";
        }
        rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        if (pending != null && ++waiting > 200) { pending = null; ready = false; status = "หมดเวลารอ กดโหลดใหม่เพื่อลองอีกครั้ง"; rebuildWidgets(); }
        if (debounce > 0 && pending == null && --debounce == 0) request(Action.QUERY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        TitleTextures.frame(g, W, H);
        TitleTextures.panels(g);
        new NeoTitlePlaque(218, -5, 164, 30, Component.literal("จัดการฉายา")).render(g, font);
        if (data != null) g.drawString(font, "ปลดล็อกแล้ว " + data.unlockedCount() + " / " + data.totalCount(), 12, 14, 0xFFB9C5D1, false);
        TitleTextures.control(g, 0, 12, 40, 276, 24);
        if (entries().isEmpty()) {
            g.drawCenteredString(font, pending != null ? "กำลังโหลดฉายา..." : "ไม่พบฉายาที่ตรงกับตัวกรอง", 169, 208, 0xFFB5C2CC);
            g.drawCenteredString(font, "ลองค้นหาใหม่ หรือเลือกแท็บทั้งหมด", 169, 224, 0xFF7C8B96);
        }
        if (data != null) {
            int pages = Math.max(1, (data.filteredCount() + 4) / 5);
            g.drawCenteredString(font, "หน้า " + (page + 1) + " / " + pages, 163, 322, 0xFFCAD3DB);
            String count = data.filteredCount() == 0 ? "0 รายการ" : "แสดง " + (page * 5 + 1) + "–" + Math.min((page + 1) * 5, data.filteredCount()) + " จาก " + data.filteredCount();
            g.drawString(font, count, 326 - font.width(count), 340, 0xFF879AA9, false);
        }
        renderDetails(g);
        g.drawString(font, font.plainSubstrByWidth(status, 310), 12, 340, ready || pending != null ? 0xFF93AAA9 : NeoGuiStyle.COLOR_ERROR, false);
        g.pose().popPose();
        renderPlayer(g);
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        if (dropdown >= 0) renderDropdown(g, mouseX, mouseY);
    }
    private void renderDetails(GuiGraphics g) {
        g.drawString(font, "ตัวอย่างฉายา", 344, 39, 0xFFBDC9D3, false);
        var entry = selectedEntry();
        if (entry == null) { g.drawCenteredString(font, "เลือกฉายาเพื่อดูตัวอย่าง", 464, 130, 0xFFB8C5D0); return; }
        g.drawCenteredString(font, font.plainSubstrByWidth(entry.display(), 228), 464, 62, 0xFF000000 | entry.color());
        if (minecraft.player != null) g.drawCenteredString(font, minecraft.player.getName(), 464, 76, 0xFFF0F5F8);
        g.drawCenteredString(font, "ลากเพื่อหมุน", 464, 216, 0xFFBBCBD5);
        String rarity = entry.rare() ? "หายาก" : entry.category() == 1 ? "เชิงขำ" : "ทั่วไป";
        TitleTextures.control(g, entry.rare() ? 2 : 0, 345, 240, 42, 15);
        g.drawCenteredString(font, rarity, 366, 243, entry.rare() ? NeoGuiStyle.COLOR_GOLD : 0xFFCFD9DF);
        g.drawString(font, font.plainSubstrByWidth(entry.name(), 192), 392, 243, entry.rare() ? NeoGuiStyle.COLOR_GOLD : 0xFFEDF3F5, false);
        g.drawString(font, font.plainSubstrByWidth(entry.description(), 236), 345, 259, 0xFFB4C0CC, false);
        String requirement = entry.unlocked() ? "✓ ปลดล็อกแล้ว" : entry.requirement();
        g.drawString(font, font.plainSubstrByWidth(requirement, 236), 345, 274, entry.unlocked() ? 0xFF24D9D5 : 0xFFE3D5AD, false);
        g.drawString(font, "แสดงฉายาเหนือหัว", 344, 296, 0xFFE4EDF0, false);
        g.drawCenteredString(font, "เลือกดูได้ก่อนใส่จริง", 464, 341, 0xFF879AA9);
    }
    private void renderPlayer(GuiGraphics g) {
        var p = minecraft.player;
        if (selectedEntry() == null || p == null) return;
        float body = p.yBodyRot, bodyOld = p.yBodyRotO, yaw = p.getYRot(), yawOld = p.yRotO;
        float pitch = p.getXRot(), pitchOld = p.xRotO, head = p.yHeadRot, headOld = p.yHeadRotO;
        g.flush(); g.enableScissor(px(344), py(87), px(584), py(213));
        try {
            p.yBodyRot = p.yBodyRotO = 180F + previewYaw;
            p.setYRot(p.yBodyRot); p.yRotO = p.yBodyRot; p.setXRot(0); p.xRotO = 0;
            p.yHeadRot = p.yHeadRotO = p.yBodyRot;
            float entityScale = p.getScale();
            InventoryScreen.renderEntityInInventory(g, px(464), py(150), size(58) / entityScale,
                    new Vector3f(0, p.getBbHeight() / 2F + 0.0625F * entityScale, 0),
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), p);
        } finally {
            p.yBodyRot = body; p.yBodyRotO = bodyOld; p.setYRot(yaw); p.yRotO = yawOld;
            p.setXRot(pitch); p.xRotO = pitchOld; p.yHeadRot = head; p.yHeadRotO = headOld;
            g.disableScissor();
        }
    }
    private void openDropdown(int type) { dropdown = dropdown == type ? -1 : type; menuIndex = type == 0 ? category : sort; }
    private String[] choices() { return dropdown == 0 ? CATEGORIES : SORTS; }
    private void choose(int index) { if (dropdown == 0) category = index; else sort = index; filtersChanged(); }
    private void renderDropdown(GuiGraphics g, int mx, int my) {
        int left = dropdown == 0 ? 12 : 174;
        g.flush(); g.pose().pushPose(); g.pose().translate(x, y, 600); g.pose().scale(scale, scale, 1);
        for (int i = 0; i < choices().length; i++) {
            boolean hover = lx(mx) >= left && lx(mx) < left + 152 && ly(my) >= 126 + i * 22 && ly(my) < 148 + i * 22;
            TitleTextures.control(g, hover || menuIndex == i ? 2 : 0, left, 126 + i * 22, 152, 22);
            g.drawString(font, choices()[i], left + 8, 133 + i * 22, 0xFFF0F4F6, false);
        }
        g.flush(); g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (dropdown >= 0) {
            int left = dropdown == 0 ? 12 : 174;
            int index = (int) Math.floor((ly(my) - 126) / 22);
            if (button == 0 && lx(mx) >= left && lx(mx) < left + 152 && index >= 0 && index < choices().length) choose(index);
            else dropdown = -1;
            return true;
        }
        if (button == 0 && lx(mx) >= 340 && lx(mx) < 588 && ly(my) >= 87 && ly(my) < 230) { dragging = true; return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) { previewYaw = (previewYaw + (float) (dx / scale) * 1.4F) % 360F; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (dropdown >= 0) {
            if (key == 256) dropdown = -1;
            else if (key == 264 || key == 258) menuIndex = (menuIndex + 1) % choices().length;
            else if (key == 265) menuIndex = Math.floorMod(menuIndex - 1, choices().length);
            else if (key == 257 || key == 32) choose(menuIndex);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char c, int modifiers) { return dropdown >= 0 || super.charTyped(c, modifiers); }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dropdown >= 0) return true;
        if (ready && pending == null && lx(mx) >= 12 && lx(mx) < 326 && ly(my) >= 132 && ly(my) < 310) {
            if (dy < 0 && next.active) { page++; request(Action.QUERY); }
            else if (dy > 0 && previous.active) { page--; request(Action.QUERY); }
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}

