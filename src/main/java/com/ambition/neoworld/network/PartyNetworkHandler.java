package com.ambition.neoworld.network;

import java.util.List;

import com.ambition.neoworld.dungeon.DungeonRuntime;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.party.Party;
import com.ambition.neoworld.party.PartyActionResult;
import com.ambition.neoworld.party.PartyManager;
import com.ambition.neoworld.party.PartyMember;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PartyNetworkHandler {
    private PartyNetworkHandler() {
    }

    public static void handleAction(PartyActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_DUNGEON_USE, 0)) {
            context.reply(empty("ไม่มีสิทธิ์ใช้งานปาร์ตี้", false));
            return;
        }

        PartyActionResult result = switch (payload.action()) {
            case REFRESH -> PartyActionResult.success(
                    PartyManager.get(player.getServer()).findForPlayer(player.getUUID()).orElse(null), "");
            case CREATE_TRIO -> isBusy(player)
                    ? PartyActionResult.failure("คุณอยู่ในห้องหรือ Dungeon Instance แล้ว")
                    : PartyManager.get(player.getServer()).create(player, 3);
            case CREATE_RAID -> isBusy(player)
                    ? PartyActionResult.failure("คุณอยู่ในห้องหรือ Dungeon Instance แล้ว")
                    : PartyManager.get(player.getServer()).create(player, 6);
            case INVITE -> invite(player, payload.target());
            case ACCEPT -> accept(player, payload.target());
            case DECLINE -> decline(player, payload.target());
            case LEAVE -> partyHasRoom(player)
                    ? PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว")
                    : PartyManager.get(player.getServer()).leave(player.getUUID());
            case KICK -> kick(player, payload.target());
            case DISBAND -> partyHasRoom(player)
                    ? PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว")
                    : PartyManager.get(player.getServer()).disband(player.getUUID());
            case READY -> PartyManager.get(player.getServer()).toggleReady(player.getUUID());
        };
        context.reply(stateFor(player, result.message(), result.successful()));
    }

    private static PartyActionResult invite(ServerPlayer player, String targetName) {
        if (partyHasRoom(player)) return PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว");
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) return PartyActionResult.failure("ไม่พบผู้เล่นออนไลน์: " + targetName);
        if (isBusy(target)) return PartyActionResult.failure("ผู้เล่นนี้อยู่ในห้องหรือ Dungeon Instance แล้ว");
        PartyActionResult result = PartyManager.get(player.getServer()).invite(player, target);
        if (result.successful()) {
            target.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[NeoWorld] "
                    + player.getGameProfile().getName() + " เชิญคุณเข้าปาร์ตี้"));
        }
        return result;
    }

    private static PartyActionResult accept(ServerPlayer player, String leaderName) {
        if (isBusy(player)) return PartyActionResult.failure("คุณอยู่ในห้องหรือ Dungeon Instance แล้ว");
        ServerPlayer leader = player.getServer().getPlayerList().getPlayerByName(leaderName);
        if (leader == null) return PartyActionResult.failure("ไม่พบหัวหน้าปาร์ตี้ออนไลน์: " + leaderName);
        if (partyHasRoom(leader)) return PartyActionResult.failure("ปาร์ตี้นี้ถูกผูกกับห้องดันเจี้ยนแล้ว");
        return PartyManager.get(player.getServer()).accept(player, leader);
    }

    private static PartyActionResult decline(ServerPlayer player, String leaderName) {
        ServerPlayer leader = player.getServer().getPlayerList().getPlayerByName(leaderName);
        if (leader == null) return PartyActionResult.failure("ไม่พบหัวหน้าปาร์ตี้ออนไลน์: " + leaderName);
        return PartyManager.get(player.getServer()).decline(player, leader);
    }

    private static PartyActionResult kick(ServerPlayer player, String targetName) {
        if (partyHasRoom(player)) return PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว");
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) return PartyActionResult.failure("ไม่พบผู้เล่นออนไลน์: " + targetName);
        return PartyManager.get(player.getServer()).kick(player.getUUID(), target.getUUID());
    }

    private static boolean isBusy(ServerPlayer player) {
        return DungeonRoomManager.get(player.getServer()).findForPlayer(player.getUUID()).isPresent()
                || DungeonRuntime.get(player.getServer()).getForPlayer(player.getUUID()).isPresent();
    }

    private static boolean partyHasRoom(ServerPlayer player) {
        Party party = PartyManager.get(player.getServer()).findForPlayer(player.getUUID()).orElse(null);
        return party != null && party.members().stream()
                .anyMatch(member -> DungeonRoomManager.get(player.getServer())
                        .findForPlayer(member.playerId())
                        .isPresent());
    }

    private static PartyStatePayload stateFor(ServerPlayer viewer, String message, boolean success) {
        PartyManager manager = PartyManager.get(viewer.getServer());
        Party party = manager.findForPlayer(viewer.getUUID()).orElse(null);
        List<PartyStatePayload.MemberEntry> members = party == null ? List.of() : party.members().stream()
                .map(member -> memberEntry(viewer, party, member))
                .toList();
        List<PartyStatePayload.InviteEntry> invites = manager.invitesFor(viewer.getUUID()).stream()
                .map(invite -> new PartyStatePayload.InviteEntry(
                        invite.leaderName(), invite.capacity(), invite.memberCount()))
                .toList();
        List<PartyStatePayload.PartyEntry> parties = manager.parties().stream()
                .map(candidate -> new PartyStatePayload.PartyEntry(
                        leaderName(candidate),
                        candidate.capacity(),
                        candidate.memberCount(),
                        !candidate.isFull()))
                .toList();
        List<PartyStatePayload.NearbyPlayerEntry> nearbyPlayers = viewer.serverLevel().players().stream()
                .filter(candidate -> !candidate.getUUID().equals(viewer.getUUID()))
                .filter(candidate -> candidate.distanceToSqr(viewer) <= 64.0D * 64.0D)
                .sorted(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(viewer)))
                .limit(32)
                .map(candidate -> nearbyEntry(viewer, manager, candidate))
                .toList();
        return new PartyStatePayload(
                party != null,
                party == null ? 0 : party.capacity(),
                party == null ? "" : leaderName(party),
                members,
                invites,
                parties,
                nearbyPlayers,
                message,
                success
        );
    }

    private static PartyStatePayload empty(String message, boolean success) {
        return new PartyStatePayload(false, 0, "", List.of(), List.of(), List.of(), List.of(), message, success);
    }

    private static PartyStatePayload.MemberEntry memberEntry(ServerPlayer viewer, Party party, PartyMember member) {
        ServerPlayer player = viewer.getServer().getPlayerList().getPlayer(member.playerId());
        int level = player == null ? 0 : player.getData(ModAttachments.PLAYER_DATA).getLevel();
        return new PartyStatePayload.MemberEntry(
                member.playerId(),
                member.playerName(),
                level,
                player != null,
                member.playerId().equals(party.leaderId()),
                party.isReady(member.playerId())
        );
    }

    private static String leaderName(Party party) {
        return party.members().stream()
                .filter(member -> member.playerId().equals(party.leaderId()))
                .map(PartyMember::playerName)
                .findFirst()
                .orElse("unknown");
    }

    private static PartyStatePayload.NearbyPlayerEntry nearbyEntry(
            ServerPlayer viewer,
            PartyManager manager,
            ServerPlayer candidate
    ) {
        int level = candidate.getData(ModAttachments.PLAYER_DATA).getLevel();
        int distance = (int) Math.round(Math.sqrt(candidate.distanceToSqr(viewer)));
        boolean inParty = manager.findForPlayer(candidate.getUUID()).isPresent();
        boolean inRoom = DungeonRoomManager.get(viewer.getServer()).findForPlayer(candidate.getUUID()).isPresent();
        boolean inDungeon = DungeonRuntime.get(viewer.getServer()).getForPlayer(candidate.getUUID()).isPresent();
        String status;
        boolean available;
        if (inDungeon || inRoom) {
            status = "อยู่ดันเจี้ยน";
            available = false;
        } else if (inParty) {
            status = "มีปาร์ตี้แล้ว";
            available = false;
        } else {
            status = "ว่าง";
            available = true;
        }
        return new PartyStatePayload.NearbyPlayerEntry(
                candidate.getGameProfile().getName(), level, distance, status, available);
    }
}
