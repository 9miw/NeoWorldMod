package com.ambition.neoworld.party;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.ambition.neoworld.network.PartyHudPayload;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyHudSync {
    private static final Map<MinecraftServer, Map<UUID, PartyHudPayload>> LAST_SENT = new WeakHashMap<>();

    private PartyHudSync() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        Map<UUID, PartyHudPayload> previous = LAST_SENT.computeIfAbsent(server, ignored -> new HashMap<>());
        Set<UUID> recipients = new HashSet<>();
        for (Party party : PartyManager.get(server).parties()) {
            PartyHudPayload snapshot = new PartyHudPayload(party.members().stream()
                    .map(member -> sample(server, member)).toList());
            for (PartyMember member : party.members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
                if (player == null) continue;
                recipients.add(member.playerId());
                if (!snapshot.equals(previous.get(member.playerId()))) {
                    PacketDistributor.sendToPlayer(player, snapshot);
                    previous.put(member.playerId(), snapshot);
                }
            }
        }
        previous.keySet().removeIf(id -> {
            if (recipients.contains(id)) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) PacketDistributor.sendToPlayer(player, PartyHudPayload.EMPTY);
            return true;
        });
    }

    public static void forget(MinecraftServer server, UUID playerId) {
        Map<UUID, PartyHudPayload> previous = LAST_SENT.get(server);
        if (previous != null) previous.remove(playerId);
    }

    private static PartyHudPayload.Member sample(MinecraftServer server, PartyMember member) {
        ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
        if (player == null) return new PartyHudPayload.Member(member.playerId(), member.playerName(), 0, 0, 0);
        float maximum = player.getMaxHealth();
        maximum = Float.isFinite(maximum) ? Math.max(0, maximum) : 0;
        float health = player.getHealth();
        health = Float.isFinite(health) ? Math.clamp(health, 0, maximum) : 0;
        return new PartyHudPayload.Member(member.playerId(), member.playerName(),
                player.getData(ModAttachments.PLAYER_DATA).getLevel(), health, maximum);
    }
}
