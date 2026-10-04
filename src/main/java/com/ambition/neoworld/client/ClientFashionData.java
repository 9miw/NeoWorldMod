package com.ambition.neoworld.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.ambition.neoworld.data.fashion.FashionSlot;

import net.minecraft.world.item.ItemStack;

public final class ClientFashionData {
    private static final Map<Integer, EnumMap<FashionSlot, ItemStack>> PLAYER_SLOTS = new HashMap<>();

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
        Map<FashionSlot, ItemStack> slots = PLAYER_SLOTS.get(playerId);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        return slots.getOrDefault(slot, ItemStack.EMPTY).copy();
    }

    public static void remove(int playerId) {
        PLAYER_SLOTS.remove(playerId);
    }

    public static void reset() {
        PLAYER_SLOTS.clear();
    }
}
