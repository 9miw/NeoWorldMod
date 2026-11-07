package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.party.PartyScreenController;
import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload;
import com.ambition.neoworld.network.dungeon.DungeonRoomQueryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Native-scale waiting room with independent content, member and reward scrolling. */
public final class DungeonRoomScreen extends Screen {
    private static final ResourceLocation DIVIDER = asset("browser/divider"), CLOCK = asset("hud/clock");
    private static final int TEXT = 0xFFE7ECEF, MUTED = 0xFFABB4BA, GOLD = 0xFFF2C14E, GREEN = 0xFF5CDA86;
    private DungeonWaitingContentLayout content;
    private DungeonRoomViewPayload state;
    private NeoButton start, ready, leave, disband, party;
    private NeoScrollbar bodyBar, memberBar, rewardBar;
    private int x, y, w, h, bodyX, bodyY, bodyW, bodyH, footerY, slotSize, pitch, rewardWidth, ticks;
    private boolean actionPending;
    private ItemStack hoveredItem;
    private int hoveredRewardIndex = -1;
    private Component hoveredText;

    public DungeonRoomScreen(DungeonRoomViewPayload state) { super(Component.literal("ห้องดันเจี้ยน")); this.state = state; }

    public static void receive(DungeonRoomViewPayload payload) {
        if (payload.open()) DungeonBrowserScreenController.cancelOpen();
        var minecraft = Minecraft.getInstance();
        var current = NeoPopupScreen.findScreen(DungeonRoomScreen.class);
        if (payload.open() && (current == null || current.state.roomId() != payload.roomId())) minecraft.setScreen(new DungeonRoomScreen(payload));
        else if (current != null) {
            if (minecraft.screen == current && current.state.roomId() != 0 && (payload.roomId() == 0 || !payload.waiting())) {
                current.onClose(); return;
            }
            current.state = payload; current.actionPending = false; current.refresh();
        }
    }

    @Override protected void init() {
        double oldBody = bodyBar == null ? 0 : bodyBar.getValue();
        double oldMembers = memberBar == null ? 0 : memberBar.getValue(), oldRewards = rewardBar == null ? 0 : rewardBar.getValue();
        var layout = DungeonCreateRoomLayout.fitWaiting(width, height);
        w = layout.width(); h = layout.height(); x = (width - w) / 2; y = (height - h) / 2;
        bodyX = x + 24; bodyW = layout.bodyWidth(); bodyY = y + layout.bodyTop(); bodyH = layout.bodyHeight();
        content = DungeonWaitingContentLayout.fit(bodyH);
        footerY = y + layout.footerTop(); rewardWidth = layout.rewardWidth(); slotSize = content.slotSize(); pitch = slotSize + 4;
        bodyBar = addRenderableWidget(new NeoScrollbar(x + w - 22, bodyY, 6, bodyH, Component.literal("รายละเอียดห้อง"))
                .range(bodyH, content.contentHeight()).scrollStep(20));
        bodyBar.visible = bodyBar.isScrollable(); bodyBar.value(oldBody);
        memberBar = addRenderableWidget(new NeoScrollbar(bodyX + bodyW - 7, cy(content.memberTop()), 6, content.memberHeight(),
                Component.literal("รายชื่อสมาชิก")).scrollStep(content.rowHeight()));
        rewardBar = addRenderableWidget(new NeoScrollbar(bodyX + 6, cy(content.rewardTop()) + slotSize + 4, rewardWidth, 6,
                Component.literal("รายการรางวัล")).orientation(NeoScrollbar.Orientation.HORIZONTAL).scrollStep(pitch));
        int leaveWidth = Math.min(132, (w - 100) / 2), mainWidth = Math.min(160, w / 3);
        leave = add(NeoGuiIcons.label(NeoGuiIcons.LEAVE, "ออกจากห้อง"), x + 22, footerY, leaveWidth, 28, () -> command("leave"), NeoButton.Style.STANDARD);
        party = add(NeoGuiIcons.label(NeoGuiIcons.MEMBER, "จัดการปาร์ตี้"), x + 22, footerY, leaveWidth, 28,
                () -> { if (party.active && party.visible) PartyScreenController.open(); }, NeoButton.Style.STANDARD);
        party.setTooltip(Tooltip.create(Component.literal("เปิดหน้าจัดการปาร์ตี้ โดยยังอยู่ในห้องดันเจี้ยน")));
        disband = add(NeoGuiIcons.label(NeoGuiIcons.DISBAND, ""), x + 28 + leaveWidth, footerY, 28, 28,
                () -> NeoPopupScreen.showChoice(Component.literal("ยุบห้องดันเจี้ยน"), Component.literal("ยุบห้องนี้ โดยปาร์ตี้ยังอยู่?"),
                        Component.literal("ยืนยัน"), () -> command("disband"), Component.literal("ยกเลิก"), () -> {}), NeoButton.Style.DESTRUCTIVE);
        disband.setTooltip(Tooltip.create(Component.literal("ยุบห้องดันเจี้ยน (ไม่ยุบปาร์ตี้)")));
        start = add(Component.literal("เริ่มดันเจี้ยน"),
                x + w - 22 - mainWidth, footerY, mainWidth, 28, () -> command("start"), NeoButton.Style.PRIMARY);
        ready = add(NeoGuiIcons.label(NeoGuiIcons.READY, "พร้อม"), x + w - 22 - mainWidth, footerY, mainWidth, 28,
                () -> command("ready"), NeoButton.Style.PRIMARY);
        var close = add(Component.literal("X"), x + w + 2, y + Math.round(w * 50F / 1468F), 18, 18, this::onClose,
                NeoButton.Style.STANDARD).textScrolling(false);
        close.setTooltip(Tooltip.create(Component.literal("ปิดหน้าต่าง (ยังอยู่ในห้อง)")));
        refresh(); memberBar.value(oldMembers); rewardBar.value(oldRewards);
    }

