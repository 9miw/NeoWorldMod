package com.ambition.neoworld.network.pvp;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C readiness challenge after teleport; closing a challenge clears only that nonce. */
public record PvpArenaLoadPayload(UUID token,boolean active,BlockPos spawn) implements CustomPacketPayload {
    public static final Type<PvpArenaLoadPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_arena_load"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpArenaLoadPayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> { b.writeUUID(p.token); b.writeBoolean(p.active); b.writeBlockPos(p.spawn); },
            b -> new PvpArenaLoadPayload(b.readUUID(),b.readBoolean(),b.readBlockPos()));
    public PvpArenaLoadPayload {
        if(token==null || spawn==null) throw new IllegalArgumentException("Missing PvP loading challenge");
        spawn=spawn.immutable();
    }
    public static PvpArenaLoadPayload closed(UUID token) { return new PvpArenaLoadPayload(token,false,BlockPos.ZERO); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
