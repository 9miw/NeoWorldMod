package com.ambition.neoworld.client.state;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;

import com.ambition.neoworld.fashion.data.FashionSlot;

import net.minecraft.world.item.ItemStack;

public final class ClientFashionData {
    private static final Map<Integer, EnumMap<FashionSlot, ItemStack>> PLAYER_SLOTS = new HashMap<>();
    private static final Map<ResourceLocation, ItemStack> WARDROBE = new LinkedHashMap<>();
    private record Preview(int entityId,Map<FashionSlot,ItemStack> slots) { }
    private static final ThreadLocal<Preview> PREVIEW = new ThreadLocal<>();

    /** Temporary render-only context; live player caches and wardrobe remain untouched. */
    public static void withPreview(int entityId,Map<FashionSlot,ItemStack> slots,Runnable render) {
        var previous=PREVIEW.get();
        PREVIEW.set(new Preview(entityId,slots));
        try { render.run(); }
        finally { if(previous==null) PREVIEW.remove(); else PREVIEW.set(previous); }
    }

    private ClientFashionData() {
    }

    public static void update(int playerId, Map<FashionSlot, ItemStack> slots) {
        EnumMap<FashionSlot, ItemStack> copy = new EnumMap<>(FashionSlot.class);
        slots.forEach((slot, stack) -> {
            if (!stack.isEmpty()) {
                copy.put(slot, stack.copy());
            }
        });
        if (copy.isEmpty()) {
            PLAYER_SLOTS.remove(playerId);
        } else {
            PLAYER_SLOTS.put(playerId, copy);
        }
    }

    public static ItemStack get(int playerId, FashionSlot slot) {
        var preview=PREVIEW.get();
        if(preview!=null && preview.entityId()==playerId) return preview.slots().getOrDefault(slot,ItemStack.EMPTY).copy();
        Map<FashionSlot, ItemStack> slots = PLAYER_SLOTS.get(playerId);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        return slots.getOrDefault(slot, ItemStack.EMPTY).copy();
    }

    public static void remove(int playerId) {
        PLAYER_SLOTS.remove(playerId);
    }

    public static void updateWardrobe(Map<ResourceLocation, ItemStack> wardrobe) {
        WARDROBE.clear();
        wardrobe.forEach((id, stack) -> WARDROBE.put(id, stack.copy()));
    }

    public static Map<ResourceLocation, ItemStack> wardrobe() {
        Map<ResourceLocation, ItemStack> copy = new LinkedHashMap<>();
        WARDROBE.forEach((id, stack) -> copy.put(id, stack.copy()));
        return copy;
    }

    public static boolean owns(ResourceLocation id) {
        return WARDROBE.containsKey(id);
    }

    public static void reset() {
        PLAYER_SLOTS.clear();
        WARDROBE.clear();
    }
}
