package com.ambition.neoworld.dungeon.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** One structure template and its offset from an instance slot origin. */
public record DungeonTemplatePiece(ResourceLocation templateId, BlockPos offset) {
    public DungeonTemplatePiece {
        if (templateId == null || offset == null) {
            throw new IllegalArgumentException("Template id and offset are required");
        }
    }
}
