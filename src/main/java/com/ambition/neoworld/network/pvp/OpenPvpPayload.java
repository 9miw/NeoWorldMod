package com.ambition.neoworld.network.pvp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record OpenPvpPayload() implements CustomPacketPayload {
    public static final OpenPvpPayload INSTANCE=new OpenPvpPayload();
    public static final Type<OpenPvpPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","open_pvp"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenPvpPayload> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
