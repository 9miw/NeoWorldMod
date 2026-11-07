package com.ambition.neoworld.navigation;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One graph per save, shared across dimensions. Unloaded chunks do not erase markers. */
public final class NavigationSavedData extends SavedData {
    private static final Factory<NavigationSavedData> FACTORY=new Factory<>(NavigationSavedData::new,NavigationSavedData::load);
    public record Target(String dimension,double x,double y,double z,double radius,UUID npc) {
        public Target {
            if(ResourceLocation.tryParse(dimension)==null || !Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                    || !Double.isFinite(radius)||radius<1||radius>32 || Math.abs(x)>30_000_000 || Math.abs(z)>30_000_000)
                throw new IllegalArgumentException("Invalid quest destination");
        }
    }
    public record Binding(Target objective,Target turnIn) {}
    private final NavigationGraph graph=new NavigationGraph();
    private final Map<Integer,Binding> bindings=new TreeMap<>();
    private double linkRadius=10;
    private long bindingRevision;
    public NavigationGraph graph() { return graph; }
    public double linkRadius() { return linkRadius; }
    public long revision() { return graph.revision()+bindingRevision; }
    public Binding binding(int quest) { return bindings.get(quest); }
    public void bind(int quest,boolean turnIn,Target target) {
        if(quest<0 || (!bindings.containsKey(quest)&&bindings.size()>=4096)) throw new IllegalArgumentException("Invalid quest ID or binding limit reached");
        Binding old=bindings.getOrDefault(quest,new Binding(null,null));
        bindings.put(quest,turnIn ? new Binding(old.objective(),target) : new Binding(target,old.turnIn()));
        bindingRevision++; setDirty();
    }
    public void unbind(int quest) { if(bindings.remove(quest)!=null) { bindingRevision++; setDirty(); } }
    public void radius(double value) {
        if(!Double.isFinite(value)||value<1||value>16) throw new IllegalArgumentException("Link radius must be 1–16");
        linkRadius=value; bindingRevision++; setDirty();
    }
    public static NavigationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"neoworld_quest_navigation");
    }
    public static String id(ServerLevel level,BlockPos pos) { return level.dimension().location()+"/"+pos.asLong(); }
    public void placed(ServerLevel level,BlockPos pos) {
        String id=id(level,pos);
        if(graph.node(id)==null) {
            if(graph.nodes().size()>=8192) { NeoWorld.LOGGER.warn("Navigation marker limit reached at {}",pos); return; }
            graph.add(new NavigationGraph.Node(id,level.dimension().location().toString(),pos.getX()+.5,pos.getY(),pos.getZ()+.5));
        }
        refresh(level,id); setDirty();
    }
    public void removed(ServerLevel level,BlockPos pos) { graph.remove(id(level,pos)); setDirty(); }
    public void refresh(ServerLevel level,String id) {
        // Never invalidate persisted links merely because their chunks are currently unloaded.
        NavigationGraph.Node a=graph.node(id); if(a==null) return;
        var candidates=new LinkedHashMap<String,NavigationGraph.Node>();
        graph.nearby(a.dimension(),a.x(),a.y(),a.z(),linkRadius).forEach(n->candidates.put(n.id(),n));
        graph.neighbors(id).forEach(neighbor->candidates.put(neighbor,graph.node(neighbor)));
        for(var b:candidates.values()) {
            if(a.id().equals(b.id())||!a.dimension().equals(b.dimension())) continue;
            if(graph.override(a.id(),b.id())!=null) continue;
            if(a.distance(b)>linkRadius) { graph.disconnect(a.id(),b.id()); continue; }
            if(!NavigationWalk.loaded(level,a,b)) continue;
            if(NavigationWalk.between(level,a,b)) graph.connect(a.id(),b.id());
            else graph.disconnect(a.id(),b.id());
        }
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        ListTag nodes=new ListTag();
        for(var n:graph.nodes()) {
            CompoundTag value=new CompoundTag(); value.putString("id",n.id()); value.putString("dimension",n.dimension());
            value.putDouble("x",n.x()); value.putDouble("y",n.y()); value.putDouble("z",n.z()); nodes.add(value);
        }
        tag.put("nodes",nodes); tag.putDouble("linkRadius",linkRadius);
        ListTag edges=new ListTag();
        for(var e:graph.edges()) { CompoundTag value=new CompoundTag(); value.putString("a",e.a()); value.putString("b",e.b()); edges.add(value); }
        tag.put("edges",edges);
        ListTag overrides=new ListTag();
        graph.overrides().forEach((e,enabled)->{ CompoundTag value=new CompoundTag(); value.putString("a",e.a()); value.putString("b",e.b()); value.putBoolean("enabled",enabled); overrides.add(value); });
        tag.put("overrides",overrides);
        ListTag quests=new ListTag();
        bindings.forEach((id,b)->{ CompoundTag value=new CompoundTag(); value.putInt("quest",id);
            if(b.objective()!=null) value.put("objective",writeTarget(b.objective()));
            if(b.turnIn()!=null) value.put("turnIn",writeTarget(b.turnIn())); quests.add(value); });
        tag.put("bindings",quests); return tag;
    }
    private static CompoundTag writeTarget(Target t) {
        CompoundTag value=new CompoundTag(); value.putString("dimension",t.dimension());
        value.putDouble("x",t.x()); value.putDouble("y",t.y()); value.putDouble("z",t.z()); value.putDouble("radius",t.radius());
        if(t.npc()!=null) value.putUUID("npc",t.npc()); return value;
    }
    private static Target readTarget(CompoundTag t) {
        return new Target(t.getString("dimension"),t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),t.getDouble("radius"),t.hasUUID("npc")?t.getUUID("npc"):null);
    }
    public static NavigationSavedData load(CompoundTag tag,HolderLookup.Provider provider) {
        NavigationSavedData data=new NavigationSavedData(); int rejected=0;
        ListTag nodes=tag.getList("nodes",Tag.TAG_COMPOUND);
        for(int i=0;i<nodes.size();i++) try {
            var n=nodes.getCompound(i);
            if(ResourceLocation.tryParse(n.getString("dimension"))==null) throw new IllegalArgumentException();
            data.graph.add(new NavigationGraph.Node(n.getString("id"),n.getString("dimension"),n.getDouble("x"),n.getDouble("y"),n.getDouble("z")));
        } catch(IllegalArgumentException ex) { rejected++; }
        ListTag edges=tag.getList("edges",Tag.TAG_COMPOUND);
        for(int i=0;i<edges.size();i++) try { var e=edges.getCompound(i); data.graph.connect(e.getString("a"),e.getString("b")); }
        catch(IllegalArgumentException ex) { rejected++; }
        ListTag overrides=tag.getList("overrides",Tag.TAG_COMPOUND);
        for(int i=0;i<overrides.size();i++) try { var e=overrides.getCompound(i); data.graph.override(e.getString("a"),e.getString("b"),e.getBoolean("enabled")); }
        catch(IllegalArgumentException ex) { rejected++; }
        if(tag.contains("linkRadius")) try { data.radius(tag.getDouble("linkRadius")); } catch(IllegalArgumentException ex) { rejected++; }
        ListTag quests=tag.getList("bindings",Tag.TAG_COMPOUND);
        for(int i=0;i<quests.size();i++) try {
            var q=quests.getCompound(i); int id=q.getInt("quest");
            if(q.contains("objective")) data.bind(id,false,readTarget(q.getCompound("objective")));
            if(q.contains("turnIn")) data.bind(id,true,readTarget(q.getCompound("turnIn")));
        } catch(IllegalArgumentException ex) { rejected++; }
        if(rejected>0) NeoWorld.LOGGER.warn("Ignored {} invalid quest navigation records",rejected);
        return data;
    }
}
