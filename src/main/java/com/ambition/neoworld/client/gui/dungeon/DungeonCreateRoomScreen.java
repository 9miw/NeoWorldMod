package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Fixed-capacity confirmation; only the content scrolls on compact viewports. */
public final class DungeonCreateRoomScreen extends Screen {
    private static final ResourceLocation DIVIDER = asset("browser/divider"), CLOCK = asset("hud/clock");
    private static final int TEXT = 0xFFE7ECEF, MUTED = 0xFFABB4BA, GOLD = 0xFFF2C14E;
    private static final String[] MODES = {"solo", "trio", "raid"};
    private static final int[] CAPACITIES = {1, 3, 6};
    private final Screen parent;
    private final DungeonBrowserDataPayload.Entry dungeon;
    private final int selected;
    private NeoButton create;
    private NeoScrollbar bodyBar, rewardBar;
    private int x, y, w, h, bodyX, bodyY, bodyW, bodyH, footerY, slotSize, pitch, rewardWidth;
    private float spacing;
    private ItemStack hoveredItem;
    private Component hoveredText;

    public DungeonCreateRoomScreen(Screen parent, DungeonBrowserDataPayload.Entry dungeon) {
        super(Component.literal("สร้างห้องดันเจี้ยน"));
        this.parent = parent; this.dungeon = dungeon;
        selected = DungeonCreateRoomLayout.modeIndex(dungeon.modeMask());
    }

