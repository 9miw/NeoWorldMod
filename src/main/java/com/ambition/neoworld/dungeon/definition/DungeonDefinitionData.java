package com.ambition.neoworld.dungeon.definition;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import com.ambition.neoworld.dungeon.structure.DungeonTemplatePiece;

import net.minecraft.core.BlockPos;
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

    public void updateStructurePieces(DungeonDefinition definition, java.util.List<DungeonTemplatePiece> pieces) {
        definition.setStructurePieces(pieces);
        setDirty();
    }

    public void updateBoss(DungeonDefinition definition, DungeonBossConfig boss) {
        definition.setBoss(boss);
        setDirty();
    }

    public void addMonsterWave(DungeonDefinition definition, DungeonMonsterWave wave) {
        definition.addMonsterWave(wave);
        setDirty();
    }

    public void clearMonsterWaves(DungeonDefinition definition) {
        definition.clearMonsterWaves();
        setDirty();
    }

    public void updateReward(DungeonDefinition definition, DungeonRewardConfig reward) {
        definition.setReward(reward);
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
            ListTag structurePieces = new ListTag();
            for (DungeonTemplatePiece piece : definition.structurePieces()) {
                CompoundTag pieceTag = new CompoundTag();
                pieceTag.putString("templateId", piece.templateId().toString());
                pieceTag.putInt("offsetX", piece.offset().getX());
                pieceTag.putInt("offsetY", piece.offset().getY());
                pieceTag.putInt("offsetZ", piece.offset().getZ());
                structurePieces.add(pieceTag);
            }
            entry.put("structurePieces", structurePieces);
            if (definition.boss() != null) {
                CompoundTag boss = new CompoundTag();
                boss.putInt("cloneTab", definition.boss().cloneTab());
                boss.putString("cloneName", definition.boss().cloneName());
                entry.put("boss", boss);
            }
            ListTag waves = new ListTag();
            for (DungeonMonsterWave wave : definition.monsterWaves()) {
                CompoundTag waveTag = new CompoundTag();
                waveTag.putInt("cloneTab", wave.cloneTab());
                waveTag.putString("cloneName", wave.cloneName());
                waveTag.putInt("count", wave.count());
                waves.add(waveTag);
            }
            entry.put("monsterWaves", waves);
            if (definition.reward() != null) {
                CompoundTag reward = new CompoundTag();
                reward.putString("lootTableId", definition.reward().lootTableId().toString());
                reward.putInt("experience", definition.reward().experience());
                reward.putLong("money", definition.reward().money());
                entry.put("reward", reward);
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
            java.util.List<DungeonTemplatePiece> structurePieces = new java.util.ArrayList<>();
            ListTag pieceTags = entry.getList("structurePieces", Tag.TAG_COMPOUND);
            for (int pieceIndex = 0; pieceIndex < pieceTags.size(); pieceIndex++) {
                CompoundTag pieceTag = pieceTags.getCompound(pieceIndex);
                ResourceLocation templateId = ResourceLocation.tryParse(pieceTag.getString("templateId"));
                if (templateId != null) {
                    structurePieces.add(new DungeonTemplatePiece(templateId, new BlockPos(
                            pieceTag.getInt("offsetX"),
                            pieceTag.getInt("offsetY"),
                            pieceTag.getInt("offsetZ")
                    )));
                }
            }
            DungeonBossConfig boss = null;
            if (entry.contains("boss", Tag.TAG_COMPOUND)) {
                CompoundTag bossTag = entry.getCompound("boss");
                String cloneName = bossTag.getString("cloneName").trim();
                int cloneTab = bossTag.getInt("cloneTab");
                if (!cloneName.isEmpty() && cloneTab > 0) {
                    boss = new DungeonBossConfig(cloneTab, cloneName);
                }
            }
            java.util.List<DungeonMonsterWave> monsterWaves = new java.util.ArrayList<>();
            ListTag waves = entry.getList("monsterWaves", Tag.TAG_COMPOUND);
            for (int waveIndex = 0; waveIndex < waves.size(); waveIndex++) {
                CompoundTag waveTag = waves.getCompound(waveIndex);
                String cloneName = waveTag.getString("cloneName").trim();
                int cloneTab = waveTag.getInt("cloneTab");
                if (!cloneName.isEmpty() && cloneTab > 0 && waveTag.getInt("count") > 0) {
                    monsterWaves.add(new DungeonMonsterWave(cloneTab, cloneName, waveTag.getInt("count")));
                }
            }
            DungeonRewardConfig reward = null;
            if (entry.contains("reward", Tag.TAG_COMPOUND)) {
                CompoundTag rewardTag = entry.getCompound("reward");
                ResourceLocation lootTableId = ResourceLocation.tryParse(rewardTag.getString("lootTableId"));
                if (lootTableId != null) {
                    reward = new DungeonRewardConfig(
                            lootTableId,
                            Math.max(0, rewardTag.getInt("experience")),
                            Math.max(0L, rewardTag.getLong("money"))
                    );
                }
            }
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
                    structurePieces,
                    boss,
                    monsterWaves,
                    reward,
                    status
            );
            data.definitions.put(id, definition);
        }
        return data;
    }
}
