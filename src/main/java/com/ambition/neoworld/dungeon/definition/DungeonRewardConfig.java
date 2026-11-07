package com.ambition.neoworld.dungeon.definition;

import net.minecraft.resources.ResourceLocation;

public record DungeonRewardConfig(
        ResourceLocation lootTableId, ResourceLocation presetId
) {
    public DungeonRewardConfig(ResourceLocation lootTableId) { this(lootTableId, null); }

    public DungeonRewardConfig {
        if ((lootTableId == null) == (presetId == null)) throw new IllegalArgumentException("Choose one reward source");
    }

    public ResourceLocation sourceId() { return presetId == null ? lootTableId : presetId; }
}