    @Override protected void init() {
        double oldBody = bodyBar == null ? 0 : bodyBar.getValue(), oldRewards = rewardBar == null ? 0 : rewardBar.getValue();
        var layout = DungeonCreateRoomLayout.fitV5(width, height);
        w = layout.width(); h = layout.height(); x = (width - w) / 2; y = (height - h) / 2;
        bodyX = x + 12; bodyW = layout.bodyWidth(); bodyY = y + layout.bodyTop(); footerY = y + layout.footerTop();
        bodyH = layout.bodyHeight(); spacing = layout.spacing();
        rewardWidth = layout.rewardWidth(); slotSize = layout.slotSize(); pitch = slotSize + 4;
        bodyBar = addRenderableWidget(new NeoScrollbar(x + w - 18, bodyY, 6, bodyH, Component.literal("รายละเอียดดันเจี้ยน"))
                .range(bodyH, layout.contentHeight()).scrollStep(18));
        bodyBar.visible = bodyBar.isScrollable(); bodyBar.value(oldBody);
        rewardBar = addRenderableWidget(new NeoScrollbar(bodyX + 6, 0, rewardWidth, 6, Component.literal("รายการรางวัล"))
                .orientation(NeoScrollbar.Orientation.HORIZONTAL).scrollStep(pitch));
        int count = dungeon.rewardItems().size();
        rewardBar.range(rewardWidth, count == 0 ? 0 : count * pitch - 4);
        rewardBar.visible = rewardBar.isScrollable(); rewardBar.value(oldRewards); positionRewardBar();
        int buttonWidth = Math.min(142, (w - 62) / 2);
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("\u2190 ย้อนกลับ"), ignored -> onClose())
                .bounds(x + 12, footerY, buttonWidth, 24)).style(NeoButton.Style.STANDARD));
        create = addRenderableWidget(new NeoButton(Button.builder(
                NeoGuiIcons.label(NeoGuiIcons.LEAVE, "สร้างห้อง"), ignored -> createRoom())
                .bounds(x + w - 12 - buttonWidth, footerY, buttonWidth, 24)).style(NeoButton.Style.PRIMARY)
                .frameRenderer((g, button) -> {
                    DungeonBrowserV5Skin.socket(g, "primary", button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                            1134, 280, 100, 8);
                    if (!button.active) g.fill(button.getX()+6, button.getY()+6,
                            button.getX()+button.getWidth()-6, button.getY()+button.getHeight()-6, 0xAA192020);
                    else if (button.isHoveredOrFocused()) g.renderOutline(button.getX()+3, button.getY()+3,
                            button.getWidth()-6, button.getHeight()-6, 0xFF65E4EA);
                    g.setColor(1, 1, 1, 1);
                }));
        create.active = selected >= 0;
        create.setTooltip(Tooltip.create(Component.literal(selected < 0 ? "ดันเจี้ยนต้องกำหนดโหมดเดียวก่อนสร้างห้อง" : "สร้างห้องตามจำนวนผู้เล่นที่ดันกำหนด")));
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("X"), ignored -> onClose())
                .bounds(x + w + 2, y + Math.round(w * 50F / 1468F), 18, 18)
                .tooltip(Tooltip.create(Component.literal("ย้อนกลับ")))).textScrolling(false));
    }

    private int cy(int offset) { return bodyY + Math.round(offset * spacing) - (int) bodyBar.getValue(); }
    private void positionRewardBar() { rewardBar.setY(cy(178) + slotSize + 4); }

    private void createRoom() {
        if (selected < 0 || minecraft.getConnection() == null || !dungeon.id().matches("[a-z0-9_]+")) return;
        create.active = false;
        minecraft.getConnection().sendCommand("neow dungeon room create " + dungeon.id() + " " + MODES[selected]);
        minecraft.setScreen(null);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta); hoveredItem = null; hoveredText = null;
        DungeonBrowserV5Skin.frame(g, x, y, w, h);
        g.drawCenteredString(font, NeoGuiRender.trim(font, title.getString(), Math.max(30, w * 34 / 100 - 8)),
                x + w / 2, y + Math.round(w * 60F / 1468F) - font.lineHeight / 2, TEXT);
        positionRewardBar();
        g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        renderBody(g, mx, my);
        if (rewardBar.visible) rewardBar.render(g, mx, my, delta);
        g.disableScissor();
        for (var renderable : renderables) if (renderable != rewardBar) renderable.render(g, mx, my, delta);
        if (w >= 430) {
            Component mode = NeoGuiIcons.label(NeoGuiIcons.MEMBER, selected < 0 ? "ยังไม่กำหนดโหมด" : "โหมด " + CAPACITIES[selected] + " คน");
            if (font.width(mode) <= w - 2 * create.getWidth() - 56) g.drawCenteredString(font, mode, x + w / 2, footerY + 10, MUTED);
        }
        if (hoveredItem != null) g.renderTooltip(font, hoveredItem, mx, my);
        else if (hoveredText != null) g.renderTooltip(font, hoveredText, mx, my);
    }

    private void renderBody(GuiGraphics g, int mx, int my) {
        int split = bodyX + bodyW * 51 / 100, rightX = split + 14;
        int leftW = Math.max(1, split - bodyX - 12), rightW = bodyX + bodyW - rightX - 4;
        DungeonBrowserV5Skin.entrance(g, false, bodyX, cy(0), 17);
        text(g, dungeon.displayName(), bodyX + 22, cy(0), leftW - 22, GOLD);
        text(g, dungeon.id(), bodyX + 22, cy(14), leftW - 22, MUTED);
        if (inBody(mx, my) && inside(mx, my, bodyX, cy(0), leftW, font.lineHeight)) hoveredText = Component.literal(dungeon.displayName());
        int valueWidth = Math.max(28, leftW / 3);
        metadata(g, 0, "เลเวลแนะนำ", "Lv. " + dungeon.minimumLevel() + "+", 36, leftW, valueWidth);
        metadata(g, -1, "จำนวนผู้เล่น", selected < 0 ? "ไม่ระบุ" : CAPACITIES[selected] + " คน", 52, leftW, valueWidth);
        metadata(g, -2, "เวลา", dungeon.timeLimitSeconds() <= 0 ? "ไม่จำกัด"
                : "%d:%02d".formatted(dungeon.timeLimitSeconds() / 60, dungeon.timeLimitSeconds() % 60), 68, leftW, valueWidth);
        int previewHeight = Math.max(24, Math.min(Math.round(82 * spacing), rightW / 2));
        DungeonPreviewImages.draw(g, dungeon.imageId(), rightX, cy(0), rightW, previewHeight);
        g.renderOutline(rightX - 1, cy(0) - 1, rightW + 2, previewHeight + 2, 0xFF657074);
        divider(g, 88);
        g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.MEMBER, ""), bodyX, cy(100), TEXT, false);
        text(g, "เงื่อนไขเข้าร่วม", bodyX + 18, cy(100), bodyW - 18, GOLD);
        String first = selected < 0 ? "ต้องกำหนดโหมดเดียว" : selected == 0 ? "เล่นคนเดียว" : "ต้องเป็นหัวหน้าปาร์ตี้";
        String second = selected < 0 ? "ยังสร้างห้องไม่ได้" : selected == 0 ? "ไม่ต้องมีปาร์ตี้" : "ปาร์ตี้ " + CAPACITIES[selected] + " คน";
        g.drawString(font, NeoGuiIcons.label(selected == 0 ? NeoGuiIcons.READY : NeoGuiIcons.LEADER, ""), bodyX + 4, cy(119), TEXT, false);
        text(g, first, bodyX + 22, cy(119), leftW - 22, TEXT);
        g.drawString(font, NeoGuiIcons.label(selected == 0 ? NeoGuiIcons.READY : NeoGuiIcons.MEMBER, ""), bodyX + 4, cy(135), TEXT, false);
        text(g, second, bodyX + 22, cy(135), leftW - 22, TEXT);
        DungeonGuiIcons.render(g, 1, rightX, cy(118) - 1, 13);
        text(g, dungeon.waveCount() + " Wave", rightX + 18, cy(118), rightW - 18, TEXT);
        DungeonGuiIcons.render(g, 2, rightX, cy(134) - 1, 13);
        text(g, "บอส: " + (dungeon.bossName().isBlank() ? "ไม่มี" : dungeon.bossName()), rightX + 18, cy(134), rightW - 18, TEXT);
        divider(g, 151);
        NeoSystemIcons.render(g, NeoSystemIcons.Icon.MARKET, bodyX, cy(161) - 2, 13, true);
        text(g, "รางวัล", bodyX + 18, cy(161), bodyW - 34, GOLD);
        if (dungeon.previewIncomplete()) {
            text(g, "*", bodyX + bodyW - 10, cy(161), 10, GOLD);
            if (inBody(mx, my) && inside(mx, my, bodyX + bodyW - 14, cy(161), 14, font.lineHeight)) hoveredText = Component.literal("ตัวอย่างรางวัล จำนวนและโอกาสขึ้นกับการตั้งค่า");
        }
        renderRewards(g, mx, my);
    }

    private void renderRewards(GuiGraphics g, int mx, int my) {
        int leftEdge = bodyX + 6, top = cy(178), offset = (int) rewardBar.getValue();
        g.enableScissor(leftEdge, top, leftEdge + rewardWidth, top + slotSize);
        try {
            for (int i = offset / pitch; i < dungeon.rewardItems().size(); i++) {
                int left = leftEdge + i * pitch - offset;
                if (left >= leftEdge + rewardWidth) break;
                g.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), left, top, slotSize, slotSize);
                ItemStack stack = dungeon.rewardItems().get(i);
                int padding = slotSize == 18 ? 1 : 2;
                g.pose().pushPose();
                g.pose().translate(left + padding, top + padding, 0);
                float scale = (slotSize - padding * 2) / 16F;
                g.pose().scale(scale, scale, 1);
                g.renderItem(stack, 0, 0); g.renderItemDecorations(font, stack, 0, 0);
                g.pose().popPose();
                if (inBody(mx, my) && inside(mx, my, leftEdge, top, rewardWidth, slotSize) && mx >= left && mx < left + slotSize) hoveredItem = stack;
            }
        } finally { g.disableScissor(); }
        if (dungeon.rewardItems().isEmpty()) text(g, "ไม่มีตัวอย่างไอเทม", leftEdge, top + 4, rewardWidth, MUTED);
    }

    private void metadata(GuiGraphics g, int icon, String label, String value, int top, int leftWidth, int valueWidth) {
        if (icon >= 0) DungeonGuiIcons.render(g, icon, bodyX + 2, cy(top) - 1, 12);
        else if (icon == -1) g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.MEMBER, ""), bodyX + 2, cy(top), TEXT, false);
        else g.blit(CLOCK, bodyX + 2, cy(top) - 1, 12, 12, 0, 0, 32, 32, 32, 32);
        text(g, label, bodyX + 20, cy(top), leftWidth - valueWidth - 26, MUTED);
        String fitted = NeoGuiRender.trim(font, value, valueWidth);
        g.drawString(font, fitted, bodyX + leftWidth - font.width(fitted), cy(top), TEXT, false);
    }

    private void divider(GuiGraphics g, int top) { g.blit(DIVIDER, bodyX, cy(top), bodyW - 4, 3, 0, 0, 884, 20, 884, 20); }
    private void text(GuiGraphics g, String value, int left, int top, int max, int color) {
        if (max > 0) g.drawString(font, NeoGuiRender.trim(font, value, max), left, top, color, false);
    }
    private boolean inBody(double mx, double my) { return inside(mx, my, bodyX, bodyY, bodyW, bodyH); }
    private static boolean inside(double mx, double my, int left, int top, int width, int height) {
        return mx >= left && mx < left + width && my >= top && my < top + height;
    }
    private static ResourceLocation asset(String name) { return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/" + name + ".png"); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        positionRewardBar();
        boolean visible = rewardBar.visible;
        // Clipped reward controls must not intercept the fixed footer; vanilla owns drag/focus.
        rewardBar.visible = visible && inBody(mx, my);
        try { return super.mouseClicked(mx, my, button); }
        finally { rewardBar.visible = visible; }
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (inBody(mx, my)) {
            if (inside(mx, my, bodyX + 6, cy(178), rewardWidth, slotSize + 12) && rewardBar.isScrollable()) rewardBar.scroll(dy != 0 ? dy : dx);
            else bodyBar.scroll(dy);
            positionRewardBar(); return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
