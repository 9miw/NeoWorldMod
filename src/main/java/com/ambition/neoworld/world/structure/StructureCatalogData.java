package com.ambition.neoworld.world.structure;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Shared Overworld catalog. Template files retain their original IDs and paths. */
public final class StructureCatalogData extends SavedData {
    private static final Factory<StructureCatalogData> FACTORY = new Factory<>(StructureCatalogData::new, StructureCatalogData::load);
    private final Map<ResourceLocation,List<StructurePiece>> maps = new LinkedHashMap<>();
    private final Set<String> migrated = new HashSet<>();

    public static StructureCatalogData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"neoworld_structures");
    }
    public Set<ResourceLocation> mapIds() { return Set.copyOf(maps.keySet()); }
    public Optional<List<StructurePiece>> map(ResourceLocation id) { return Optional.ofNullable(maps.get(id)); }
    public void addMap(ResourceLocation id,List<StructurePiece> pieces) {
        validate(id,pieces);
        if(maps.containsKey(id)) throw new IllegalArgumentException("Map already exists: " + id);
        maps.put(id,List.copyOf(pieces));setDirty();
    }
    public void replaceMap(ResourceLocation id,List<StructurePiece> expected,List<StructurePiece> pieces,ResourceLocation backup) {
        validate(id,pieces);validate(backup,expected);
        if(!expected.equals(maps.get(id)) || maps.containsKey(backup)) throw new IllegalArgumentException("Map changed during update");
        maps.put(backup,List.copyOf(expected));maps.put(id,List.copyOf(pieces));setDirty();
    }
    public void removeMap(ResourceLocation id,List<StructurePiece> expected) {
        if(!expected.equals(maps.get(id))) throw new IllegalArgumentException("Map changed during removal");
        maps.remove(id);setDirty();
    }
    public void importLegacy(String source,Map<ResourceLocation,List<StructurePiece>> legacy) {
        if(migrated.contains(source)) return;
        // Validate the complete source before changing either the catalog or migration marker.
        legacy.forEach((id,pieces) -> {
            validate(id,pieces);
            if(maps.containsKey(id) && !maps.get(id).equals(pieces))
                throw new IllegalStateException("Conflicting legacy structure ID " + id + " from " + source + "; original data preserved");
        });
        legacy.forEach((id,pieces) -> maps.putIfAbsent(id,List.copyOf(pieces)));
        migrated.add(source);setDirty();
    }
    private static void validate(ResourceLocation id,List<StructurePiece> pieces) {
        if(id == null || pieces.isEmpty() || pieces.size()>512) throw new IllegalArgumentException("Invalid structure map");
        List.copyOf(pieces);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        maps.forEach((id,pieces) -> {
            ListTag list = new ListTag();
            for(var piece:pieces) {
                CompoundTag value = new CompoundTag();value.putString("id",piece.templateId().toString());
                value.putInt("x",piece.offset().getX());value.putInt("y",piece.offset().getY());value.putInt("z",piece.offset().getZ());list.add(value);
            }
            entries.put(id.toString(),list);
        });
        tag.put("maps",entries);ListTag sources = new ListTag();
        migrated.stream().sorted().forEach(source -> sources.add(StringTag.valueOf(source)));
        tag.put("migrated",sources);tag.putInt("format",1);return tag;
    }
    public static StructureCatalogData load(CompoundTag tag,HolderLookup.Provider registries) {
        if(tag.getInt("format")>1) throw new IllegalStateException("Unsupported structure catalog format");
        if(tag.contains("maps") && !tag.contains("maps",Tag.TAG_COMPOUND))throw new IllegalStateException("Malformed structure catalog; original data must be repaired");
        var data = new StructureCatalogData();var entries = tag.getCompound("maps");
        for(String key:entries.getAllKeys()) {
            var id = ResourceLocation.parse(key);var list = entries.getList(key,Tag.TAG_COMPOUND);
            List<StructurePiece> pieces = new ArrayList<>();
            for(int i=0;i<list.size();i++) {
                var value = list.getCompound(i);
                pieces.add(new StructurePiece(ResourceLocation.parse(value.getString("id")),new BlockPos(value.getInt("x"),value.getInt("y"),value.getInt("z"))));
            }
            validate(id,pieces);data.maps.put(id,List.copyOf(pieces));
        }
        var sources = tag.getList("migrated",Tag.TAG_STRING);
        for(int i=0;i<sources.size();i++) data.migrated.add(sources.getString(i));
        return data;
    }
}
