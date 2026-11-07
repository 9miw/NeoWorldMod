package com.ambition.neoworld.network.ranking;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import com.ambition.neoworld.ranking.RankingTable;
import com.ambition.neoworld.ranking.RankingTable.Category;
import com.ambition.neoworld.ranking.RankingTable.Entry;
import com.ambition.neoworld.ranking.RankingAppearance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RankingDataPayload(UUID request,Category category,String error,RankingTable.View view,java.util.List<Preview> previews) implements CustomPacketPayload {
    public record Preview(UUID id,RankingAppearance appearance) {
        public Preview { Objects.requireNonNull(id); Objects.requireNonNull(appearance); }
    }
    public RankingDataPayload(UUID request,Category category,String error,RankingTable.View view) { this(request,category,error,view,java.util.List.of()); }
    public static final Type<RankingDataPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","ranking_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RankingDataPayload> STREAM_CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.request); b.writeEnum(p.category); b.writeUtf(p.error,256);
        b.writeVarInt(p.view.leaders().size()); p.view.leaders().forEach(e -> write(b,e));
        b.writeBoolean(p.view.personal()!=null); if(p.view.personal()!=null) write(b,p.view.personal());
        b.writeVarInt(p.view.position());
        b.writeVarInt(p.previews.size());
        for(var preview:p.previews) { b.writeUUID(preview.id()); RankingAppearance.STREAM_CODEC.encode(b,preview.appearance()); }
    },b -> {
        UUID request=b.readUUID(); Category category=b.readEnum(Category.class); String error=b.readUtf(256);
        int count=b.readVarInt(); if(count<0 || count>10) throw new IllegalArgumentException("Invalid ranking list length");
        var entries=new ArrayList<Entry>(count); for(int i=0;i<count;i++) entries.add(read(b));
        var personal=b.readBoolean() ? read(b) : null;
        var view=new RankingTable.View(entries,personal,b.readVarInt());
        int previewCount=b.readVarInt(); if(previewCount<0 || previewCount>3) throw new IllegalArgumentException("Invalid podium count");
        var previews=new ArrayList<Preview>(previewCount);
        for(int i=0;i<previewCount;i++) previews.add(new Preview(b.readUUID(),RankingAppearance.STREAM_CODEC.decode(b)));
        return new RankingDataPayload(request,category,error,view,previews);
    });
    public RankingDataPayload {
        Objects.requireNonNull(request); Objects.requireNonNull(category); Objects.requireNonNull(error); Objects.requireNonNull(view);
        if(error.length()>256) throw new IllegalArgumentException("Ranking error too long");
        previews=java.util.List.copyOf(previews);
        if(previews.size()>3 || previews.stream().map(Preview::id).distinct().count()!=previews.size()) throw new IllegalArgumentException("Invalid podium previews");
    }
    private static void write(RegistryFriendlyByteBuf b,Entry e) {
        b.writeUUID(e.id()); b.writeUtf(e.name(),64); b.writeVarInt(e.level()); b.writeVarLong(e.exp());
        b.writeVarLong(e.silver()); b.writeInt(e.rating()); b.writeVarInt(e.wins()); b.writeVarInt(e.losses());
        b.writeVarLong(e.totalExp());
    }
    private static Entry read(RegistryFriendlyByteBuf b) {
        return new Entry(b.readUUID(),b.readUtf(64),b.readVarInt(),b.readVarLong(),b.readVarLong(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readVarLong());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
