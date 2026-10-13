package com.ambition.neoworld.client.gui.dungeon;

import java.util.ArrayList;
import java.util.List;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload;
import com.ambition.neoworld.network.dungeon.DungeonRoomQueryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Waiting room; only the member viewport scrolls. Server commands validate room actions. */
public final class DungeonRoomScreen extends Screen {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/dungeon_browser_frame.png");
    private static final int ROWS = 3, LIST_X = 242, LIST_Y = 91, LIST_W = 288, LIST_H = 126;
    private DungeonRoomViewPayload state;
    private final List<NeoButton> buttons = new ArrayList<>();
    private NeoButton start, ready, leave, disband;
    private int offset, ticks;
    private boolean dragging;
    private double dragOffset;
    private float scale;
    private int originX, originY;

    public DungeonRoomScreen(DungeonRoomViewPayload state) {
        super(Component.literal("ห้องดันเจี้ยน"));
        this.state = state;
    }

    public static void receive(DungeonRoomViewPayload payload) {
        var minecraft = Minecraft.getInstance();
        var current = NeoPopupScreen.findScreen(DungeonRoomScreen.class);
        if (payload.open()) minecraft.setScreen(new DungeonRoomScreen(payload));
        else if (current != null) {
            if (minecraft.screen == current && current.state.roomId() != 0
                    && (payload.roomId() == 0 || !payload.waiting())) {
                current.onClose();
                return;
            }
            current.state = payload;
            current.offset = Math.min(current.offset, current.maxOffset());
            current.updateButtons();
        }
    }

    @Override protected void init() {
        scale = Math.min(1.5F, Math.min((width - 8) / 560F, (height - 8) / 310F));
        originX = Math.round((width - 560 * scale) / 2);
        originY = Math.round((height - 310 * scale) / 2);
        buttons.clear();
        add(Component.literal("X"), 524, 29, 18, 18, this::onClose, NeoButton.Style.STANDARD);
        leave = add(NeoGuiIcons.label(NeoGuiIcons.LEAVE, "ออกจากห้อง"), 28, 263, 132, 23,
                () -> command("leave"), NeoButton.Style.STANDARD);
        disband = add(NeoGuiIcons.label(NeoGuiIcons.DISBAND, "ยกเลิกดันเจี้ยน"), 166, 263, 132, 23,
                () -> NeoPopupScreen.showChoice(Component.literal("ยกเลิกดันเจี้ยน"),
                        Component.literal("ยกเลิกห้องดันเจี้ยน โดยปาร์ตี้ยังอยู่?"), Component.literal("ยืนยัน"),
                        () -> command("disband"), Component.literal("ยกเลิก"), () -> {}), NeoButton.Style.DESTRUCTIVE);
        start = add(Component.literal("เริ่มดันเจี้ยน"), 342, 263, 188, 23,
                () -> command("start"), NeoButton.Style.PRIMARY);
        ready = add(NeoGuiIcons.label(NeoGuiIcons.READY, "พร้อม"), 342, 263, 188, 23, () -> {
            command("ready");
        }, NeoButton.Style.PRIMARY);
        updateButtons();
    }

    private NeoButton add(Component label, int x, int y, int w, int h, Runnable action, NeoButton.Style style) {
        NeoButton button = addRenderableWidget(new NeoButton(Button.builder(label, ignored -> action.run()).bounds(x, y, w, h)).style(style));
        buttons.add(button);
        return button;
    }

    private void updateButtons() {
        if (start == null || minecraft.player == null) return;
        var self = state.members().stream().filter(m -> m.id().equals(minecraft.player.getUUID())).findFirst().orElse(null);
        boolean owner = self != null && self.owner();
        start.visible = owner;
        start.active = state.waiting() && state.members().size() == state.capacity()
                && state.members().stream().allMatch(m -> m.online() && m.ready());
        ready.visible = self != null && !owner;
        ready.active = state.waiting();
        ready.setMessage(NeoGuiIcons.label(NeoGuiIcons.READY, self != null && self.ready() ? "ยกเลิกพร้อม" : "พร้อม"));
        leave.active = self != null && state.waiting();
        disband.visible = owner;
        disband.active = owner && state.waiting();
    }

