package com.ambition.neoworld.dungeon.definition;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ambition.neoworld.dungeon.room.DungeonRoomType;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world persistent dungeon definitions configured by administrators. */
public final class DungeonDefinitionData extends SavedData {
    private static final String DATA_NAME = "neoworld_dungeons";
    private static final Factory<DungeonDefinitionData> FACTORY = new Factory<>(
            DungeonDefinitionData::new,
            DungeonDefinitionData::load
    );

    private final Map<String, DungeonDefinition> definitions = new LinkedHashMap<>();

    public static DungeonDefinitionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<DungeonDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<DungeonDefinition> all() {
        return java.util.List.copyOf(definitions.values());
    }

    public Optional<DungeonDefinition> create(String id) {
        if (!id.matches("[a-z0-9_]+") || definitions.containsKey(id)) {
            return Optional.empty();
        }
        DungeonDefinition definition = new DungeonDefinition(id);
        definitions.put(id, definition);
        setDirty();
        return Optional.of(definition);
    }

    public void updateName(DungeonDefinition definition, String displayName) {
        definition.setDisplayName(displayName);
        setDirty();
    }

    public void updateMinimumLevel(DungeonDefinition definition, int minimumLevel) {
        definition.setMinimumLevel(minimumLevel);
        setDirty();
    }

    public void updateTimeLimit(DungeonDefinition definition, int seconds) {
        definition.setTimeLimitSeconds(seconds);
        setDirty();
    }

    public void updateRoomTypes(DungeonDefinition definition, EnumSet<DungeonRoomType> roomTypes) {
        definition.setRoomTypes(roomTypes);
        setDirty();
    }

    public void updateStructure(DungeonDefinition definition, ResourceLocation structureId) {
        definition.setStructureId(structureId);
        setDirty();
    }

    public void updateStatus(DungeonDefinition definition, DungeonDefinitionStatus status) {
        definition.setStatus(status);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (DungeonDefinition definition : definitions.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", definition.id());
            entry.putString("displayName", definition.displayName());
            entry.putInt("minimumLevel", definition.minimumLevel());
            entry.putInt("timeLimitSeconds", definition.timeLimitSeconds());
            entry.putString("roomTypes", definition.roomTypes().stream()
                    .map(DungeonRoomType::commandName)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(",")));
            if (definition.structureId() != null) {
                entry.putString("structureId", definition.structureId().toString());
            }
            entry.putString("status", definition.status().name());
            list.add(entry);
        }
        tag.put("definitions", list);
        return tag;
    }

    private static DungeonDefinitionData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonDefinitionData data = new DungeonDefinitionData();
        ListTag list = tag.getList("definitions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            if (!id.matches("[a-z0-9_]+")) continue;

            EnumSet<DungeonRoomType> roomTypes = EnumSet.noneOf(DungeonRoomType.class);
            for (String value : entry.getString("roomTypes").split(",")) {
                DungeonRoomType.parse(value).ifPresent(roomTypes::add);
            }
            ResourceLocation structureId = ResourceLocation.tryParse(entry.getString("structureId"));
            DungeonDefinitionStatus status;
            try {
                status = DungeonDefinitionStatus.valueOf(entry.getString("status"));
            } catch (IllegalArgumentException exception) {
                status = DungeonDefinitionStatus.DRAFT;
            }

            DungeonDefinition definition = new DungeonDefinition(
                    id,
                    entry.getString("displayName"),
                    Math.max(1, entry.getInt("minimumLevel")),
                    Math.max(1, entry.getInt("timeLimitSeconds")),
                    roomTypes,
                    structureId,
                    status
            );
            data.definitions.put(id, definition);
        }
        return data;
    }
}
