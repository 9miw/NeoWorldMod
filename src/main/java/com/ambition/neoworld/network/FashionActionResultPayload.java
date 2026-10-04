package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FashionActionResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<FashionActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "fashion_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FashionActionResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FashionActionResultPayload::success,
            ByteBufCodecs.STRING_UTF8, FashionActionResultPayload::message,
            FashionActionResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
