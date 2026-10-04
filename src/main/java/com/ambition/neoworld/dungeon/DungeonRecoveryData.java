package com.ambition.neoworld.dungeon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persists return locations so players can recover after a server restart. */
public final class DungeonRecoveryData extends SavedData {
    private static final String DATA_NAME = "neoworld_dungeon_recovery";
    private static final Factory<DungeonRecoveryData> FACTORY = new Factory<>(
            DungeonRecoveryData::new,
            DungeonRecoveryData::load
    );

    private final Map<UUID, DungeonReturnPoint> returnPoints = new LinkedHashMap<>();

    public static DungeonRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void put(UUID playerId, DungeonReturnPoint point) {
        returnPoints.put(playerId, point);
        setDirty();
    }

    public Optional<DungeonReturnPoint> remove(UUID playerId) {
        DungeonReturnPoint removed = returnPoints.remove(playerId);
        if (removed != null) setDirty();
        return Optional.ofNullable(removed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        returnPoints.forEach((playerId, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", point.dimensionId());
            entry.putDouble("x", point.x());
            entry.putDouble("y", point.y());
            entry.putDouble("z", point.z());
            entry.putFloat("yaw", point.yaw());
            entry.putFloat("pitch", point.pitch());
            players.put(playerId.toString(), entry);
        });
        tag.put("players", players);
        return tag;
    }

    private static DungeonRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonRecoveryData data = new DungeonRecoveryData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                CompoundTag entry = players.getCompound(key);
                data.returnPoints.put(playerId, new DungeonReturnPoint(
                        entry.getString("dimension"),
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        entry.getFloat("yaw"),
                        entry.getFloat("pitch")
                ));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed recovery entries instead of blocking world load.
            }
        }
        return data;
    }
}
