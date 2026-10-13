package com.ambition.neoworld.client.gui.party;

import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.network.party.PartyActionPayload;
import com.ambition.neoworld.network.party.PartyStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyScreenController {
    private static final java.util.Set<String> presentedInvites = new java.util.HashSet<>();
    private static String activeInvite = "";
    private PartyScreenController() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PartyScreen());
    }

    public static void receiveState(PartyStatePayload payload) {
        PartyScreen screen = NeoPopupScreen.findScreen(PartyScreen.class);
        if (screen != null) {
            screen.receiveState(payload);
        } else if (!payload.message().isEmpty() && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(payload.message()), false);
        }
        var leaders = payload.invites().stream().map(PartyStatePayload.InviteEntry::leaderName).toList();
        presentedInvites.retainAll(leaders);
        if (!activeInvite.isEmpty()) return;
        payload.invites().stream().filter(invite -> !presentedInvites.contains(invite.leaderName()))
                .findFirst().ifPresent(PartyScreenController::showInvite);
    }

    private static void showInvite(PartyStatePayload.InviteEntry invite) {
        activeInvite = invite.leaderName();
        presentedInvites.add(activeInvite);
        String mode = invite.capacity() == 3 ? "Trio" : "Raid";
        NeoPopupScreen.showCountdownChoice(Component.literal("คำเชิญปาร์ตี้"),
                Component.literal(invite.leaderName() + " เชิญคุณเข้าปาร์ตี้\n" + mode + " | สมาชิก "
                        + invite.memberCount() + "/" + invite.capacity()),
                Math.max(1, invite.remainingSeconds()),
                Component.literal("ตอบรับ"), () -> respond(PartyActionPayload.Action.ACCEPT, invite.leaderName()),
                Component.literal("ปฏิเสธ"), () -> respond(PartyActionPayload.Action.DECLINE, invite.leaderName()),
                () -> respond(PartyActionPayload.Action.DECLINE, invite.leaderName()));
    }

    private static void respond(PartyActionPayload.Action action, String leader) {
        activeInvite = "";
        PacketDistributor.sendToServer(new PartyActionPayload(action, leader));
    }

    public static void reset() {
        activeInvite = "";
        presentedInvites.clear();
    }
}
