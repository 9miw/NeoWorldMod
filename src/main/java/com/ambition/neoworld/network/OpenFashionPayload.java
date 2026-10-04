package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFashionPayload() implements CustomPacketPayload {
    public static final OpenFashionPayload INSTANCE = new OpenFashionPayload();
    public static final Type<OpenFashionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_fashion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFashionPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
