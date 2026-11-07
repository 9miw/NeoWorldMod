package com.ambition.neoworld.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import noppes.npcs.blocks.tiles.TileWaypoint;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Observed Custom NPCs Waypoints survive chunk unload without loading terrain to search for them. */
public final class NavigationLocationDirectory extends SavedData {
    private static final Factory<NavigationLocationDirectory> FACTORY =
            new Factory<>(NavigationLocationDirectory::new, NavigationLocationDirectory::load);
    private record Entry(String name, NavigationSavedData.Target target) {}
    private final Map<String, Entry> entries = new HashMap<>();

    public static NavigationLocationDirectory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_navigation_locations");
    }

    public static void observe(ServerLevel level, TileWaypoint waypoint) {
        if (level.getServer().overworld() == null) return;
        get(level.getServer()).observe(waypoint.name, level.dimension().location().toString(),
                waypoint.getBlockPos(), waypoint.range);
    }

    public void observe(String name, String dimension, BlockPos pos, int range) {
        String key = dimension + "/" + pos.asLong();
        if (name == null || name.isEmpty()) {
            if (entries.remove(key) != null) setDirty();
            return;
        }
        if (!entries.containsKey(key) && entries.size() >= 8192) return;
        var next = new Entry(name, new NavigationSavedData.Target(dimension,
                pos.getX() + .5, pos.getY(), pos.getZ() + .5, Math.clamp(range, 1, 32), null));
        if (!next.equals(entries.put(key, next))) setDirty();
    }

    public NavigationSavedData.Target select(Collection<String> names, String dimension, double x, double y, double z) {
        return entries.values().stream()
                .filter(e -> names.stream().anyMatch(name -> name.equalsIgnoreCase(e.name())))
                .min(Comparator.comparingInt((Entry e) -> e.target().dimension().equals(dimension) ? 0 : 1)
                        .thenComparingDouble(e -> e.target().dimension().equals(dimension)
                                ? distance(e.target(), x, y, z) : 0)
                        .thenComparing(e -> e.target().dimension())
                        .thenComparingDouble(e -> e.target().x()).thenComparingDouble(e -> e.target().y())
                        .thenComparingDouble(e -> e.target().z()))
                .map(Entry::target).orElse(null);
    }

    /** Validate known candidates only if their chunks are already loaded. */
    public void refresh(MinecraftServer server, Collection<String> names) {
        for (var entry : java.util.List.copyOf(entries.values())) {
            if (names.stream().noneMatch(name -> name.equalsIgnoreCase(entry.name()))) continue;
            var target = entry.target();
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(target.dimension())));
            var pos = BlockPos.containing(target.x(), target.y(), target.z());
            if (level == null || !level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof TileWaypoint waypoint) observe(level, waypoint);
            else observe("", target.dimension(), pos, 1);
        }
    }

    private static double distance(NavigationSavedData.Target t, double x, double y, double z) {
        return (t.x()-x)*(t.x()-x) + (t.y()-y)*(t.y()-y) + (t.z()-z)*(t.z()-z);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var values = new ListTag();
        for (var entry : entries.values()) {
            var target = entry.target();
            var value = new CompoundTag();
            value.putString("name", entry.name());
            value.putString("dimension", target.dimension());
            value.putLong("pos", BlockPos.containing(target.x(), target.y(), target.z()).asLong());
            value.putInt("range", (int) target.radius());
            values.add(value);
        }
        tag.put("locations", values);
        return tag;
    }

    public static NavigationLocationDirectory load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new NavigationLocationDirectory();
        var values = tag.getList("locations", Tag.TAG_COMPOUND);
        for (int i = 0; i < values.size(); i++) {
            var value = values.getCompound(i);
            try {
                data.observe(value.getString("name"), value.getString("dimension"),
                        BlockPos.of(value.getLong("pos")), value.getInt("range"));
            } catch (IllegalArgumentException ignored) {
                // Invalid records must not prevent a world from loading.
            }
        }
        return data;
    }
}
