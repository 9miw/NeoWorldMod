package com.ambition.neoworld.pvp.room;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class PvpRoomEvents {
    private PvpRoomEvents() { }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { PvpRoomManager.get(event.getServer()).tick(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { PvpRoomManager.get(event.getServer()).shutdown(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { PvpRoomManager.stop(event.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PvpRoomManager.get(player.getServer()).disconnect(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var manager=PvpRoomManager.get(player.getServer()); manager.recover(player); manager.sendQueueState(player);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PvpRoomManager.get(player.getServer()).recover(player);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var manager = PvpRoomManager.get(player.getServer());
            if (manager.inArena(player.getUUID())) {
                event.setCanceled(true);
                player.setHealth(1);
                manager.death(player);
            }
        }
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var manager = PvpRoomManager.get(level.getServer());
        var attacker = event.getSource().getEntity();
        var target = event.getEntity();
        boolean targetIn = target instanceof ServerPlayer player && manager.inArena(player.getUUID());
        boolean attackerIn = attacker instanceof ServerPlayer player && manager.inArena(player.getUUID());
        if (!targetIn && !attackerIn) return;
        if (targetIn && manager.room(target.getUUID()).state() == PvpRoom.State.COUNTDOWN) { event.setCanceled(true); return; }
        if (attacker != null && (!targetIn || !attackerIn || !manager.canFight(attacker.getUUID(), target.getUUID()))) event.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && PvpRoomManager.get(player.getServer()).inArena(player.getUUID())) event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PvpRoomManager.get(player.getServer()).inArena(player.getUUID())) event.setCanceled(true);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && PvpRoomManager.get(player.getServer()).inArena(player.getUUID())) event.setCanceled(true);
    }
}
