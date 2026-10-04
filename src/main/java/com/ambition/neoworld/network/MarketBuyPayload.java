package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketBuyPayload(long listingId) implements CustomPacketPayload {
    public static final Type<MarketBuyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketBuyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarLong(payload.listingId),
            buffer -> new MarketBuyPayload(buffer.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
