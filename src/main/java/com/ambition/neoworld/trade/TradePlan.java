package com.ambition.neoworld.trade;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Pure preflight: never mutates inventories or offers. */
public final class TradePlan {
    private TradePlan() {}

    public record Offer(int slot, ItemStack item) {
        public Offer { item = item.copy(); }
    }

    public static long balance(long current, long outgoing, long incoming) {
        if (current < 0 || outgoing < 0 || incoming < 0 || outgoing > current
                || incoming > Long.MAX_VALUE - (current - outgoing)) {
            throw new IllegalArgumentException("Silver ไม่เพียงพอหรือยอดเงินเกินขีดจำกัด");
        }
        return current - outgoing + incoming;
    }

    public static List<ItemStack> inventory(List<ItemStack> source, List<Offer> outgoing,
                                             List<Offer> incoming) {
        return TradeInventoryPlan.plan(source.stream().map(TradePlan::stack).toList(),
                outgoing.stream().map(o -> new TradeInventoryPlan.Offer<>(o.slot, stack(o.item))).toList(),
                incoming.stream().map(o -> new TradeInventoryPlan.Offer<>(o.slot, stack(o.item))).toList(),
                ItemStack::isSameItemSameComponents).stream()
                .map(s -> s.count() == 0 ? ItemStack.EMPTY : s.identity().copyWithCount(s.count())).toList();
    }

    private static TradeInventoryPlan.Stack<ItemStack> stack(ItemStack item) {
        return item.isEmpty() ? new TradeInventoryPlan.Stack<>(null, 0, 64)
                : new TradeInventoryPlan.Stack<>(item.copyWithCount(1), item.getCount(), item.getMaxStackSize());
    }
}
