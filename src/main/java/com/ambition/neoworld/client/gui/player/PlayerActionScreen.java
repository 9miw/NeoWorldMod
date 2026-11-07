package com.ambition.neoworld.client.gui.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.input.PlayerActionKeyHandler;
import com.ambition.neoworld.network.PlayerProfilePayload;
import com.ambition.neoworld.network.player.PlayerActionPayload;
import com.ambition.neoworld.network.player.PlayerActionResultPayload;
import com.ambition.neoworld.client.gui.player.PlayerActionLayout.Rect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector4f;

/** One held screen owns UUID-bound panels for every nearby player. */
public final class PlayerActionScreen extends Screen {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/player/action_frame.png");
    private static final ResourceLocation BUTTONS = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/buttons.png");
    private final Map<UUID, Panel> panels = new LinkedHashMap<>();
    private int rotation, hiddenCount;

    private static final class Panel {
        final UUID target;
        String name;
        UUID pending;
        PlayerActionPayload.Action action;
        int waiting;
        float scale = 1;
        Rect bounds, body;
        NeoButton profile, invite;
        Panel(Player player) { target = player.getUUID(); name = player.getGameProfile().getName(); }
        int scaled(int value) { return Math.round(value * scale); }
        void visible(boolean visible) { if (profile != null) { profile.visible = visible; invite.visible = visible; } }
        void active() { if (profile != null) { profile.active = pending == null; invite.active = pending == null; } }
    }
    public PlayerActionScreen() { super(Component.literal("แอคชั่นผู้เล่น")); }
    private boolean validViewer() {
        return minecraft != null && minecraft.level != null && minecraft.player != null
                && minecraft.player.isAlive() && !minecraft.player.isSpectator();
    }
    private boolean validTarget(Player target) {
        return validViewer() && target != null && target != minecraft.player && !target.isRemoved()
                && target.isAlive() && !target.isSpectator() && target.distanceToSqr(minecraft.player) <= 25;
    }
    private Player target(Panel panel) { return minecraft.level == null ? null : minecraft.level.getPlayerByUUID(panel.target); }

