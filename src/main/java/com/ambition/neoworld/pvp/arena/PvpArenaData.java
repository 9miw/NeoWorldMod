package com.ambition.neoworld.pvp.arena;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Arena templates belong to the world, independently of dungeon definitions. */
public final class PvpArenaData extends SavedData {
    private static final Factory<PvpArenaData> FACTORY = new Factory<>(PvpArenaData::new, PvpArenaData::load);
    private final Map<String, ResourceLocation> arenas = new TreeMap<>();

    public static PvpArenaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_pvp_arenas");
    }

    public ResourceLocation template(String id) { return arenas.get(id); }
    public Map<String, ResourceLocation> all() { return Map.copyOf(arenas); }

    public boolean register(String id, ResourceLocation template) {
        if (!id.matches("[a-z0-9_]+") || arenas.containsKey(id)) return false;
        arenas.put(id, template);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag entries = new CompoundTag();
        arenas.forEach((id, template) -> entries.putString(id, template.toString()));
        tag.put("arenas", entries);
        return tag;
    }

    private static PvpArenaData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpArenaData data = new PvpArenaData();
        CompoundTag entries = tag.getCompound("arenas");
        for (String id : entries.getAllKeys()) {
            ResourceLocation template = ResourceLocation.tryParse(entries.getString(id));
            if (id.matches("[a-z0-9_]+") && template != null) data.arenas.put(id, template);
        }
        return data;
    }
}
