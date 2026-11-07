package com.ambition.neoworld.killer;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class CombatLogoutEvents {
    private CombatLogoutEvents() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void tick(ServerTickEvent.Post event) { CombatLogoutManager.tick(event.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) { CombatLogoutManager.stopping(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CombatLogoutManager.stopped(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CombatLogoutManager.loggedOut(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CombatLogoutManager.loggedOut(player);
    }
}
