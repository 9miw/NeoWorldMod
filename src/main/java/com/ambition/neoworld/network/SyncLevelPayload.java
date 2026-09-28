package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncLevelPayload(int level, long exp, long maxExp) implements CustomPacketPayload {
    public static final Type<SyncLevelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "sync_level"));

    public static final StreamCodec<ByteBuf, SyncLevelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncLevelPayload::level,
            ByteBufCodecs.VAR_LONG, SyncLevelPayload::exp,
            ByteBufCodecs.VAR_LONG, SyncLevelPayload::maxExp,
            SyncLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
