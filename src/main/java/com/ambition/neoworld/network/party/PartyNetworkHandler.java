package com.ambition.neoworld.network.party;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import com.ambition.neoworld.dungeon.instance.DungeonRuntime;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.experience.MonsterExperienceConfig;
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

    public static void sendState(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, stateFor(player, "", true));
    }

    public static void handleAction(PartyActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_DUNGEON_USE, 0)) {
            context.reply(empty("ไม่มีสิทธิ์ใช้งานปาร์ตี้", false));
            return;
        }

        PartyActionResult result = switch (payload.action()) {
            case REFRESH, BROWSE_ALL, BROWSE_GENERAL, BROWSE_DUNGEON -> PartyActionResult.success(
                    PartyManager.get(player.getServer()).findForPlayer(player.getUUID()).orElse(null), "");
            case CREATE_TRIO, CREATE_DUNGEON_TRIO -> isBusy(player)
                    ? PartyActionResult.failure("คุณอยู่ในห้องหรือ Dungeon Instance แล้ว")
                    : PartyManager.get(player.getServer()).create(player, 3,
                            payload.action() == PartyActionPayload.Action.CREATE_TRIO ? Party.Purpose.GENERAL : Party.Purpose.DUNGEON);
            case CREATE_RAID, CREATE_DUNGEON_RAID -> isBusy(player)
                    ? PartyActionResult.failure("คุณอยู่ในห้องหรือ Dungeon Instance แล้ว")
                    : PartyManager.get(player.getServer()).create(player, 6,
                            payload.action() == PartyActionPayload.Action.CREATE_RAID ? Party.Purpose.GENERAL : Party.Purpose.DUNGEON);
            case PURPOSE_GENERAL, PURPOSE_DUNGEON -> partyHasRoom(player)
                    ? PartyActionResult.failure("เปลี่ยนจุดประสงค์ไม่ได้ขณะอยู่ในห้องดันเจี้ยน")
                    : PartyManager.get(player.getServer()).changePurpose(player.getUUID(),
                            payload.action() == PartyActionPayload.Action.PURPOSE_GENERAL ? Party.Purpose.GENERAL : Party.Purpose.DUNGEON);
            case REQUEST_JOIN -> requestJoin(player, payload.target());
            case ALLOW_INVITES, BLOCK_INVITES -> PartyManager.get(player.getServer()).setAcceptsInvites(
                    player, payload.action() == PartyActionPayload.Action.ALLOW_INVITES);
            case ACCEPT_REQUEST, DECLINE_REQUEST -> answerJoin(player, payload.target(),
                    payload.action() == PartyActionPayload.Action.ACCEPT_REQUEST);
            case INVITE -> invite(player, payload.target(), payload.inviteRange());
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
        int page = switch (payload.action()) {
            case BROWSE_ALL, BROWSE_GENERAL, BROWSE_DUNGEON -> parsePage(payload.target());
            default -> -1;
        };
        Party.Purpose filter = switch (payload.action()) {
            case BROWSE_GENERAL -> Party.Purpose.GENERAL;
            case BROWSE_DUNGEON -> Party.Purpose.DUNGEON;
            default -> null;
        };
        context.reply(stateFor(player, result.message(), result.successful(), filter, page));
    }

    private static int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static PartyActionResult requestJoin(ServerPlayer player, String target) {
        UUID id;
        try {
            id = UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            return PartyActionResult.failure("ไม่พบปาร์ตี้");
        }
        var server = player.getServer();
        var manager = PartyManager.get(server);
        var party = manager.find(id).orElse(null);
        var leader = party == null ? null : server.getPlayerList().getPlayer(party.leaderId());
        if (leader == null || isBusy(player) || partyLocked(server, party)) {
            return PartyActionResult.failure("ปาร์ตี้นี้ยังไม่สามารถรับสมาชิกได้");
        }
        var result = manager.requestJoin(player, party);
        if (result.successful()) sendState(leader);
        return result;
    }

    private static PartyActionResult answerJoin(ServerPlayer leader, String target, boolean accept) {
        var server = leader.getServer();
        var requester = server.getPlayerList().getPlayerByName(target);
        if (requester == null) return PartyActionResult.failure("ผู้ขอเข้าร่วมไม่ได้ออนไลน์");
        if (accept && (isBusy(requester) || partyHasRoom(leader)
                || !ModPermissions.hasPermission(requester.createCommandSourceStack(), ModPermissions.COMMAND_DUNGEON_USE, 0))) {
            return PartyActionResult.failure("ผู้เล่นหรือปาร์ตี้ไม่พร้อมเข้าร่วมแล้ว");
        }
        var result = PartyManager.get(server).answerJoin(leader, requester, accept);
        if (result.successful()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(requester,
                    stateFor(requester, accept ? "เข้าร่วมปาร์ตี้สำเร็จ" : "หัวหน้าปาร์ตี้ปฏิเสธคำขอ", accept));
            if (accept) for (var member : result.party().members()) {
                var player = server.getPlayerList().getPlayer(member.playerId());
                if (player != null && player != requester && player != leader) sendState(player);
            }
        }
        return result;
    }

    private static PartyActionResult invite(ServerPlayer player, String targetName, int range) {
        if (partyHasRoom(player)) return PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว");
        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) return PartyActionResult.failure("ไม่พบผู้เล่นออนไลน์: " + targetName);
        if (player.serverLevel() != target.serverLevel() || player.distanceToSqr(target) > (double) range * range) {
            return PartyActionResult.failure("ผู้เล่นไม่ได้อยู่ในโลกเดียวกันหรืออยู่นอกระยะ " + range + " บล็อก");
        }
        return inviteTarget(player, target);
    }

    public static PartyActionResult inviteTarget(ServerPlayer player, ServerPlayer target) {
        if (partyHasRoom(player)) return PartyActionResult.failure("ปาร์ตี้ถูกผูกกับห้องดันเจี้ยนแล้ว");
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
        return party != null && partyLocked(player.getServer(), party);
    }

    private static boolean partyLocked(net.minecraft.server.MinecraftServer server, Party party) {
        return party.members().stream().anyMatch(member ->
                DungeonRoomManager.get(server).findForPlayer(member.playerId()).isPresent()
                || DungeonRuntime.get(server).getForPlayer(member.playerId()).isPresent());
    }

    private static PartyStatePayload stateFor(ServerPlayer viewer, String message, boolean success) {
        return stateFor(viewer, message, success, null, -1);
    }

    private static PartyStatePayload stateFor(ServerPlayer viewer, String message, boolean success,
                                              Party.Purpose filter, int requestedPage) {
        PartyManager manager = PartyManager.get(viewer.getServer());
        Party party = manager.findForPlayer(viewer.getUUID()).orElse(null);
        List<PartyStatePayload.MemberEntry> members = party == null ? List.of() : party.members().stream()
                .map(member -> memberEntry(viewer, party, member))
                .toList();
        var invites = new ArrayList<>(manager.invitesFor(viewer.getUUID()).stream()
                .map(invite -> new PartyStatePayload.InviteEntry(
                        invite.leaderName(), invite.capacity(), invite.memberCount(), invite.remainingSeconds()))
                .toList());
        if (party != null) for (var request : manager.joinRequestsFor(viewer.getUUID())) {
            var requester = viewer.getServer().getPlayerList().getPlayer(request.requesterId());
            if (requester != null) invites.add(new PartyStatePayload.InviteEntry(requester.getGameProfile().getName(),
                    party.capacity(), party.memberCount(), (int) ((request.expiresAt() - viewer.getServer().getTickCount() + 19) / 20), true));
        }
        var matching = requestedPage < 0 ? List.<Party>of() : manager.parties().stream()
                .filter(candidate -> filter == null || candidate.purpose() == filter).toList();
        int size = PartyStatePayload.BROWSE_PAGE_SIZE;
        int pages = Math.max(1, (matching.size() + size - 1) / size);
        int page = Math.min(requestedPage, pages - 1);
        var parties = page < 0 ? List.<PartyStatePayload.PartyEntry>of() : matching.stream()
                .skip((long) page * size).limit(size).map(candidate -> {
                    boolean locked = partyLocked(viewer.getServer(), candidate);
                    return new PartyStatePayload.PartyEntry(leaderName(candidate), candidate.capacity(),
                            candidate.memberCount(), !locked && !candidate.isFull(), candidate.id(),
                            candidate.leaderId(), candidate.purpose(), locked);
                }).toList();
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
                success,
                MonsterExperienceConfig.sharesPartyExperience(viewer.serverLevel().dimension().location()),
                party == null ? Party.Purpose.GENERAL : party.purpose(), page, pages,
                PartyManager.acceptsInvites(viewer)
        );
    }

    private static PartyStatePayload empty(String message, boolean success) {
        return new PartyStatePayload(false, 0, "", List.of(), List.of(), List.of(), List.of(), message, success, false);
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
                false // Reserved legacy field; readiness belongs to DungeonRoom.
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
        int distance = (int) Math.ceil(Math.sqrt(candidate.distanceToSqr(viewer)));
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
        } else if (!PartyManager.acceptsInvites(candidate)) {
            status = "ปิดรับคำเชิญ";
            available = false;
        } else if (manager.hasPendingInvite(viewer.getUUID(), candidate.getUUID())) {
            status = PartyStatePayload.INVITE_SENT;
            available = false;
        } else {
            status = "ว่าง";
            available = true;
        }
        return new PartyStatePayload.NearbyPlayerEntry(
                candidate.getGameProfile().getName(), level, distance, status, available);
    }
}
