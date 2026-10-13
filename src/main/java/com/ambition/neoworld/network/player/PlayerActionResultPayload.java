package com.ambition.neoworld.network.player;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerActionResultPayload(UUID requestId, UUID targetId, boolean success, String message) implements CustomPacketPayload {
    public static final Type<PlayerActionResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "player_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerActionResultPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.requestId); b.writeUUID(p.targetId); b.writeBoolean(p.success); b.writeUtf(p.message, 512); },
            b -> new PlayerActionResultPayload(b.readUUID(), b.readUUID(), b.readBoolean(), b.readUtf(512)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
