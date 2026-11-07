package com.ambition.neoworld.network.pvp;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S acknowledgement; the connection supplies player identity. */
public record PvpArenaReadyPayload(UUID token) implements CustomPacketPayload {
    public static final Type<PvpArenaReadyPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_arena_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpArenaReadyPayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> b.writeUUID(p.token),b -> new PvpArenaReadyPayload(b.readUUID()));
    public PvpArenaReadyPayload { if(token==null) throw new IllegalArgumentException("Missing loading nonce"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
