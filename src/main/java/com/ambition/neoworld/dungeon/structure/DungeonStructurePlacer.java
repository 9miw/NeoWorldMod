package com.ambition.neoworld.dungeon.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.ambition.neoworld.world.structure.*;

/** Dungeon marker requirements are supplied to the generic World placer. */
public final class DungeonStructurePlacer {
    private DungeonStructurePlacer() {}
    private static StructureLayout world(DungeonTemplateLayout layout) {
        return new StructureLayout(layout.dungeonId(),layout.pieces().stream()
                .map(p -> new StructurePiece(p.templateId(),p.offset())).toList(),layout.requiredMarkers());
    }
    private static DungeonPlacementResult local(StructurePlacementResult result) {
        return new DungeonPlacementResult(result.successful(),result.placedTemplateCount(),result.markers(),result.error());
    }
    public static DungeonPlacementResult place(ServerLevel level,DungeonTemplateLayout layout,BlockPos origin) {
        return local(StructurePlacer.place(level,world(layout),origin));
    }
    public static DungeonPlacementResult validate(ServerLevel level,DungeonTemplateLayout layout) {
        return local(StructurePlacer.validate(level,world(layout)));
    }
}
