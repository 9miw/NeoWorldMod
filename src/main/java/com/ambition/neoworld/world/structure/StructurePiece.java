package com.ambition.neoworld.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** One structure template and its offset from an instance slot origin. */
public record StructurePiece(ResourceLocation templateId, BlockPos offset) {
    public StructurePiece {
        if (templateId == null || offset == null) {
            throw new IllegalArgumentException("Template id and offset are required");
        }
        offset = offset.immutable();
    }
}
