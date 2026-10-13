package com.ambition.neoworld.network.fashion;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.fashion.data.FashionSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FashionEquipPayload(FashionSlot slot, ResourceLocation fashionId) implements CustomPacketPayload {
    public static final Type<FashionEquipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "fashion_equip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FashionEquipPayload> STREAM_CODEC = StreamCodec.composite(
            FashionSlot.STREAM_CODEC, FashionEquipPayload::slot,
            ResourceLocation.STREAM_CODEC, FashionEquipPayload::fashionId,
            FashionEquipPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
