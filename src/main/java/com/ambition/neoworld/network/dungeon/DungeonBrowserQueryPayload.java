package com.ambition.neoworld.network.dungeon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBrowserQueryPayload() implements CustomPacketPayload {
    public static final DungeonBrowserQueryPayload INSTANCE = new DungeonBrowserQueryPayload();
    public static final Type<DungeonBrowserQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_browser_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBrowserQueryPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