    private NeoButton add(Component label, int left, int top, int width, int height, Runnable action, NeoButton.Style style) {
        var button = addRenderableWidget(new NeoButton(Button.builder(label, ignored -> action.run()).bounds(left, top, width, height)).style(style));
        if (style == NeoButton.Style.PRIMARY) button.frameRenderer((g, control) -> {
            DungeonBrowserV5Skin.socket(g, "primary", control.getX(), control.getY(), control.getWidth(), control.getHeight(),
                    1134, 280, 100, 8);
            if (!control.active) g.fill(control.getX()+6, control.getY()+6,
                    control.getX()+control.getWidth()-6, control.getY()+control.getHeight()-6, 0xAA192020);
            else if (control.isHoveredOrFocused()) g.renderOutline(control.getX()+3, control.getY()+3,
                    control.getWidth()-6, control.getHeight()-6, 0xFF65E4EA);
            if (control == start && control.getWidth() >= font.width(control.getMessage()) + 40)
                DungeonGuiIcons.render(g, 1, control.getX()+(control.getWidth()-font.width(control.getMessage()))/2-19,
                        control.getY()+(control.getHeight()-14)/2, 14);
            g.setColor(1, 1, 1, 1);
        });
        return button;
    }

    private void refresh() {
        if (start == null || minecraft.player == null) return;
        var self = state.members().stream().filter(member -> member.id().equals(minecraft.player.getUUID())).findFirst().orElse(null);
        var actions = DungeonRoomActions.forMember(self != null, self != null && self.owner(), state.capacity());
        boolean enabled = !actionPending && state.waiting();
        start.visible = actions.start(); start.active = start.visible && !actionPending && state.canStart(minecraft.player.getUUID());
        ready.visible = actions.ready(); ready.active = ready.visible && enabled;
        ready.setMessage(NeoGuiIcons.label(NeoGuiIcons.READY, self != null && self.ready() ? "ยกเลิกพร้อม" : "พร้อม"));
        leave.visible = actions.leave(); leave.active = leave.visible && enabled;
        disband.visible = actions.disband(); disband.active = disband.visible && enabled;
        party.visible = actions.party(); party.active = party.visible && enabled;
        start.setTooltip(Tooltip.create(Component.literal(start.active ? "เริ่มดันเจี้ยน" : "รอสมาชิกครบและพร้อมทุกคน")));
        memberBar.range(content.memberHeight(), state.members().size() * content.rowHeight()); memberBar.visible = memberBar.isScrollable();
        int count = preview() == null ? 0 : preview().rewardItems().size();
        rewardBar.range(rewardWidth, count == 0 ? 0 : count * pitch - 4); rewardBar.visible = rewardBar.isScrollable();
    }

