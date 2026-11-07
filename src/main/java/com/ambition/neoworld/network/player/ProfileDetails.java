package com.ambition.neoworld.network.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Bounded, display-only metadata captured alongside the combat snapshot. */
public record ProfileDetails(boolean owner, long capturedAt, String title, int titleColor,
        Pvp pvp, List<Gear> gear, List<EquippedSkill> skills) {
    public static final ProfileDetails EMPTY = new ProfileDetails(false,0,"",0,Pvp.UNAVAILABLE,List.of(),List.of());
    private static final Set<String> SLOTS = Set.of("head","chest","legs","feet", "fashion_head","fashion_face",
            "fashion_back","fashion_body","fashion_legs","fashion_feet");
    public ProfileDetails {
        bounded(title,128); if(capturedAt<0 || titleColor<0 || titleColor>0xFFFFFF || pvp==null) throw new IllegalArgumentException("Invalid profile metadata");
        gear=List.copyOf(gear); skills=List.copyOf(skills);
        if(gear.size()>10 || skills.size()>64) throw new IllegalArgumentException("Profile metadata too large");
        validateGearSlots(gear.stream().map(Gear::slot).toList());
    }
    /** Pure policy check, shared by snapshot construction and headless regression tests. */
    public static void validateGearSlots(List<String> names) {
        if(names.size()>10) throw new IllegalArgumentException("Too many gear slots");
        var seen=new HashSet<String>();
        for(String name:names) if(!SLOTS.contains(name) || !seen.add(name)) throw new IllegalArgumentException("Invalid or duplicate gear slot");
    }
    public record Pvp(boolean available,int rating,int position,int wins,int losses,boolean loading) {
        public Pvp(boolean available,int rating,int position,int wins,int losses) { this(available,rating,position,wins,losses,false); }
        public static final Pvp UNAVAILABLE=new Pvp(false,0,0,0,0);
        public static final Pvp LOADING=new Pvp(false,0,0,0,0,true);
        public Pvp { if(position<0 || wins<0 || losses<0 || (available && loading)) throw new IllegalArgumentException("Invalid PvP values"); }
        public double winRate() { long total=(long)wins+losses; return total==0 ? 0 : wins*100D/total; }
    }
    public record Gear(String slot,ItemStack stack) {
        public Gear { if(!SLOTS.contains(slot) || stack==null) throw new IllegalArgumentException("Invalid display slot"); stack=stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public record EquippedSkill(String id,String key,String texture,String slot,boolean enabled,List<String> bonuses) {
        public EquippedSkill {
            bounded(id,128);bounded(key,192);bounded(texture,256);bounded(slot,64);
            bonuses=List.copyOf(bonuses);if(bonuses.size()>8) throw new IllegalArgumentException("Too many skill bonuses");
            bonuses.forEach(s -> bounded(s,256));
        }
    }
    public ProfileDetails owner(boolean value) { return new ProfileDetails(value,capturedAt,title,titleColor,pvp,gear,skills); }
    public ProfileDetails pvp(Pvp value) { return new ProfileDetails(owner,capturedAt,title,titleColor,value,gear,skills); }
    public void write(RegistryFriendlyByteBuf b) {
        b.writeBoolean(owner);b.writeLong(capturedAt);b.writeUtf(title,128);b.writeInt(titleColor);
        b.writeBoolean(pvp.available);b.writeInt(pvp.rating);b.writeVarInt(pvp.position);b.writeVarInt(pvp.wins);b.writeVarInt(pvp.losses);
        b.writeBoolean(pvp.loading);
        b.writeVarInt(gear.size());for(var g:gear) { b.writeUtf(g.slot,32);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,g.stack); }
        b.writeVarInt(skills.size());for(var s:skills) {
            b.writeUtf(s.id,128);b.writeUtf(s.key,192);b.writeUtf(s.texture,256);b.writeUtf(s.slot,64);b.writeBoolean(s.enabled);
            b.writeVarInt(s.bonuses.size());for(var line:s.bonuses) b.writeUtf(line,256);
        }
    }
    public static ProfileDetails read(RegistryFriendlyByteBuf b) {
        boolean owner=b.readBoolean();long time=b.readLong();String title=b.readUtf(128);int color=b.readInt();
        var pvp=new Pvp(b.readBoolean(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());
        int count=count(b,10);var gear=new ArrayList<Gear>(count);
        for(int i=0;i<count;i++) gear.add(new Gear(b.readUtf(32),ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
        count=count(b,64);var skills=new ArrayList<EquippedSkill>(count);
        for(int i=0;i<count;i++) {
            String id=b.readUtf(128),key=b.readUtf(192),texture=b.readUtf(256),slot=b.readUtf(64);boolean enabled=b.readBoolean();
            int n=count(b,8);var lines=new ArrayList<String>(n);for(int j=0;j<n;j++) lines.add(b.readUtf(256));
            skills.add(new EquippedSkill(id,key,texture,slot,enabled,lines));
        }
        return new ProfileDetails(owner,time,title,color,pvp,gear,skills);
    }
    private static int count(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt();if(n<0 || n>max) throw new IllegalArgumentException("Invalid profile list size");return n; }
    private static void bounded(String s,int max) { if(s==null || s.length()>max) throw new IllegalArgumentException("Invalid profile text"); }
}
