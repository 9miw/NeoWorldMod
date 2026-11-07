package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.trade.TradeInventoryPlan;
import com.ambition.neoworld.trade.TradeInventoryPlan.Offer;
import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import java.util.List;
import java.util.function.BiPredicate;

/** Plans a complete exchange without mutating the inventory. -1 denotes offhand. */
public final class LootBoxInventoryPlan {
    private LootBoxInventoryPlan() {}

    /** Check before rolling: selective stack capacity must not filter undesirable outcomes. */
    public static <T> boolean hasRewardSlot(List<Stack<T>> inventory, int boxSlot) {
        return hasRewardSlots(inventory, boxSlot, 1);
    }

    public static <T> boolean hasRewardSlots(List<Stack<T>> inventory, int boxSlot, int rewards) {
        if (inventory.size() != 36 || boxSlot < -1 || boxSlot >= 36) return false;
        if (rewards < 0 || rewards > LootBoxRules.MAX_REWARDS) return false;
        long available = inventory.stream().filter(stack -> stack.count() == 0).count()
                + (boxSlot >= 0 && inventory.get(boxSlot).count() == 1 ? 1 : 0);
        return available >= rewards;
    }

    public static <T> List<Stack<T>> plan(List<Stack<T>> inventory, int boxSlot,
            Stack<T> box, Stack<T> reward, BiPredicate<T, T> sameIdentity) {
        return plan(inventory, boxSlot, box, List.of(reward), sameIdentity);
    }

    public static <T> List<Stack<T>> plan(List<Stack<T>> inventory, int boxSlot,
            Stack<T> box, List<Stack<T>> rewards, BiPredicate<T, T> sameIdentity) {
        if (boxSlot < -1 || boxSlot >= 36 || box.count() != 1 || rewards.size() > LootBoxRules.MAX_REWARDS
                || rewards.stream().anyMatch(reward -> reward.count() < 1 || reward.count() > reward.limit())) {
            throw new IllegalArgumentException("Invalid loot box exchange");
        }
        return TradeInventoryPlan.plan(inventory,
                boxSlot == -1 ? List.of() : List.of(new Offer<>(boxSlot, box)),
                rewards.stream().map(reward -> new Offer<>(0, reward)).toList(), sameIdentity);
    }
}
