package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonRoomQueryPayload() implements CustomPacketPayload {
    public static final DungeonRoomQueryPayload INSTANCE = new DungeonRoomQueryPayload();
    public static final Type<DungeonRoomQueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_room_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonRoomQueryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
