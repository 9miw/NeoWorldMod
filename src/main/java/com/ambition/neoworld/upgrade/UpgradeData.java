package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

/** Per-stack upgrade metadata. No attributes or enchantments are modified here. */
public final class UpgradeData {
    public static final int MAX_LEVEL = 15;

    private UpgradeData() {}

    public static int level(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.UPGRADE_LEVEL.get(), 0);
    }

    public static int failures(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.UPGRADE_FAILURES.get(), 0);
    }

    public static void set(ItemStack stack, int level, int failures) {
        if (stack.isEmpty() || stack.getCount() != 1 || level < 0 || level > MAX_LEVEL || failures < 0) {
            throw new IllegalArgumentException("Upgrade metadata requires one item, level 0..15 and non-negative failures");
        }
        stack.set(ModDataComponents.UPGRADE_LEVEL.get(), level);
        stack.set(ModDataComponents.UPGRADE_FAILURES.get(), failures);
    }
}
