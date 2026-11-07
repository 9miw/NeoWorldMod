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
    private final Map<ResourceLocation, java.util.List<DungeonTemplatePiece>> maps = new LinkedHashMap<>();
    private com.ambition.neoworld.world.structure.StructureCatalogData sharedMaps;
    public Map<ResourceLocation,java.util.List<DungeonTemplatePiece>> legacyMaps() { return Map.copyOf(maps); }
    public void attachStructureCatalog(com.ambition.neoworld.world.structure.StructureCatalogData catalog) { sharedMaps = catalog; }
    public static java.util.List<com.ambition.neoworld.world.structure.StructurePiece> worldPieces(java.util.List<DungeonTemplatePiece> pieces) {
        return pieces.stream().map(p -> new com.ambition.neoworld.world.structure.StructurePiece(p.templateId(),p.offset())).toList();
    }
    public static java.util.List<DungeonTemplatePiece> dungeonPieces(java.util.List<com.ambition.neoworld.world.structure.StructurePiece> pieces) {
        return pieces.stream().map(p -> new DungeonTemplatePiece(p.templateId(),p.offset())).toList();
    }
    public void relinkStructures(java.util.List<DungeonTemplatePiece> original,java.util.List<DungeonTemplatePiece> replacement) {
        var users = mapUsers(original);
        if(users.stream().anyMatch(d -> !d.structurePieces().equals(original))) throw new IllegalArgumentException("Partial dungeon map references");
        users.forEach(d -> d.setStructurePieces(replacement));setDirty();
    }
    private final Map<ResourceLocation, java.util.List<CompoundTag>> lootPresets = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<Integer, DungeonLootRule>> lootRules = new LinkedHashMap<>();

    public DungeonLootRule lootRule(ResourceLocation id, int slot) {
        return lootRules.getOrDefault(id, Map.of()).getOrDefault(slot, DungeonLootRule.DEFAULT);
    }

    public void setLootRule(ResourceLocation id, int slot, DungeonLootRule rule) {
        var items = lootPresets.get(id);
        if (items == null || slot < 0 || slot >= items.size() || items.get(slot).isEmpty()) {
            throw new IllegalArgumentException("Missing reward or empty slot");
        }
        lootRules.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(slot, rule);
        definitions.values().stream().filter(definition -> definition.reward() != null
                && id.equals(definition.reward().presetId())).forEach(definition -> definition.setStatus(DungeonDefinitionStatus.DRAFT));
        setDirty();
    }

    public java.util.Set<ResourceLocation> lootPresetIds() { return java.util.Set.copyOf(lootPresets.keySet()); }

    public void addLootPreset(ResourceLocation id, java.util.List<CompoundTag> items) {
        if (lootPresets.containsKey(id) || items.isEmpty() || items.size() > 54 || items.stream().allMatch(CompoundTag::isEmpty)) {
            throw new IllegalArgumentException("Reward name exists, chest is empty or exceeds 54 slots");
        }
        lootPresets.put(id, items.stream().map(CompoundTag::copy).toList());
        setDirty();
    }

    public java.util.List<net.minecraft.world.item.ItemStack> lootPresetItems(ResourceLocation id, HolderLookup.Provider registries) {
        var items = lootPresets.get(id);
        if (items == null) throw new IllegalArgumentException("Missing saved reward: " + id);
        var decoded = items.stream().map(tag -> {
            var stack = net.minecraft.world.item.ItemStack.parseOptional(registries, tag);
            if (!tag.isEmpty() && stack.isEmpty()) throw new IllegalArgumentException("Invalid saved reward item: " + id);
            return stack;
        }).toList();
        for (int slot = 0; slot < decoded.size(); slot++) {
            var stack = decoded.get(slot);
            if (!stack.isEmpty() && lootRule(id, slot).maximum() > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("Amount exceeds item stack limit in slot " + (slot + 1));
            }
        }
        return decoded;
    }

    public java.util.Set<ResourceLocation> mapIds() { return sharedMaps == null ? java.util.Set.copyOf(maps.keySet()) : sharedMaps.mapIds(); }

    public Optional<java.util.List<DungeonTemplatePiece>> map(ResourceLocation id) {
        if(sharedMaps != null) return sharedMaps.map(id).map(DungeonDefinitionData::dungeonPieces);
        return Optional.ofNullable(maps.get(id));
    }

    public void addMap(ResourceLocation id, java.util.List<DungeonTemplatePiece> pieces) {
        if(sharedMaps != null) { sharedMaps.addMap(id,worldPieces(pieces)); return; }
        if (maps.containsKey(id) || pieces.isEmpty() || pieces.size() > 512) {
            throw new IllegalArgumentException("Map already exists or has invalid pieces: " + id);
        }
        maps.put(id, java.util.List.copyOf(pieces));
        setDirty();
    }

    public java.util.List<DungeonDefinition> mapUsers(java.util.List<DungeonTemplatePiece> pieces) {
        var ids = pieces.stream().map(DungeonTemplatePiece::templateId).collect(java.util.stream.Collectors.toSet());
        return definitions.values().stream().filter(definition -> definition.structurePieces().stream()
                .anyMatch(piece -> ids.contains(piece.templateId()))).toList();
    }

    public void replaceMap(ResourceLocation id, java.util.List<DungeonTemplatePiece> expected,
            java.util.List<DungeonTemplatePiece> replacement, ResourceLocation backup) {
        if(sharedMaps != null) {
            if(mapUsers(expected).stream().anyMatch(d -> !d.structurePieces().equals(expected))) throw new IllegalArgumentException("Partial dungeon map references");
            sharedMaps.replaceMap(id,worldPieces(expected),worldPieces(replacement),backup);
            relinkStructures(expected,replacement);return;
        }
        if (!expected.equals(maps.get(id)) || maps.containsKey(backup) || replacement.isEmpty() || replacement.size() > 512) {
            throw new IllegalArgumentException("Map changed during update");
        }
        var users = mapUsers(expected);
        if (users.stream().anyMatch(definition -> !definition.structurePieces().equals(expected))) {
            throw new IllegalArgumentException("A dungeon uses only part of this map; unlink it before updating");
        }
        maps.put(backup, java.util.List.copyOf(expected));
        maps.put(id, java.util.List.copyOf(replacement));
        users.forEach(definition -> definition.setStructurePieces(replacement));
        setDirty();
    }

    public void removeMap(ResourceLocation id, java.util.List<DungeonTemplatePiece> expected) {
        if(sharedMaps != null) {
            if(!mapUsers(expected).isEmpty()) throw new IllegalArgumentException("Map still linked to dungeon");
            sharedMaps.removeMap(id,worldPieces(expected));return;
        }
        if (!expected.equals(maps.get(id)) || !mapUsers(expected).isEmpty()) {
            throw new IllegalArgumentException("Map changed or is still linked to a dungeon");
        }
        maps.remove(id);
        setDirty();
    }

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

    public void updateImage(DungeonDefinition definition, ResourceLocation imageId) {
        definition.setImageId(imageId);
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

    public void updateMonsterWave(DungeonDefinition definition, int index, DungeonMonsterWave wave) {
        definition.replaceMonsterWave(index, wave);
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
        ListTag presets = new ListTag();
        lootPresets.forEach((id, items) -> {
            CompoundTag preset = new CompoundTag();
            preset.putString("id", id.toString());
            ListTag contents = new ListTag();
            items.forEach(item -> contents.add(item.copy()));
            preset.put("items", contents);
            ListTag rules = new ListTag();
            lootRules.getOrDefault(id, Map.of()).forEach((slot, rule) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.putDouble("chance", rule.chance());
                entry.putInt("minimum", rule.minimum());
                entry.putInt("maximum", rule.maximum());
                rules.add(entry);
            });
            if (!rules.isEmpty()) preset.put("rules", rules);
            presets.add(preset);
        });
        tag.put("lootPresets", presets);
        ListTag mapList = new ListTag();
        maps.forEach((id, pieces) -> {
            CompoundTag map = new CompoundTag();
            map.putString("id", id.toString());
            ListTag parts = new ListTag();
            for (DungeonTemplatePiece piece : pieces) {
                CompoundTag part = new CompoundTag();
                part.putString("id", piece.templateId().toString());
                part.putInt("x", piece.offset().getX());
                part.putInt("y", piece.offset().getY());
                part.putInt("z", piece.offset().getZ());
                parts.add(part);
            }
            map.put("parts", parts);
            mapList.add(map);
        });
        tag.put("maps", mapList);
        ListTag list = new ListTag();
        for (DungeonDefinition definition : definitions.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", definition.id());
            entry.putString("displayName", definition.displayName());
            if (definition.imageId() != null) entry.putString("imageId", definition.imageId().toString());
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
                waveTag.putString("spawnMode", wave.spawnMode().id());
                waves.add(waveTag);
            }
            entry.put("monsterWaves", waves);
            if (definition.reward() != null) {
                CompoundTag reward = new CompoundTag();
                if (definition.reward().presetId() != null) reward.putString("presetId", definition.reward().presetId().toString());
                else reward.putString("lootTableId", definition.reward().lootTableId().toString());
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
                    monsterWaves.add(new DungeonMonsterWave(cloneTab, cloneName, waveTag.getInt("count"),
                            DungeonMonsterWave.SpawnMode.fromSaved(waveTag.getString("spawnMode"))));
                }
            }
            DungeonRewardConfig reward = null;
            if (entry.contains("reward", Tag.TAG_COMPOUND)) {
                CompoundTag rewardTag = entry.getCompound("reward");
                ResourceLocation lootTableId = ResourceLocation.tryParse(rewardTag.getString("lootTableId"));
                ResourceLocation presetId = rewardTag.getString("presetId").isBlank() ? null
                        : ResourceLocation.tryParse(rewardTag.getString("presetId"));
                if (presetId != null) reward = new DungeonRewardConfig(null, presetId);
                else if (lootTableId != null) {
                    reward = new DungeonRewardConfig(
                            lootTableId
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
            ResourceLocation imageId = ResourceLocation.tryParse(entry.getString("imageId"));
            if (DungeonDefinition.isImageResource(imageId)) definition.setImageId(imageId);
            data.definitions.put(id, definition);
        }
        ListTag mapList = tag.getList("maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < mapList.size(); i++) {
            CompoundTag map = mapList.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(map.getString("id"));
            ListTag parts = map.getList("parts", Tag.TAG_COMPOUND);
            if (id == null || parts.isEmpty() || parts.size() > 512) continue;
            java.util.List<DungeonTemplatePiece> pieces = new java.util.ArrayList<>();
            for (int j = 0; j < parts.size(); j++) {
                CompoundTag part = parts.getCompound(j);
                ResourceLocation template = ResourceLocation.tryParse(part.getString("id"));
                if (template == null) break;
                pieces.add(new DungeonTemplatePiece(template, new BlockPos(part.getInt("x"), part.getInt("y"), part.getInt("z"))));
            }
            if (pieces.size() == parts.size()) data.maps.put(id, java.util.List.copyOf(pieces));
        }
        ListTag presets = tag.getList("lootPresets", Tag.TAG_COMPOUND);
        for (int i = 0; i < presets.size(); i++) {
            CompoundTag preset = presets.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(preset.getString("id"));
            ListTag items = preset.getList("items", Tag.TAG_COMPOUND);
            if (id == null || items.isEmpty() || items.size() > 54) continue;
            java.util.List<CompoundTag> contents = new java.util.ArrayList<>();
            for (int j = 0; j < items.size(); j++) contents.add(items.getCompound(j).copy());
            data.lootPresets.put(id, java.util.List.copyOf(contents));
            ListTag rules = preset.getList("rules", Tag.TAG_COMPOUND);
            for (int j = 0; j < rules.size(); j++) {
                CompoundTag entry = rules.getCompound(j);
                int slot = entry.getInt("slot");
                if (slot < 0 || slot >= contents.size() || contents.get(slot).isEmpty()) continue;
                DungeonLootRule rule;
                try { rule = new DungeonLootRule(entry.getDouble("chance"), entry.getInt("minimum"), entry.getInt("maximum")); }
                catch (IllegalArgumentException exception) { rule = new DungeonLootRule(0, 0, 0); }
                data.lootRules.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(slot, rule);
            }
        }
        return data;
    }
}
