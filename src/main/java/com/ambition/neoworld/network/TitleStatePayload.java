package com.ambition.neoworld.network;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TitleStatePayload(UUID playerId, String titleId, String name, int color) implements CustomPacketPayload {
    public static final Type<TitleStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "title_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleStatePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeUUID(payload.playerId()); buffer.writeUtf(payload.titleId(), 64);
            buffer.writeUtf(payload.name(), 64); buffer.writeInt(payload.color());
        },
        buffer -> new TitleStatePayload(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64), buffer.readInt()));
    public TitleStatePayload {
        if (playerId == null || titleId == null || titleId.length() > 64 || name == null || name.length() > 64
                || color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Invalid title state");
    }
    public static TitleStatePayload empty(UUID id) { return new TitleStatePayload(id, "", "", 0); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
