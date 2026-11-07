package com.ambition.neoworld.dungeon.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Compatibility adapter; overlap ownership and NBT normalization belong to World. */
public final class DungeonOverlapPlan<S> {
    private final com.ambition.neoworld.world.structure.StructureOverlapPlan<S> plan = new com.ambition.neoworld.world.structure.StructureOverlapPlan<>();
    public String addMarker(String source,BlockPos pos,String name) { return plan.addMarker(source,pos,name); }
    public String addBlock(int piece,String source,BlockPos pos,S state,boolean air,CompoundTag nbt) { return plan.addBlock(piece,source,pos,state,air,nbt); }
    public boolean allows(int piece,BlockPos pos) { return plan.allows(piece,pos); }
    public java.util.Map<String,java.util.List<BlockPos>> markers() { return plan.markers(); }
}
