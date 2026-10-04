package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketActionResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<MarketActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketActionResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.success);
                buffer.writeUtf(payload.message, 256);
            },
            buffer -> new MarketActionResultPayload(buffer.readBoolean(), buffer.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
