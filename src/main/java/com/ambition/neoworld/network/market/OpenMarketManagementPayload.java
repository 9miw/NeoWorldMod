package com.ambition.neoworld.network.market;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMarketManagementPayload() implements CustomPacketPayload {
    public static final OpenMarketManagementPayload INSTANCE = new OpenMarketManagementPayload();
    public static final Type<OpenMarketManagementPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_market_management"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMarketManagementPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
