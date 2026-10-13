package com.ambition.neoworld.network.activity;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActivityRequestPayload(UUID requestId, Action action, String key, int tab, int page)
        implements CustomPacketPayload {
    public enum Action { QUERY, CHECK_IN, CLAIM, CLAIM_ALL }
    public static final Type<ActivityRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "activity_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivityRequestPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.requestId); b.writeEnum(p.action); b.writeUtf(p.key, 96); b.writeVarInt(p.tab); b.writeVarInt(p.page); },
            b -> new ActivityRequestPayload(b.readUUID(), b.readEnum(Action.class), b.readUtf(96), b.readVarInt(), b.readVarInt()));
    public ActivityRequestPayload {
        if (requestId == null || action == null || key == null || key.length() > 96 || tab < 0 || tab > 3
                || page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid activity request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
