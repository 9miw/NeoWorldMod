package com.ambition.neoworld.network.pvp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned queue status, independent of the selection screen. */
public record PvpQueueStatePayload(boolean queued,int rating,int elapsedSeconds) implements CustomPacketPayload {
    public static final PvpQueueStatePayload EMPTY=new PvpQueueStatePayload(false,0,0);
    public static final Type<PvpQueueStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_queue_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpQueueStatePayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> { b.writeBoolean(p.queued); b.writeVarInt(p.rating); b.writeVarInt(p.elapsedSeconds); },
            b -> new PvpQueueStatePayload(b.readBoolean(),b.readVarInt(),b.readVarInt()));
    public PvpQueueStatePayload { if(elapsedSeconds<0 || (!queued && (rating!=0 || elapsedSeconds!=0))) throw new IllegalArgumentException("Invalid queue state"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
