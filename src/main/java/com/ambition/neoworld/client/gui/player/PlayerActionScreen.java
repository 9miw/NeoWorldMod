package com.ambition.neoworld.client.gui.player;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.player.PlayerActionPayload;
import com.ambition.neoworld.network.player.PlayerActionResultPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captures target identity once; crosshair changes never retarget an open menu. */
public final class PlayerActionScreen extends Screen {
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/buttons.png");
    private final UUID targetId;
    private final String targetName;
    private UUID pending;
    private PlayerActionPayload.Action pendingAction;
    private int waitTicks;
    private int x, y, panelWidth;

    public PlayerActionScreen(UUID targetId, String targetName) {
        super(Component.literal("แอคชั่นผู้เล่น"));
        this.targetId = targetId;
        this.targetName = targetName;
    }
    @Override protected void init() {
        panelWidth = Math.min(220, width - 16);
        x = (width - panelWidth) / 2; y = (height - 142) / 2;
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(x + panelWidth - 26, y + 5, 20, 20)
                .tooltip(Tooltip.create(Component.literal("ปิด")))).style(NeoButton.Style.DESTRUCTIVE));
        addAction("ดูโปรไฟล์", PlayerActionPayload.Action.PROFILE, 40);
        addAction("แลกของ", PlayerActionPayload.Action.TRADE, 70);
        addAction("เชิญเข้าปาร์ตี้", PlayerActionPayload.Action.INVITE, 100);
    }
    private void addAction(String label, PlayerActionPayload.Action action, int offset) {
        NeoButton button = addRenderableWidget(new NeoButton(Button.builder(Component.literal(label), b -> send(action))
                .bounds(x + 12, y + offset, panelWidth - 24, 24)));
        button.active = pending == null && (action == PlayerActionPayload.Action.PROFILE
                || minecraft.player == null || !targetId.equals(minecraft.player.getUUID()));
    }
    private void send(PlayerActionPayload.Action action) {
        if (pending != null) return;
        pending = UUID.randomUUID(); pendingAction = action; waitTicks = 0;
        rebuildWidgets();
        PacketDistributor.sendToServer(new PlayerActionPayload(pending, targetId, action));
    }
    public boolean acceptsProfile(com.ambition.neoworld.network.PlayerProfilePayload payload) {
        return pendingAction == PlayerActionPayload.Action.PROFILE && targetId.equals(payload.targetId()) && payload.requestId().equals(pending);
    }
    public static void receive(PlayerActionResultPayload result) {
        if (PlayerProfileScreen.receiveError(result)) return;
        PlayerActionScreen screen = NeoPopupScreen.findScreen(PlayerActionScreen.class);
        if (screen == null || !screen.targetId.equals(result.targetId()) || !result.requestId().equals(screen.pending)) return;
        var action = screen.pendingAction;
        screen.pending = null;
        screen.rebuildWidgets();
        if (action == PlayerActionPayload.Action.TRADE && result.success()) return; // Existing trade controller owns its popup.
        NeoPopupScreen.showAlert(Component.literal(result.success() ? (action == PlayerActionPayload.Action.PROFILE ? "โปรไฟล์ผู้เล่น" : "ปาร์ตี้") : "ทำรายการไม่ได้"),
                Component.literal(result.message()), () -> {});
    }
    @Override public void tick() {
        if (pending != null && ++waitTicks >= 200) {
            pending = null; rebuildWidgets();
            NeoPopupScreen.showAlert(Component.literal("หมดเวลารอ"), Component.literal("ไม่ได้รับคำตอบจากเซิร์ฟเวอร์"), () -> {});
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(x, y, x + panelWidth, y + 142, NeoGuiStyle.COLOR_FRAME_LIGHT);
        g.fill(x + 1, y + 1, x + panelWidth - 1, y + 141, NeoGuiStyle.COLOR_FRAME);
        g.fill(x + 3, y + 3, x + panelWidth - 3, y + 139, NeoGuiStyle.COLOR_PANEL);
        int plaqueWidth = Math.min(110, panelWidth - 32);
        RenderSystem.enableBlend();
        g.blit(PLAQUE, x + (panelWidth - plaqueWidth) / 2, y - 14, plaqueWidth, 20,
                28, 441, 350, 144, 1536, 1024);
        g.drawCenteredString(font, title, x + panelWidth / 2, y - 9, NeoGuiStyle.COLOR_TEXT);
        g.drawString(font, font.plainSubstrByWidth(targetName, panelWidth - 48), x + 12, y + 13, NeoGuiStyle.COLOR_GOLD, false);
        // Screen.render would render the world background again.
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
