package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.fashion.FashionSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FashionEquipPayload(FashionSlot slot, int inventorySlot) implements CustomPacketPayload {
    public static final Type<FashionEquipPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "fashion_equip"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FashionEquipPayload> STREAM_CODEC = StreamCodec.composite(
            FashionSlot.STREAM_CODEC, FashionEquipPayload::slot,
            ByteBufCodecs.VAR_INT, FashionEquipPayload::inventorySlot,
            FashionEquipPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
