package com.ambition.neoworld.fashion.data;

import java.util.EnumMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.ambition.neoworld.registry.ModDataComponents;

public class FashionData {
    private static final Codec<Map<FashionSlot, ItemStack>> SLOT_CODEC =
            Codec.unboundedMap(FashionSlot.CODEC, ItemStack.OPTIONAL_CODEC);
    private static final Codec<FashionData> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SLOT_CODEC.fieldOf("equipped").forGetter(FashionData::asMap),
            ItemStack.CODEC.listOf().optionalFieldOf("wardrobe", List.of()).forGetter(data -> List.copyOf(data.wardrobe().values()))
    ).apply(instance, FashionData::new));
    public static final Codec<FashionData> CODEC = Codec.either(CURRENT_CODEC, SLOT_CODEC)
            .xmap(value -> value.map(data -> data, FashionData::new), data -> com.mojang.datafixers.util.Either.left(data));

    private final EnumMap<FashionSlot, ItemStack> slots = new EnumMap<>(FashionSlot.class);
    private final Map<ResourceLocation, ItemStack> unlocked = new LinkedHashMap<>();

    public FashionData() {
    }

    public FashionData(Map<FashionSlot, ItemStack> savedSlots) {
        this(savedSlots, List.of());
    }

    public FashionData(Map<FashionSlot, ItemStack> savedSlots, List<ItemStack> wardrobe) {
        wardrobe.forEach(this::unlock);
        // Previously equipped physical items become permanent wardrobe entries.
        savedSlots.values().forEach(this::unlock);
        savedSlots.forEach(this::set);
    }

    public boolean unlock(ItemStack stack) {
        if (stack.isEmpty() || stack.get(ModDataComponents.FASHION_SLOT.get()) == null) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (unlocked.containsKey(id)) return false;
        unlocked.put(id, stack.copyWithCount(1));
        return true;
    }

    public ItemStack owned(ResourceLocation id) {
        return unlocked.getOrDefault(id, ItemStack.EMPTY).copy();
    }

    public Map<ResourceLocation, ItemStack> wardrobe() {
        Map<ResourceLocation, ItemStack> copy = new LinkedHashMap<>();
        unlocked.forEach((id, stack) -> copy.put(id, stack.copy()));
        return copy;
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
