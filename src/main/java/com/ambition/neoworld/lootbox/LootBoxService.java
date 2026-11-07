package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.lootbox.LootBoxOpenedPayload;
import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.registry.ModDataComponents;
import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public final class LootBoxService {
    private static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "loot_boxes/basic");
    private LootBoxService() {}

    public static boolean open(ServerPlayer player, InteractionHand hand) {
        return open(player, hand, false, LootBoxAutoRun.INTERVAL);
    }

    public static boolean openAutomatic(ServerPlayer player, InteractionHand hand, boolean fast) {
        return open(player, hand, true, fast ? LootBoxAutoRun.FAST_INTERVAL : LootBoxAutoRun.INTERVAL);
    }

    private static boolean open(ServerPlayer player, InteractionHand hand, boolean automatic, int cooldown) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.LOOT_BOX.get()) || !player.isAlive() || player.isSpectator()
                || player.getCooldowns().isOnCooldown(held.getItem())) return false;
        // Never overwrite an open container, crafting operation, or a quarantined inventory.
        if (player.containerMenu != player.inventoryMenu
                || com.ambition.neoworld.market.service.MarketDurability.isQuarantined(player)) return false;
        List<Stack<ItemStack>> before = new ArrayList<>();
        for (int i = 0; i < 36; i++) before.add(stack(player.getInventory().getItem(i)));
        int boxSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : -1;
        String boxId = held.get(ModDataComponents.LOOT_BOX_ID.get());
        List<LootBoxDefinitions.Entry> entries = null;
        if (boxId != null) {
            try { entries = LootBoxDefinitions.get(player.server).entries(boxId); }
            catch (IllegalArgumentException unknown) { return unconfigured(player, held); }
            if (entries.isEmpty()) return unconfigured(player, held);
        }
        int possibleRewards = entries == null ? 1 : LootBoxDefinitions.get(player.server).count(boxId);
        if (entries != null) {
            try { LootBoxRules.validateCount(entries.stream().map(LootBoxDefinitions.Entry::chance).toList(), possibleRewards); }
            catch (IllegalArgumentException invalid) { return unconfigured(player, held); }
        }
        if (!LootBoxInventoryPlan.hasRewardSlots(before, boxSlot, possibleRewards)) {
            player.displayClientMessage(Component.translatable("message.neoworld.loot_box.full"), true);
            player.getCooldowns().addCooldown(held.getItem(), 20);
            return false;
        }
        List<ItemStack> rewards = new ArrayList<>();
        if (entries == null) {
            var table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, TABLE));
            var params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.CHEST);
            rewards.addAll(table.getRandomItems(params));
            if (rewards.size() != 1) return unconfigured(player, held);
        } else {
            var selected = LootBoxRules.select(entries.stream().map(LootBoxDefinitions.Entry::chance).toList(), possibleRewards,
                    player.getRandom()::nextDouble);
            for (int index : selected) rewards.add(entries.get(index).item());
        }
        if (rewards.stream().anyMatch(item -> item.isEmpty() || item.getCount() > item.getMaxStackSize()))
            return unconfigured(player, held);
        List<Stack<ItemStack>> after;
        try {
            after = LootBoxInventoryPlan.plan(before, boxSlot,
                    stack(held.copyWithCount(1)), rewards.stream().map(LootBoxService::stack).toList(), ItemStack::isSameItemSameComponents);
        } catch (IllegalArgumentException full) {
            player.displayClientMessage(Component.translatable("message.neoworld.loot_box.full"), true);
            player.getCooldowns().addCooldown(held.getItem(), 20);
            return false;
        }
        ItemStack boxTemplate = held.copyWithCount(1);
        // A single server-thread exchange. No client packet can claim or reroll this result.
        if (hand == InteractionHand.OFF_HAND) held.shrink(1);
        for (int i = 0; i < 36; i++) {
            var slot = after.get(i);
            player.getInventory().setItem(i, slot.count() == 0 ? ItemStack.EMPTY : slot.identity().copyWithCount(slot.count()));
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.getCooldowns().addCooldown(ModItems.LOOT_BOX.get(), cooldown);
        for (var reward : rewards) com.ambition.neoworld.economy.analytics.EconomyAnalytics.coinIssued(player, reward,
                "loot_box_open", boxId == null ? TABLE.toString() : boxId);
        var opening = LootBoxAutoService.remember(player, hand, boxTemplate);
        PacketDistributor.sendToPlayer(player, new LootBoxOpenedPayload(opening, automatic, rewards));
        return true;
    }

    private static boolean unconfigured(ServerPlayer player, ItemStack held) {
        player.displayClientMessage(Component.translatable("message.neoworld.loot_box.unconfigured"), true);
        player.getCooldowns().addCooldown(held.getItem(), 20);
        return false;
    }

    private static Stack<ItemStack> stack(ItemStack item) {
        return new Stack<>(item.isEmpty() ? null : item.copyWithCount(1), item.getCount(),
                item.isEmpty() ? 64 : item.getMaxStackSize());
    }
}