    private boolean syncRoster() {
        if (!validViewer()) return false;
        boolean changed = panels.entrySet().removeIf(entry -> !validTarget(target(entry.getValue())));
        for (Player player : minecraft.level.players()) {
            if (!validTarget(player)) continue;
            Panel panel = panels.get(player.getUUID());
            if (panel == null) { panels.put(player.getUUID(), new Panel(player)); changed = true; }
            else panel.name = player.getGameProfile().getName();
        }
        return changed;
    }
    @Override protected void init() {
        syncRoster();
        for (Panel panel : panels.values()) {
            panel.bounds = null;
            panel.profile = addAction(panel, "ดูโปรไฟล์", PlayerActionPayload.Action.PROFILE);
            panel.invite = addAction(panel, "เชิญเข้าปาร์ตี้", PlayerActionPayload.Action.INVITE);
            panel.visible(false); panel.active();
        }
    }
    private NeoButton addAction(Panel panel, String label, PlayerActionPayload.Action action) {
        return addRenderableWidget(new NeoButton(Button.builder(Component.empty(), button -> send(panel, action))
                .createNarration(supplier -> Component.literal(panel.name + ": " + label)).bounds(0, 0, 156, 34)))
                .frameRenderer((graphics, button) -> renderAction(graphics, panel, button, label,
                        action == PlayerActionPayload.Action.PROFILE ? NeoSystemIcons.Icon.PROFILE : NeoSystemIcons.Icon.PARTY));
    }
    private void send(Panel panel, PlayerActionPayload.Action action) {
        if (!PlayerActionKeyHandler.isHeld() || panel.bounds == null || panel.pending != null || !validTarget(target(panel))) return;
        panel.pending = UUID.randomUUID(); panel.action = action; panel.waiting = 0; panel.active();
        PacketDistributor.sendToServer(new PlayerActionPayload(panel.pending, panel.target, action));
    }
    public boolean acceptsProfile(PlayerProfilePayload payload) {
        Panel panel = panels.get(payload.targetId());
        return panel != null && PlayerActionKeyHandler.isHeld() && validTarget(target(panel))
                && panel.action == PlayerActionPayload.Action.PROFILE && payload.requestId().equals(panel.pending);
    }
    public static void receive(PlayerActionResultPayload result) {
        if (PlayerProfileScreen.receiveError(result)) return;
        PlayerActionScreen screen = NeoPopupScreen.findScreen(PlayerActionScreen.class);
        if (screen == null || !PlayerActionKeyHandler.isHeld() || !screen.validViewer()) return;
        Panel panel = screen.panels.get(result.targetId());
        if (panel == null || !result.requestId().equals(panel.pending) || !screen.validTarget(screen.target(panel))) return;
        panel.pending = null; panel.active();
        NeoPopupScreen.showAlert(Component.literal(panel.name), Component.literal(result.message()), () -> {});
    }
    @Override public void tick() {
        if (!PlayerActionKeyHandler.isHeld() || !validViewer()) { onClose(); return; }
        if (syncRoster()) rebuildWidgets();
        for (Panel panel : panels.values()) {
            if (panel.pending != null && ++panel.waiting >= 200) {
                panel.pending = null; panel.active();
                minecraft.player.displayClientMessage(Component.literal(panel.name + ": หมดเวลารอคำตอบจากเซิร์ฟเวอร์"), false);
            }
        }
    }
    private Vector4f project(Vec3 position, RenderLevelStageEvent event) {
        Vec3 relative = position.subtract(event.getCamera().getPosition());
        return new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1)
                .mul(event.getModelViewMatrix()).mul(event.getProjectionMatrix());
    }
    private Rect projectBody(Player player, RenderLevelStageEvent event) {
        Vec3 position = player.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vector4f center = project(position.add(0, player.getBbHeight() * 0.7, 0), event);
        if (center.w <= 0 || Math.abs(center.x) > center.w || Math.abs(center.y) > center.w) {
            // Off-camera players remain reachable at a matching edge instead of disappearing.
            float divisor = Math.max(0.01F, Math.abs(center.w));
            int x = Math.round((center.x / divisor + 1) * width / 2F);
            int y = Math.round((1 - center.y / divisor) * height / 2F);
            if (center.w <= 0) x = center.x >= 0 ? width - 8 : 8;
            return new Rect(Math.clamp(x, 8, width - 8), Math.clamp(y, 8, Math.max(8, height - 70)), 1, 1);
        }
        AABB box = player.getBoundingBox().move(position.subtract(player.position())).inflate(0.08);
        float left = width, right = 0, top = height, bottom = 0;
        for (int corner = 0; corner < 8; corner++) {
            Vector4f p = project(new Vec3((corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY, (corner & 4) == 0 ? box.minZ : box.maxZ), event);
            if (p.w <= 0) return new Rect(0, 0, width, height); // Camera intersects the body: keep panels off it.
            float x = (p.x / p.w + 1) * width / 2F, y = (1 - p.y / p.w) * height / 2F;
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        int x = (int) Math.floor(left), y = (int) Math.floor(top);
        return new Rect(x, y, (int) Math.ceil(right) - x, (int) Math.ceil(bottom) - y);
    }
    public void updateAnchor(RenderLevelStageEvent event) {
        if (!PlayerActionKeyHandler.isHeld() || !validViewer()) { onClose(); return; }
        if (syncRoster()) rebuildWidgets();
        List<Panel> ordered = new ArrayList<>(panels.values());
        ordered.sort(Comparator.<Panel>comparingDouble(p -> target(p).distanceToSqr(minecraft.player)).thenComparing(p -> p.target));
        List<Rect> obstacles = new ArrayList<>();
        obstacles.add(hintBounds(true));
        for (Panel panel : ordered) {
            panel.bounds = null; panel.visible(false);
            panel.body = projectBody(target(panel), event); obstacles.add(panel.body);
        }
        hiddenCount = 0;
        int start = ordered.isEmpty() ? 0 : Math.floorMod(rotation, ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Panel panel = ordered.get((start + i) % ordered.size());
            float viewport = Math.clamp(width / 640F, 0.75F, 1F);
            panel.scale = viewport * (float) (1.2 - Math.min(5, target(panel).distanceTo(minecraft.player)) * 0.06);
            int w = Math.max(panel.scaled(180), font.width("เชิญเข้าปาร์ตี้") + panel.scaled(72));
            int h = panel.scaled(142);
            panel.bounds = PlayerActionLayout.place(panel.body, w, h, width, height - 70, obstacles);
            if (panel.bounds == null) { hiddenCount++; continue; }
            obstacles.add(panel.bounds);
            positionButton(panel, panel.profile, 50); positionButton(panel, panel.invite, 94);
            panel.visible(true); panel.active();
        }
    }
    private void positionButton(Panel panel, NeoButton button, int offset) {
        button.setPosition(panel.bounds.x() + panel.scaled(12), panel.bounds.y() + panel.scaled(offset));
        button.setWidth(panel.bounds.width() - panel.scaled(24)); button.setHeight(panel.scaled(34));
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (PlayerActionKeyHandler.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (PlayerActionKeyHandler.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return PlayerActionKeyHandler.isHeld() && super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (hiddenCount > 0 && vertical != 0) { rotation += vertical > 0 ? 1 : -1; return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!PlayerActionKeyHandler.isHeld()) return;
        RenderSystem.enableBlend();
        for (Panel panel : panels.values()) {
            if (panel.bounds == null) continue;
            Rect b = panel.bounds;
            sliced(g, FRAME, b.x(), b.y(), b.width(), b.height(), 88, 134,
                    new int[]{100, 1080, 100}, new int[]{220, 468, 100},
                    panel.scaled(14), panel.scaled(42), panel.scaled(14), 1456, 1080);
            int textY = b.y() + (panel.scaled(42) - font.lineHeight * 2 - 2) / 2;
            g.drawCenteredString(font, title, b.x() + b.width() / 2, textY, NeoGuiStyle.COLOR_TEXT);
            g.drawCenteredString(font, font.plainSubstrByWidth(panel.name, b.width() - panel.scaled(24)),
                    b.x() + b.width() / 2, textY + font.lineHeight + 2, NeoGuiStyle.COLOR_GOLD);
        }
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        renderHint(g);
    }
    private void renderAction(GuiGraphics g, Panel panel, NeoButton button, String label, NeoSystemIcons.Icon icon) {
        boolean hover = button.active && button.isHoveredOrFocused();
        int state = !button.active ? 3 : hover ? 1 : 0;
        int[] sourceX = {28, 407, 784, 1162}, sourceWidth = {350, 347, 350, 348};
        int edge = panel.scaled(8);
        sliced(g, BUTTONS, button.getX(), button.getY(), button.getWidth(), button.getHeight(), sourceX[state], hover ? 441 : 168,
                new int[]{44, sourceWidth[state] - 88, 44}, new int[]{44, 56, 44}, edge, edge, edge, 1536, 1024);
        int size = panel.scaled(20);
        NeoSystemIcons.render(g, icon, button.getX() + panel.scaled(12), button.getY() + (button.getHeight() - size) / 2, size, button.active);
        int textX = button.getX() + panel.scaled(40), textWidth = button.getWidth() - panel.scaled(48);
        String text = font.plainSubstrByWidth(label, textWidth);
        g.drawString(font, text, textX + (textWidth - font.width(text)) / 2, button.getY() + (button.getHeight() - font.lineHeight) / 2,
                !button.active ? NeoGuiStyle.COLOR_DISABLED : hover ? 0xFFCAFFFF : NeoGuiStyle.COLOR_TEXT, false);
    }
    private static void sliced(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height,
                               int u, int v, int[] sourceWidths, int[] sourceHeights,
                               int edge, int top, int bottom, int textureWidth, int textureHeight) {
        int[] widths = {edge, width - edge * 2, edge}, heights = {top, height - top - bottom, bottom};
        int dy = y, sv = v;
        for (int row = 0; row < 3; row++) {
            int dx = x, su = u;
            for (int col = 0; col < 3; col++) {
                if (widths[col] > 0 && heights[row] > 0) g.blit(texture, dx, dy, widths[col], heights[row], su, sv, sourceWidths[col], sourceHeights[row], textureWidth, textureHeight);
                dx += widths[col]; su += sourceWidths[col];
            }
            dy += heights[row]; sv += sourceHeights[row];
        }
    }
    private int hintWidth() { return Math.min(300, width / 2 - 116); }
    private Component hint() {
        Component base = PlayerActionKeyHandler.holdHint();
        if (panels.isEmpty()) return base.copy().append(" • ไม่มีผู้เล่นใกล้เคียง");
        if (hiddenCount > 0) return base.copy().append(" • ซ่อน " + hiddenCount + "/" + panels.size() + " แผง: เลื่อนเมาส์เพื่อสลับ");
        return base;
    }
    private Rect hintBounds(boolean maximum) {
        int available = hintWidth() >= 80 ? hintWidth() : Math.max(1, width - 24);
        Component content = maximum ? PlayerActionKeyHandler.holdHint().copy()
                .append(" • ซ่อน " + panels.size() + "/" + panels.size() + " แผง: เลื่อนเมาส์เพื่อสลับ") : hint();
        var lines = font.split(content, available);
        int boxWidth = 0;
        for (var line : lines) boxWidth = Math.max(boxWidth, font.width(line));
        int boxHeight = lines.size() * (font.lineHeight + 2) + 8;
        return new Rect(width - boxWidth - 17, height - (hintWidth() >= 80 ? 8 : 70) - boxHeight,
                boxWidth + 10, boxHeight);
    }
    private void renderHint(GuiGraphics g) {
        int available = hintWidth() >= 80 ? hintWidth() : Math.max(1, width - 24);
        var lines = font.split(hint(), available);
        Rect box = hintBounds(false);
        int lineHeight = font.lineHeight + 2;
        g.fill(box.x(), box.y(), box.x() + box.width(), box.y() + box.height(), 0xBB101419);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), box.x() + 5,
                box.y() + 4 + i * lineHeight, NeoGuiStyle.COLOR_TEXT, false);
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
}
