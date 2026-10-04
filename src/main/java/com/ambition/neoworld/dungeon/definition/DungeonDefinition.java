package com.ambition.neoworld.dungeon.definition;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import com.ambition.neoworld.dungeon.structure.DungeonTemplatePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class DungeonDefinition {
    private final String id;
    private String displayName;
    private int minimumLevel;
    private int timeLimitSeconds;
    private EnumSet<DungeonRoomType> roomTypes;
    private ResourceLocation structureId;
    private List<DungeonTemplatePiece> structurePieces;
    private DungeonBossConfig boss;
    private List<DungeonMonsterWave> monsterWaves;
    private DungeonRewardConfig reward;
    private DungeonDefinitionStatus status;

    DungeonDefinition(String id) {
        this(id, defaultDisplayName(id), 1, 1_200, EnumSet.allOf(DungeonRoomType.class), null,
                List.of(), null, List.of(), null,
                DungeonDefinitionStatus.DRAFT);
    }

    DungeonDefinition(
            String id,
            String displayName,
            int minimumLevel,
            int timeLimitSeconds,
            Set<DungeonRoomType> roomTypes,
            ResourceLocation structureId,
            List<DungeonTemplatePiece> structurePieces,
            DungeonBossConfig boss,
            List<DungeonMonsterWave> monsterWaves,
            DungeonRewardConfig reward,
            DungeonDefinitionStatus status
    ) {
        this.id = id;
        this.displayName = displayName;
        this.minimumLevel = minimumLevel;
        this.timeLimitSeconds = timeLimitSeconds;
        this.roomTypes = roomTypes.isEmpty()
                ? EnumSet.noneOf(DungeonRoomType.class)
                : EnumSet.copyOf(roomTypes);
        this.structureId = structureId;
        this.structurePieces = structurePieces.isEmpty() && structureId != null
                ? List.of(new DungeonTemplatePiece(structureId, BlockPos.ZERO))
                : List.copyOf(structurePieces);
        this.boss = boss;
        this.monsterWaves = List.copyOf(monsterWaves);
        this.reward = reward;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int minimumLevel() {
        return minimumLevel;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public Set<DungeonRoomType> roomTypes() {
        return Set.copyOf(roomTypes);
    }

    public ResourceLocation structureId() {
        return structureId;
    }

    public List<DungeonTemplatePiece> structurePieces() {
        return structurePieces;
    }

    public DungeonDefinitionStatus status() {
        return status;
    }

    public DungeonBossConfig boss() {
        return boss;
    }

    public List<DungeonMonsterWave> monsterWaves() {
        return monsterWaves;
    }

    public DungeonRewardConfig reward() {
        return reward;
    }

    public boolean supports(DungeonRoomType roomType) {
        return roomTypes.contains(roomType);
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setMinimumLevel(int minimumLevel) {
        this.minimumLevel = minimumLevel;
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setRoomTypes(Set<DungeonRoomType> roomTypes) {
        this.roomTypes = EnumSet.copyOf(roomTypes);
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setStructureId(ResourceLocation structureId) {
        this.structureId = structureId;
        this.structurePieces = structureId == null
                ? List.of()
                : List.of(new DungeonTemplatePiece(structureId, BlockPos.ZERO));
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setStructurePieces(List<DungeonTemplatePiece> structurePieces) {
        this.structurePieces = List.copyOf(structurePieces);
        this.structureId = structurePieces.isEmpty() ? null : structurePieces.getFirst().templateId();
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setBoss(DungeonBossConfig boss) {
        this.boss = boss;
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void addMonsterWave(DungeonMonsterWave wave) {
        this.monsterWaves = new java.util.ArrayList<>(monsterWaves);
        this.monsterWaves.add(wave);
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void clearMonsterWaves() {
        this.monsterWaves = List.of();
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setReward(DungeonRewardConfig reward) {
        this.reward = reward;
        this.status = DungeonDefinitionStatus.DRAFT;
    }

    void setStatus(DungeonDefinitionStatus status) {
        this.status = status;
    }

    private static String defaultDisplayName(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
