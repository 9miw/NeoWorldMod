package com.ambition.neoworld.item;

import java.util.List;

import com.ambition.neoworld.registry.ModDataComponents;
import com.ambition.neoworld.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ExperienceItem extends Item {
    public ExperienceItem(Properties properties) {
        super(properties);
    }

    public static long getValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        return stack.getOrDefault(ModDataComponents.EXP_VALUE.get(), 1L);
    }

    public static long getMinimumValue(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? 0L : stack.getOrDefault(ModDataComponents.EXP_MIN_VALUE.get(), getValue(stack));
    }

    public static long getRandomValue(ItemStack stack) {
        long minimum = getMinimumValue(stack);
        long maximum = getValue(stack);
        if (minimum <= 0L || maximum < minimum) return 0L;
        long range = maximum - minimum;
        if (range == Long.MAX_VALUE) return minimum + (long) (Math.random() * Long.MAX_VALUE);
        return minimum + (long) (Math.random() * (range + 1L));
    }

    public static long getTotalValue(ItemStack stack) {
        long value = getValue(stack);
        if (value <= 0L || stack == null || stack.isEmpty()) return 0L;
        return value > Long.MAX_VALUE / stack.getCount()
                ? Long.MAX_VALUE : value * stack.getCount();
    }

    public static ItemStack createStack(long value) {
        return createStack(value, value);
    }

    public static ItemStack createStack(long minimum, long maximum) {
        if (minimum <= 0L || maximum < minimum) {
            throw new IllegalArgumentException("Invalid EXP range");
        }
        ItemStack stack = new ItemStack(ModItems.EXPERIENCE_ITEM.get());
        stack.set(ModDataComponents.EXP_VALUE.get(), maximum);
        stack.set(ModDataComponents.EXP_MIN_VALUE.get(), minimum);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("EXP: " + String.format("%,d", getMinimumValue(stack))
                        + " - " + String.format("%,d", getValue(stack)))
                .withStyle(ChatFormatting.AQUA));
    }
}
