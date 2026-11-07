package com.ambition.neoworld.network.pvp;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Invitation token is server-issued and valid only for its intended recipient. */
public record PvpInvitationPayload(UUID token,int room,String owner) implements CustomPacketPayload {
    public static final Type<PvpInvitationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_invitation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpInvitationPayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> { b.writeUUID(p.token); b.writeVarInt(p.room); b.writeUtf(p.owner,64); },
            b -> new PvpInvitationPayload(b.readUUID(),b.readVarInt(),b.readUtf(64)));
    public PvpInvitationPayload {
        if(token==null || room<1 || owner==null || owner.isEmpty() || owner.length()>64) throw new IllegalArgumentException("Invalid invitation");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
