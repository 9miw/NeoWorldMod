package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMainHubPayload() implements CustomPacketPayload {
    public static final OpenMainHubPayload INSTANCE = new OpenMainHubPayload();
    public static final Type<OpenMainHubPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_main_hub"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMainHubPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
