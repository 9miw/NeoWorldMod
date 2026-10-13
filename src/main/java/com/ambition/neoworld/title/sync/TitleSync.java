package com.ambition.neoworld.title.sync;

import com.ambition.neoworld.title.service.TitleService;
import com.ambition.neoworld.title.TitleRegistry;

import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.TitleStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class TitleSync {
    private static final Map<MinecraftServer, Map<UUID, TitleStatePayload>> LAST = new WeakHashMap<>();
    private TitleSync() {}
    private static TitleStatePayload sample(ServerPlayer player) {
        var state = TitleService.get().cached(player.getUUID());
        if (state == null || !state.showTitle()) return TitleStatePayload.empty(player.getUUID());
        return TitleRegistry.find(state.equipped()).map(title -> new TitleStatePayload(
                player.getUUID(), title.id(), title.display(), title.rgb())).orElseGet(() -> TitleStatePayload.empty(player.getUUID()));
    }
    public static void send(ServerPlayer player) {
        var payload = sample(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        LAST.computeIfAbsent(player.getServer(), ignored -> new HashMap<>()).put(player.getUUID(), payload);
    }
    public static void clear() { LAST.clear(); }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!TitleService.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target)
            PacketDistributor.sendToPlayer(viewer, sample(target));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!TitleService.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, TitleStatePayload.empty(player.getUUID()));
            TitleService.get().load(player);
        }
    }
    @SubscribeEvent public static void stopTracking(PlayerEvent.StopTracking event) {
        if (!TitleService.isRunning()) return;
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target)
            PacketDistributor.sendToPlayer(viewer, TitleStatePayload.empty(target.getUUID()));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var cache = LAST.get(player.getServer());
            if (cache != null) cache.remove(player.getUUID());
            if (TitleService.isRunning()) TitleService.get().forget(player.getUUID());
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!TitleService.isRunning()) return;
        var server = event.getServer();
        if (server.getTickCount() % 5 != 0) return;
        var cache = LAST.computeIfAbsent(server, ignored -> new HashMap<>());
        for (var player : server.getPlayerList().getPlayers()) {
            if (server.getTickCount() % 100 == 0) TitleService.get().load(player);
            var payload = sample(player);
            if (!payload.equals(cache.put(player.getUUID(), payload)))
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
    }
}
