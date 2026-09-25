package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet แจ้งเตือนฝั่ง Client เมื่อเลเวลอัป (เล่นเสียง/เอฟเฟกต์/Title)
public record LevelUpCelebrationPayload(int newLevel) implements CustomPacketPayload {
    public static final Type<LevelUpCelebrationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "level_up_celebration"));

    public static final StreamCodec<ByteBuf, LevelUpCelebrationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LevelUpCelebrationPayload::newLevel,
            LevelUpCelebrationPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
