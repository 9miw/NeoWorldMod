package com.ambition.neoworld.client.gui.party;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.party.PartyActionPayload;
import com.ambition.neoworld.network.party.PartyStatePayload;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyScreen extends Screen {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(
            "neoworld", "textures/gui/market/market_frame.png");
    private static final int WHITE = 0xFFE7ECEF;
    private static final int MUTED = 0xFFA3ADB3;
    private static final int GOLD = 0xFFE7BE69;
    private static final int TEAL = 0xFF61C8BB;
    private static final int RED = 0xFFEA8989;
    private static final int ROW_HEIGHT = 26;
    private PartyStatePayload state = new PartyStatePayload(false, 0, "", List.of(), List.of(),
            List.of(), List.of(), "", true);
    private boolean invitePage;
    private int x;
    private int y;
    private int panelWidth;
    private int panelHeight;
    private int listWidth;
    private int detailX;
    private int detailWidth;
    private int listTop;
    private int listBottom;
    private int scroll;
    private int range = 64;
    private boolean draggingScroll;
    private String selectedName = "";
    private String status = "";
    private int statusTicks;
    private int statusColor = WHITE;
    private NeoButton primary;
    private NeoButton kick;
    private NeoButton disband;
    private NeoButton invite;

    public PartyScreen() {
        super(Component.literal("Party"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(510, width - 16);
        panelHeight = Math.min(310, height - 16);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        listWidth = Math.round((panelWidth - 36) * 0.65F);
        detailX = x + 22 + listWidth;
        detailWidth = panelWidth - listWidth - 36;
        listTop = y + 72;
        listBottom = y + panelHeight - 64;
        rebuildButtons();
        send(PartyActionPayload.Action.REFRESH, "");
    }

    private boolean isInvitePage() {
        return invitePage && state.inParty();
    }

    private boolean isLeader() {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getGameProfile().getName().equals(state.leaderName());
    }

    private void rebuildButtons() {
        clearWidgets();
        primary = null;
        kick = null;
        disband = null;
        invite = null;
        add("X", x + panelWidth - 29, y + 30, 18, 18, NeoButton.Style.DESTRUCTIVE,
                this::onClose).setTooltip(Tooltip.create(Component.literal("ปิด")));
        int footerY = y + panelHeight - 35;
        if (isInvitePage()) {
            add("<", x + 12, y + 30, 24, 18, NeoButton.Style.STANDARD,
                    () -> changePage(false)).setTooltip(Tooltip.create(Component.literal("กลับห้องปาร์ตี้")));
            int buttonWidth = Math.min(48, (listWidth - 20) / 3);
            for (int i = 0; i < 3; i++) {
                int value = 16 << i;
                add(value + "m", x + 14 + i * (buttonWidth + 5), footerY, buttonWidth, 22,
                        NeoButton.Style.STANDARD, () -> {
                            range = value;
                            scroll = 0;
                            selectedName = "";
                            rebuildButtons();
                        }).selected(range == value);
            }
            primary = add("ส่งคำเชิญ", detailX + 8, listBottom - 30, detailWidth - 16, 24,
                    NeoButton.Style.PRIMARY, () -> send(PartyActionPayload.Action.INVITE, selectedName));
        } else if (state.inParty()) {
            primary = add("เลือกดันเจี้ยน", detailX + 8, y + 74, detailWidth - 16, 18,
                    NeoButton.Style.PRIMARY, () -> {
                        if (minecraft != null && minecraft.getConnection() != null)
                            minecraft.getConnection().sendCommand("neoworld dungeon");
                    });
            invite = add("เชิญผู้เล่น", detailX + 8, y + 96, detailWidth - 16, 18,
                    NeoButton.Style.STANDARD, () -> changePage(true));
            add("ออกจากปาร์ตี้", detailX + 8, Math.max(y + 140, listBottom - 22), detailWidth - 16, 18,
                    NeoButton.Style.STANDARD, () -> confirm("ออกจากปาร์ตี้", PartyActionPayload.Action.LEAVE, ""));
            kick = add("เตะ", x + 14, footerY, 64, 22, NeoButton.Style.DESTRUCTIVE,
                    () -> confirm("เตะ " + selectedName, PartyActionPayload.Action.KICK, selectedName));
            disband = add("ยุบปาร์ตี้", x + 84, footerY, 90, 22, NeoButton.Style.DESTRUCTIVE,
                    () -> confirm("ยุบปาร์ตี้", PartyActionPayload.Action.DISBAND, ""));
        } else {
            add("สร้าง Trio", x + 14, footerY, 94, 22, NeoButton.Style.PRIMARY,
                    () -> send(PartyActionPayload.Action.CREATE_TRIO, ""));
            add("สร้าง Raid", x + 114, footerY, 94, 22, NeoButton.Style.PRIMARY,
                    () -> send(PartyActionPayload.Action.CREATE_RAID, ""));
            primary = add("ขอเข้าร่วม", detailX + 8, listBottom - 30, detailWidth - 16, 24,
                    NeoButton.Style.PRIMARY, () -> {});
            primary.setTooltip(Tooltip.create(Component.literal("ระบบขอเข้าร่วมยังไม่พร้อม กรุณารับคำเชิญจากหัวหน้าปาร์ตี้")));
        }
        add(isInvitePage() ? "สแกนใหม่" : "รีเฟรช", detailX + 8, footerY, detailWidth - 16, 22,
                NeoButton.Style.STANDARD, () -> send(PartyActionPayload.Action.REFRESH, ""));
        updateButtons();
    }

    private NeoButton add(String label, int bx, int by, int bw, int bh, NeoButton.Style style, Runnable action) {
        String glyph = switch (label) {
            case "พร้อม" -> NeoGuiIcons.READY;
            case "ส่งคำเชิญ", "เชิญผู้เล่น", "ขอเข้าร่วม" -> NeoGuiIcons.INVITE;
            case "เตะ" -> NeoGuiIcons.KICK;
            case "ออกจากปาร์ตี้" -> NeoGuiIcons.LEAVE;
            case "ยุบปาร์ตี้" -> NeoGuiIcons.DISBAND;
            case "รีเฟรช", "สแกนใหม่" -> NeoGuiIcons.REFRESH;
            case "สร้าง Trio", "สร้าง Raid" -> NeoGuiIcons.LEADER;
            default -> "";
        };
        Component message = glyph.isEmpty() ? Component.literal(label) : NeoGuiIcons.label(glyph, label);
        return addRenderableWidget(new NeoButton(Button.builder(message, button -> action.run())
                .bounds(bx, by, Math.max(18, bw), bh)).style(style));
    }

    private void changePage(boolean inviting) {
        invitePage = inviting;
        selectedName = "";
        scroll = 0;
        rebuildButtons();
        if (inviting) send(PartyActionPayload.Action.REFRESH, "");
    }

    private void confirm(String label, PartyActionPayload.Action action, String target) {
        NeoPopupScreen.showChoice(Component.literal(label), Component.literal("ยืนยัน" + label + "?"),
                Component.literal("ยืนยัน"), () -> send(action, target), Component.literal("ยกเลิก"), () -> {});
    }

    private List<PartyStatePayload.NearbyPlayerEntry> nearby() {
        return state.nearbyPlayers().stream().filter(player -> player.distanceMeters() <= range).toList();
    }

    private void updateButtons() {
        if (primary != null) {
            if (isInvitePage()) {
                primary.active = isLeader() && state.members().size() < state.capacity()
                        && nearby().stream().anyMatch(p -> p.name().equals(selectedName) && p.available());
            } else if (state.inParty()) {
                primary.visible = isLeader();
                primary.active = isLeader();
                primary.setTooltip(Tooltip.create(Component.literal("เปิดหน้าดันเจี้ยน")));
            } else {
                primary.active = false;
            }
        }
        if (invite != null) invite.active = isLeader() && state.members().size() < state.capacity();
        if (disband != null) disband.active = isLeader();
        if (kick != null) kick.active = isLeader() && state.members().stream()
                .anyMatch(p -> p.name().equals(selectedName) && !p.leader());
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        String title = isInvitePage() ? "เชิญผู้เล่น" : state.inParty() ? "ห้องปาร์ตี้" : "รวมห้องปาร์ตี้";
        centered(graphics, title, x + panelWidth / 2, y + 12, WHITE);
        String summary = isInvitePage() ? "ผู้เล่นรอบตัว · " + range + "m"
                : state.inParty() ? (state.capacity() == 3 ? "Trio" : "Raid") + " · สมาชิก "
                + state.members().size() + "/" + state.capacity() : "ปาร์ตี้ที่เปิดอยู่ · " + state.parties().size();
        text(graphics, summary, x + 16, y + 42, listWidth - 8, GOLD);
        panel(graphics, x + 12, y + 60, listWidth, listBottom - y - 58);
        panel(graphics, detailX, y + 60, detailWidth, listBottom - y - 58);
        renderRows(graphics, mouseX, mouseY);
        renderDetails(graphics);
        if (statusTicks > 0) text(graphics, status, x + 14, y + panelHeight - 51, panelWidth - 28, statusColor);
        updateButtons();
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        renderRoleTooltip(graphics, mouseX, mouseY);
    }

    private void renderFrame(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.fill(x + 5, y + 28, x + panelWidth - 5, y + panelHeight - 5, 0xF5191E22);
        // Reuse only the outer Market frame pieces; its inventory sockets do not belong on Party pages.
        sprite(graphics, x, y + 26, 9, 9, 0, 80, 32, 32);
        sprite(graphics, x + 9, y + 26, panelWidth - 18, 9, 50, 80, 350, 32);
        sprite(graphics, x + panelWidth - 9, y + 26, 9, 9, 1524, 80, 32, 32);
        sprite(graphics, x, y + 35, 9, panelHeight - 44, 0, 140, 32, 160);
        sprite(graphics, x + panelWidth - 9, y + 35, 9, panelHeight - 44, 1524, 140, 32, 160);
        sprite(graphics, x, y + panelHeight - 9, 9, 9, 0, 968, 32, 32);
        sprite(graphics, x + 9, y + panelHeight - 9, panelWidth - 18, 9, 60, 968, 350, 32);
        sprite(graphics, x + panelWidth - 9, y + panelHeight - 9, 9, 9, 1524, 968, 32, 32);
        int plaqueWidth = Math.min(218, panelWidth - 96);
        sprite(graphics, x + (panelWidth - plaqueWidth) / 2, y, plaqueWidth, 33, 412, 0, 710, 130);
    }

    private void sprite(GuiGraphics graphics, int bx, int by, int bw, int bh, int u, int v, int uw, int vh) {
        graphics.blit(FRAME, bx, by, bw, bh, u, v, uw, vh, 1556, 1011);
    }

    private void panel(GuiGraphics graphics, int bx, int by, int bw, int bh) {
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF101619);
        graphics.renderOutline(bx, by, bw, bh, 0xFF69747B);
        graphics.renderOutline(bx + 1, by + 1, bw - 2, bh - 2, 0xFF292F34);
    }

    private int rowCount() {
        return isInvitePage() ? nearby().size() : state.inParty() ? state.members().size() : state.parties().size();
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        scroll = Math.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows()));
        graphics.enableScissor(x + 14, listTop, x + listWidth + 10, listBottom);
        for (int index = scroll; index < Math.min(rowCount(), scroll + visibleRows()); index++) {
            String name;
            String metadata;
            String label;
            int color;
            String role;
            if (isInvitePage()) {
                var p = nearby().get(index);
                name = p.name();
                metadata = "Lv." + p.level() + " · " + p.distanceMeters() + "m";
                label = p.status();
                color = p.available() ? TEAL : MUTED;
                role = NeoGuiIcons.MEMBER;
            } else if (state.inParty()) {
                var p = state.members().get(index);
                name = p.name();
                metadata = "Lv." + p.level() + (p.leader() ? " · หัวหน้า" : "");
                label = p.online() ? "Online" : "Offline";
                color = p.online() ? TEAL : MUTED;
                role = p.leader() ? NeoGuiIcons.LEADER : NeoGuiIcons.MEMBER;
            } else {
                var p = state.parties().get(index);
                name = p.leaderName();
                metadata = (p.capacity() == 3 ? "Trio" : "Raid") + " · " + p.memberCount() + "/" + p.capacity();
                label = p.open() ? "รับคน" : "เต็ม";
                color = p.open() ? TEAL : MUTED;
                role = NeoGuiIcons.LEADER;
            }
            int rowY = listTop + (index - scroll) * ROW_HEIGHT;
            int rowX = x + 17;
            int rowWidth = listWidth - 16;
            boolean selected = name.equals(selectedName);
            boolean hover = inside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT - 3);
            graphics.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT - 3,
                    selected ? 0xFF23443F : hover ? 0xFF303E43 : 0xFF20292E);
            graphics.renderOutline(rowX, rowY, rowWidth, ROW_HEIGHT - 3, selected ? TEAL : 0xFF465158);
            int statusWidth = Math.min(72, rowWidth / 3);
            graphics.drawString(font, NeoGuiIcons.icon(role), rowX + 5, rowY + 8, WHITE, false);
            text(graphics, name, rowX + 26, rowY + 3, rowWidth - statusWidth - 32, WHITE);
            text(graphics, metadata, rowX + 26, rowY + 13, rowWidth - statusWidth - 32, MUTED);
            int statusX = rowX + rowWidth - statusWidth;
            text(graphics, label, statusX, rowY + 8, statusWidth - 4, color);
        }
        if (rowCount() == 0) text(graphics, isInvitePage() ? "ไม่พบผู้เล่นรอบตัว" : "ยังไม่มีปาร์ตี้",
                x + 23, listTop + 12, listWidth - 20, MUTED);
        graphics.disableScissor();
        if (rowCount() > visibleRows()) {
            int trackX = x + listWidth + 7;
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(10, trackHeight * visibleRows() / rowCount());
            int thumbY = listTop + (trackHeight - thumbHeight) * scroll / (rowCount() - visibleRows());
            graphics.fill(trackX, listTop, trackX + 3, listBottom, 0xFF313B40);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, TEAL);
        }
    }

    private void renderRoleTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, x + 22, listTop, 18, visibleRows() * ROW_HEIGHT)) return;
        int index = scroll + (mouseY - listTop) / ROW_HEIGHT;
        if (index >= rowCount()) return;
        boolean leader = !isInvitePage() && (!state.inParty() || state.members().get(index).leader());
        graphics.renderTooltip(font, Component.literal(leader ? "หัวหน้าปาร์ตี้" : "สมาชิก"), mouseX, mouseY);
    }

    private void renderDetails(GuiGraphics graphics) {
        if (state.inParty() && !isInvitePage()) return;
        int tx = detailX + 9;
        int tw = detailWidth - 18;
        text(graphics, selectedName.isEmpty() ? "เลือก" + (isInvitePage() ? "ผู้เล่น" : "ปาร์ตี้") : selectedName,
                tx, y + 75, tw, GOLD);
        if (isInvitePage()) {
            nearby().stream().filter(p -> p.name().equals(selectedName)).findFirst().ifPresent(p -> {
                text(graphics, "Lv. " + p.level(), tx, y + 100, tw, WHITE);
                if (y + 127 < listBottom - 32) text(graphics, "ระยะ " + p.distanceMeters() + "m", tx, y + 117, tw, MUTED);
                if (y + 144 < listBottom - 32) text(graphics, p.status(), tx, y + 134, tw, p.available() ? TEAL : RED);
            });
        } else {
            state.parties().stream().filter(p -> p.leaderName().equals(selectedName)).findFirst().ifPresent(p -> {
                text(graphics, p.capacity() == 3 ? "Trio" : "Raid", tx, y + 100, tw, TEAL);
                if (y + 127 < listBottom - 32) text(graphics, "สมาชิก " + p.memberCount() + "/" + p.capacity(), tx, y + 117, tw, WHITE);
                if (y + 144 < listBottom - 32) text(graphics, p.open() ? "เปิดรับสมาชิก" : "สมาชิกเต็ม", tx, y + 134, tw, MUTED);
            });
        }
    }

    private void text(GuiGraphics graphics, String value, int tx, int ty, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(1, maxWidth)), tx, ty, color, false);
    }

    private void centered(GuiGraphics graphics, String value, int center, int ty, int color) {
        graphics.drawString(font, value, center - font.width(value) / 2, ty, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && rowCount() > visibleRows()
                && inside(mx, my, x + listWidth + 4, listTop, 8, listBottom - listTop)) {
            draggingScroll = true;
            scrollTo(my);
            return true;
        }
        if (button == 0 && inside(mx, my, x + 17, listTop, listWidth - 16, visibleRows() * ROW_HEIGHT)) {
            int index = scroll + (int) (my - listTop) / ROW_HEIGHT;
            if (index < rowCount()) {
                selectedName = isInvitePage() ? nearby().get(index).name() : state.inParty()
                        ? state.members().get(index).name() : state.parties().get(index).leaderName();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void scrollTo(double mouseY) {
        int trackHeight = listBottom - listTop;
        int thumbHeight = Math.max(10, trackHeight * visibleRows() / rowCount());
        double fraction = (mouseY - listTop - thumbHeight / 2.0) / Math.max(1, trackHeight - thumbHeight);
        scroll = Math.clamp((int) Math.round(fraction * (rowCount() - visibleRows())),
                0, Math.max(0, rowCount() - visibleRows()));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingScroll && rowCount() > visibleRows()) {
            scrollTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, x + 12, listTop, listWidth, listBottom - listTop)) {
            scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, rowCount() - visibleRows()));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private static boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isInvitePage()) {
            changePage(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void receiveState(PartyStatePayload payload) {
        boolean wasInParty = state.inParty();
        state = payload;
        if (wasInParty != state.inParty()) {
            invitePage = false;
            selectedName = "";
            scroll = 0;
        }
        rebuildButtons();
        if (!payload.message().isEmpty()) {
            status = payload.message();
            statusColor = payload.success() ? TEAL : RED;
            statusTicks = 100;
        }
    }

    private void send(PartyActionPayload.Action action, String target) {
        PacketDistributor.sendToServer(new PartyActionPayload(action, target));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
