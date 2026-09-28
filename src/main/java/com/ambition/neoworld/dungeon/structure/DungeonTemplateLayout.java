package com.ambition.neoworld.dungeon.structure;

import java.util.List;
import java.util.Set;

/** Templates and required DATA markers that make up one complete dungeon. */
public record DungeonTemplateLayout(
        String dungeonId,
        List<DungeonTemplatePiece> pieces,
        Set<String> requiredMarkers
) {
    public DungeonTemplateLayout {
        if (dungeonId == null || dungeonId.isBlank()) {
            throw new IllegalArgumentException("Dungeon id cannot be blank");
        }
        pieces = List.copyOf(pieces);
        requiredMarkers = Set.copyOf(requiredMarkers);
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("A dungeon layout must contain at least one template");
        }
    }
}
