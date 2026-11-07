package com.ambition.neoworld.navigation;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Last observed NPC positions survive chunk unload; no forced chunk loading or world scan. */
public final class NavigationNpcDirectory extends SavedData {
    private static final Factory<NavigationNpcDirectory> FACTORY=new Factory<>(NavigationNpcDirectory::new,NavigationNpcDirectory::load);
    public record Entry(String name,NavigationSavedData.Target target) {}
    public record Selection(String name,NavigationSavedData.Target target,boolean fallback) {}
    private final Map<UUID,Entry> entries=new HashMap<>();
    public static NavigationNpcDirectory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"neoworld_navigation_npcs");
    }
    public void observe(String name,NavigationSavedData.Target target) {
        if(name==null||target.npc()==null) throw new IllegalArgumentException("NPC name and UUID are required");
        if(!entries.containsKey(target.npc())&&entries.size()>=8192) return;
        Entry next=new Entry(name,target);
        if(!next.equals(entries.put(target.npc(),next))) setDirty();
    }
    public void forget(UUID id) { if(entries.remove(id)!=null) setDirty(); }
    public Selection select(String name,String dimension,double x,double y,double z) {
        var named=entries.values().stream().filter(e->e.name().equals(name)).toList();
        var candidates=named.isEmpty() ? entries.values().stream().filter(e->e.target().dimension().equals(dimension)).toList() : named;
        // Distances across dimensions are meaningless. Prefer this dimension, then stable IDs for another dimension.
        Comparator<Entry> order=Comparator.comparingInt((Entry e)->e.target().dimension().equals(dimension)?0:1)
                .thenComparingDouble(e->e.target().dimension().equals(dimension) ? distance(e.target(),x,y,z) : 0)
                .thenComparing(e->e.target().dimension()).thenComparing(e->e.target().npc());
        return candidates.stream().min(order).map(e->new Selection(e.name(),e.target(),named.isEmpty())).orElse(null);
    }
    private static double distance(NavigationSavedData.Target t,double x,double y,double z) {
        return (t.x()-x)*(t.x()-x)+(t.y()-y)*(t.y()-y)+(t.z()-z)*(t.z()-z);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        ListTag values=new ListTag();
        for(var entry:entries.values()) {
            var target=entry.target(); CompoundTag value=new CompoundTag();
            value.putString("name",entry.name()); value.putUUID("uuid",target.npc()); value.putString("dimension",target.dimension());
            value.putDouble("x",target.x()); value.putDouble("y",target.y()); value.putDouble("z",target.z()); values.add(value);
        }
        tag.put("npcs",values); return tag;
    }
    public static NavigationNpcDirectory load(CompoundTag tag,HolderLookup.Provider provider) {
        var data=new NavigationNpcDirectory(); var values=tag.getList("npcs",Tag.TAG_COMPOUND); int invalid=0;
        for(int i=0;i<values.size();i++) try {
            var value=values.getCompound(i);
            if(!value.hasUUID("uuid")) throw new IllegalArgumentException("Missing NPC UUID");
            data.observe(value.getString("name"),new NavigationSavedData.Target(value.getString("dimension"),
                    value.getDouble("x"),value.getDouble("y"),value.getDouble("z"),3,value.getUUID("uuid")));
        } catch(IllegalArgumentException ex) { invalid++; }
        if(invalid>0) NeoWorld.LOGGER.warn("Ignored {} invalid navigation NPC records",invalid);
        return data;
    }
}
