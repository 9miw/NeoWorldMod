package com.ambition.neoworld.network.activity;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenActivityPayload() implements CustomPacketPayload {
    public static final OpenActivityPayload INSTANCE = new OpenActivityPayload();
    public static final Type<OpenActivityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_activity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenActivityPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
