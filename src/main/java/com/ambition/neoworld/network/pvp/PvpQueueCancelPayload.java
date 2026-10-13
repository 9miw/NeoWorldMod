package com.ambition.neoworld.network.pvp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record PvpQueueCancelPayload() implements CustomPacketPayload {
    public static final PvpQueueCancelPayload INSTANCE=new PvpQueueCancelPayload();
    public static final Type<PvpQueueCancelPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_queue_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpQueueCancelPayload> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
