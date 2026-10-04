package com.ambition.neoworld.dungeon.definition;

import net.minecraft.resources.ResourceLocation;

public record DungeonRewardConfig(
        ResourceLocation lootTableId,
        int experience,
        long money
) {
}
