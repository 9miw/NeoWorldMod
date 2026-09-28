package com.ambition.neoworld.experience;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.item.ExperienceItem;
import com.ambition.neoworld.level.PlayerLevelManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ExperienceEventHandler {
    private ExperienceEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 5 == 0) {
            processInventoryExperience(player);
        }
    }

    @SubscribeEvent
    public static void onExperiencePickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity == null || !itemEntity.isAlive() || itemEntity.hasPickUpDelay()) return;
        if (!(itemEntity.getItem().getItem() instanceof ExperienceItem)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) return;

        ItemStack stack = itemEntity.getItem();
        long value = ExperienceItem.getRandomValue(stack);
        value = value > 0L && stack.getCount() > Long.MAX_VALUE / value
                ? Long.MAX_VALUE : value * stack.getCount();
        if (value <= 0L) return;

        consume(player, stack, value);
        itemEntity.discard();
        event.setCanPickup(TriState.FALSE);
    }

    private static void processInventoryExperience(ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof ExperienceItem)) continue;

            long value = ExperienceItem.getRandomValue(stack);
            if (value <= 0L) continue;
            if (value > Long.MAX_VALUE / stack.getCount()) value = Long.MAX_VALUE;
            else value *= stack.getCount();

            consume(player, stack, value);
            inventory.setItem(i, ItemStack.EMPTY);
        }

        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.getItem() instanceof ExperienceItem) {
                long value = ExperienceItem.getRandomValue(carried);
                if (value > 0L) {
                    if (value > Long.MAX_VALUE / carried.getCount()) value = Long.MAX_VALUE;
                    else value *= carried.getCount();
                    consume(player, carried, value);
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                }
            }
        }
    }

    private static void consume(ServerPlayer player, ItemStack stack, long value) {
        PlayerLevelManager.addExp(player, value);
        player.displayClientMessage(
                Component.literal("§b+" + String.format("%,d", value) + " EXP"), true);
    }
}
