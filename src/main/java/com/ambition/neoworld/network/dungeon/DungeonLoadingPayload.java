package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonLoadingPayload(boolean visible, int roomId, String title) implements CustomPacketPayload {
    public static final Type<DungeonLoadingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_loading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonLoadingPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.visible());
                buffer.writeVarInt(payload.roomId());
                buffer.writeUtf(payload.title(), 32767);
            }, buffer -> new DungeonLoadingPayload(buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(32767)));

    public DungeonLoadingPayload {
        if (roomId <= 0 || title == null || title.length() > 32767) {
            throw new IllegalArgumentException("Invalid dungeon loading state");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
