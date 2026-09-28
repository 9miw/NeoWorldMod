package com.ambition.neoworld.dungeon.room;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
}
