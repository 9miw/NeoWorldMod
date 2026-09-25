package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet ซิงค์ข้อมูลยอดเงินระบบ Economy (Silver, Gold, Diamond) จาก Server ไปยัง Client
public record SyncEconomyPayload(long silver, long gold, long diamond) implements CustomPacketPayload {
    public static final Type<SyncEconomyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "sync_economy"));

    public static final StreamCodec<ByteBuf, SyncEconomyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SyncEconomyPayload::silver,
            ByteBufCodecs.VAR_LONG, SyncEconomyPayload::gold,
            ByteBufCodecs.VAR_LONG, SyncEconomyPayload::diamond,
            SyncEconomyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
