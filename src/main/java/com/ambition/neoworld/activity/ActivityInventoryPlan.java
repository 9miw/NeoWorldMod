package com.ambition.neoworld.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Simulate the whole reward first: a full inventory must never grant only part of a reward. */
public final class ActivityInventoryPlan {
    private ActivityInventoryPlan() {}
    public interface StackAccess<T> {
        int count(T stack);
        int limit(T stack);
        boolean same(T left, T right);
        T copy(T stack);
        T withCount(T stack, int count);
    }
    public static Optional<List<ItemStack>> plan(List<ItemStack> slots, List<ItemStack> rewards, int inventoryLimit) {
        return plan(slots, rewards, inventoryLimit, new StackAccess<ItemStack>() {
            public int count(ItemStack stack) { return stack.getCount(); }
            public int limit(ItemStack stack) { return stack.getMaxStackSize(); }
            public boolean same(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
            public ItemStack copy(ItemStack stack) { return stack.copy(); }
            public ItemStack withCount(ItemStack stack, int count) { return stack.copyWithCount(count); }
        });
    }
    /** The packing algorithm has no registry or game-loader dependency. */
    public static <T> Optional<List<T>> plan(List<T> slots, List<T> rewards, int inventoryLimit, StackAccess<T> access) {
        var result = new ArrayList<T>();
        for (var slot : slots) result.add(access.copy(slot));
        for (var source : rewards) {
            int remaining = access.count(source);
            for (int i = 0; i < result.size() && remaining > 0; i++) {
                var slot = result.get(i);
                if (access.count(slot) > 0 && access.same(slot, source)) {
                    int moved = Math.min(remaining, Math.max(0, Math.min(inventoryLimit, access.limit(slot)) - access.count(slot)));
                    result.set(i, access.withCount(slot, access.count(slot) + moved)); remaining -= moved;
                }
            }
            for (int i = 0; i < result.size() && remaining > 0; i++) {
                if (access.count(result.get(i)) == 0) {
                    int moved = Math.min(remaining, Math.min(inventoryLimit, access.limit(source)));
                    result.set(i, access.withCount(source, moved)); remaining -= moved;
                }
            }
            if (remaining > 0) return Optional.empty();
        }
        return Optional.of(result);
    }
}
