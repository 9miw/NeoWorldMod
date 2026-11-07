package com.ambition.neoworld.world.structure;

import java.util.List;
import java.util.Set;

/** Templates and required DATA markers that make up one complete structure. */
public record StructureLayout(
        String id,
        List<StructurePiece> pieces,
        Set<String> requiredMarkers
) {
    public StructureLayout {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Structure id cannot be blank");
        }
        pieces = List.copyOf(pieces);
        requiredMarkers = Set.copyOf(requiredMarkers);
        if (pieces.isEmpty() || pieces.size() > StructureCapturePlan.MAX_PARTS) {
            throw new IllegalArgumentException("A structure layout must contain 1..512 templates");
        }
    }
}
