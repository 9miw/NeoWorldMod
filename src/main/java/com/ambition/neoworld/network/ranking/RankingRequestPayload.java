package com.ambition.neoworld.network.ranking;

import java.util.Objects;
import java.util.UUID;
import com.ambition.neoworld.ranking.RankingTable.Category;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RankingRequestPayload(UUID request,Category category) implements CustomPacketPayload {
    public static final Type<RankingRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","ranking_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RankingRequestPayload> STREAM_CODEC=StreamCodec.of(
            (b,p) -> { b.writeUUID(p.request); b.writeEnum(p.category); },
            b -> new RankingRequestPayload(b.readUUID(),b.readEnum(Category.class)));
    public RankingRequestPayload { Objects.requireNonNull(request); Objects.requireNonNull(category); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