    private void command(String action) {
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("neoworld dungeon room " + action);
        query();
    }
    private void query() { PacketDistributor.sendToServer(DungeonRoomQueryPayload.INSTANCE); }
    @Override public void tick() { if (++ticks % 20 == 0) query(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        int mx = (int) localX(mouseX), my = (int) localY(mouseY);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(FRAME, 0, 0, 560, 310, 0, 0, 1672, 940, 1672, 940);
        g.drawCenteredString(font, title, 280, 22, 0xE7ECEF);
        if (state.roomId() == 0) {
            g.drawCenteredString(font, "คุณไม่ได้อยู่ในห้องดันเจี้ยน", 280, 145, 0xABB4BA);
        } else {
            var preview = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/previews/"
                    + (state.structure().contains("forgotten_mine") ? "forgotten_mine" : "default_dungeon") + ".png");
            g.blit(preview, 28, 55, 196, 110, 0, 0, 1672, 940, 1672, 940);
            text(g, state.title(), 28, 174, 196, 0xF2C14E);
            text(g, "ห้อง #" + state.roomId() + " | โหมด " + state.capacity() + " คน", 28, 192, 196, 0xE7ECEF);
            text(g, "เลเวลขั้นต่ำ " + state.minimumLevel(), 28, 208, 196, 0xABB4BA);
            String time = state.seconds() <= 0 ? "ไม่จำกัด" : "%d:%02d".formatted(state.seconds() / 60, state.seconds() % 60);
            text(g, "เวลา " + time + " | Wave " + state.waves(), 28, 224, 196, 0xABB4BA);
            text(g, "สมาชิก " + state.members().size() + "/" + state.capacity(), LIST_X, 64, 250, 0xE7ECEF);
            renderMembers(g, mx, my);
            long count = state.members().stream().filter(m -> m.ready() && m.online()).count();
            String status = state.waiting() ? "พร้อม " + count + "/" + state.capacity() : "ดันเจี้ยนเริ่มแล้ว";
            text(g, status, LIST_X, 235, LIST_W, 0x68DACE);
        }
        for (NeoButton button : buttons) if (button.visible) button.render(g, mx, my, delta);
        g.pose().popPose();
    }

    private void renderMembers(GuiGraphics g, int mx, int my) {
        for (int row = 0; row < ROWS && offset + row < state.members().size(); row++) {
            var member = state.members().get(offset + row);
            int y = LIST_Y + row * 42;
            g.fill(LIST_X, y, LIST_X + LIST_W - 18, y + 38, 0xFF192326);
            g.renderOutline(LIST_X, y, LIST_W - 18, 38, 0xFF505C62);
            g.drawString(font, NeoGuiIcons.icon(member.owner() ? NeoGuiIcons.LEADER : NeoGuiIcons.MEMBER), LIST_X + 7, y + 10, 0xFFFFFF, false);
            text(g, member.name(), LIST_X + 31, y + 7, 122, 0xE7ECEF);
            text(g, member.online() ? "Lv. " + member.level() : "ออฟไลน์", LIST_X + 31, y + 22, 122, 0xABB4BA);
            if (member.ready()) g.drawString(font, NeoGuiIcons.icon(NeoGuiIcons.READY), LIST_X + 163, y + 10, 0xFFFFFF, false);
            text(g, member.ready() ? "พร้อม" : "ยังไม่พร้อม", LIST_X + 185, y + 15, 80, member.ready() ? 0x68DACE : 0xABB4BA);
        }
        int x = LIST_X + LIST_W - 12;
        g.fill(x, LIST_Y, x + 12, LIST_Y + LIST_H, 0xFF0C1114);
        g.drawCenteredString(font, "^", x + 6, LIST_Y, maxOffset() > 0 ? 0xFFFFFF : 0x646D72);
        g.drawCenteredString(font, "v", x + 6, LIST_Y + LIST_H - 10, maxOffset() > 0 ? 0xFFFFFF : 0x646D72);
        int y = thumbY(), h = thumbHeight();
        g.fill(x + 2, y, x + 10, y + h, dragging ? 0xFF68DACE : 0xFF879399);
    }

    private void text(GuiGraphics g, String s, int x, int y, int w, int color) {
        g.drawString(font, font.plainSubstrByWidth(s, w), x, y, color, false);
    }
    private int maxOffset() { return Math.max(0, state.members().size() - ROWS); }
    private int thumbHeight() { return Math.max(18, (LIST_H - 24) * ROWS / Math.max(ROWS, state.members().size())); }
    private int thumbY() { return LIST_Y + 12 + (maxOffset() == 0 ? 0 : (LIST_H - 24 - thumbHeight()) * offset / maxOffset()); }
    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = localX(x), my = localY(y);
        if (button == 0 && mx >= LIST_X + LIST_W - 12 && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + LIST_H) {
            if (my < LIST_Y + 12) offset = Math.max(0, offset - 1);
            else if (my >= LIST_Y + LIST_H - 12) offset = Math.min(maxOffset(), offset + 1);
            else if (maxOffset() > 0) {
                dragging = true;
                dragOffset = my >= thumbY() && my < thumbY() + thumbHeight() ? my - thumbY() : thumbHeight() / 2D;
                dragTo(my);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    private void dragTo(double y) {
        double travel = Math.max(1, LIST_H - 24 - thumbHeight());
        offset = (int) Math.round(Math.clamp((y - dragOffset - LIST_Y - 12) / travel, 0D, 1D) * maxOffset());
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { dragTo(localY(y)); return true; }
        return super.mouseDragged(localX(x), localY(y), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(localX(x), localY(y), button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (localX(x) >= LIST_X && localX(x) < LIST_X + LIST_W && localY(y) >= LIST_Y && localY(y) < LIST_Y + LIST_H) {
            offset = Math.clamp(offset - (int) Math.signum(dy), 0, maxOffset());
            return true;
        }
        return super.mouseScrolled(localX(x), localY(y), dx, dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}
