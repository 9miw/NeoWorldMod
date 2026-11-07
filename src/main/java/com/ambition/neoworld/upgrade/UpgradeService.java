package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

/** Call only on the server thread. A future menu must validate table access before calling. */
public final class UpgradeService {
    public static final TagKey<Item> UPGRADEABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "upgradeable"));
    public enum Error { NONE, INVALID_PLAYER, INVALID_SLOT, STALE_ITEM, INVALID_ITEM, MAX_LEVEL,
        INVALID_DATA, MISSING_STONES, INVALID_PROTECTION, NOT_ENOUGH_SILVER, INVALID_BOOSTER }
    public record Attempt(Error error, UpgradeRules.Result result, long silverSpent, int stonesSpent) {
        static Attempt reject(Error error) { return new Attempt(error, null, 0, 0); }
    }

    private UpgradeService() {}

    public static boolean eligible(ItemStack stack) {
        Item item = stack.getItem();
        boolean equipment = item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof ArmorItem || stack.is(UPGRADEABLE);
        return !stack.isEmpty() && stack.getCount() == 1 && stack.getMaxStackSize() == 1 && equipment
                && (stack.is(UPGRADEABLE) || UpgradeConfig.EXTRA_ALLOWED_ITEMS.get()
                .contains(BuiltInRegistries.ITEM.getKey(item).toString()));
    }

    /** Expected stack is the server's preview snapshot, not trusted client metadata. Slots are main inventory only. */
    public static Attempt attempt(ServerPlayer player, int itemSlot, ItemStack expected, int stoneSlot,
                                  int protectionSlot, UpgradeRules.Protection protection) {
        return attemptInContainer(player, player.getInventory(), 36, itemSlot, expected, stoneSlot, protectionSlot, -1, protection);
    }

    static Attempt attemptInContainer(ServerPlayer player, net.minecraft.world.Container inventory, int limit,
                                      int itemSlot, ItemStack expected, int stoneSlot, int protectionSlot, int boosterSlot,
                                      UpgradeRules.Protection protection) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(player, "upgrade", "slot:" + itemSlot, "SINK")) {
            if (player.getServer() == null || !player.getServer().isSameThread()) {
                throw new IllegalStateException("Upgrade attempts must run on the server thread");
            }
            if (!player.isAlive() || player.isSpectator()) return Attempt.reject(Error.INVALID_PLAYER);
            if (itemSlot < 0 || itemSlot >= limit || stoneSlot < 0 || stoneSlot >= limit || itemSlot == stoneSlot) {
                return Attempt.reject(Error.INVALID_SLOT);
            }
            if (protection == null || (protection == UpgradeRules.Protection.NONE ? protectionSlot != -1
                    : protectionSlot < 0 || protectionSlot >= limit || protectionSlot == itemSlot || protectionSlot == stoneSlot)) {
                return Attempt.reject(Error.INVALID_PROTECTION);
            }
            if (protection != UpgradeRules.Protection.NONE && protection != UpgradeRules.Protection.FULL)
                return Attempt.reject(Error.INVALID_PROTECTION);
            if (boosterSlot != -1 && (boosterSlot < 0 || boosterSlot >= limit || boosterSlot == itemSlot
                    || boosterSlot == stoneSlot || boosterSlot == protectionSlot)) return Attempt.reject(Error.INVALID_BOOSTER);
            ItemStack equipment = inventory.getItem(itemSlot);
            if (expected == null || !ItemStack.matches(equipment, expected)) return Attempt.reject(Error.STALE_ITEM);
            if (!eligible(equipment)) return Attempt.reject(Error.INVALID_ITEM);
            int level = UpgradeData.level(equipment), failures = UpgradeData.failures(equipment);
            if (level < 0 || level > 15 || failures < 0) return Attempt.reject(Error.INVALID_DATA);
            if (level == 15) return Attempt.reject(Error.MAX_LEVEL);
            int target = level + 1;
            Item stone = target <= 6 ? ModItems.UPGRADE_STONE.get() : ModItems.ADVANCED_UPGRADE_STONE.get();
            int stoneCost = UpgradeConfig.stoneCost(target);
            long silverCost = UpgradeConfig.silverCost(target);
            ItemStack stones = inventory.getItem(stoneSlot);
            if (!stones.is(stone) || stones.getCount() < stoneCost) return Attempt.reject(Error.MISSING_STONES);
            ItemStack scroll = protection == UpgradeRules.Protection.NONE ? ItemStack.EMPTY : inventory.getItem(protectionSlot);
            if (protection != UpgradeRules.Protection.NONE && !scroll.is(ModItems.PROTECTION_SCROLL.get())) {
                return Attempt.reject(Error.INVALID_PROTECTION);
            }
            ItemStack booster = boosterSlot == -1 ? ItemStack.EMPTY : inventory.getItem(boosterSlot);
            if (boosterSlot != -1 && !booster.is(ModItems.SUCCESS_BOOSTER.get())) return Attempt.reject(Error.INVALID_BOOSTER);
            var chances = UpgradeConfig.chances(target);
            var data = player.getData(ModAttachments.PLAYER_DATA);
            if (data.getSilver() < silverCost) return Attempt.reject(Error.NOT_ENOUGH_SILVER);
            var result = UpgradeRules.resolve(level, failures, chances.success(), chances.unchanged(),
                    chances.downgrade(), chances.reset(), UpgradeRules.combinedBonus(UpgradeConfig.failureBonus(failures), !booster.isEmpty()),
                    protection, player.getRandom().nextInt(100));
            // Prepare the updated copy before charging. Existing enchantments and all other components are retained.
            ItemStack upgraded = equipment.copy();
            UpgradeData.set(upgraded, result.level(), result.failures());
            if (!data.removeSilver(silverCost)) return Attempt.reject(Error.NOT_ENOUGH_SILVER);
            stones.shrink(stoneCost);
            if (!scroll.isEmpty()) scroll.shrink(1);
            if (!booster.isEmpty()) booster.shrink(1);
            inventory.setItem(itemSlot, upgraded);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            PlayerEconomyManager.syncToClient(player);
            return new Attempt(Error.NONE, result, silverCost, stoneCost);
        }
    }
}
