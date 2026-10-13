package com.ambition.neoworld.world;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class WorldManagementEvents {
    private WorldManagementEvents() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        WorldManagementManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        WorldManagementManager.stop(event.getServer());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        WorldManagementManager.forget(event.getServer());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Pre event) {
        WorldManagementManager.get(event.getServer()).tick();
    }

    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        WorldManagementManager manager = WorldManagementManager.get(level.getServer());
        if (manager.isLocked(event.getDimension())
                || event.getEntity() instanceof ServerPlayer player && !manager.remember(player, event.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorldManagementManager.get(player.getServer()).recover(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorldManagementManager.get(player.getServer()).recordLogout(player);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WorldManagementManager.get(player.getServer()).recover(player);
    }
}
