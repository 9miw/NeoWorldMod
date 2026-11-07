package com.ambition.neoworld.killer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.ambition.neoworld.mixin.CombatLogoutPlayerAccessor;
import com.ambition.neoworld.trade.service.TradeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Retains the real player, including inventory and attachments, after a combat disconnect. */
public final class CombatLogoutManager {
    private static final class Session {
        final CombatLogoutState<ServerPlayer> state = new CombatLogoutState<>();
        final Set<UUID> warned = ConcurrentHashMap.newKeySet();
        boolean stopping;
    }
    private static final Map<MinecraftServer, Session> SESSIONS = new ConcurrentHashMap<>();
    private CombatLogoutManager() {}
    private static Session session(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new Session()); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }

    public static void recordDamage(ServerPlayer attacker, ServerPlayer victim, boolean arenaCombat) {
        if (attacker instanceof FakePlayer || victim instanceof FakePlayer || attacker.isCreative() || victim.isCreative()
                || attacker.isSpectator() || victim.isSpectator()) return;
        session(victim.server).state.tag(attacker.getUUID(), victim.getUUID(), now(victim.server), arenaCombat);
    }

    public static boolean isRetained(ServerPlayer player) {
        var session = SESSIONS.get(player.server);
        var body = session == null ? null : session.state.body(player.getUUID());
        return body != null && body.player() == player;
    }

    public static boolean hasBody(MinecraftServer server, UUID id) {
        var session = SESSIONS.get(server);
        return session != null && session.state.body(id) != null;
    }

    /** Called only from the vanilla disconnect path, before disconnect()/PlayerList.remove(). */
    public static boolean begin(ServerPlayer player) {
        var session = session(player.server);
        if (com.ambition.neoworld.market.service.MarketDurability.isQuarantined(player)
                || session.stopping || !player.server.isRunning()
                || player.connection.isAcceptingMessages() || player.server.isSingleplayerOwner(player.getGameProfile())
                || player instanceof FakePlayer || player.isCreative() || player.isSpectator()) return false;
        KillerManager.flushDeaths(player.server);
        if (!player.isAlive() || player.isRemoved() || PvpRoomManager.get(player.server).room(player.getUUID()) != null) return false;
        if (!session.state.retain(player.getUUID(), player, now(player.server))) return false;
        // Stop client-owned interactions immediately; gameplay state remains on the one real entity.
        player.closeContainer();
        player.stopUsingItem();
        player.stopRiding();
        player.ejectPassengers();
        if (player.isSleeping()) player.stopSleepInBed(true, false);
        player.setDeltaMovement(Vec3.ZERO);
        TradeManager.get(player.server).disconnect(player.getUUID());
        PvpRoomManager.get(player.server).disconnect(player);
        return true;
    }

    /** Runs before PlayerList.load: persist/remove the old body, then load the latest state once. */
    public static void prepareJoin(MinecraftServer server, ServerPlayer incoming) {
        UUID id = incoming.getUUID();
        var session = session(server);
        var body = session.state.body(id);
        if (body != null) {
            long combatUntil = now(server) + session.state.remaining(id, now(server));
            boolean alive = body.player().isAlive();
            int invulnerability = ((CombatLogoutPlayerAccessor) body.player()).neoworld$getSpawnInvulnerableTime();
            finish(server, id);
            if (alive) {
                session.state.tagUntil(id, Math.max(combatUntil, body.expiresAt()));
                // ServerPlayer constructors grant 60 ticks of protection; reconnect is not a new life.
                ((CombatLogoutPlayerAccessor) incoming).neoworld$setSpawnInvulnerableTime(invulnerability);
            }
        }
        // Two authenticated configuration handshakes must not install the same account twice.
        if (server.getPlayerList().getPlayer(id) != null)
            throw new IllegalStateException("Player already joined during configuration");
    }

    public static void tick(MinecraftServer server) {
        var session = SESSIONS.get(server);
        if (session == null || session.stopping) return;
        for (var body : session.state.bodies()) {
            ServerPlayer player = body.player();
            if (player.isAlive() && !player.isRemoved() && now(server) < body.expiresAt()) {
                // Network listeners normally drive doTick; a disconnected listener no longer does.
                player.doTick();
                player.serverLevel().getChunkSource().move(player);
            }
            if (!player.isAlive() || player.isRemoved() || now(server) >= body.expiresAt()) finish(server, body.playerId());
        }
        if (server.getTickCount() % 20 == 0) {
            session.state.expireCombat(now(server));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isRetained(player) || !player.connection.isAcceptingMessages()) continue;
                int ticks = session.state.remaining(player.getUUID(), now(server));
                if (ticks > 0) {
                    session.warned.add(player.getUUID());
                    player.displayClientMessage(Component.translatable("message.neoworld.combat_logout.warning", (ticks + 19) / 20)
                            .withStyle(ChatFormatting.RED), true);
                } else if (session.warned.remove(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("message.neoworld.combat_logout.safe")
                            .withStyle(ChatFormatting.GREEN), true);
                }
            }
        }
    }

    private static void finish(MinecraftServer server, UUID id) {
        var session = SESSIONS.get(server);
        if (session == null) return;
        // Settle death/sin while all participating players are still in PlayerList.
        KillerManager.flushDeaths(server);
        var body = session.state.take(id);
        if (body == null) return;
        ServerPlayer player = body.player();
        player.disconnect();
        server.getPlayerList().remove(player); // Standard logout events, save, removal and tab cleanup, once.
    }

    public static void loggedOut(ServerPlayer player) {
        var session = SESSIONS.get(player.server);
        if (session == null) return;
        session.state.clearCombat(player.getUUID());
        session.warned.remove(player.getUUID());
    }

    public static void stopping(MinecraftServer server) {
        var session = session(server);
        session.stopping = true;
        for (var body : session.state.bodies()) finish(server, body.playerId());
        session.state.clear();
    }

    public static void stopped(MinecraftServer server) { SESSIONS.remove(server); }
}
