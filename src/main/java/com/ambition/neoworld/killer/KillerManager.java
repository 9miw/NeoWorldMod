package com.ambition.neoworld.killer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.economy.analytics.EconomyAnalytics;
import com.ambition.neoworld.network.killer.KillerStatePayload;
import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server manager coordinating combat tracking, sin accumulation, and death penalties. */
public final class KillerManager {
    private static final KillerCombatTracker COMBAT_TRACKER = new KillerCombatTracker();
    private static final Map<MinecraftServer, KillerDeathQueue<ServerPlayer>> DEATHS = new WeakHashMap<>();

    private KillerManager() {}

    public static KillerCombatTracker combatTracker() {
        return COMBAT_TRACKER;
    }

    public static boolean isRed(ServerPlayer player) {
        if (player == null) return false;
        return KillerRules.isRed(player.getData(ModAttachments.KILLER_DATA).getSin());
    }

    public static int getSin(ServerPlayer player) {
        if (player == null) return 0;
        return player.getData(ModAttachments.KILLER_DATA).getSin();
    }

    public static void recordDamage(ServerPlayer victim, DamageSource source, float damage) {
        if (victim == null || damage <= 0) return;
        ServerPlayer attacker = resolveAttacker(source);
        if (attacker != null && !attacker.getUUID().equals(victim.getUUID())) {
            long tick = victim.server.overworld().getGameTime();
            boolean arenaCombat = PvpRoomManager.get(victim.server).inArena(victim.getUUID())
                    || PvpRoomManager.get(victim.server).inArena(attacker.getUUID());
            COMBAT_TRACKER.recordDamage(attacker.getUUID(), victim.getUUID(), tick, arenaCombat);
            CombatLogoutManager.recordDamage(attacker, victim, arenaCombat);
        }
    }

    public static void observeDeath(ServerPlayer victim, DamageSource source, BooleanSupplier isCanceled) {
        if (victim == null) return;
        long tick = victim.server.overworld().getGameTime();
        // Capture combat facts now; later listeners, respawn, or another death may clear the tracker.
        ServerPlayer killer = resolveAttacker(source);
        if (killer == null) {
            var last = COMBAT_TRACKER.lastAttacker(victim.getUUID(), tick);
            if (last.isPresent()) {
                killer = victim.server.getPlayerList().getPlayer(last.get());
            }
        }
        boolean arenaCombat = PvpRoomManager.get(victim.server).inArena(victim.getUUID())
                || killer != null && PvpRoomManager.get(victim.server).inArena(killer.getUUID());
        if (arenaCombat) {
            COMBAT_TRACKER.clear(victim.getUUID());
            if (killer != null) COMBAT_TRACKER.clear(killer.getUUID());
            return;
        }
        boolean victimWasRed = isRed(victim);
        UUID killerId = killer == null ? null : killer.getUUID();
        boolean selfDefense = killer != null && COMBAT_TRACKER.canDefend(killerId, victim.getUUID(), tick);
        int sinToAdd = KillerRules.sinForKill(victimWasRed, selfDefense, false);
        DEATHS.computeIfAbsent(victim.server, ignored -> new KillerDeathQueue<>()).submit(victim, isCanceled,
                recipient -> commitDeath(recipient, killerId, victimWasRed, sinToAdd));
    }

    private static void commitDeath(ServerPlayer victim, UUID killerId, boolean victimWasRed, int sinToAdd) {
        if (victimWasRed) {
            PlayerData wallet = victim.getData(ModAttachments.PLAYER_DATA);
            long charged = chargeDeathPenalty(wallet, () -> {
                var scope = EconomyAnalytics.observe(victim, "killer_death_penalty", "", "SINK");
                return scope::close;
            });
            if (charged > 0) PlayerEconomyManager.syncToClient(victim);
        }
        ServerPlayer killer = killerId == null ? null : victim.server.getPlayerList().getPlayer(killerId);
        if (killer != null && !killer.getUUID().equals(victim.getUUID())) {
            if (sinToAdd > 0) {
                KillerData killerData = killer.getData(ModAttachments.KILLER_DATA);
                killerData.addKill();
                syncToTrackingAndSelf(killer);
            }
        }

        COMBAT_TRACKER.clear(victim.getUUID());
    }

    @FunctionalInterface
    interface Observation extends AutoCloseable {
        @Override void close();
    }

    static long chargeDeathPenalty(PlayerData wallet, Supplier<Observation> observation) {
        long penalty = KillerRules.deathPenalty(wallet.getSilver());
        if (penalty == 0) return 0;
        try (var ignored = observation.get()) {
            return wallet.removeSilver(penalty) ? penalty : 0;
        }
    }

    public static void flushDeaths(MinecraftServer server) {
        var queue = DEATHS.get(server);
        if (queue != null) queue.flush();
        if (server.getTickCount() % 20 == 0) COMBAT_TRACKER.expire(server.overworld().getGameTime());
    }

    public static void flushDeath(ServerPlayer original, ServerPlayer replacement) {
        var queue = DEATHS.get(original.server);
        if (queue != null) queue.flush(original, replacement);
    }

    public static void stop(MinecraftServer server) {
        var queue = DEATHS.remove(server);
        if (queue != null) queue.clear();
        COMBAT_TRACKER.clearAll();
    }

    public static void tick(ServerPlayer player) {
        if (player == null || !player.isAlive() || CombatLogoutManager.isRetained(player)) return;
        KillerData data = player.getData(ModAttachments.KILLER_DATA);
        if (data.getSin() <= 0) return;
        if (data.tickOnline(!FtbChunksKillerZones.isPvpProtected(player))) {
            syncToTrackingAndSelf(player);
        }
    }

    public static void syncTo(ServerPlayer subject, ServerPlayer receiver) {
        if (subject == null || receiver == null) return;
        int sin = subject.getData(ModAttachments.KILLER_DATA).getSin();
        PacketDistributor.sendToPlayer(receiver, new KillerStatePayload(subject.getUUID(), sin));
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        if (player == null) return;
        int sin = player.getData(ModAttachments.KILLER_DATA).getSin();
        KillerStatePayload payload = new KillerStatePayload(player.getUUID(), sin);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static ServerPlayer resolveAttacker(DamageSource source) {
        if (source == null) return null;
        var entity = source.getEntity();
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof TamableAnimal animal && animal.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        var direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            COMBAT_TRACKER.clear(playerId);
        }
    }
}
