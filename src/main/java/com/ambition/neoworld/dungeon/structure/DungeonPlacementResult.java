package com.ambition.neoworld.dungeon.structure;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

public record DungeonPlacementResult(
        boolean successful,
        int placedTemplateCount,
        Map<String, List<BlockPos>> markers,
        String error
) {
    public DungeonPlacementResult {
        markers = markers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
        ));
        error = error == null ? "" : error;
    }

    public static DungeonPlacementResult failure(String error) {
        return new DungeonPlacementResult(false, 0, Map.of(), error);
    }

    public static DungeonPlacementResult success(
            int placedTemplateCount,
            Map<String, List<BlockPos>> markers
    ) {
        return new DungeonPlacementResult(true, placedTemplateCount, markers, "");
    }
}
