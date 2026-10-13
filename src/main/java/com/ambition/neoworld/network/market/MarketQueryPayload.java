package com.ambition.neoworld.network.market;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.model.MarketCategory;
import com.ambition.neoworld.market.model.MarketSort;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketQueryPayload(int requestId, int page, MarketCategory category, String search, MarketSort sort)
        implements CustomPacketPayload {
    public static final Type<MarketQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketQueryPayload> STREAM_CODEC = StreamCodec.of(
            MarketQueryPayload::encode, MarketQueryPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, MarketQueryPayload payload) {
        buffer.writeVarInt(payload.requestId);
        buffer.writeVarInt(payload.page);
        buffer.writeEnum(payload.category);
        buffer.writeUtf(payload.search, 64);
        buffer.writeEnum(payload.sort);
    }

    private static MarketQueryPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MarketQueryPayload(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readEnum(MarketCategory.class), buffer.readUtf(64), buffer.readEnum(MarketSort.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
