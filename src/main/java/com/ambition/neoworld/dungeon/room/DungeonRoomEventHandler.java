package com.ambition.neoworld.dungeon.room;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class DungeonRoomEventHandler {
    private DungeonRoomEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRoomManager.get(player.getServer()).handleDisconnect(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRoomManager.get(player.getServer()).handleDeath(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            for (String deathKey : new String[] { DungeonRoomManager.DUNGEON_DEATH, DungeonRoomManager.DEATH_EXIT }) {
                if (event.getOriginal().getPersistentData().getBoolean(deathKey)) {
                    event.getEntity().getPersistentData().putBoolean(deathKey, true);
                }
            }
        }
        String key = DungeonRoomManager.SPECTATOR_RETURN_MODE;
        var original = event.getOriginal().getPersistentData();
        if (original.contains(key, Tag.TAG_INT)) {
            event.getEntity().getPersistentData().putInt(key, original.getInt(key));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRoomManager.get(player.getServer()).handlePlayerReady(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonRoomManager.get(player.getServer()).handlePlayerReady(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DungeonRoomManager.get(event.getServer()).tick();
    }

    public static boolean isDungeonDeath(ServerPlayer player) {
        return !player.isAlive() && player.getPersistentData().getBoolean(DungeonRoomManager.DUNGEON_DEATH);
    }

    public static void requestDeathExit(ServerPlayer player) {
        if (isDungeonDeath(player)) player.getPersistentData().putBoolean(DungeonRoomManager.DEATH_EXIT, true);
    }
}
