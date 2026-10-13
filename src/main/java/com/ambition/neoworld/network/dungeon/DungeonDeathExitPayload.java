package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonDeathExitPayload() implements CustomPacketPayload {
    public static final DungeonDeathExitPayload INSTANCE = new DungeonDeathExitPayload();
    public static final Type<DungeonDeathExitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_death_exit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeathExitPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
