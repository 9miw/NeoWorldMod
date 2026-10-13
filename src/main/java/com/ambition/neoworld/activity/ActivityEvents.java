package com.ambition.neoworld.activity;

import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ActivityEvents {
    private static final Map<MinecraftServer, Runtime> SERVERS = new IdentityHashMap<>();
    private ActivityEvents() {}
    public static Runtime runtime(MinecraftServer server) { return SERVERS.get(server); }
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        try {
            var rewards = ActivityRewards.load(); ActivityService.validate(rewards);
            SERVERS.put(event.getServer(), new Runtime(rewards));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid activity rewards in " + ActivityRewards.path() + "; fix the file before starting the server", exception);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && runtime(player.getServer()) != null)
            runtime(player.getServer()).sessions.put(player.getUUID(), new Session(player));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && runtime(player.getServer()) != null)
            runtime(player.getServer()).sessions.remove(player.getUUID());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var runtime = runtime(event.getServer());
        if (runtime != null && event.getServer().getTickCount() % 20 == 0)
            for (var player : event.getServer().getPlayerList().getPlayers()) runtime.sample(player);
    }
    public static final class Runtime {
        public final ActivityRewards rewards;
        private final Map<UUID, Session> sessions = new HashMap<>();
        Runtime(ActivityRewards rewards) { this.rewards = rewards; }
        public boolean allowRequest(ServerPlayer player) {
            var s = sessions.computeIfAbsent(player.getUUID(), ignored -> new Session(player));
            long now = System.nanoTime();
            if (s.lastRequest != 0 && now - s.lastRequest < 150_000_000L) return false;
            s.lastRequest = now; return true;
        }
        public boolean isAfk(ServerPlayer player) {
            var s = sessions.get(player.getUUID());
            return s != null && System.nanoTime() - s.lastActive >= rewards.afkSeconds() * 1_000_000_000L;
        }
        void sample(ServerPlayer player) {
            var s = sessions.computeIfAbsent(player.getUUID(), ignored -> new Session(player));
            long now = System.nanoTime();
            boolean moved = player.position().distanceToSqr(s.position) > 0.0001 || player.getYRot() != s.yaw
                    || player.getXRot() != s.pitch || player.swinging || player.isUsingItem();
            long elapsed = Math.max(0, Math.min(5_000_000_000L, now - s.lastSample));
            boolean activeBefore = now - s.lastActive < rewards.afkSeconds() * 1_000_000_000L;
            if (moved) s.lastActive = now;
            var data = player.getData(ModAttachments.ACTIVITY_DATA);
            long oldDay = data.day;
            if (data.roll(Instant.now()) && !player.isSpectator() && player.isAlive() && (moved || activeBefore)) {
                if (oldDay != data.day) s.fraction = 0; // Do not count time from the previous period.
                else s.fraction += elapsed;
                int seconds = (int) (s.fraction / 1_000_000_000L); s.fraction %= 1_000_000_000L;
                data.addOnlineSeconds(seconds, rewards.online());
            } else s.fraction = 0;
            s.lastSample = now; s.position = player.position(); s.yaw = player.getYRot(); s.pitch = player.getXRot();
        }
    }
    private static final class Session {
        Vec3 position;
        float yaw, pitch;
        long lastSample = System.nanoTime(), lastActive = lastSample, lastRequest, fraction;
        Session(ServerPlayer player) { position = player.position(); yaw = player.getYRot(); pitch = player.getXRot(); }
    }
}
