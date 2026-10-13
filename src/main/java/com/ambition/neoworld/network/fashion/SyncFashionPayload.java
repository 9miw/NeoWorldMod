package com.ambition.neoworld.network.fashion;

import java.util.EnumMap;
import java.util.Map;
import java.util.LinkedHashMap;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.fashion.data.FashionSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncFashionPayload(int playerId, Map<FashionSlot, ItemStack> slots,
                                 boolean includesWardrobe, Map<ResourceLocation, ItemStack> wardrobe) implements CustomPacketPayload {
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
        Map<ResourceLocation, ItemStack> owned = new LinkedHashMap<>();
        wardrobe.forEach((id, stack) -> owned.put(id, stack.copy()));
        wardrobe = owned;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SyncFashionPayload payload) {
        buffer.writeVarInt(payload.playerId);
        buffer.writeVarInt(payload.slots.size());
        payload.slots.forEach((slot, stack) -> {
            FashionSlot.STREAM_CODEC.encode(buffer, slot);
            ItemStack.STREAM_CODEC.encode(buffer, stack);
        });
        buffer.writeBoolean(payload.includesWardrobe);
        if (payload.includesWardrobe) {
            buffer.writeVarInt(payload.wardrobe.size());
            payload.wardrobe.forEach((id, stack) -> {
                ResourceLocation.STREAM_CODEC.encode(buffer, id);
                ItemStack.STREAM_CODEC.encode(buffer, stack);
            });
        }
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
        boolean includesWardrobe = buffer.readBoolean();
        Map<ResourceLocation, ItemStack> wardrobe = new LinkedHashMap<>();
        if (includesWardrobe) {
            int count = buffer.readVarInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid wardrobe size: " + count);
            for (int i = 0; i < count; i++) {
                wardrobe.put(ResourceLocation.STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer));
            }
        }
        return new SyncFashionPayload(playerId, slots, includesWardrobe, wardrobe);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
