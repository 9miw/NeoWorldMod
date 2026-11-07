package com.ambition.neoworld.dungeon.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.ambition.neoworld.world.structure.StructureCapturePlan;

/** Compatibility value object; partitioning is owned by World. */
public record DungeonCapturePlan(BlockPos minimum,BlockPos maximum,long volume,List<Part> parts) {
    public static final int EDGE=StructureCapturePlan.EDGE, MAX_PARTS=StructureCapturePlan.MAX_PARTS;
    public static final long MAX_VOLUME=StructureCapturePlan.MAX_VOLUME;
    public record Part(ResourceLocation id,BlockPos origin,BlockPos size,BlockPos offset) {}
    public DungeonCapturePlan { parts=List.copyOf(parts); }
    public static DungeonCapturePlan create(BlockPos first,BlockPos second,ResourceLocation prefix) {
        var plan=StructureCapturePlan.create(first,second,prefix);
        return new DungeonCapturePlan(plan.minimum(),plan.maximum(),plan.volume(),plan.parts().stream()
                .map(p -> new Part(p.id(),p.origin(),p.size(),p.offset())).toList());
    }
}
