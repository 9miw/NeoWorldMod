package com.ambition.neoworld.party;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PartyManager {
    private static final Map<MinecraftServer, PartyManager> MANAGERS = new WeakHashMap<>();

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyByPlayer = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, Long>> invitesByTarget = new LinkedHashMap<>();
    private final Map<UUID, JoinRequest> joinRequests = new LinkedHashMap<>();
    private final MinecraftServer server;

    private PartyManager(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized PartyManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, PartyManager::new);
    }

    public PartyActionResult create(ServerPlayer leader, int capacity) {
        return create(leader, capacity, Party.Purpose.GENERAL);
    }

    public PartyActionResult create(ServerPlayer leader, int capacity, Party.Purpose purpose) {
        if (capacity != 3 && capacity != 6) {
            return PartyActionResult.failure("ขนาดปาร์ตี้ต้องเป็น 3 หรือ 6 คน");
        }
        if (partyByPlayer.containsKey(leader.getUUID())) {
            return PartyActionResult.failure("คุณอยู่ในปาร์ตี้แล้ว");
        }
        Party party = new Party(UUID.randomUUID(), capacity, memberOf(leader));
        party.setPurpose(purpose);
        parties.put(party.id(), party);
        partyByPlayer.put(leader.getUUID(), party.id());
        return PartyActionResult.success(party, "สร้างปาร์ตี้สำเร็จ");
    }

    public PartyActionResult changePurpose(UUID playerId, Party.Purpose purpose) {
        Party party = findForPlayer(playerId).orElse(null);
        if (party == null || !party.leaderId().equals(playerId)) {
            return PartyActionResult.failure("เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่เปลี่ยนจุดประสงค์ได้");
        }
        party.setPurpose(purpose);
        return PartyActionResult.success(party, "เปลี่ยนจุดประสงค์แล้ว");
    }

    public PartyActionResult requestJoin(ServerPlayer requester, Party party) {
        if (partyByPlayer.containsKey(requester.getUUID()) || party.isFull()) {
            return PartyActionResult.failure("คุณมีปาร์ตี้แล้วหรือปาร์ตี้นี้เต็ม");
        }
        pruneJoinRequests();
        if (joinRequests.containsKey(requester.getUUID())) {
            return PartyActionResult.failure("คุณมีคำขอที่รอการตอบรับอยู่แล้ว");
        }
        if (joinRequests.values().stream().filter(request -> request.partyId().equals(party.id())).count() >= 12) {
            return PartyActionResult.failure("ปาร์ตี้นี้มีคำขอรอตอบรับเต็มแล้ว");
        }
        joinRequests.put(requester.getUUID(), new JoinRequest(requester.getUUID(), party.id(), party.leaderId(),
                server.getTickCount() + 1200L));
        return PartyActionResult.success(party, "ส่งคำขอเข้าร่วมแล้ว");
    }

    public java.util.List<JoinRequest> joinRequestsFor(UUID leaderId) {
        pruneJoinRequests();
        return joinRequests.values().stream().filter(request -> request.leaderId().equals(leaderId)).toList();
    }

    public PartyActionResult answerJoin(ServerPlayer leader, ServerPlayer requester, boolean accept) {
        pruneJoinRequests();
        JoinRequest request = joinRequests.get(requester.getUUID());
        if (request == null || !request.leaderId().equals(leader.getUUID())) {
            return PartyActionResult.failure("ไม่พบคำขอหรือคำขอหมดอายุแล้ว");
        }
        Party party = parties.get(request.partyId());
        joinRequests.remove(requester.getUUID());
        if (!accept) return PartyActionResult.success(party, "ปฏิเสธคำขอแล้ว");
        if (party.isFull() || partyByPlayer.containsKey(requester.getUUID())) {
            return PartyActionResult.failure("คุณไม่สามารถรับผู้เล่นนี้เข้าปาร์ตี้ได้แล้ว");
        }
        return join(requester, party);
    }

    private void pruneJoinRequests() {
        joinRequests.values().removeIf(request -> {
            Party party = parties.get(request.partyId());
            return request.expiresAt() <= server.getTickCount() || party == null
                    || !party.leaderId().equals(request.leaderId()) || partyByPlayer.containsKey(request.requesterId());
        });
    }

    public record JoinRequest(UUID requesterId, UUID partyId, UUID leaderId, long expiresAt) {}

    public PartyActionResult invite(ServerPlayer leader, ServerPlayer target) {
        Party party = findForPlayer(leader.getUUID()).orElse(null);
        if (party == null) {
            return PartyActionResult.failure("คุณยังไม่มีปาร์ตี้");
        }
        if (!party.leaderId().equals(leader.getUUID())) {
            return PartyActionResult.failure("เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่เชิญได้");
        }
        if (party.isFull()) {
            return PartyActionResult.failure("ปาร์ตี้เต็มแล้ว");
        }
        if (party.contains(target.getUUID())) {
            return PartyActionResult.failure("ผู้เล่นนี้อยู่ในปาร์ตี้แล้ว");
        }
        if (partyByPlayer.containsKey(target.getUUID())) {
            return PartyActionResult.failure("ผู้เล่นนี้อยู่ในปาร์ตี้อื่นแล้ว");
        }
        if (!acceptsInvites(target)) {
            return PartyActionResult.failure("ผู้เล่นนี้ปิดรับคำเชิญปาร์ตี้");
        }
        if (hasPendingInvite(leader.getUUID(), target.getUUID())) {
            return PartyActionResult.failure("ส่งคำเชิญให้ผู้เล่นนี้แล้ว");
        }
        invitesByTarget
                .computeIfAbsent(target.getUUID(), ignored -> new LinkedHashMap<>())
                .put(leader.getUUID(), server.getTickCount() + 20L * 60L);
        com.ambition.neoworld.network.party.PartyNetworkHandler.sendState(target);
        return PartyActionResult.success(party, "ส่งคำเชิญสำเร็จ");
    }

    public static boolean acceptsInvites(ServerPlayer player) {
        return player.getData(com.ambition.neoworld.registry.ModAttachments.PARTY_INVITES_ENABLED);
    }

    public boolean hasPendingInvite(UUID leaderId, UUID targetId) {
        var invites = invitesByTarget.get(targetId);
        Long deadline = invites == null ? null : invites.get(leaderId);
        return deadline != null && deadline > server.getTickCount();
    }

    public PartyActionResult setAcceptsInvites(ServerPlayer player, boolean enabled) {
        player.setData(com.ambition.neoworld.registry.ModAttachments.PARTY_INVITES_ENABLED, enabled);
        if (!enabled) invitesByTarget.remove(player.getUUID());
        return PartyActionResult.success(findForPlayer(player.getUUID()).orElse(null),
                enabled ? "เปิดรับคำเชิญปาร์ตี้แล้ว" : "ปิดรับคำเชิญปาร์ตี้แล้ว");
    }

    public PartyActionResult accept(ServerPlayer target, ServerPlayer leader) {
        Map<UUID, Long> invites = invitesByTarget.get(target.getUUID());
        if (invites == null || !invites.containsKey(leader.getUUID())) {
            return PartyActionResult.failure("ไม่พบคำเชิญจากผู้เล่นนี้");
        }
        if (invites.get(leader.getUUID()) < server.getTickCount()) {
            invites.remove(leader.getUUID());
            return PartyActionResult.failure("คำเชิญหมดอายุแล้ว");
        }
        if (partyByPlayer.containsKey(target.getUUID())) {
            return PartyActionResult.failure("คุณอยู่ในปาร์ตี้แล้ว");
        }
        Party party = findForPlayer(leader.getUUID()).orElse(null);
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            return PartyActionResult.failure("ปาร์ตี้ของผู้เชิญไม่พร้อมใช้งานแล้ว");
        }
        if (party.isFull()) {
            return PartyActionResult.failure("ปาร์ตี้เต็มแล้ว");
        }
        return join(target, party);
    }

    private PartyActionResult join(ServerPlayer target, Party party) {
        party.addMember(memberOf(target));
        partyByPlayer.put(target.getUUID(), party.id());
        invitesByTarget.remove(target.getUUID());
        joinRequests.remove(target.getUUID());
        return PartyActionResult.success(party, "เข้าร่วมปาร์ตี้สำเร็จ");
    }

    public PartyActionResult decline(ServerPlayer target, ServerPlayer leader) {
        Map<UUID, Long> invites = invitesByTarget.get(target.getUUID());
        if (invites == null || invites.remove(leader.getUUID()) == null) {
            return PartyActionResult.failure("ไม่พบคำเชิญจากผู้เล่นนี้");
        }
        if (invites.isEmpty()) {
            invitesByTarget.remove(target.getUUID());
        }
        return PartyActionResult.success(findForPlayer(leader.getUUID()).orElse(null), "ปฏิเสธคำเชิญแล้ว");
    }

    public PartyActionResult leave(UUID playerId) {
        Party party = findForPlayer(playerId).orElse(null);
        if (party == null) {
            return PartyActionResult.failure("คุณไม่ได้อยู่ในปาร์ตี้");
        }
        party.removeMember(playerId);
        partyByPlayer.remove(playerId);
        if (party.isEmpty()) {
            parties.remove(party.id());
            return PartyActionResult.success(party, "ออกจากปาร์ตี้แล้ว");
        }
        return PartyActionResult.success(party, "ออกจากปาร์ตี้แล้ว");
    }

    public PartyActionResult kick(UUID leaderId, UUID targetId) {
        Party party = findForPlayer(leaderId).orElse(null);
        if (party == null) {
            return PartyActionResult.failure("คุณยังไม่มีปาร์ตี้");
        }
        if (!party.leaderId().equals(leaderId)) {
            return PartyActionResult.failure("เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่เตะได้");
        }
        if (leaderId.equals(targetId)) {
            return PartyActionResult.failure("หัวหน้าปาร์ตี้ต้องใช้คำสั่งออกหรือยุบปาร์ตี้");
        }
        if (!party.contains(targetId)) {
            return PartyActionResult.failure("ผู้เล่นนี้ไม่ได้อยู่ในปาร์ตี้");
        }
        party.removeMember(targetId);
        partyByPlayer.remove(targetId);
        return PartyActionResult.success(party, "เตะสมาชิกออกจากปาร์ตี้แล้ว");
    }

    public PartyActionResult disband(UUID leaderId) {
        Party party = findForPlayer(leaderId).orElse(null);
        if (party == null) {
            return PartyActionResult.failure("คุณยังไม่มีปาร์ตี้");
        }
        if (!party.leaderId().equals(leaderId)) {
            return PartyActionResult.failure("เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่ยุบปาร์ตี้ได้");
        }
        party.members().forEach(member -> partyByPlayer.remove(member.playerId()));
        parties.remove(party.id());
        return PartyActionResult.success(party, "ยุบปาร์ตี้แล้ว");
    }

    public PartyActionResult toggleReady(UUID playerId) {
        var result = com.ambition.neoworld.dungeon.room.DungeonRoomManager.get(server).toggleReady(playerId);
        return result.successful() ? PartyActionResult.success(findForPlayer(playerId).orElse(null), result.message())
                : PartyActionResult.failure(result.message());
    }

    public PartyActionResult handleDisconnect(UUID playerId) {
        com.ambition.neoworld.dungeon.room.DungeonRoomManager.get(server).clearPlayerReady(playerId);
        invitesByTarget.remove(playerId);
        joinRequests.remove(playerId);
        joinRequests.values().removeIf(request -> request.leaderId().equals(playerId));
        invitesByTarget.values().removeIf(invites -> {
            invites.remove(playerId);
            return invites.isEmpty();
        });
        return leave(playerId);
    }

    public java.util.List<PendingInvite> invitesFor(UUID targetId) {
        Map<UUID, Long> invites = invitesByTarget.get(targetId);
        if (invites == null || invites.isEmpty()) return java.util.List.of();
        long now = server.getTickCount();
        invites.entrySet().removeIf(entry -> entry.getValue() < now);
        if (invites.isEmpty()) {
            invitesByTarget.remove(targetId);
            return java.util.List.of();
        }
        return invites.entrySet().stream()
                .map(entry -> {
                    UUID leaderId = entry.getKey();
                    Party party = findForPlayer(leaderId).orElse(null);
                    PartyMember leader = party == null ? null : party.members().stream()
                            .filter(member -> member.playerId().equals(leaderId))
                            .findFirst()
                            .orElse(null);
                    return party == null || leader == null
                            ? null
                            : new PendingInvite(leaderId, leader.playerName(), party.capacity(), party.memberCount(),
                                    (int) Math.min(Integer.MAX_VALUE,
                                            Math.max(0L, (entry.getValue() - now + 19L) / 20L)));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public Optional<Party> findForPlayer(UUID playerId) {
        UUID partyId = partyByPlayer.get(playerId);
        return partyId == null ? Optional.empty() : Optional.ofNullable(parties.get(partyId));
    }

    public Optional<Party> find(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    public Collection<Party> parties() {
        return java.util.List.copyOf(parties.values());
    }

    private static PartyMember memberOf(ServerPlayer player) {
        return new PartyMember(player.getUUID(), player.getGameProfile().getName());
    }

    public record PendingInvite(UUID leaderId, String leaderName, int capacity, int memberCount,
                                int remainingSeconds) {
    }
}
