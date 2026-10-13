package com.ambition.neoworld.market.model;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

public enum MarketCategory {
    ALL,
    BLOCKS,
    COMBAT,
    TOOLS,
    FOOD,
    MATERIALS;

    public static MarketCategory classify(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem) return BLOCKS;
        if (item instanceof SwordItem || item instanceof ArmorItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof ShieldItem) {
            return COMBAT;
        }
        if (item instanceof DiggerItem || item instanceof FishingRodItem || item instanceof ShearsItem) return TOOLS;
        if (stack.has(DataComponents.FOOD)) return FOOD;
        return MATERIALS;
    }
}
