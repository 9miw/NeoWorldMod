package com.ambition.neoworld.network.pvp;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record PvpMatchOfferPayload(UUID offer,boolean active,int seconds,Player self,Player opponent,boolean selfReady,boolean opponentReady) implements CustomPacketPayload {
    /** Equipment order: main hand, off hand, feet, legs, chest, head. */
    public record Player(UUID id,String name,int rating,int position,List<ItemStack> equipment) {
        public Player { if(id==null || name==null || name.length()>64 || position< -1 || equipment.size()!=6) throw new IllegalArgumentException("Invalid player preview"); equipment=equipment.stream().map(ItemStack::copy).toList(); }
    }
    public static final Type<PvpMatchOfferPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","pvp_match_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PvpMatchOfferPayload> STREAM_CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.offer); b.writeBoolean(p.active); if(!p.active) return;
        b.writeVarInt(p.seconds); write(b,p.self); write(b,p.opponent); b.writeBoolean(p.selfReady); b.writeBoolean(p.opponentReady);
    },b -> {
        UUID id=b.readUUID(); if(!b.readBoolean()) return closed(id);
        int seconds=b.readVarInt(); if(seconds<0 || seconds>30) throw new IllegalArgumentException("Invalid offer time");
        return new PvpMatchOfferPayload(id,true,seconds,read(b),read(b),b.readBoolean(),b.readBoolean());
    });
    public PvpMatchOfferPayload {
        if(offer==null || seconds<0 || seconds>30 || active && (self==null || opponent==null || self.id().equals(opponent.id()))) throw new IllegalArgumentException("Invalid match offer");
    }
    public static PvpMatchOfferPayload closed(UUID id) { return new PvpMatchOfferPayload(id,false,0,null,null,false,false); }
    private static void write(RegistryFriendlyByteBuf b,Player p) {
        b.writeUUID(p.id); b.writeUtf(p.name,64); b.writeVarInt(p.rating); b.writeVarInt(p.position);
        for(ItemStack stack:p.equipment) ItemStack.OPTIONAL_STREAM_CODEC.encode(b,stack);
    }
    private static Player read(RegistryFriendlyByteBuf b) {
        UUID id=b.readUUID(); String name=b.readUtf(64); int rating=b.readVarInt(),position=b.readVarInt();
        var equipment=new java.util.ArrayList<ItemStack>(); for(int i=0;i<6;i++) equipment.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
        return new Player(id,name,rating,position,equipment);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
