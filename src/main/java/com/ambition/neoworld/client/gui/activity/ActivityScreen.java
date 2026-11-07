package com.ambition.neoworld.client.gui.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.activity.ActivityRequestPayload;
import com.ambition.neoworld.network.activity.ActivityRequestPayload.Action;
import com.ambition.neoworld.network.activity.ActivityStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owns shell/navigation/networking. Each panel owns its layout, widgets and rendering. */
public final class ActivityScreen extends Screen {
    private static final int W = 640, H = 420, NAV_X = 22, NAV_Y = 76, NAV_W = 132, NAV_STEP = 32;
    private static final int NAV_CONTROLS_Y = H - 46;
    private static final int NAV_HEIGHT = NAV_CONTROLS_Y - NAV_Y - 8;
    private static final int NAV_ROWS = NAV_HEIGHT / NAV_STEP;
    static final int CONTENT_X = 180, CONTENT_Y = 58, CONTENT_W = 444, CONTENT_H = 330;
    private final List<ActivityPanel> panels = ActivityPanelRegistry.panels();
    private final Map<Integer, Integer> pages = new HashMap<>();
    private final Map<Integer, Map<Integer, ActivityStatePayload>> snapshots = new HashMap<>(), incoming = new HashMap<>();
    private com.ambition.neoworld.network.activity.ActivitySnapshotProgress progress;
    private boolean opened;
    private final List<NeoButton> requestButtons = new ArrayList<>();
    private final ActivityPanelContext context = new ActivityPanelContext(this);
    private ActivityPanel selected = panels.getFirst();
    private ActivityStatePayload data;
    private UUID pending;
    private Action pendingAction;
    private int x, y, navigationOffset, waiting, cooldown;
    private float scale;
    private String status = "กำลังโหลด...", hoveredReward;
    public ActivityScreen() { super(Component.literal("กิจกรรม")); }
    public static void open() { Minecraft.getInstance().setScreen(new ActivityScreen()); }
    public static void receive(ActivityStatePayload response) {
        if (Minecraft.getInstance().screen instanceof ActivityScreen screen) screen.update(response);
    }
    @Override protected void init() {
        requestButtons.clear();
        scale = Math.max(0.1F, Math.min(1.25F, Math.min((width - 12F) / W, (height - 12F) / H)));
        x = (width - size(W)) / 2; y = (height - size(H)) / 2;
        shellButton("X", 602, 27, 20, 18, this::onClose).style(NeoButton.Style.DESTRUCTIVE)
                .setTooltip(Tooltip.create(Component.literal("ปิดกิจกรรม")));
        navigationOffset = Math.max(0, Math.min(navigationOffset, Math.max(0, panels.size() - NAV_ROWS)));
        for (int row = 0; row < Math.min(NAV_ROWS, panels.size() - navigationOffset); row++) {
            var panel = panels.get(navigationOffset + row);
            var button = shellButton(ActivityTextures.label(panel), NAV_X, NAV_Y + row * NAV_STEP, NAV_W, 28,
                    () -> select(panel)).selected(selected == panel).textScale(1.35F);
            ActivityTextures.skin(button, true);
            button.active = idle(); requestButtons.add(button);
        }
        var up = shellButton("^", NAV_X, NAV_CONTROLS_Y, 30, 22, () -> scrollNavigation(-1));
        up.active = navigationOffset > 0;
        up.setTooltip(Tooltip.create(Component.literal("เลื่อนรายการกิจกรรมขึ้น")));
        var down = shellButton("v", NAV_X + NAV_W - 30, NAV_CONTROLS_Y, 30, 22, () -> scrollNavigation(1));
        down.active = navigationOffset + NAV_ROWS < panels.size();
        down.setTooltip(Tooltip.create(Component.literal("เลื่อนรายการกิจกรรมลง")));
        selected.init(context);
        if (!opened && pending == null) { opened = true; request(Action.QUERY, ""); }
    }
    private void select(ActivityPanel panel) {
        if (!idle() || selected == panel) return;
        selected = panel; showCached(); rebuildWidgets();
    }
    private void showCached() {
        var tab = snapshots.get(selected.id());
        if (tab == null) { data = null; return; }
        int page = Math.min(page(), Math.max(0, tab.size() - 1));
        pages.put(selected.id(), page); data = tab.get(page);
    }
    private void scrollNavigation(int direction) {
        int next = Math.max(0, Math.min(navigationOffset + direction, Math.max(0, panels.size() - NAV_ROWS)));
        if (next != navigationOffset) { navigationOffset = next; rebuildWidgets(); }
    }
    private NeoButton shellButton(String label, int bx, int by, int bw, int bh, Runnable action) {
        return shellButton(Component.literal(label), bx, by, bw, bh, action);
    }
    private NeoButton shellButton(Component label, int bx, int by, int bw, int bh, Runnable action) {
        return addRenderableWidget(ActivityTextures.skin(new NeoButton(Button.builder(label, b -> action.run())
                .bounds(px(bx), py(by), size(bw), size(bh))), false));
    }
    NeoButton panelButton(String label, int bx, int by, int bw, int bh, Runnable action, boolean enabled) {
        var button = shellButton(label, CONTENT_X + bx, CONTENT_Y + by, bw, bh, action);
        button.active = enabled && idle(); requestButtons.add(button); return button;
    }
    <T extends AbstractWidget> T panelWidget(T widget) {
        int bx = widget.getX(), by = widget.getY(), bw = widget.getWidth(), bh = widget.getHeight();
        widget.setPosition(px(CONTENT_X + bx), py(CONTENT_Y + by));
        widget.setWidth(size(bw)); widget.setHeight(size(bh));
        return addRenderableWidget(widget);
    }
    Font panelFont() { return font; }
    ActivityStatePayload data() { return data; }
    int panelId() { return selected.id(); }
    int page() { return pages.getOrDefault(selected.id(), 0); }
    boolean idle() { return pending == null && cooldown == 0; }
    void tooltip(String text) { hoveredReward = text; }
    void rebuildPanel() { rebuildWidgets(); }
    void changePage(int delta) {
        if (!idle() || data == null) return;
        int last = Math.max(0, (data.total() - 1) / ActivityStatePayload.PAGE_SIZE);
        int next = Math.max(0, Math.min(last, page() + delta));
        if (next != page()) { pages.put(selected.id(), next); showCached(); rebuildWidgets(); }
    }
    private int px(int value) { return x + Math.round(value * scale); }
    private int py(int value) { return y + Math.round(value * scale); }
    private int size(int value) { return Math.max(1, Math.round(value * scale)); }
    void request(Action action, String key) {
        if (!idle()) return;
        pending = UUID.randomUUID(); pendingAction = action; waiting = 0; cooldown = 4;
        progress = new com.ambition.neoworld.network.activity.ActivitySnapshotProgress(pending);
        incoming.clear();
        if (action != Action.QUERY) status = "กำลังรับรางวัล...";
        PacketDistributor.sendToServer(new ActivityRequestPayload(pending, action, key, selected.id(), page()));
        requestButtons.forEach(button -> button.active = false);
    }
    private void update(ActivityStatePayload response) {
        if (progress == null || !response.requestId().equals(pending)
                || !progress.accept(response.requestId(), response.tab(), response.page(), response.total())) return;
        incoming.computeIfAbsent(response.tab(), ignored -> new HashMap<>()).put(response.page(), response);
        if (!progress.complete()) return;
        boolean action = pendingAction != Action.QUERY;
        snapshots.clear(); snapshots.putAll(incoming); incoming.clear();
        pending = null; progress = null; waiting = 0; showCached();
        if (action || status.equals("กำลังโหลด...") || status.startsWith("หมดเวลารอ")) status = response.message();
        if (cooldown == 0) rebuildWidgets();
    }
    @Override public void tick() {
        super.tick();
        selected.tick(context);
        if (cooldown > 0 && --cooldown == 0 && pending == null) rebuildWidgets();
        if (pending != null && ++waiting > 200) {
            pending = null; progress = null; incoming.clear();
            status = "หมดเวลารอ กรุณาปิดและเปิดหน้ากิจกรรมใหม่"; rebuildWidgets();
        }
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double lx = (mouseX - x) / scale, ly = (mouseY - y) / scale;
        if (lx >= NAV_X && lx < NAV_X + NAV_W + 10 && ly >= NAV_Y && ly < NAV_Y + NAV_HEIGHT) {
            if (deltaY != 0) scrollNavigation(deltaY > 0 ? -1 : 1); return true;
        }
        if (lx >= CONTENT_X && lx < CONTENT_X + CONTENT_W && ly >= CONTENT_Y && ly < CONTENT_Y + CONTENT_H
                && selected.mouseScrolled(context, lx - CONTENT_X, ly - CONTENT_Y, deltaY)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick); hoveredReward = null;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        ActivityTextures.frame(g, W, H);
        float titleScale = Math.min(2F, 160F / Math.max(1, font.width(title)));
        g.pose().pushPose();
        g.pose().translate(W / 2F, 20F - font.lineHeight * titleScale / 2F, 0);
        g.pose().scale(titleScale, titleScale, 1);
        g.drawCenteredString(font, title, 0, 0, NeoGuiStyle.COLOR_TEXT);
        g.pose().popPose();
        float navigationHeadingScale = Math.min(1.5F, NAV_W / (float) Math.max(1, font.width("รายการกิจกรรม")));
        g.pose().pushPose(); g.pose().translate(NAV_X, 52, 0);
        g.pose().scale(navigationHeadingScale, navigationHeadingScale, 1);
        g.drawString(font, "รายการกิจกรรม", 0, 0, NeoGuiStyle.COLOR_GOLD, false);
        g.pose().popPose();
        if (panels.size() > NAV_ROWS) {
            int track = NAV_HEIGHT, thumb = Math.max(16, track * NAV_ROWS / panels.size());
            int top = NAV_Y + (track - thumb) * navigationOffset / (panels.size() - NAV_ROWS);
            ActivityTextures.scrollbar(g, 157, NAV_Y, 5, track, top, thumb);
        }
        g.drawCenteredString(font, panels.size() + " กิจกรรม", NAV_X + NAV_W / 2, NAV_CONTROLS_Y + 7, NeoGuiStyle.COLOR_TEXT_MUTED);
        g.pose().popPose();
        // Set clipping in screen coordinates with an identity pose, including at small GUI scales.
        g.enableScissor(px(CONTENT_X), py(CONTENT_Y), px(CONTENT_X + CONTENT_W), py(CONTENT_Y + CONTENT_H));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.pose().translate(CONTENT_X, CONTENT_Y, 0);
        selected.render(context, g, (mouseX - x) / scale - CONTENT_X, (mouseY - y) / scale - CONTENT_Y, partialTick);
        g.pose().popPose(); g.disableScissor();
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, font.plainSubstrByWidth(status, CONTENT_W), CONTENT_X, 400, NeoGuiStyle.COLOR_TEXT_MUTED, false);
        g.pose().popPose();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        if (hoveredReward != null) g.renderTooltip(font, font.split(Component.literal(hoveredReward), 280), mouseX, mouseY);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (selected.mouseClicked(context, (mx - x) / scale - CONTENT_X, (my - y) / scale - CONTENT_Y, button)) return true;
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (selected.mouseDragged(context, (mx - x) / scale - CONTENT_X, (my - y) / scale - CONTENT_Y, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = selected.mouseReleased(context, button);
        return super.mouseReleased(mx, my, button) || handled;
    }
}
