package com.ambition.neoworld.network.player;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerActionPayload(UUID requestId, UUID targetId, Action action) implements CustomPacketPayload {
    public enum Action { PROFILE, TRADE, INVITE }
    public static final Type<PlayerActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "player_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.requestId); b.writeUUID(p.targetId); b.writeEnum(p.action); },
            b -> new PlayerActionPayload(b.readUUID(), b.readUUID(), b.readEnum(Action.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
