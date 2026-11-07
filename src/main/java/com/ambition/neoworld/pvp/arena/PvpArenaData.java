package com.ambition.neoworld.pvp.arena;

import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Arena templates belong to the world, independently of dungeon definitions. */
public final class PvpArenaData extends SavedData {
    private static final Factory<PvpArenaData> FACTORY = new Factory<>(PvpArenaData::new, PvpArenaData::load);
    public record Arena(List<PvpArenaPiece> pieces, boolean validated) {
        public Arena(List<PvpArenaPiece> pieces) { this(pieces,!pieces.isEmpty()); }
        public Arena {
            pieces=List.copyOf(pieces);
            if(pieces.size()>512) throw new IllegalArgumentException("Too many PvP pieces");
        }
    }
    private final Map<String, Arena> arenas = new TreeMap<>();
    private final Map<ResourceLocation,List<PvpArenaPiece>> maps = new TreeMap<>();
    private com.ambition.neoworld.world.structure.StructureCatalogData sharedMaps;
    public Map<ResourceLocation,List<PvpArenaPiece>> legacyMaps() { return Map.copyOf(maps); }
    public void attachStructureCatalog(com.ambition.neoworld.world.structure.StructureCatalogData catalog) { sharedMaps = catalog; }
    public static List<com.ambition.neoworld.world.structure.StructurePiece> worldPieces(List<PvpArenaPiece> pieces) {
        return pieces.stream().map(p -> new com.ambition.neoworld.world.structure.StructurePiece(p.template(),p.offset())).toList();
    }
    public static List<PvpArenaPiece> arenaPieces(List<com.ambition.neoworld.world.structure.StructurePiece> pieces) {
        return pieces.stream().map(p -> new PvpArenaPiece(p.templateId(),p.offset())).toList();
    }
    public void relinkStructures(List<PvpArenaPiece> original,List<PvpArenaPiece> replacement) {
        var users=mapUsers(original);
        if(users.stream().anyMatch(id -> !pieces(id).equals(original))) throw new IllegalArgumentException("Partial arena references");
        users.forEach(id -> arenas.put(id,new Arena(replacement,false)));setDirty();
    }
    public java.util.Set<ResourceLocation> mapIds() {
        if(sharedMaps==null)return java.util.Set.copyOf(maps.keySet());
        return sharedMaps.mapIds().stream().filter(id -> map(id)!=null).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public List<PvpArenaPiece> map(ResourceLocation id) {
        if(sharedMaps==null)return maps.get(id);
        try { return sharedMaps.map(id).map(PvpArenaData::arenaPieces).orElse(null); }
        catch(IllegalArgumentException exception) { return null; }
    }
    public List<String> mapUsers(List<PvpArenaPiece> pieces) {
        var ids=pieces.stream().map(PvpArenaPiece::template).collect(java.util.stream.Collectors.toSet());
        return arenas.entrySet().stream().filter(e -> e.getValue().pieces().stream().anyMatch(p -> ids.contains(p.template())))
                .map(Map.Entry::getKey).toList();
    }
    public void addMap(ResourceLocation id,List<PvpArenaPiece> pieces) {
        if(sharedMaps!=null) { sharedMaps.addMap(id,worldPieces(pieces));return; }
        if(maps.containsKey(id) || pieces.isEmpty() || pieces.size()>512) throw new IllegalArgumentException("Map exists or has invalid pieces");
        maps.put(id,List.copyOf(pieces)); setDirty();
    }
    public void replaceMap(ResourceLocation id,List<PvpArenaPiece> expected,List<PvpArenaPiece> pieces,ResourceLocation backup) {
        if(sharedMaps!=null) {
            if(mapUsers(expected).stream().anyMatch(user -> !pieces(user).equals(expected))) throw new IllegalArgumentException("Partial arena references");
            sharedMaps.replaceMap(id,worldPieces(expected),worldPieces(pieces),backup);relinkStructures(expected,pieces);return;
        }
        if(expected==null || !expected.equals(maps.get(id)) || maps.containsKey(backup) || pieces.isEmpty() || pieces.size()>512)
            throw new IllegalArgumentException("Map changed while saving");
        var users=mapUsers(expected);
        if(users.stream().anyMatch(user -> !arenas.get(user).pieces().equals(expected))) throw new IllegalArgumentException("Map has partial arena references");
        maps.put(backup,List.copyOf(expected)); maps.put(id,List.copyOf(pieces));
        users.forEach(user -> arenas.put(user,new Arena(pieces,false))); setDirty();
    }
    public void removeMap(ResourceLocation id,List<PvpArenaPiece> expected) {
        if(sharedMaps!=null) {
            if(!mapUsers(expected).isEmpty()) throw new IllegalArgumentException("Map still linked to arena");
            sharedMaps.removeMap(id,worldPieces(expected));return;
        }
        if(expected==null || !expected.equals(maps.get(id)) || !mapUsers(expected).isEmpty()) throw new IllegalArgumentException("Map changed or is in use");
        maps.remove(id); setDirty();
    }
    public void validated(String id) {
        var arena=arenas.get(id); if(arena==null || arena.pieces().isEmpty()) throw new IllegalArgumentException("Empty arena");
        arenas.put(id,new Arena(arena.pieces(),true)); setDirty();
    }
    private static ListTag encode(List<PvpArenaPiece> pieces) {
        ListTag result=new ListTag();
        for(var piece:pieces) {
            CompoundTag value=new CompoundTag(); value.putString("template",piece.template().toString());
            value.putInt("x",piece.offset().getX()); value.putInt("y",piece.offset().getY()); value.putInt("z",piece.offset().getZ()); result.add(value);
        }
        return result;
    }
    private static List<PvpArenaPiece> decode(ListTag values) {
        if(values.isEmpty() || values.size()>512) throw new IllegalArgumentException("Invalid map pieces");
        var result=new java.util.ArrayList<PvpArenaPiece>();
        for(int i=0;i<values.size();i++) {
            var value=values.getCompound(i);
            result.add(new PvpArenaPiece(ResourceLocation.parse(value.getString("template")),new BlockPos(value.getInt("x"),value.getInt("y"),value.getInt("z"))));
        }
        return List.copyOf(result);
    }

    public static PvpArenaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_pvp_arenas");
    }

