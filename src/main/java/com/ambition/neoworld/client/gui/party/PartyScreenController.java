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
        var leaders = payload.invites().stream().map(PartyScreenController::inviteKey).toList();
        presentedInvites.retainAll(leaders);
        if (!activeInvite.isEmpty()) return;
        payload.invites().stream().filter(invite -> !presentedInvites.contains(inviteKey(invite)))
                .findFirst().ifPresent(PartyScreenController::showInvite);
    }

    private static String inviteKey(PartyStatePayload.InviteEntry invite) {
        return (invite.joinRequest() ? "request:" : "invite:") + invite.leaderName();
    }

    private static void showInvite(PartyStatePayload.InviteEntry invite) {
        activeInvite = inviteKey(invite);
        presentedInvites.add(activeInvite);
        String mode = invite.capacity() == 3 ? "Trio" : "Raid";
        var accept = invite.joinRequest() ? PartyActionPayload.Action.ACCEPT_REQUEST : PartyActionPayload.Action.ACCEPT;
        var decline = invite.joinRequest() ? PartyActionPayload.Action.DECLINE_REQUEST : PartyActionPayload.Action.DECLINE;
        NeoPopupScreen.showCountdownChoice(Component.literal(invite.joinRequest() ? "คำขอเข้าปาร์ตี้" : "คำเชิญปาร์ตี้"),
                Component.literal(invite.leaderName() + (invite.joinRequest() ? " ขอเข้าปาร์ตี้ของคุณ\n" : " เชิญคุณเข้าปาร์ตี้\n") + mode + " | สมาชิก "
                        + invite.memberCount() + "/" + invite.capacity()),
                Math.max(1, invite.remainingSeconds()),
                Component.literal("ตอบรับ"), () -> respond(accept, invite.leaderName()),
                Component.literal("ปฏิเสธ"), () -> respond(decline, invite.leaderName()),
                () -> respond(decline, invite.leaderName()));
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
