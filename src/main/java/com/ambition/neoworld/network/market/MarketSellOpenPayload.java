package com.ambition.neoworld.network.market;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketSellOpenPayload() implements CustomPacketPayload {
    public static final MarketSellOpenPayload INSTANCE = new MarketSellOpenPayload();
    public static final Type<MarketSellOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_sell_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSellOpenPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
