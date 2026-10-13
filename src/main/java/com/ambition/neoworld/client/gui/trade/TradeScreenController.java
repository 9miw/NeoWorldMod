package com.ambition.neoworld.client.gui.trade;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.network.TradeActionPayload;
import com.ambition.neoworld.network.TradeActionPayload.Action;
import com.ambition.neoworld.network.TradeStatePayload;
import com.ambition.neoworld.network.TradeStatePayload.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class TradeScreenController {
    private static TradeStatePayload state;
    private static Screen ownedPopup, requestParent;
    private static TradeScreen tradeScreen;
    private static int clientTicks;
    private static int generation;
    private static UUID countdownSession;
    private static int countdownRevision = -1;
    private static boolean awaitingAcceptance;
    private static boolean locallyCancelled;
    private static boolean applyAfterOpen;
    private TradeScreenController() {}

    public static void receive(TradeStatePayload next) {
        Minecraft mc = Minecraft.getInstance();
        if (state != null && !state.sessionId().equals(next.sessionId())) return;
        if (state != null && next.revision() < state.revision()) return;
        if (next.phase() == Phase.CANCELLED || next.phase() == Phase.COMMITTED) {
            if (state == null) return;
            closeOwned(mc); reset();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(next.message()), false);
            return;
        }
        if (locallyCancelled) return;
        boolean first = state == null;
        state = next;
        if (next.phase() == Phase.REQUESTED) {
            if (!first) return;
            requestParent = mc.screen;
            UUID id = next.sessionId(); int revision = next.revision();
            if (next.requester()) {
                popup(() -> NeoPopupScreen.showCountdown(Component.literal("คำขอแลกเปลี่ยน"),
                        Component.literal("รอ " + next.partner() + " ยอมรับคำขอ"), seconds(next), Component.literal("ยกเลิก"),
                        () -> sendFor(id, revision, Action.CANCEL), () -> sendFor(id, revision, Action.CANCEL)));
            } else {
                popup(() -> NeoPopupScreen.showCountdownChoice(Component.literal("คำขอแลกเปลี่ยน"),
                        Component.literal(next.partner() + " ขอแลกเปลี่ยนกับคุณ"), seconds(next),
                        Component.literal("ยอมรับ"), () -> sendFor(id, revision, Action.ACCEPT),
                        Component.literal("ปฏิเสธ"), () -> sendFor(id, revision, Action.CANCEL),
                        () -> sendFor(id, revision, Action.CANCEL)));
            }
            return;
        }
        // Vanilla's server-opened menu creates the screen; a feature snapshot must not create a local container.
        if (tradeScreen == null || !tradeScreen.getMenu().belongsTo(next.sessionId())) return;
        if (mc.player == null || mc.player.containerMenu != tradeScreen.getMenu()
                || NeoPopupScreen.findScreen(TradeScreen.class) != tradeScreen) {
            cancel(); return; // A queued snapshot must not reopen a menu closed by vanilla hotkeys.
        }
        {
            tradeScreen.update(next);
            if (next.phase() == Phase.EDITING && ownedPopup != null) {
                if (mc.screen == ownedPopup) mc.setScreen(tradeScreen);
                ownedPopup = null; generation++;
            }
        }
        if (next.phase() == Phase.COUNTDOWN
                && (!next.sessionId().equals(countdownSession) || next.revision() != countdownRevision)) {
            countdownSession = next.sessionId(); countdownRevision = next.revision();
            UUID id = next.sessionId(); int revision = next.revision();
            popup(() -> NeoPopupScreen.showCountdownChoice(Component.literal("กำลังแลกเปลี่ยน"),
                    Component.literal("ทั้งสองฝ่ายยืนยันแล้ว กำลังรอเซิร์ฟเวอร์โอนของ"), seconds(next),
                    Component.literal("ถอนยืนยัน"), () -> sendFor(id, revision, Action.UNCONFIRM),
                    Component.literal("ยกเลิก"), () -> sendFor(id, revision, Action.CANCEL), () -> {}));
        }
        if (!next.message().isEmpty() && mc.player != null) mc.player.displayClientMessage(Component.literal(next.message()), false);
    }
    public static void attach(TradeScreen screen) {
        tradeScreen = screen; ownedPopup = null; generation++;
        if (state != null && screen.getMenu().belongsTo(state.sessionId()) && state.phase() != Phase.REQUESTED) screen.update(state);
        applyAfterOpen = true;
    }
    private static int seconds(TradeStatePayload state) { return Math.max(1, (state.remainingTicks() + 19) / 20); }
    private static void popup(Runnable show) {
        Minecraft mc = Minecraft.getInstance(); int token = ++generation;
        show.run();
        mc.execute(() -> { if (token == generation && state != null && mc.screen instanceof NeoPopupScreen) ownedPopup = mc.screen; });
    }
    private static void sendFor(UUID id, int revision, Action action) {
        if (state != null && state.sessionId().equals(id)) {
            if (action == Action.ACCEPT) awaitingAcceptance = true;
            if (action == Action.CANCEL) locallyCancelled = true;
            PacketDistributor.sendToServer(new TradeActionPayload(id, revision, action, -1, 0, 0));
        }
    }
    public static void send(Action action, int slot, int count, long silver) {
        if (state != null) PacketDistributor.sendToServer(new TradeActionPayload(state.sessionId(), state.revision(), action, slot, count, silver));
    }
    public static void cancel() { locallyCancelled = true; send(Action.CANCEL, -1, 0, 0); }
    private static void closeOwned(Minecraft mc) {
        if (mc.screen == ownedPopup || (tradeScreen != null && NeoPopupScreen.findScreen(TradeScreen.class) == tradeScreen)) {
            mc.setScreen(tradeScreen == null ? requestParent : null);
        }
        ownedPopup = null; generation++;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        clientTicks++;
        if (state == null) return;
        if (applyAfterOpen && tradeScreen != null && NeoPopupScreen.findScreen(TradeScreen.class) == tradeScreen
                && state.phase() != Phase.REQUESTED) {
            applyAfterOpen = false; receive(state);
        }
        if (locallyCancelled) { if (clientTicks % 20 == 0) send(Action.CANCEL, -1, 0, 0); return; }
        if (state.phase() == Phase.REQUESTED) {
            if (ownedPopup != null && Minecraft.getInstance().screen != ownedPopup && !awaitingAcceptance && clientTicks % 20 == 0) cancel();
            return;
        }
        if (NeoPopupScreen.findScreen(TradeScreen.class) != tradeScreen) {
            if (clientTicks % 20 == 0) cancel();
        } else if (clientTicks % 20 == 0) send(Action.HEARTBEAT, -1, 0, 0);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    private static void reset() {
        state = null; tradeScreen = null; ownedPopup = null; requestParent = null;
        countdownSession = null; countdownRevision = -1; generation++;
        awaitingAcceptance = false;
        locallyCancelled = false;
        applyAfterOpen = false;
    }
}