    private void command(String action) {
        if (minecraft.getConnection() == null || actionPending) return;
        actionPending = true; refresh(); minecraft.getConnection().sendCommand("neow dungeon room " + action); query();
    }
    private void query() { PacketDistributor.sendToServer(DungeonRoomQueryPayload.INSTANCE); }
    @Override public void tick() { if (++ticks % 20 == 0) query(); }
    private int cy(int top) { return bodyY + top - (int) bodyBar.getValue(); }
    private void positionBars() { memberBar.setY(cy(content.memberTop())); rewardBar.setY(cy(content.rewardTop()) + slotSize + 4); }
    private DungeonBrowserDataPayload.Entry preview() { return state.preview().entries().isEmpty() ? null : state.preview().entries().getFirst(); }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta); hoveredItem = null; hoveredRewardIndex = -1; hoveredText = null; positionBars();
        DungeonBrowserV5Skin.frame(g, x, y, w, h);
        g.drawCenteredString(font, title, x + w / 2, y + Math.round(w * 60F / 1468F) - font.lineHeight / 2, TEXT);
        g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        if (state.roomId() == 0) text(g, "คุณไม่ได้อยู่ในห้องดันเจี้ยน", bodyX, cy(80), bodyW, MUTED);
        else renderBody(g, mx, my);
        if (memberBar.visible) memberBar.render(g, mx, my, delta);
        if (rewardBar.visible) rewardBar.render(g, mx, my, delta);
        g.disableScissor();
        for (var renderable : renderables) if (renderable != memberBar && renderable != rewardBar) renderable.render(g, mx, my, delta);
        if (hoveredItem != null) g.renderTooltip(font, hoveredItem, mx, my);
        else if (hoveredText != null) g.renderTooltip(font, hoveredText, mx, my);
    }

    private void renderBody(GuiGraphics g, int mx, int my) {
        int split = bodyX + bodyW / 2, right = split + 12, leftW = split - bodyX - 14, rightW = bodyX + bodyW - right - 4;
        DungeonBrowserV5Skin.entrance(g, false, bodyX, cy(0), 22);
        text(g, state.title(), bodyX + 28, cy(0), leftW - 28, GOLD);
        text(g, "ห้อง #" + state.roomId(), bodyX + 28, cy(14), leftW - 28, MUTED);
        if (inBody(mx, my) && inside(mx, my, bodyX, cy(0), leftW, 10)) hoveredText = Component.literal(state.title());
        metadata(g, 0, "เลเวลแนะนำ", "Lv. " + state.minimumLevel() + "+", content.levelTop(), leftW);
        metadata(g, -2, "เวลา", state.seconds() <= 0 ? "ไม่จำกัด" : "%d:%02d".formatted(state.seconds() / 60, state.seconds() % 60), content.timeTop(), leftW);
        metadata(g, -1, "ผู้เล่น", state.capacity() + " คน", content.capacityTop(), leftW);
        int ph = Math.max(24, Math.min(content.previewHeight(), rightW / 2));
        int previewY = cy(2);
        DungeonPreviewImages.draw(g, preview() == null ? "" : preview().imageId(), right, previewY, rightW, ph);
        g.renderOutline(right - 1, previewY - 1, rightW + 2, ph + 2, 0xFF657074);
        divider(g, content.metadataDivider());
        g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.MEMBER, ""), bodyX, cy(content.membersHeading()), TEXT, false);
        text(g, "สมาชิกในห้อง", bodyX + 18, cy(content.membersHeading()), bodyW - 82, GOLD);
        rightText(g, state.members().size() + " / " + state.capacity() + " คน", bodyX + bodyW - 9, cy(content.membersHeading()), 70, TEXT);
        int nameX = bodyX + 40, levelX = bodyX + bodyW * 51 / 100, statusX = bodyX + bodyW * 74 / 100;
        text(g, "ผู้เล่น", nameX, cy(content.tableHeading()), levelX - nameX - 6, MUTED);
        text(g, "เลเวล", levelX, cy(content.tableHeading()), statusX - levelX - 6, MUTED);
        text(g, "สถานะ", statusX, cy(content.tableHeading()), bodyX + bodyW - statusX - 10, MUTED);
        renderMembers(g, mx, my, nameX, levelX, statusX);
        boolean allReady = state.capacity() > 0 && state.readyCount() == state.capacity() && state.members().size() == state.capacity();
        text(g, !state.waiting() ? "กำลังเริ่มดันเจี้ยน" : allReady ? "พร้อมเริ่มดันเจี้ยน"
                : "รอสมาชิกพร้อม " + state.readyCount() + " / " + state.capacity() + " คน",
                bodyX + 6, cy(content.statusTop()), bodyW - 12, allReady ? GREEN : GOLD);
        divider(g, content.rewardDivider());
        NeoSystemIcons.render(g, NeoSystemIcons.Icon.MARKET, bodyX, cy(content.rewardHeading()) - 2, 13, true);
        text(g, "รางวัล", bodyX + 18, cy(content.rewardHeading()), bodyW - 36, GOLD);
        if (preview() != null && preview().previewIncomplete()) {
            text(g, "*", bodyX + bodyW - 12, cy(content.rewardHeading()), 10, GOLD);
            if (inBody(mx, my) && inside(mx, my, bodyX + bodyW - 14, cy(content.rewardHeading()), 14, font.lineHeight)) {
                hoveredText = Component.literal("ตัวอย่างรางวัล จำนวนและโอกาสขึ้นกับการตั้งค่า");
            }
        }
        renderRewards(g, mx, my);
    }

    private void renderMembers(GuiGraphics g, int mx, int my, int nameX, int levelX, int statusX) {
        int offset = (int) memberBar.getValue(), top = cy(content.memberTop());
        int rowHeight = content.rowHeight(), memberHeight = content.memberHeight();
        g.enableScissor(bodyX, top, bodyX + bodyW - 10, top + memberHeight);
        try {
            for (int i = offset / rowHeight; i < state.members().size(); i++) {
                int row = top + i * rowHeight - offset;
                int textTop = row + (rowHeight-font.lineHeight)/2;
                if (row >= top + memberHeight) break;
                var member = state.members().get(i);
                if (member.owner()) g.drawString(font, NeoGuiIcons.icon(NeoGuiIcons.LEADER), bodyX + 1, row + 2, TEXT, false);
                var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(member.id());
                var skin = info == null ? DefaultPlayerSkin.get(member.id()).texture() : info.getSkin().texture();
                PlayerFaceRenderer.draw(g, skin, bodyX + 20, row + (rowHeight-14)/2, 14);
                text(g, member.name(), nameX, textTop, levelX - nameX - 6, TEXT);
                text(g, member.online() ? "Lv. " + member.level() : "-", levelX, textTop, statusX - levelX - 6, MUTED);
                if (member.online() && member.ready()) g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.READY, ""), statusX, textTop, TEXT, false);
                else g.renderOutline(statusX, textTop, 8, 8, 0xFF6F7D83);
                text(g, !member.online() ? "ออฟไลน์" : member.ready() ? "พร้อม" : "ยังไม่พร้อม", statusX + 13, textTop,
                        bodyX + bodyW - statusX - 24, member.online() && member.ready() ? GREEN : MUTED);
                g.fill(bodyX, row + rowHeight - 1, bodyX + bodyW - 12, row + rowHeight, 0xFF414D53);
                if (inBody(mx, my) && inside(mx, my, bodyX, row, bodyW - 12, rowHeight)
                        && inside(mx, my, bodyX, top, bodyW - 12, memberHeight)) hoveredText = Component.literal(member.name());
            }
        } finally { g.disableScissor(); }
    }

    private void renderRewards(GuiGraphics g, int mx, int my) {
        int top = cy(content.rewardTop()), leftEdge = bodyX + 6, offset = (int) rewardBar.getValue();
        var items = preview() == null ? java.util.List.<ItemStack>of() : preview().rewardItems();
        g.enableScissor(leftEdge, top, leftEdge + rewardWidth, top + slotSize);
        try {
            for (int i = offset / pitch; i < items.size(); i++) {
                int left = leftEdge + i * pitch - offset;
                if (left >= leftEdge + rewardWidth) break;
                g.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), left, top, slotSize, slotSize);
                int padding = slotSize == 18 ? 1 : 2;
                g.pose().pushPose(); g.pose().translate(left + padding, top + padding, 0);
                float scale = (slotSize - padding * 2) / 16F; g.pose().scale(scale, scale, 1);
                g.renderItem(items.get(i), 0, 0); g.renderItemDecorations(font, items.get(i), 0, 0); g.pose().popPose();
                if (inBody(mx, my) && inside(mx, my, leftEdge, top, rewardWidth, slotSize) && mx >= left && mx < left + slotSize) {
                    hoveredItem = items.get(i); hoveredRewardIndex = i;
                }
            }
        } finally { g.disableScissor(); }
        if (items.isEmpty()) text(g, "ไม่มีตัวอย่างไอเทม", leftEdge, top + 4, rewardWidth, MUTED);
    }

    private void metadata(GuiGraphics g, int icon, String label, String value, int top, int available) {
        if (icon >= 0) DungeonGuiIcons.render(g, icon, bodyX, cy(top) - 1, 12);
        else if (icon == -1) g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.MEMBER, ""), bodyX, cy(top), TEXT, false);
        else g.blit(CLOCK, bodyX, cy(top) - 1, 12, 12, 0, 0, 32, 32, 32, 32);
        int valueWidth = Math.max(28, available / 3);
        text(g, label, bodyX + 18, cy(top), available - valueWidth - 24, MUTED);
        rightText(g, value, bodyX + available, cy(top), valueWidth, TEXT);
    }

    public com.ambition.neoworld.network.dungeon.DungeonRewardDetail rewardTooltipDetail(ItemStack stack) {
        var entry = preview();
        if (entry == null || hoveredItem == null || hoveredRewardIndex < 0
                || hoveredRewardIndex >= entry.rewardDetails().size() || !ItemStack.matches(stack, hoveredItem)) return null;
        return entry.rewardDetails().get(hoveredRewardIndex);
    }
    private void divider(GuiGraphics g, int top) { g.blit(DIVIDER, bodyX, cy(top), bodyW - 4, 3, 0, 0, 884, 20, 884, 20); }
    private void text(GuiGraphics g, String value, int left, int top, int max, int color) { if (max > 0) g.drawString(font, NeoGuiRender.trim(font, value, max), left, top, color, false); }
    private void rightText(GuiGraphics g, String value, int right, int top, int max, int color) {
        String fitted = NeoGuiRender.trim(font, value, max); g.drawString(font, fitted, right - font.width(fitted), top, color, false);
    }
    private boolean inBody(double mx, double my) { return inside(mx, my, bodyX, bodyY, bodyW, bodyH); }
    private static boolean inside(double mx, double my, int left, int top, int width, int height) { return mx >= left && mx < left + width && my >= top && my < top + height; }
    private static ResourceLocation asset(String name) { return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/" + name + ".png"); }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        positionBars();
        boolean members = memberBar.visible, rewards = rewardBar.visible;
        memberBar.visible = members && inBody(mx, my); rewardBar.visible = rewards && inBody(mx, my);
        try { return super.mouseClicked(mx, my, button); }
        finally { memberBar.visible = members; rewardBar.visible = rewards; }
    }
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (inBody(mx, my)) {
            if (inside(mx, my, bodyX, cy(content.memberTop()), bodyW, content.memberHeight()) && memberBar.isScrollable()) memberBar.scroll(dy);
            else if (inside(mx, my, bodyX, cy(content.rewardTop()), bodyW, slotSize + 12) && rewardBar.isScrollable()) rewardBar.scroll(dy != 0 ? dy : dx);
            else bodyBar.scroll(dy);
            positionBars(); return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}
