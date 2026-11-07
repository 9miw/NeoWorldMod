package com.ambition.neoworld.client.gui.party;

import java.util.List;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import com.ambition.neoworld.network.party.PartyActionPayload;
import com.ambition.neoworld.network.party.PartyStatePayload;
import com.ambition.neoworld.party.Party.Purpose;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyScreen extends Screen {
    private static final int WHITE = 0xFFE7ECEF;
    private static final int MUTED = 0xFFA3ADB3;
    private static final int GOLD = 0xFFE7BE69;
    private static final int TEAL = 0xFF61C8BB;
    private static final int RED = 0xFFEA8989;
    private static final int ROW_HEIGHT = 26;
    private static final int FINDER_NOTICE_TICKS = 50;
    private PartyStatePayload state = new PartyStatePayload(false, 0, "", List.of(), List.of(),
            List.of(), List.of(), "", true, false);
    private boolean choosingCapacity;
    private boolean createPending;
    private List<Renderable> createBackdrop = List.of();
    private static final int CREATE_HEIGHT = 128;
    private int capacityChoice = 3;
    private Purpose purposeChoice = Purpose.GENERAL;
    private PartyActionPayload.Action browseFilter = PartyActionPayload.Action.BROWSE_ALL;
    private List<PartyStatePayload.PartyEntry> browseEntries = List.of();
    private UUID selectedParty;
    private int browsePage;
    private int browsePages = 1;
    private int browseTicks;
    private NeoButton previousPage;
    private NeoButton nextPage;
    private NeoButton invitePreference;
    private final NeoTitlePlaque finderTitle = new NeoTitlePlaque(0, 0, 0, 0, Component.literal("ค้นหาปาร์ตี้"))
            .style(NeoTitlePlaque.Style.SLIM);
    private final NeoTitlePlaque roomTitle = new NeoTitlePlaque(0, 0, 0, 0, Component.literal("ห้องปาร์ตี้"))
            .style(NeoTitlePlaque.Style.SLIM);
    private final NeoTitlePlaque inviteTitle = new NeoTitlePlaque(0, 0, 0, 0, Component.literal("เชิญเพื่อน"))
            .style(NeoTitlePlaque.Style.SLIM);
    private int roomRefreshTicks;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension;
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
        rebuildButtons();
        if (isBrowsing()) browse(browsePage);
        else send(PartyActionPayload.Action.REFRESH, "");
    }

    private boolean isBrowsing() { return !state.inParty() && !choosingCapacity; }

    private boolean isRoom() { return state.inParty() && !isInvitePage(); }

    private void layoutPanels() {
        if (isRoom()) {
            int usable = panelWidth - 24;
            listWidth = isLeader() ? Math.round((usable - 8) * 0.70F) : usable;
            detailX = x + 20 + listWidth;
            detailWidth = usable - listWidth - 8;
            listTop = y + 69;
            listBottom = y + panelHeight - 48;
        } else if (isInvitePage()) {
            listWidth = panelWidth - 24;
            listTop = y + 82;
            listBottom = y + panelHeight - 43;
        } else {
            listWidth = Math.round((panelWidth - 36) * 0.65F);
            detailX = x + 22 + listWidth;
            detailWidth = panelWidth - listWidth - 36;
            listTop = y + 72;
            listBottom = y + panelHeight - 64;
        }
    }

    private void browse(int page) {
        send(browseFilter, Integer.toString(page));
        browseTicks = 0;
    }

    private boolean isInvitePage() {
        return invitePage && state.inParty();
    }

    private boolean isLeader() {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getGameProfile().getName().equals(state.leaderName());
    }

    private void rebuildButtons() {
        layoutPanels();
        clearWidgets();
        createBackdrop = List.of();
        primary = null;
        kick = null;
        disband = null;
        invite = null;
        previousPage = null;
        nextPage = null;
        invitePreference = null;
        add("X", x + panelWidth - 29, y + (isRoom() ? 27 : 30), 18, 18,
                isRoom() ? NeoButton.Style.STANDARD : NeoButton.Style.DESTRUCTIVE,
                this::onClose).setTooltip(Tooltip.create(Component.literal("ปิด")));
        int footerY = y + panelHeight - 35;
        if (isInvitePage()) {
            addInviteButtons();
        } else if (isRoom()) {
            addRoomButtons(footerY);
        } else {
            int createButtonX = x + panelWidth - 111;
            add("สร้างปาร์ตี้", createButtonX, y + 54, 97, 20, NeoButton.Style.PRIMARY, this::openCreate);
            addBrowseFilters(createButtonX);
            int center = x + panelWidth / 2;
            int actionWidth = Math.min(118, (panelWidth - 132) / 2);
            invitePreference = add("", x + 14, footerY, actionWidth, 22, NeoButton.Style.STANDARD,
                    () -> send(state.acceptsInvites() ? PartyActionPayload.Action.BLOCK_INVITES
                            : PartyActionPayload.Action.ALLOW_INVITES, ""));
            invitePreference.setTooltip(Tooltip.create(Component.literal("เปิดหรือปิดรับคำเชิญจากผู้เล่นอื่น")));
            previousPage = add("<", center - 43, footerY, 22, 22, NeoButton.Style.STANDARD, () -> browse(browsePage - 1));
            nextPage = add(">", center + 21, footerY, 22, 22, NeoButton.Style.STANDARD, () -> browse(browsePage + 1));
            primary = add("ขอเข้าร่วม", x + panelWidth - 14 - actionWidth, footerY, actionWidth, 22, NeoButton.Style.PRIMARY, () -> {
                if (selectedParty != null) send(PartyActionPayload.Action.REQUEST_JOIN, selectedParty.toString());
            });
            if (choosingCapacity) {
                createBackdrop = List.copyOf(renderables);
                clearWidgets();
                addCreateButtons();
            }
        }
        updateButtons();
    }

    private void addInviteButtons() {
        add("<", x + 12, y + 27, 24, 18, NeoButton.Style.STANDARD,
                () -> changePage(false)).setTooltip(Tooltip.create(Component.literal("กลับห้องปาร์ตี้")));
        int rangeWidth = Math.min(58, (panelWidth - 70) / 3);
        for (int i = 0; i < 3; i++) {
            int value = 16 << i;
            add(value + " บล็อก", x + 14 + i * (rangeWidth + 4), y + 54, rangeWidth, 20, NeoButton.Style.STANDARD, () -> {
                range = value;
                scroll = 0;
                rebuildButtons();
            }).selected(range == value);
        }
        var refresh = add("", x + panelWidth - 42, y + 54, 28, 20, NeoButton.Style.STANDARD,
                () -> send(PartyActionPayload.Action.REFRESH, ""));
        refresh.setMessage(NeoGuiIcons.label(NeoGuiIcons.REFRESH, ""));
        refresh.setTooltip(Tooltip.create(Component.literal("รีเฟรชผู้เล่นใกล้ตัว")));

        var players = nearby();
        scroll = Math.clamp(scroll, 0, Math.max(0, players.size() - visibleRows()));
        for (int i = scroll; i < Math.min(players.size(), scroll + visibleRows()); i++) {
            var player = players.get(i);
            boolean sent = PartyStatePayload.INVITE_SENT.equals(player.status());
            if (!player.available() && !sent) continue;
            var button = add(sent ? player.status() : "เชิญ", inviteActionX(), listTop + (i - scroll) * ROW_HEIGHT + 2,
                    62, 18, NeoButton.Style.PRIMARY, () -> PacketDistributor.sendToServer(
                            new PartyActionPayload(PartyActionPayload.Action.INVITE, player.name(), range)));
            button.active = !sent && isLeader() && state.members().size() < state.capacity();
        }
    }

    private int inviteActionX() { return x + listWidth - 65; }

    private int createWidth() { return Math.min(302, width - 24); }
    private int createX() { return (width - createWidth()) / 2; }
    private int createY() { return (height - CREATE_HEIGHT) / 2; }

    private void addCreateButtons() {
        int left = createX();
        int top = createY();
        int labelWidth = Math.min(86, createWidth() / 3);
        int optionX = left + 12 + labelWidth;
        int optionWidth = (createWidth() - 30 - labelWidth) / 2;
        int secondX = optionX + optionWidth + 6;
        add("ทั่วไป", optionX, top + 30, optionWidth, 20, NeoButton.Style.STANDARD,
                () -> selectPurpose(Purpose.GENERAL)).selected(purposeChoice == Purpose.GENERAL).active = !createPending;
        add("ลงดันเจี้ยน", secondX, top + 30, optionWidth, 20, NeoButton.Style.STANDARD,
                () -> selectPurpose(Purpose.DUNGEON)).selected(purposeChoice == Purpose.DUNGEON).active = !createPending;
        add("3 คน", optionX, top + 55, optionWidth, 20, NeoButton.Style.STANDARD,
                () -> selectCapacity(3)).selected(capacityChoice == 3).active = !createPending;
        add("6 คน", secondX, top + 55, optionWidth, 20, NeoButton.Style.STANDARD,
                () -> selectCapacity(6)).selected(capacityChoice == 6).active = !createPending;
        int actionWidth = (createWidth() - 30) / 2;
        add("ยกเลิก", left + 12, top + 84, actionWidth, 20, NeoButton.Style.STANDARD,
                this::closeCreate).active = !createPending;
        add("สร้างปาร์ตี้", left + 18 + actionWidth, top + 84, actionWidth, 20, NeoButton.Style.PRIMARY,
                this::submitCreate).active = !createPending;
    }

    private void openCreate() {
        choosingCapacity = true;
        purposeChoice = Purpose.GENERAL;
        capacityChoice = 3;
        statusTicks = 0;
        rebuildButtons();
    }

    private void closeCreate() {
        if (createPending) return;
        choosingCapacity = false;
        statusTicks = 0;
        rebuildButtons();
    }

    private void submitCreate() {
        if (createPending) return;
        createPending = true;
        statusTicks = 0;
        rebuildButtons();
        send(createAction(), "");
    }

    private void addRoomButtons(int footerY) {
        boolean leader = isLeader();
        int center = x + panelWidth / 2;
        int footerWidth = Math.min(116, (panelWidth - 36) / 2);
        add("ออกจากปาร์ตี้", leader ? center - footerWidth - 4 : center - footerWidth / 2,
                footerY, footerWidth, 22, NeoButton.Style.STANDARD,
                () -> confirm("ออกจากปาร์ตี้", PartyActionPayload.Action.LEAVE, ""));
        if (!leader) return;
        int bx = detailX + 6;
        int bw = detailWidth - 12;
        int step = Math.min(26, (listBottom - listTop) / 4);
        int bh = Math.min(22, step - 3);
        invite = add("เชิญเพื่อน", bx, listTop, bw, bh, NeoButton.Style.PRIMARY, () -> changePage(true));
        primary = add("เลือกดันเจี้ยน", bx, listTop + step, bw, bh, NeoButton.Style.STANDARD, () -> {
            if (minecraft != null && minecraft.getConnection() != null)
                minecraft.getConnection().sendCommand("neow dungeon");
        });
        add("เปลี่ยนจุดประสงค์", bx, listTop + step * 2, bw, bh,
                NeoButton.Style.STANDARD, this::confirmPurposeChange);
        kick = add("เตะสมาชิก", bx, listTop + step * 3, bw, bh, NeoButton.Style.DESTRUCTIVE,
                () -> confirm("เตะ " + selectedName, PartyActionPayload.Action.KICK, selectedName));
        disband = add("ยุบปาร์ตี้", center + 4, footerY, footerWidth, 22, NeoButton.Style.DESTRUCTIVE,
                () -> confirm("ยุบปาร์ตี้", PartyActionPayload.Action.DISBAND, ""));
    }

    private void addBrowseFilters(int createButtonX) {
        String[] labels = {"ทั้งหมด", "ทั่วไป", "ลงดันเจี้ยน"};
        PartyActionPayload.Action[] actions = {PartyActionPayload.Action.BROWSE_ALL,
                PartyActionPayload.Action.BROWSE_GENERAL, PartyActionPayload.Action.BROWSE_DUNGEON};
        int left = x + 14;
        int gap = 4;
        int availableWidth = createButtonX - left - 16 - gap * (labels.length - 1);
        int[] preferred = new int[labels.length];
        int totalWidth = 0;
        for (int i = 0; i < labels.length; i++) {
            preferred[i] = Math.max(18, font.width(labels[i]) + 14);
            totalWidth += preferred[i];
        }
        for (int i = 0; i < actions.length; i++) {
            var filter = actions[i];
            int buttonWidth = Math.max(18, preferred[i] * Math.min(totalWidth, availableWidth) / totalWidth);
            add(labels[i], left, y + 54, buttonWidth, 20,
                    NeoButton.Style.STANDARD, () -> {
                        browseFilter = filter;
                        selectedParty = null;
                        browsePage = 0;
                        browsePages = 1;
                        browseEntries = List.of();
                        rebuildButtons();
                        browse(0);
                    }).selected(filter == browseFilter);
            left += buttonWidth + gap;
        }
    }

    private void selectPurpose(Purpose purpose) {
        purposeChoice = purpose;
        rebuildButtons();
    }

    private void confirmPurposeChange() {
        boolean toDungeon = state.purpose() == Purpose.GENERAL;
        confirm("เปลี่ยนจุดประสงค์เป็น " + purposeLabel(toDungeon ? Purpose.DUNGEON : Purpose.GENERAL),
                toDungeon ? PartyActionPayload.Action.PURPOSE_DUNGEON : PartyActionPayload.Action.PURPOSE_GENERAL, "");
    }

    private PartyActionPayload.Action createAction() {
        if (purposeChoice == Purpose.DUNGEON) return capacityChoice == 3
                ? PartyActionPayload.Action.CREATE_DUNGEON_TRIO : PartyActionPayload.Action.CREATE_DUNGEON_RAID;
        return capacityChoice == 3 ? PartyActionPayload.Action.CREATE_TRIO : PartyActionPayload.Action.CREATE_RAID;
    }

    private void selectCapacity(int capacity) {
        capacityChoice = capacity;
        rebuildButtons();
    }

    private NeoButton add(String label, int bx, int by, int bw, int bh, NeoButton.Style style, Runnable action) {
        String glyph = switch (label) {
            case "พร้อม" -> NeoGuiIcons.READY;
            case "ส่งคำเชิญ", "เชิญเพื่อน" -> NeoGuiIcons.INVITE;
            case "เตะสมาชิก" -> NeoGuiIcons.KICK;
            case "ออกจากปาร์ตี้" -> NeoGuiIcons.LEAVE;
            case "ยุบปาร์ตี้" -> NeoGuiIcons.DISBAND;
            case "รีเฟรช", "สแกนใหม่" -> NeoGuiIcons.REFRESH;
            case "สร้างปาร์ตี้" -> NeoGuiIcons.LEADER;
            default -> "";
        };
        Component message = glyph.isEmpty() ? Component.literal(label) : NeoGuiIcons.label(glyph, label);
        NeoButton button = new NeoButton(Button.builder(message, ignored -> action.run())
                .bounds(bx, by, Math.max(18, bw), bh)).style(style);
        if (!state.inParty() || style != NeoButton.Style.DESTRUCTIVE) {
            button.frameRenderer(PartyFinderTheme::button);
        }
        return addRenderableWidget(button);
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
        if (invitePreference != null) {
            boolean enabled = state.acceptsInvites();
            invitePreference.setMessage(Component.literal(enabled ? "รับคำเชิญ: เปิด" : "รับคำเชิญ: ปิด"));
            invitePreference.selected(enabled);
        }
        if (primary != null) {
            if (state.inParty()) {
                primary.visible = isLeader();
                primary.active = isLeader();
                primary.setTooltip(Tooltip.create(Component.literal("เปิดหน้าดันเจี้ยน")));
            } else {
                primary.active = browseEntries.stream().anyMatch(entry -> entry.partyId().equals(selectedParty) && entry.open());
            }
        }
        if (previousPage != null) previousPage.active = browsePage > 0;
        if (nextPage != null) nextPage.active = browsePage + 1 < browsePages;
        if (invite != null) invite.active = isLeader() && state.members().size() < state.capacity();
        if (disband != null) disband.active = isLeader();
        if (kick != null) {
            kick.visible = kick.active = isLeader() && state.members().stream()
                    .anyMatch(p -> p.name().equals(selectedName) && !p.leader());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
        if (isBrowsing() && ++browseTicks >= 60) browse(browsePage);
        if (state.inParty() && ++roomRefreshTicks >= 60) {
            roomRefreshTicks = 0;
            send(PartyActionPayload.Action.REFRESH, "");
        }
        if (minecraft != null && minecraft.level != null && !minecraft.level.dimension().equals(lastDimension)) {
            var previous = lastDimension;
            lastDimension = minecraft.level.dimension();
            if (previous != null) send(PartyActionPayload.Action.REFRESH, "");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        PartyFinderTheme.frame(graphics, x, y + 18, panelWidth, panelHeight - 18);
        int titleWidth = Math.min(198, panelWidth - 84);
        (isInvitePage() ? inviteTitle : state.inParty() ? roomTitle : finderTitle)
                .bounds(x + (panelWidth - titleWidth) / 2, y + 3, titleWidth, 31).render(graphics, font);
        if (isRoom()) {
            PartyFinderTheme.badge(graphics, x + 12, y + 42, panelWidth - 48, 18, false);
            String summary = purposeLabel(state.purpose()) + " · สมาชิก " + state.members().size() + "/" + state.capacity()
                    + (state.partyExpEnabled() ? " · แชร์ EXP เปิด · 32 บล็อก" : " · แชร์ EXP ปิดในโลกนี้");
            text(graphics, summary, x + 18, y + 47, panelWidth - 60, GOLD);
            PartyFinderTheme.frame(graphics, x + 12, listTop - 4, listWidth, listBottom - listTop + 4);
            if (isLeader()) PartyFinderTheme.frame(graphics, detailX, listTop - 4, detailWidth, listBottom - listTop + 4);
            renderRows(graphics, mouseX, mouseY);
        } else if (isInvitePage()) {
            PartyFinderTheme.frame(graphics, x + 12, listTop - 4, listWidth, listBottom - listTop + 4);
            renderRows(graphics, mouseX, mouseY);
        } else {
            renderBrowser(graphics, choosingCapacity ? -1 : mouseX, choosingCapacity ? -1 : mouseY);
        }
        if (state.inParty() && statusTicks > 0) text(graphics, status, x + 14,
                isRoom() ? y + panelHeight - 46 : y + panelHeight - 32,
                panelWidth - 48, statusColor);
        updateButtons();
        if (choosingCapacity) {
            renderCreatePopup(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        if (state.inParty()) renderRoleTooltip(graphics, mouseX, mouseY);
        if (isBrowsing() && statusTicks > 0) renderFinderNotice(graphics);
    }

    private void renderFinderNotice(GuiGraphics graphics) {
        var lines = font.split(Component.literal(status), Math.max(1, width - 32));
        int lineHeight = font.lineHeight + 2;
        int noticeY = (height - lines.size() * lineHeight) / 2;
        int alpha = Math.min(255, statusTicks * 25 + 5);
        int color = (statusColor & 0x00FFFFFF) | (alpha << 24);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 400);
            for (var line : lines) {
                graphics.drawString(font, line, (width - font.width(line)) / 2, noticeY, color, true);
                noticeY += lineHeight;
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderCreatePopup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable widget : createBackdrop) widget.render(graphics, -1, -1, partialTick);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 400);
            graphics.fill(0, 0, width, height, 0xA0000000);
            int left = createX();
            int top = createY();
            PartyFinderTheme.frame(graphics, left, top, createWidth(), CREATE_HEIGHT);
            centered(graphics, "สร้างปาร์ตี้", width / 2, top + 12, WHITE);
            int labelWidth = Math.min(86, createWidth() / 3) - 4;
            text(graphics, "จุดประสงค์", left + 12, top + 36, labelWidth, MUTED);
            text(graphics, "จำนวนสมาชิก", left + 12, top + 61, labelWidth, MUTED);
            if (createPending || statusTicks > 0) {
                text(graphics, createPending ? "กำลังสร้างปาร์ตี้..." : status,
                        left + 12, top + 112, createWidth() - 24, createPending ? MUTED : statusColor);
            }
            for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static String purposeLabel(Purpose purpose) {
        return purpose == Purpose.DUNGEON ? "ลงดันเจี้ยน" : "ทั่วไป";
    }

    private int browseRowHeight() { return Math.max(12, Math.min(38, (panelHeight - 147) / 4)); }

    private void renderBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = x + 14;
        int tableWidth = panelWidth - 28;
        int purposeX = left + tableWidth * 34 / 100;
        int countX = left + tableWidth * 60 / 100;
        int statusX = left + tableWidth * 74 / 100;
        int rowsY = y + 98;
        PartyFinderTheme.badge(graphics, left, y + 79, tableWidth, 18, false);
        text(graphics, "หัวหน้าปาร์ตี้", left + 6, y + 84, purposeX - left - 8, WHITE);
        text(graphics, "จุดประสงค์", purposeX, y + 84, countX - purposeX - 5, WHITE);
        text(graphics, "สมาชิก", countX, y + 84, statusX - countX - 4, WHITE);
        text(graphics, "สถานะ", statusX, y + 84, left + tableWidth - statusX - 4, WHITE);
        int rowHeight = browseRowHeight();
        for (int i = 0; i < browseEntries.size(); i++) {
            var entry = browseEntries.get(i);
            int rowY = rowsY + i * rowHeight;
            boolean selected = entry.partyId().equals(selectedParty);
            boolean hovered = inside(mouseX, mouseY, left, rowY, tableWidth, rowHeight - 2);
            PartyFinderTheme.row(graphics, left, rowY, tableWidth, rowHeight - 2, selected, hovered, entry.open());
            int baseline = rowY + (rowHeight - font.lineHeight) / 2;
            var connection = minecraft == null ? null : minecraft.getConnection();
            var leader = connection == null ? null : connection.getPlayerInfo(entry.leaderId());
            int faceSize = Math.min(16, rowHeight - 4);
            if (leader != null) PlayerFaceRenderer.draw(graphics, leader.getSkin(), left + 5, rowY + (rowHeight - faceSize) / 2, faceSize);
            graphics.drawString(font, NeoGuiIcons.icon(NeoGuiIcons.LEADER), left + 7 + faceSize, baseline, GOLD, false);
            int nameX = left + 25 + faceSize;
            text(graphics, entry.leaderName(), nameX, baseline, purposeX - nameX - 4, WHITE);
            finderBadge(graphics, purposeLabel(entry.purpose()), purposeX, baseline, countX - purposeX - 8,
                    entry.purpose() == Purpose.DUNGEON, WHITE);
            centered(graphics, entry.memberCount() + "/" + entry.capacity(), (countX + statusX) / 2, baseline, WHITE);
            finderBadge(graphics, entry.locked() ? "กำลังลงดัน" : entry.open() ? "รับสมาชิก" : "เต็ม",
                    statusX, baseline, left + tableWidth - statusX - 7, entry.open(), entry.open() ? WHITE : MUTED);
        }
        if (browseEntries.isEmpty()) centered(graphics, "ไม่พบปาร์ตี้", x + panelWidth / 2, rowsY + 16, MUTED);
        graphics.fill(left, y + panelHeight - 42, left + tableWidth, y + panelHeight - 41, 0xFF687781);
        finderBadge(graphics, (browsePage + 1) + " / " + browsePages,
                x + panelWidth / 2 - 19, y + panelHeight - 29, 38, false, WHITE);
    }

    private void finderBadge(GuiGraphics graphics, String label, int x, int y, int width, boolean accent, int color) {
        PartyFinderTheme.badge(graphics, x, y - 3, width, font.lineHeight + 5, accent);
        centered(graphics, font.plainSubstrByWidth(label, Math.max(1, width - 8)), x + width / 2, y, color);
    }

    private int rowCount() {
        return isInvitePage() ? nearby().size() : state.members().size();
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        var nearbyPlayers = isInvitePage() ? nearby() : List.<PartyStatePayload.NearbyPlayerEntry>of();
        scroll = Math.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows()));
        graphics.enableScissor(x + 14, listTop, x + listWidth + 10, listBottom);
        for (int index = scroll; index < Math.min(rowCount(), scroll + visibleRows()); index++) {
            int rowY = listTop + (index - scroll) * ROW_HEIGHT;
            int rowX = x + 17;
            int rowWidth = listWidth - 16;
            if (isInvitePage()) {
                renderInviteRow(graphics, nearbyPlayers.get(index), rowX, rowY, rowWidth);
                continue;
            }
            var member = state.members().get(index);
            String name = member.name();
            boolean self = minecraft != null && minecraft.player != null
                    && member.playerId().equals(minecraft.player.getUUID());
            String metadata = "Lv." + member.level() + (self ? " · คุณ" : "");
            String label = member.online() ? "ออนไลน์" : "ออฟไลน์";
            int color = member.online() ? TEAL : MUTED;
            boolean selected = isLeader() ? name.equals(selectedName) : self;
            boolean hover = inside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT - 3);
            PartyFinderTheme.row(graphics, rowX, rowY, rowWidth, ROW_HEIGHT - 3,
                    selected, hover && isLeader(), member.online());
            var connection = minecraft == null ? null : minecraft.getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(member.playerId());
            if (info != null) PlayerFaceRenderer.draw(graphics, info.getSkin(), rowX + 5, rowY + 4, 16);
            int statusWidth = Math.min(72, rowWidth / 3);
            int nameInset = 26;
            int nameWidth = rowWidth - statusWidth - nameInset - 6;
            if (member.leader()) {
                Component crown = NeoGuiIcons.label(NeoGuiIcons.LEADER, "");
                String visibleName = font.plainSubstrByWidth(name, Math.max(0, nameWidth - font.width(crown) - 3));
                graphics.drawString(font, visibleName, rowX + nameInset, rowY + 3, WHITE, false);
                graphics.drawString(font, crown, rowX + nameInset + font.width(visibleName) + 3, rowY + 3, GOLD, false);
            } else {
                text(graphics, name, rowX + nameInset, rowY + 3, nameWidth, WHITE);
            }
            text(graphics, metadata, rowX + nameInset, rowY + 13, nameWidth, self ? TEAL : MUTED);
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

    private void renderInviteRow(GuiGraphics graphics, PartyStatePayload.NearbyPlayerEntry player,
                                 int rowX, int rowY, int rowWidth) {
        PartyFinderTheme.row(graphics, rowX, rowY, rowWidth, ROW_HEIGHT - 3, false, false, player.available());
        var connection = minecraft == null ? null : minecraft.getConnection();
        var info = connection == null ? null : connection.getPlayerInfo(player.name());
        if (info != null) PlayerFaceRenderer.draw(graphics, info.getSkin(), rowX + 5, rowY + 4, 16);
        else graphics.drawString(font, NeoGuiIcons.icon(NeoGuiIcons.MEMBER), rowX + 5, rowY + 8, WHITE, false);
        int nameX = rowX + 27;
        int actionX = inviteActionX();
        text(graphics, player.name(), nameX, rowY + 3, actionX - nameX - 8, WHITE);
        text(graphics, "Lv." + player.level() + " · " + player.distanceMeters() + "m",
                nameX, rowY + 13, actionX - nameX - 8, MUTED);
        if (!player.available() && !PartyStatePayload.INVITE_SENT.equals(player.status())) {
            text(graphics, player.status(), actionX, rowY + 7, 62, MUTED);
        }
    }

    private void renderRoleTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, x + 22, listTop, 18, visibleRows() * ROW_HEIGHT)) return;
        int index = scroll + (mouseY - listTop) / ROW_HEIGHT;
        if (index >= rowCount()) return;
        if (isInvitePage()) {
            var player = nearby().get(index);
            graphics.renderTooltip(font, Component.literal(player.status()), mouseX, mouseY);
        } else {
            boolean leader = state.members().get(index).leader();
            graphics.renderTooltip(font, Component.literal(leader ? "หัวหน้าปาร์ตี้" : "สมาชิก"), mouseX, mouseY);
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
        if (choosingCapacity) return super.mouseClicked(mx, my, button);
        if (isBrowsing()) {
            int row = (int) (my - y - 98) / browseRowHeight();
            if (button == 0 && inside(mx, my, x + 14, y + 98, panelWidth - 28, browseEntries.size() * browseRowHeight())) {
                selectedParty = browseEntries.get(row).partyId();
                updateButtons();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (button == 0 && rowCount() > visibleRows()
                && inside(mx, my, x + listWidth + 4, listTop, 8, listBottom - listTop)) {
            draggingScroll = true;
            scrollTo(my);
            return true;
        }
        if (button == 0 && isRoom() && isLeader()
                && inside(mx, my, x + 17, listTop, listWidth - 16, visibleRows() * ROW_HEIGHT)) {
            int index = scroll + (int) (my - listTop) / ROW_HEIGHT;
            if (index < rowCount()) {
                selectedName = state.members().get(index).name();
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
        setScroll((int) Math.round(fraction * (rowCount() - visibleRows())));
    }

    private void setScroll(int offset) {
        int next = Math.clamp(offset, 0, Math.max(0, rowCount() - visibleRows()));
        if (next == scroll) return;
        scroll = next;
        if (isInvitePage()) rebuildButtons();
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
            setScroll(scroll - (int) Math.signum(vertical));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private static boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && choosingCapacity && !state.inParty()) {
            closeCreate();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isInvitePage()) {
            changePage(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void receiveState(PartyStatePayload payload) {
        boolean wasInParty = state.inParty();
        boolean wasLeader = isLeader();
        boolean inviteRowsChanged = isInvitePage() && (!state.nearbyPlayers().equals(payload.nearbyPlayers())
                || state.members().size() != payload.members().size() || state.capacity() != payload.capacity());
        state = payload;
        if (payload.browsePage() >= 0) {
            browsePage = payload.browsePage();
            browsePages = payload.browsePages();
            browseEntries = payload.parties();
            if (browseEntries.stream().noneMatch(entry -> entry.partyId().equals(selectedParty))) selectedParty = null;
        }
        if (wasInParty != state.inParty()) {
            choosingCapacity = false;
            createPending = false;
            invitePage = false;
            selectedName = "";
            scroll = 0;
            selectedParty = null;
            if (!state.inParty()) browse(0);
        }
        if (!isLeader() && invitePage) {
            invitePage = false;
            scroll = 0;
        }
        if (inviteRowsChanged || wasInParty != state.inParty() || wasLeader != isLeader()) rebuildButtons();
        else updateButtons();
        if (!payload.message().isEmpty()) {
            status = payload.message();
            statusColor = payload.success() ? TEAL : RED;
            statusTicks = isBrowsing() ? FINDER_NOTICE_TICKS : 100;
            if (choosingCapacity && createPending) {
                createPending = false;
                rebuildButtons();
            }
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
