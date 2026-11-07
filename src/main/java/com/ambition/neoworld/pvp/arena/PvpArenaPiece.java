package com.ambition.neoworld.pvp.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A template and its local offset from the arena's minimum corner. */
public record PvpArenaPiece(ResourceLocation template,BlockPos offset) {
    public PvpArenaPiece {
        if(template==null || offset==null || offset.getX()<0 || offset.getY()<0 || offset.getZ()<0
                || offset.getX()>=512 || offset.getY()>=224 || offset.getZ()>=512)
            throw new IllegalArgumentException("Invalid PvP structure piece");
        offset=offset.immutable();
    }
}
