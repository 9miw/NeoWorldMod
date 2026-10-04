package com.ambition.neoworld.client.gui.party;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.network.PartyActionPayload;
import com.ambition.neoworld.network.PartyStatePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyScreen extends Screen {
    private static final int COLOR_PANEL = 0xF51A2024;
    private static final int COLOR_PANEL_DARK = 0xF00E1215;
    private static final int COLOR_FRAME = 0xFF4C565E;
    private static final int COLOR_FRAME_LIGHT = 0xFF87919A;
    private static final int COLOR_SLOT = 0xFF252B2F;
    private static final int COLOR_SLOT_HOVER = 0xFF38464D;
    private static final int COLOR_TEAL = 0xFF2FB5A7;
    private static final int COLOR_TEAL_DARK = 0xFF176B65;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_RED = 0xFFAA3333;
    private static final int COLOR_MUTED = 0xFFABB4BA;

    private PartyStatePayload state = new PartyStatePayload(false, 0, "", List.of(), List.of(), List.of(), List.of(), "", true);
    private ViewMode viewMode = ViewMode.AUTO;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentY;
    private int selectedMember = -1;
    private int selectedParty = -1;
    private int selectedNearby = -1;
    private String status = "";
    private int statusColor = 0xFFE3E7EA;
    private int statusTicks;

    public PartyScreen() {
        super(Component.literal("Party"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(430, width - 10);
        panelHeight = Math.min(236, height - 10);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentY = panelY + 38;
        send(PartyActionPayload.Action.REFRESH, "");
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
        renderTitle(graphics, titleLabel());
        renderClose(graphics, mouseX, mouseY);
        if (currentView() == ViewMode.INVITE) {
            renderInvitePage(graphics, mouseX, mouseY);
        } else if (state.inParty()) {
            renderPartyRoom(graphics, mouseX, mouseY);
        } else {
            renderFinder(graphics, mouseX, mouseY);
        }
        renderFooter(graphics, mouseX, mouseY);
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        if (!state.invites().isEmpty()) {
            renderInviteModal(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, COLOR_FRAME_LIGHT);
        graphics.renderOutline(panelX + 2, panelY + 2, panelWidth - 4, panelHeight - 4, COLOR_FRAME);
        graphics.fill(panelX + 4, panelY + 4, panelX + panelWidth - 4, panelY + 22, 0xFF252D33);
    }

    private void renderTitle(GuiGraphics graphics, String label) {
        int plateWidth = 180;
        int x = panelX + (panelWidth - plateWidth) / 2;
        graphics.fill(x, panelY + 6, x + plateWidth, panelY + 25, 0xFF171C20);
        graphics.renderOutline(x, panelY + 6, plateWidth, 19, COLOR_FRAME_LIGHT);
        drawCentered(graphics, label, x, plateWidth, panelY + 12, 0xFFF4F6F7);
        graphics.drawString(font, "+", x + 7, panelY + 12, COLOR_GOLD, false);
        graphics.drawString(font, "+", x + plateWidth - 12, panelY + 12, COLOR_GOLD, false);
    }

    private void renderClose(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + panelWidth - 24;
        int y = panelY + 7;
        boolean hovered = inside(mouseX, mouseY, x, y, 16, 16);
        graphics.fill(x, y, x + 16, y + 16, hovered ? COLOR_RED : 0xFF713030);
        graphics.renderOutline(x, y, 16, 16, 0xFFB7C0C7);
        drawCentered(graphics, "X", x, 16, y + 5, 0xFFFFFFFF);
    }

    private void renderFinder(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = panelX + 9;
        int detailX = panelX + 292;
        graphics.fill(listX, contentY, detailX - 7, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(listX, contentY, detailX - listX - 7, panelHeight - 95, COLOR_FRAME);
        graphics.fill(detailX, contentY, panelX + panelWidth - 9, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(detailX, contentY, panelX + panelWidth - detailX - 9, panelHeight - 95, COLOR_FRAME);

        graphics.drawString(font, "หัวหน้า", listX + 8, contentY + 8, COLOR_MUTED, false);
        graphics.drawString(font, "โหมด", listX + 102, contentY + 8, COLOR_MUTED, false);
        graphics.drawString(font, "สมาชิก", listX + 154, contentY + 8, COLOR_MUTED, false);
        graphics.drawString(font, "สถานะ", listX + 216, contentY + 8, COLOR_MUTED, false);

        int y = contentY + 23;
        for (int i = 0; i < state.parties().size(); i++) {
            PartyStatePayload.PartyEntry party = state.parties().get(i);
            renderPartyRow(graphics, listX + 6, y, 266, i, party, mouseX, mouseY);
            y += 22;
        }
        if (state.parties().isEmpty()) {
            graphics.drawString(font, "ยังไม่มีปาร์ตี้ที่เปิดอยู่", listX + 12, contentY + 44, COLOR_MUTED, false);
        }

        PartyStatePayload.PartyEntry selected = selectedParty();
        if (selected == null) {
            drawCentered(graphics, "เลือกปาร์ตี้", detailX, panelX + panelWidth - detailX - 9, contentY + 34,
                    COLOR_MUTED);
            drawCentered(graphics, "เพื่อดูรายละเอียด", detailX, panelX + panelWidth - detailX - 9, contentY + 48,
                    0xFF707A80);
        } else {
            graphics.drawString(font, trim(selected.leaderName(), 100), detailX + 10, contentY + 15,
                    COLOR_GOLD, false);
            graphics.drawString(font, "โหมด", detailX + 10, contentY + 38, COLOR_MUTED, false);
            graphics.drawString(font, selected.capacity() == 3 ? "Trio" : "Raid", detailX + 60, contentY + 38,
                    COLOR_TEAL, false);
            graphics.drawString(font, "สมาชิก", detailX + 10, contentY + 55, COLOR_MUTED, false);
            graphics.drawString(font, selected.memberCount() + "/" + selected.capacity(), detailX + 60, contentY + 55,
                    0xFFE8ECEE, false);
            graphics.drawString(font, "สถานะ", detailX + 10, contentY + 72, COLOR_MUTED, false);
            graphics.drawString(font, selected.open() ? "รับคน" : "เต็ม", detailX + 60, contentY + 72,
                    selected.open() ? 0xFF68D391 : 0xFFFF7B72, false);
        }
        renderButton(graphics, detailX + 8, panelY + panelHeight - 65, 104, 22, "ขอเข้าร่วม", mouseX, mouseY,
                selected != null && selected.open(), true, PartyActionPayload.Action.INVITE);
    }

    private void renderPartyRow(GuiGraphics graphics, int x, int y, int rowWidth, int index,
                                PartyStatePayload.PartyEntry party, int mouseX, int mouseY) {
        boolean selected = selectedParty == index;
        boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, 19);
        graphics.fill(x, y, x + rowWidth, y + 19, selected ? 0xFF3A3020 : hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        graphics.renderOutline(x, y, rowWidth, 19, selected ? COLOR_GOLD : COLOR_FRAME);
        graphics.drawString(font, trim(party.leaderName(), 80), x + 5, y + 6, 0xFFE8ECEE, false);
        graphics.drawString(font, party.capacity() == 3 ? "Trio" : "Raid", x + 98, y + 6, COLOR_TEAL, false);
        graphics.drawString(font, party.memberCount() + "/" + party.capacity(), x + 151, y + 6, COLOR_MUTED, false);
        graphics.drawString(font, party.open() ? "รับคน" : "เต็ม", x + 211, y + 6,
                party.open() ? 0xFF68D391 : 0xFFFF7B72, false);
    }

    private void renderPartyRoom(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = panelX + 9;
        int actionX = panelX + 292;
        graphics.fill(listX, contentY, actionX - 7, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(listX, contentY, actionX - listX - 7, panelHeight - 95, COLOR_FRAME);
        graphics.fill(actionX, contentY, panelX + panelWidth - 9, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(actionX, contentY, panelX + panelWidth - actionX - 9, panelHeight - 95, COLOR_FRAME);

        String mode = state.capacity() == 3 ? "Trio" : "Raid";
        graphics.drawString(font, "โหมด " + mode + " | สมาชิก " + state.members().size() + "/" + state.capacity(),
                listX + 7, contentY + 8, COLOR_GOLD, false);
        graphics.drawString(font, "หัวหน้า " + trim(state.leaderName(), 90), listX + 142, contentY + 8,
                COLOR_MUTED, false);

        int y = contentY + 25;
        for (int i = 0; i < state.members().size(); i++) {
            renderMemberRow(graphics, listX + 6, y, 266, i, state.members().get(i), mouseX, mouseY);
            y += 24;
        }

        renderButton(graphics, actionX + 7, contentY + 14, 104, 22, "พร้อม", mouseX, mouseY,
                true, true, PartyActionPayload.Action.READY);
        renderButton(graphics, actionX + 7, contentY + 42, 104, 20, "เชิญผู้เล่น", mouseX, mouseY,
                true, true, PartyActionPayload.Action.INVITE);
        renderButton(graphics, actionX + 7, contentY + 68, 104, 20, "เริ่มดันเจี้ยน", mouseX, mouseY,
                false, true, PartyActionPayload.Action.REFRESH);
        renderButton(graphics, actionX + 7, contentY + 98, 104, 20, "ออกจากปาร์ตี้", mouseX, mouseY,
                true, false, PartyActionPayload.Action.LEAVE);
        renderButton(graphics, actionX + 7, contentY + 124, 104, 20, "ยุบปาร์ตี้", mouseX, mouseY,
                true, false, PartyActionPayload.Action.DISBAND);
    }

    private void renderMemberRow(GuiGraphics graphics, int x, int y, int rowWidth, int index,
                                 PartyStatePayload.MemberEntry member, int mouseX, int mouseY) {
        boolean selected = selectedMember == index;
        boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, 21);
        graphics.fill(x, y, x + rowWidth, y + 21, selected ? 0xFF3A3020 : hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        graphics.renderOutline(x, y, rowWidth, 21, selected ? COLOR_GOLD : COLOR_FRAME);
        graphics.drawString(font, member.leader() ? "*" : "-", x + 5, y + 7,
                member.leader() ? COLOR_GOLD : COLOR_MUTED, false);
        graphics.drawString(font, trim(member.name(), 78), x + 17, y + 7, 0xFFE8ECEE, false);
        graphics.drawString(font, "Lv." + member.level(), x + 102, y + 7, COLOR_MUTED, false);
        graphics.drawString(font, member.online() ? "Online" : "Offline", x + 148, y + 7,
                member.online() ? 0xFF68D391 : 0xFFFF7B72, false);
        graphics.drawString(font, member.ready() ? "READY" : "WAIT", x + 211, y + 7,
                member.ready() ? COLOR_TEAL : COLOR_MUTED, false);
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentView() == ViewMode.INVITE) {
            renderButton(graphics, panelX + 22, panelY + panelHeight - 32, 72, 20, "16m", mouseX, mouseY,
                    true, true, PartyActionPayload.Action.REFRESH);
            renderButton(graphics, panelX + 100, panelY + panelHeight - 32, 72, 20, "32m", mouseX, mouseY,
                    true, false, PartyActionPayload.Action.REFRESH);
            renderButton(graphics, panelX + 178, panelY + panelHeight - 32, 72, 20, "64m", mouseX, mouseY,
                    true, false, PartyActionPayload.Action.REFRESH);
            renderButton(graphics, panelX + 312, panelY + panelHeight - 32, 86, 20, "สแกนใหม่", mouseX, mouseY,
                    true, false, PartyActionPayload.Action.REFRESH);
        } else if (state.inParty()) {
            graphics.fill(panelX + 20, panelY + panelHeight - 31, panelX + 148, panelY + panelHeight - 10, 0xFF101518);
            graphics.renderOutline(panelX + 20, panelY + panelHeight - 31, 128, 21, COLOR_FRAME);
            renderButton(graphics, panelX + 156, panelY + panelHeight - 32, 86, 20, "เตะ", mouseX, mouseY,
                    selectedMember >= 0 && selectedMember < state.members().size(), false, PartyActionPayload.Action.KICK);
        } else {
            graphics.fill(panelX + 20, panelY + panelHeight - 31, panelX + 148, panelY + panelHeight - 10, 0xFF101518);
            graphics.renderOutline(panelX + 20, panelY + panelHeight - 31, 128, 21, COLOR_FRAME);
            renderButton(graphics, panelX + 156, panelY + panelHeight - 32, 72, 20, "สร้าง Trio", mouseX, mouseY,
                    true, true, PartyActionPayload.Action.CREATE_TRIO);
            renderButton(graphics, panelX + 234, panelY + panelHeight - 32, 72, 20, "สร้าง Raid", mouseX, mouseY,
                    true, true, PartyActionPayload.Action.CREATE_RAID);
        }
        if (currentView() != ViewMode.INVITE) {
            renderButton(graphics, panelX + 312, panelY + panelHeight - 32, 86, 20, "รีเฟรช", mouseX, mouseY,
                    true, false, PartyActionPayload.Action.REFRESH);
        }
        if (statusTicks > 0 && !status.isEmpty()) {
            graphics.drawString(font, trim(status, panelWidth - 24), panelX + 12, panelY + panelHeight - 43,
                    statusColor, false);
        }
    }

    private void renderInviteModal(GuiGraphics graphics, int mouseX, int mouseY) {
        PartyStatePayload.InviteEntry invite = state.invites().getFirst();
        graphics.fill(0, 0, width, height, 0x99000000);
        int boxWidth = Math.min(250, width - 24);
        int boxHeight = 116;
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, COLOR_PANEL);
        graphics.renderOutline(x, y, boxWidth, boxHeight, COLOR_FRAME_LIGHT);
        graphics.renderOutline(x + 2, y + 2, boxWidth - 4, boxHeight - 4, COLOR_FRAME);
        drawCentered(graphics, "คำเชิญปาร์ตี้", x, boxWidth, y + 12, COLOR_GOLD);
        graphics.drawString(font, trim(invite.leaderName(), boxWidth - 30), x + 16, y + 36, 0xFFFFFFFF, false);
        graphics.drawString(font, invite.capacity() == 3 ? "Trio" : "Raid", x + 16, y + 52, COLOR_TEAL, false);
        graphics.drawString(font, "สมาชิก " + invite.memberCount() + "/" + invite.capacity(), x + 72, y + 52,
                COLOR_MUTED, false);
        drawCentered(graphics, "ต้องการเชิญคุณเข้าปาร์ตี้", x, boxWidth, y + 70, 0xFFE8ECEE);
        renderModalButton(graphics, x + 18, y + 91, 96, 18, "ยืนยัน", mouseX, mouseY, true);
        renderModalButton(graphics, x + boxWidth - 114, y + 91, 96, 18, "ยกเลิก", mouseX, mouseY, false);
    }

    private void renderInvitePage(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = panelX + 9;
        int detailX = panelX + 292;
        renderButton(graphics, panelX + 10, panelY + 8, 86, 16, "< ห้องปาร์ตี้", mouseX, mouseY,
                true, false, PartyActionPayload.Action.REFRESH);
        graphics.fill(listX, contentY, detailX - 7, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(listX, contentY, detailX - listX - 7, panelHeight - 95, COLOR_FRAME);
        graphics.fill(detailX, contentY, panelX + panelWidth - 9, panelY + panelHeight - 37, COLOR_PANEL_DARK);
        graphics.renderOutline(detailX, contentY, panelX + panelWidth - detailX - 9, panelHeight - 95, COLOR_FRAME);

        graphics.drawString(font, "ผู้เล่นรอบตัว", listX + 8, contentY + 8, COLOR_GOLD, false);
        int y = contentY + 25;
        for (int i = 0; i < state.nearbyPlayers().size(); i++) {
            renderNearbyRow(graphics, listX + 6, y, 266, i, state.nearbyPlayers().get(i), mouseX, mouseY);
            y += 22;
        }
        if (state.nearbyPlayers().isEmpty()) {
            graphics.drawString(font, "ไม่พบผู้เล่นรอบตัว", listX + 12, contentY + 44, COLOR_MUTED, false);
        }

        PartyStatePayload.NearbyPlayerEntry selected = selectedNearby();
        if (selected == null) {
            drawCentered(graphics, "เลือกผู้เล่น", detailX, panelX + panelWidth - detailX - 9, contentY + 38,
                    COLOR_MUTED);
        } else {
            graphics.drawString(font, trim(selected.name(), 100), detailX + 10, contentY + 16, COLOR_GOLD, false);
            graphics.drawString(font, "Lv. " + selected.level(), detailX + 10, contentY + 38, 0xFFE8ECEE, false);
            graphics.drawString(font, selected.distanceMeters() + "m", detailX + 10, contentY + 56, COLOR_MUTED, false);
            graphics.drawString(font, selected.status(), detailX + 10, contentY + 74,
                    selected.available() ? 0xFF68D391 : 0xFFFF7B72, false);
        }
        renderButton(graphics, detailX + 8, panelY + panelHeight - 65, 104, 22, "ส่งคำเชิญ", mouseX, mouseY,
                selected != null && selected.available(), true, PartyActionPayload.Action.INVITE);
    }

    private void renderNearbyRow(GuiGraphics graphics, int x, int y, int rowWidth, int index,
                                 PartyStatePayload.NearbyPlayerEntry player, int mouseX, int mouseY) {
        boolean selected = selectedNearby == index;
        boolean hovered = inside(mouseX, mouseY, x, y, rowWidth, 19);
        int textColor = player.available() ? 0xFFE8ECEE : 0xFF7C858B;
        graphics.fill(x, y, x + rowWidth, y + 19, selected ? 0xFF3A3020 : hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        graphics.renderOutline(x, y, rowWidth, 19, selected ? COLOR_GOLD : COLOR_FRAME);
        graphics.drawString(font, trim(player.name(), 86), x + 5, y + 6, textColor, false);
        graphics.drawString(font, "Lv." + player.level(), x + 99, y + 6, COLOR_MUTED, false);
        graphics.drawString(font, player.distanceMeters() + "m", x + 149, y + 6, COLOR_MUTED, false);
        graphics.drawString(font, player.status(), x + 192, y + 6,
                player.available() ? 0xFF68D391 : 0xFFFF7B72, false);
    }

    private void renderModalButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
                                   String label, int mouseX, int mouseY, boolean primary) {
        boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        int color = primary ? (hovered ? 0xFF39C7B9 : COLOR_TEAL_DARK) : hovered ? 0xFF59636A : 0xFF3A4248;
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, color);
        graphics.renderOutline(x, y, buttonWidth, buttonHeight, COLOR_FRAME_LIGHT);
        drawCentered(graphics, label, x, buttonWidth, y + 5, 0xFFFFFFFF);
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight, String label,
                              int mouseX, int mouseY, boolean active, boolean primary,
                              PartyActionPayload.Action action) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, buttonWidth, buttonHeight);
        int color = !active ? 0xFF30383E : primary ? (hovered ? 0xFF39C7B9 : COLOR_TEAL_DARK)
                : hovered ? 0xFF59636A : 0xFF3A4248;
        if (action == PartyActionPayload.Action.DISBAND && active) color = hovered ? COLOR_RED : 0xFF713030;
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, color);
        graphics.renderOutline(x, y, buttonWidth, buttonHeight, active ? COLOR_FRAME_LIGHT : COLOR_FRAME);
        drawCentered(graphics, label, x, buttonWidth, y + 6, active ? 0xFFFFFFFF : 0xFF79838A);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, panelX + panelWidth - 24, panelY + 7, 16, 16)) {
            graphics.renderTooltip(font, Component.literal("ปิด"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!state.invites().isEmpty()) {
            return clickInviteModal(mouseX, mouseY);
        }
        if (inside(mouseX, mouseY, panelX + panelWidth - 24, panelY + 7, 16, 16)) {
            onClose();
            return true;
        }
        if (currentView() == ViewMode.INVITE) {
            if (clickInvitePage(mouseX, mouseY)) return true;
        } else if (state.inParty()) {
            if (clickPartyRoom(mouseX, mouseY)) return true;
        } else if (clickFinder(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickFinder(double mouseX, double mouseY) {
        int y = contentY + 23;
        for (int i = 0; i < state.parties().size(); i++) {
            if (inside(mouseX, mouseY, panelX + 15, y, 266, 19)) selectedParty = i;
            y += 22;
        }
        PartyStatePayload.PartyEntry selected = selectedParty();
        if (inside(mouseX, mouseY, panelX + 300, panelY + panelHeight - 65, 104, 22)
                && selected != null && selected.open()) {
            send(PartyActionPayload.Action.INVITE, selected.leaderName());
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 156, panelY + panelHeight - 32, 72, 20)) {
            send(PartyActionPayload.Action.CREATE_TRIO, "");
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 234, panelY + panelHeight - 32, 72, 20)) {
            send(PartyActionPayload.Action.CREATE_RAID, "");
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 312, panelY + panelHeight - 32, 86, 20)) {
            send(PartyActionPayload.Action.REFRESH, "");
            return true;
        }
        return false;
    }

    private boolean clickPartyRoom(double mouseX, double mouseY) {
        int y = contentY + 25;
        for (int i = 0; i < state.members().size(); i++) {
            if (inside(mouseX, mouseY, panelX + 15, y, 266, 21)) selectedMember = i;
            y += 24;
        }
        int x = panelX + 299;
        if (inside(mouseX, mouseY, x, contentY + 14, 104, 22)) {
            send(PartyActionPayload.Action.READY, "");
            return true;
        }
        if (inside(mouseX, mouseY, x, contentY + 42, 104, 20)) {
            viewMode = ViewMode.INVITE;
            send(PartyActionPayload.Action.REFRESH, "");
            return true;
        }
        if (inside(mouseX, mouseY, x, contentY + 98, 104, 20)) {
            send(PartyActionPayload.Action.LEAVE, "");
            return true;
        }
        if (inside(mouseX, mouseY, x, contentY + 124, 104, 20)) {
            send(PartyActionPayload.Action.DISBAND, "");
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 156, panelY + panelHeight - 32, 86, 20)
                && selectedMember >= 0 && selectedMember < state.members().size()) {
            send(PartyActionPayload.Action.KICK, state.members().get(selectedMember).name());
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 312, panelY + panelHeight - 32, 86, 20)) {
            send(PartyActionPayload.Action.REFRESH, "");
            return true;
        }
        return false;
    }

    private boolean clickInvitePage(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, panelX + 10, panelY + 8, 86, 16)) {
            viewMode = ViewMode.AUTO;
            return true;
        }
        int y = contentY + 25;
        for (int i = 0; i < state.nearbyPlayers().size(); i++) {
            if (inside(mouseX, mouseY, panelX + 15, y, 266, 19)) selectedNearby = i;
            y += 22;
        }
        PartyStatePayload.NearbyPlayerEntry selected = selectedNearby();
        if (inside(mouseX, mouseY, panelX + 300, panelY + panelHeight - 65, 104, 22)
                && selected != null && selected.available()) {
            send(PartyActionPayload.Action.INVITE, selected.name());
            return true;
        }
        if (inside(mouseX, mouseY, panelX + 312, panelY + panelHeight - 32, 86, 20)) {
            send(PartyActionPayload.Action.REFRESH, "");
            return true;
        }
        return false;
    }

    private boolean clickInviteModal(double mouseX, double mouseY) {
        int boxWidth = Math.min(250, width - 24);
        int boxHeight = 116;
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        if (inside(mouseX, mouseY, x + 18, y + 91, 96, 18)) {
            send(PartyActionPayload.Action.ACCEPT, state.invites().getFirst().leaderName());
            return true;
        }
        if (inside(mouseX, mouseY, x + boxWidth - 114, y + 91, 96, 18)) {
            send(PartyActionPayload.Action.DECLINE, state.invites().getFirst().leaderName());
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!state.invites().isEmpty() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            send(PartyActionPayload.Action.DECLINE, state.invites().getFirst().leaderName());
            return true;
        }
        if (currentView() == ViewMode.INVITE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            viewMode = ViewMode.AUTO;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void receiveState(PartyStatePayload payload) {
        state = payload;
        selectedMember = Math.min(selectedMember, state.members().size() - 1);
        selectedParty = Math.min(selectedParty, state.parties().size() - 1);
        selectedNearby = Math.min(selectedNearby, state.nearbyPlayers().size() - 1);
        if (!payload.message().isEmpty()) {
            status = payload.message();
            statusColor = payload.success() ? 0xFF68D391 : 0xFFFF7B72;
            statusTicks = 100;
        }
    }

    private PartyStatePayload.PartyEntry selectedParty() {
        return selectedParty >= 0 && selectedParty < state.parties().size()
                ? state.parties().get(selectedParty)
                : null;
    }

    private PartyStatePayload.NearbyPlayerEntry selectedNearby() {
        return selectedNearby >= 0 && selectedNearby < state.nearbyPlayers().size()
                ? state.nearbyPlayers().get(selectedNearby)
                : null;
    }

    private ViewMode currentView() {
        return viewMode == ViewMode.INVITE && state.inParty() ? ViewMode.INVITE : ViewMode.AUTO;
    }

    private String titleLabel() {
        if (currentView() == ViewMode.INVITE) return "เชิญผู้เล่น";
        return state.inParty() ? "ห้องปาร์ตี้" : "รวมห้องปาร์ตี้";
    }

    private void send(PartyActionPayload.Action action, String target) {
        PacketDistributor.sendToServer(new PartyActionPayload(action, target));
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int boxWidth, int y, int color) {
        graphics.drawString(font, text, x + (boxWidth - font.width(text)) / 2, y, color, false);
    }

    private String trim(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int boxWidth, int boxHeight) {
        return mouseX >= x && mouseX < x + boxWidth && mouseY >= y && mouseY < y + boxHeight;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum ViewMode {
        AUTO,
        INVITE
    }
}
