package com.ambition.neoworld.network.ranking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRankingPayload() implements CustomPacketPayload {
    public static final OpenRankingPayload INSTANCE=new OpenRankingPayload();
    public static final Type<OpenRankingPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","open_ranking"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenRankingPayload> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
