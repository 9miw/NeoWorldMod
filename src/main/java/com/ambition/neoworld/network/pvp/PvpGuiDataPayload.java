package com.ambition.neoworld.network.pvp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PvpGuiDataPayload(UUID request,String message,boolean ratingAvailable,int rating,int position,
        boolean queued,int roomId,String state,boolean owner,boolean ready,boolean canStart,boolean ranked,
        List<String> arenas,List<Member> members,List<Leader> leaders) implements CustomPacketPayload {
    public record Member(String name,boolean owner,boolean ready) { }
    public record Leader(String name,int rating) { }
    public static final Type<PvpGuiDataPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_gui_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpGuiDataPayload> STREAM_CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.request); b.writeUtf(p.message,256); b.writeBoolean(p.ratingAvailable); b.writeVarInt(p.rating); b.writeVarInt(p.position);
        b.writeBoolean(p.queued); b.writeVarInt(p.roomId); b.writeUtf(p.state,32); b.writeBoolean(p.owner); b.writeBoolean(p.ready); b.writeBoolean(p.canStart); b.writeBoolean(p.ranked);
        b.writeVarInt(p.arenas.size()); for(String arena:p.arenas) b.writeUtf(arena,64);
        b.writeVarInt(p.members.size()); for(Member member:p.members) { b.writeUtf(member.name,64); b.writeBoolean(member.owner); b.writeBoolean(member.ready); }
        b.writeVarInt(p.leaders.size()); for(Leader leader:p.leaders) { b.writeUtf(leader.name,64); b.writeVarInt(leader.rating); }
    },b -> {
        UUID request=b.readUUID(); String message=b.readUtf(256); boolean available=b.readBoolean(); int rating=b.readVarInt(),position=b.readVarInt();
        boolean queued=b.readBoolean(); int room=b.readVarInt(); String state=b.readUtf(32); boolean owner=b.readBoolean(),ready=b.readBoolean(),start=b.readBoolean(),ranked=b.readBoolean();
        List<String> arenas=new ArrayList<>(); int count=count(b,128); for(int i=0;i<count;i++) arenas.add(b.readUtf(64));
        List<Member> members=new ArrayList<>(); count=count(b,2); for(int i=0;i<count;i++) members.add(new Member(b.readUtf(64),b.readBoolean(),b.readBoolean()));
        List<Leader> leaders=new ArrayList<>(); count=count(b,10); for(int i=0;i<count;i++) leaders.add(new Leader(b.readUtf(64),b.readVarInt()));
        return new PvpGuiDataPayload(request,message,available,rating,position,queued,room,state,owner,ready,start,ranked,arenas,members,leaders);
    });
    public PvpGuiDataPayload {
        if(request==null || message==null || message.length()>256 || position<0 || roomId<0 || state==null || state.length()>32
                || arenas.size()>128 || members.size()>2 || leaders.size()>10) throw new IllegalArgumentException("Invalid PvP snapshot");
        arenas=List.copyOf(arenas); members=List.copyOf(members); leaders=List.copyOf(leaders);
    }
    private static int count(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt(); if(n<0 || n>max) throw new IllegalArgumentException("Too many PvP entries"); return n; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
