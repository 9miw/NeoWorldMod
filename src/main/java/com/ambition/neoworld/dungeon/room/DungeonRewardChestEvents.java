package com.ambition.neoworld.dungeon.room;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Routes the shared reward block to a server-owned inventory for each member. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class DungeonRewardChestEvents {
    private DungeonRewardChestEvents() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && DungeonRoomManager.get(player.getServer()).openRewardChest(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && DungeonRoomManager.get(level.getServer()).isRewardChest(level, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
