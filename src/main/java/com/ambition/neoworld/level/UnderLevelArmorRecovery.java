package com.ambition.neoworld.level;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;

/** Persistent return buffer for armor that cannot fit back into the inventory. */
public final class UnderLevelArmorRecovery {
    public static final Codec<UnderLevelArmorRecovery> CODEC = ItemStack.CODEC.listOf()
            .xmap(UnderLevelArmorRecovery::new, recovery -> recovery.pending);
    private final List<ItemStack> pending;

    public UnderLevelArmorRecovery() { this(List.of()); }

    private UnderLevelArmorRecovery(List<ItemStack> stacks) {
        pending = new ArrayList<>();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) pending.add(stack.copy());
    }

    public void retain(ItemStack stack) {
        if (!stack.isEmpty()) pending.add(stack.copy());
    }

    /** The inventory consumes the offered stack in place; any remainder stays saved here. */
    public void restore(Consumer<ItemStack> inventoryOffer) {
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            inventoryOffer.accept(stack);
            if (stack.isEmpty()) iterator.remove();
        }
    }

    /** Unlike Inventory.add, never discards an unplaced remainder in Creative mode. */
    public static void insertIntoInventory(Inventory inventory, ItemStack stack) {
        boolean changed = false;
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int slot = 0; slot < inventory.items.size() && !stack.isEmpty(); slot++) {
                ItemStack existing = inventory.getItem(slot);
                int maximum = Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());
                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                    int moved = Math.min(stack.getCount(), Math.max(0, maximum - existing.getCount()));
                    if (moved > 0) {
                        existing.grow(moved);
                        stack.shrink(moved);
                        changed = true;
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    int moved = Math.min(stack.getCount(), maximum);
                    inventory.setItem(slot, stack.copyWithCount(moved));
                    stack.shrink(moved);
                    changed = true;
                }
            }
        }
        if (changed) inventory.setChanged();
    }
}
