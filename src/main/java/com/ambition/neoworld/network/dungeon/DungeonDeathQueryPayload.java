package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonDeathQueryPayload() implements CustomPacketPayload {
    public static final DungeonDeathQueryPayload INSTANCE = new DungeonDeathQueryPayload();
    public static final Type<DungeonDeathQueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_death_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeathQueryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
