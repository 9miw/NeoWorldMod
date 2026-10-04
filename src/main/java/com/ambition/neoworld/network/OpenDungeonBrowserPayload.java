package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenDungeonBrowserPayload() implements CustomPacketPayload {
    public static final OpenDungeonBrowserPayload INSTANCE = new OpenDungeonBrowserPayload();
    public static final Type<OpenDungeonBrowserPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_dungeon_browser"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDungeonBrowserPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
