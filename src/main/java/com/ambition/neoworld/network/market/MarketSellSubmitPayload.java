package com.ambition.neoworld.network.market;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketSellSubmitPayload(int containerId, int quantity, long unitPriceSilver,
                                      long durationTicks) implements CustomPacketPayload {
    public static final Type<MarketSellSubmitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_sell_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSellSubmitPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeVarInt(payload.quantity);
                buffer.writeVarLong(payload.unitPriceSilver);
                buffer.writeVarLong(payload.durationTicks);
            },
            buffer -> new MarketSellSubmitPayload(buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
