package com.ambition.neoworld.network;

import java.util.EnumMap;
import java.util.Map;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.fashion.FashionSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncFashionPayload(int playerId, Map<FashionSlot, ItemStack> slots) implements CustomPacketPayload {
    public static final Type<SyncFashionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "sync_fashion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFashionPayload> STREAM_CODEC =
            StreamCodec.of(SyncFashionPayload::encode, SyncFashionPayload::decode);

    public SyncFashionPayload {
        EnumMap<FashionSlot, ItemStack> copy = new EnumMap<>(FashionSlot.class);
        slots.forEach((slot, stack) -> {
            if (!stack.isEmpty()) {
                copy.put(slot, stack.copy());
            }
        });
        slots = copy;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SyncFashionPayload payload) {
        buffer.writeVarInt(payload.playerId);
        buffer.writeVarInt(payload.slots.size());
        payload.slots.forEach((slot, stack) -> {
            FashionSlot.STREAM_CODEC.encode(buffer, slot);
            ItemStack.STREAM_CODEC.encode(buffer, stack);
        });
    }

    private static SyncFashionPayload decode(RegistryFriendlyByteBuf buffer) {
        int playerId = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > FashionSlot.values().length) {
            throw new IllegalArgumentException("Invalid fashion slot count: " + size);
        }
        EnumMap<FashionSlot, ItemStack> slots = new EnumMap<>(FashionSlot.class);
        for (int i = 0; i < size; i++) {
            slots.put(FashionSlot.STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer));
        }
        return new SyncFashionPayload(playerId, slots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
