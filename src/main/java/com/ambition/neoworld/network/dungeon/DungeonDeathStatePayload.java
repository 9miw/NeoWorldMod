package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonDeathStatePayload(boolean dungeonDeath) implements CustomPacketPayload {
    public static final Type<DungeonDeathStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_death_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonDeathStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DungeonDeathStatePayload::dungeonDeath, DungeonDeathStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
