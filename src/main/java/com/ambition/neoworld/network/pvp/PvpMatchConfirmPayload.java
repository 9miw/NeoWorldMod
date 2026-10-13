package com.ambition.neoworld.network.pvp;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record PvpMatchConfirmPayload(UUID offer) implements CustomPacketPayload {
    public static final Type<PvpMatchConfirmPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_match_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpMatchConfirmPayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeUUID(p.offer),b->new PvpMatchConfirmPayload(b.readUUID()));
    public PvpMatchConfirmPayload { if(offer==null) throw new IllegalArgumentException("Missing offer ID"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
