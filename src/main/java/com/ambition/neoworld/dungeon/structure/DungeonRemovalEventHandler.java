package com.ambition.neoworld.dungeon.structure;

import com.ambition.neoworld.NeoWorld;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class DungeonRemovalEventHandler {
    private DungeonRemovalEventHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DungeonRemovalManager.get(event.getServer()).tick();
    }
}
