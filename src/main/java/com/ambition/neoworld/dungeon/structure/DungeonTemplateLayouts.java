package com.ambition.neoworld.dungeon.structure;

import java.util.List;
import java.util.Set;

import com.ambition.neoworld.dungeon.definition.DungeonDefinition;

public final class DungeonTemplateLayouts {
    private DungeonTemplateLayouts() {
    }

    public static DungeonTemplateLayout from(DungeonDefinition definition) {
        if (definition.structurePieces().isEmpty()) {
            throw new IllegalArgumentException("Dungeon has no linked structure: " + definition.id());
        }
        Set<String> requiredMarkers = new java.util.HashSet<>(
                Set.of("player_spawn", "boss_spawn", "reward_chest", "exit"));
        for (int i = 0; i < definition.monsterWaves().size(); i++) {
            requiredMarkers.add("mob_spawn_" + (i + 1));
        }
        return new DungeonTemplateLayout(
                definition.id(),
                definition.structurePieces(),
                requiredMarkers
        );
    }
}
