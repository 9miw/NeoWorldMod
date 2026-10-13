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
        INVALID_DATA, MISSING_STONES, INVALID_PROTECTION, NOT_ENOUGH_GOLD }
    public record Attempt(Error error, UpgradeRules.Result result, long goldSpent, int stonesSpent) {
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
        if (player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("Upgrade attempts must run on the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) return Attempt.reject(Error.INVALID_PLAYER);
        if (itemSlot < 0 || itemSlot >= 36 || stoneSlot < 0 || stoneSlot >= 36 || itemSlot == stoneSlot) {
            return Attempt.reject(Error.INVALID_SLOT);
        }
        if (protection == null || (protection == UpgradeRules.Protection.NONE ? protectionSlot != -1
                : protectionSlot < 0 || protectionSlot >= 36 || protectionSlot == itemSlot || protectionSlot == stoneSlot)) {
            return Attempt.reject(Error.INVALID_PROTECTION);
        }
        var inventory = player.getInventory();
        ItemStack equipment = inventory.getItem(itemSlot);
        if (expected == null || !ItemStack.matches(equipment, expected)) return Attempt.reject(Error.STALE_ITEM);
        if (!eligible(equipment)) return Attempt.reject(Error.INVALID_ITEM);
        int level = UpgradeData.level(equipment), failures = UpgradeData.failures(equipment);
        if (level < 0 || level > 15 || failures < 0) return Attempt.reject(Error.INVALID_DATA);
        if (level == 15) return Attempt.reject(Error.MAX_LEVEL);
        int target = level + 1;
        Item stone = target <= 6 ? ModItems.UPGRADE_STONE.get() : target <= 12
                ? ModItems.ADVANCED_UPGRADE_STONE.get() : ModItems.SUPREME_UPGRADE_STONE.get();
        int stoneCost = UpgradeConfig.stoneCost(target);
        long goldCost = UpgradeConfig.goldCost(target);
        ItemStack stones = inventory.getItem(stoneSlot);
        if (!stones.is(stone) || stones.getCount() < stoneCost) return Attempt.reject(Error.MISSING_STONES);
        ItemStack scroll = protection == UpgradeRules.Protection.NONE ? ItemStack.EMPTY : inventory.getItem(protectionSlot);
        if (protection != UpgradeRules.Protection.NONE && !scroll.is(protection == UpgradeRules.Protection.DOWNGRADE
                ? ModItems.DOWNGRADE_PROTECTION_SCROLL.get() : ModItems.BREAK_PROTECTION_SCROLL.get())) {
            return Attempt.reject(Error.INVALID_PROTECTION);
        }
        var chances = UpgradeConfig.chances(target);
        var data = player.getData(ModAttachments.PLAYER_DATA);
        if (data.getGold() < goldCost) return Attempt.reject(Error.NOT_ENOUGH_GOLD);
        var result = UpgradeRules.resolve(level, failures, chances.success(), chances.unchanged(),
                chances.downgrade(), chances.reset(), UpgradeConfig.failureBonus(failures), protection, player.getRandom().nextInt(100));
        // Prepare the updated copy before charging. Existing enchantments and all other components are retained.
        ItemStack upgraded = equipment.copy();
        UpgradeData.set(upgraded, result.level(), result.failures());
        if (!data.removeGold(goldCost)) return Attempt.reject(Error.NOT_ENOUGH_GOLD);
        stones.shrink(stoneCost);
        if (!scroll.isEmpty()) scroll.shrink(1);
        inventory.setItem(itemSlot, upgraded);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        PlayerEconomyManager.syncToClient(player);
        return new Attempt(Error.NONE, result, goldCost, stoneCost);
    }
}
