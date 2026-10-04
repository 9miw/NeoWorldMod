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
    private final MinecraftServer server;

    private PartyManager(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized PartyManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, PartyManager::new);
    }

    public PartyActionResult create(ServerPlayer leader, int capacity) {
        if (capacity != 3 && capacity != 6) {
            return PartyActionResult.failure("ขนาดปาร์ตี้ต้องเป็น 3 หรือ 6 คน");
        }
        if (partyByPlayer.containsKey(leader.getUUID())) {
            return PartyActionResult.failure("คุณอยู่ในปาร์ตี้แล้ว");
        }
        Party party = new Party(UUID.randomUUID(), capacity, memberOf(leader));
        parties.put(party.id(), party);
        partyByPlayer.put(leader.getUUID(), party.id());
        return PartyActionResult.success(party, "สร้างปาร์ตี้สำเร็จ");
    }

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
        invitesByTarget
                .computeIfAbsent(target.getUUID(), ignored -> new LinkedHashMap<>())
                .put(leader.getUUID(), server.getTickCount() + 20L * 60L);
        return PartyActionResult.success(party, "ส่งคำเชิญสำเร็จ");
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
        party.addMember(memberOf(target));
        party.clearReady();
        partyByPlayer.put(target.getUUID(), party.id());
        invitesByTarget.remove(target.getUUID());
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
        party.clearReady();
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
        party.clearReady();
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
        Party party = findForPlayer(playerId).orElse(null);
        if (party == null) {
            return PartyActionResult.failure("คุณไม่ได้อยู่ในปาร์ตี้");
        }
        party.setReady(playerId, !party.isReady(playerId));
        return PartyActionResult.success(party, party.isReady(playerId) ? "พร้อมแล้ว" : "ยกเลิกพร้อมแล้ว");
    }

    public void handleDisconnect(UUID playerId) {
        findForPlayer(playerId).ifPresent(party -> party.setReady(playerId, false));
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
        return invites.keySet().stream()
                .map(leaderId -> {
                    Party party = findForPlayer(leaderId).orElse(null);
                    PartyMember leader = party == null ? null : party.members().stream()
                            .filter(member -> member.playerId().equals(leaderId))
                            .findFirst()
                            .orElse(null);
                    return party == null || leader == null
                            ? null
                            : new PendingInvite(leaderId, leader.playerName(), party.capacity(), party.memberCount());
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

    public record PendingInvite(UUID leaderId, String leaderName, int capacity, int memberCount) {
    }
}
