package com.ambition.neoworld.data.fashion;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.ItemStack;

public class FashionData {
    public static final Codec<FashionData> CODEC = Codec.unboundedMap(FashionSlot.CODEC, ItemStack.OPTIONAL_CODEC)
            .xmap(FashionData::new, FashionData::asMap);

    private final EnumMap<FashionSlot, ItemStack> slots = new EnumMap<>(FashionSlot.class);

    public FashionData() {
    }

    public FashionData(Map<FashionSlot, ItemStack> savedSlots) {
        savedSlots.forEach(this::set);
    }

    public ItemStack get(FashionSlot slot) {
        return slots.getOrDefault(slot, ItemStack.EMPTY).copy();
    }

    public ItemStack set(FashionSlot slot, ItemStack stack) {
        ItemStack previous = slots.getOrDefault(slot, ItemStack.EMPTY).copy();
        if (stack.isEmpty()) {
            slots.remove(slot);
        } else {
            slots.put(slot, stack.copy());
        }
        return previous;
    }

    public ItemStack clear(FashionSlot slot) {
        ItemStack previous = slots.remove(slot);
        return previous == null ? ItemStack.EMPTY : previous.copy();
    }

    public Map<FashionSlot, ItemStack> asMap() {
        EnumMap<FashionSlot, ItemStack> copy = new EnumMap<>(FashionSlot.class);
        slots.forEach((slot, stack) -> {
            if (!stack.isEmpty()) {
                copy.put(slot, stack.copy());
            }
        });
        return copy;
    }
}