    public Arena get(String id) { return arenas.get(id); }
    public List<PvpArenaPiece> pieces(String id) { var arena=get(id); return arena==null ? List.of() : arena.pieces(); }
    public Map<String, Arena> all() { return Map.copyOf(arenas); }

    public boolean create(String id) {
        if (id==null || !id.matches("[a-z0-9_]{1,64}") || arenas.containsKey(id)) return false;
        arenas.put(id, new Arena(List.of()));
        setDirty();
        return true;
    }
    public boolean removeArena(String id) {
        if(arenas.remove(id)==null) return false;
        setDirty(); return true;
    }
    public void update(String id,Arena expected,List<PvpArenaPiece> pieces) {
        if(expected==null || arenas.get(id)!=expected) throw new IllegalArgumentException("Arena changed while saving");
        if(pieces.isEmpty()) throw new IllegalArgumentException("Arena needs at least one structure");
        arenas.put(id,new Arena(pieces)); setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        arenas.forEach((id, arena) -> {
            CompoundTag entry=new CompoundTag(); ListTag pieces=new ListTag();
            for(var piece:arena.pieces()) {
                CompoundTag value=new CompoundTag(); value.putString("template",piece.template().toString());
                value.putInt("x",piece.offset().getX()); value.putInt("y",piece.offset().getY()); value.putInt("z",piece.offset().getZ()); pieces.add(value);
            }
            entry.put("pieces",pieces); entry.putBoolean("validated",arena.validated()); entries.put(id,entry);
        });
        CompoundTag catalog=new CompoundTag(); maps.forEach((id,pieces) -> catalog.put(id.toString(),encode(pieces)));
        tag.put("maps",catalog);
        tag.putInt("format",3);
        tag.put("arenas", entries);
        return tag;
    }

    public static PvpArenaData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpArenaData data = new PvpArenaData();
        CompoundTag entries = tag.getCompound("arenas");
        for (String id : entries.getAllKeys()) {
            if(!id.matches("[a-z0-9_]+")) continue;
            try {
                if(entries.contains(id,Tag.TAG_STRING)) {
                    var template=ResourceLocation.tryParse(entries.getString(id));
                    if(template!=null) data.arenas.put(id,new Arena(List.of(new PvpArenaPiece(template,BlockPos.ZERO))));
                    continue;
                }
                if(!entries.contains(id,Tag.TAG_COMPOUND)) continue;
                var values=entries.getCompound(id).getList("pieces",Tag.TAG_COMPOUND);
                if(values.size()>512) continue;
                var pieces=new java.util.ArrayList<PvpArenaPiece>();
                for(int i=0;i<values.size();i++) {
                    var value=values.getCompound(i);
                    pieces.add(new PvpArenaPiece(ResourceLocation.parse(value.getString("template")),new BlockPos(value.getInt("x"),value.getInt("y"),value.getInt("z"))));
                }
                data.arenas.put(id,new Arena(pieces,!pieces.isEmpty() && (tag.getInt("format")<3 || entries.getCompound(id).getBoolean("validated"))));
            } catch(RuntimeException exception) {
                com.ambition.neoworld.NeoWorld.LOGGER.warn("Ignoring invalid PvP arena definition {}",id);
            }
        }
        var catalog=tag.getCompound("maps");
        for(var name:catalog.getAllKeys()) try { data.maps.put(ResourceLocation.parse(name),decode(catalog.getList(name,Tag.TAG_COMPOUND))); }
        catch(RuntimeException exception) { com.ambition.neoworld.NeoWorld.LOGGER.warn("Ignoring invalid PvP map {}",name); }
        if(tag.getInt("format")<3) data.arenas.forEach((id,arena) -> {
            if(!arena.pieces().isEmpty()) data.maps.put(ResourceLocation.fromNamespaceAndPath("neoworld","pvp/legacy/"+id),arena.pieces());
        });
        if(tag.getInt("format")<3 && !data.arenas.isEmpty()) data.setDirty();
        return data;
    }
}
