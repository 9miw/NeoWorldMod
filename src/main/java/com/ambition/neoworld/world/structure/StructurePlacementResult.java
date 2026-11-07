package com.ambition.neoworld.world.structure;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

public record StructurePlacementResult(
        boolean successful,
        int placedTemplateCount,
        Map<String, List<BlockPos>> markers,
        String error
) {
    public StructurePlacementResult {
        markers = markers.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
        ));
        error = error == null ? "" : error;
    }

    public static StructurePlacementResult failure(String error) {
        return new StructurePlacementResult(false, 0, Map.of(), error);
    }

    public static StructurePlacementResult success(
            int placedTemplateCount,
            Map<String, List<BlockPos>> markers
    ) {
        return new StructurePlacementResult(true, placedTemplateCount, markers, "");
    }
}
