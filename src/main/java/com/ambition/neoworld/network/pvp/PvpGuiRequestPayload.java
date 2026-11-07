package com.ambition.neoworld.network.pvp;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PvpGuiRequestPayload(UUID request,Action action,String arena) implements CustomPacketPayload {
    public enum Action { QUERY, CREATE, QUEUE, UNQUEUE, READY, UNREADY, START, LEAVE, TOP, INVITE, ACCEPT_INVITE, DECLINE_INVITE }
    public static final Type<PvpGuiRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_gui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpGuiRequestPayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> { b.writeUUID(p.request); b.writeEnum(p.action); b.writeUtf(p.arena,64); },
            b -> new PvpGuiRequestPayload(b.readUUID(),b.readEnum(Action.class),b.readUtf(64)));
    public PvpGuiRequestPayload {
        if(request==null || action==null || arena==null || arena.length()>64
                || (action==Action.CREATE && !arena.matches("[a-z0-9_]{1,64}")) || (action==Action.QUEUE && !arena.isEmpty())) throw new IllegalArgumentException("Invalid PvP action");
        if(action==Action.INVITE || action==Action.ACCEPT_INVITE || action==Action.DECLINE_INVITE) UUID.fromString(arena);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
