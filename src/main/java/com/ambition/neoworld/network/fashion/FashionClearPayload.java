package com.ambition.neoworld.network.fashion;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.fashion.data.FashionSlot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FashionClearPayload(FashionSlot slot) implements CustomPacketPayload {
    public static final Type<FashionClearPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "fashion_clear"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FashionClearPayload> STREAM_CODEC = StreamCodec.composite(
            FashionSlot.STREAM_CODEC, FashionClearPayload::slot,
            FashionClearPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
