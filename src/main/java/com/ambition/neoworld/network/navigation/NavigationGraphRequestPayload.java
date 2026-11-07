package com.ambition.neoworld.network.navigation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Recovery only: server resolves requester from context and rate-limits full snapshots. */
public record NavigationGraphRequestPayload() implements CustomPacketPayload {
    public static final NavigationGraphRequestPayload INSTANCE=new NavigationGraphRequestPayload();
    public static final Type<NavigationGraphRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","navigation_graph_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NavigationGraphRequestPayload> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
