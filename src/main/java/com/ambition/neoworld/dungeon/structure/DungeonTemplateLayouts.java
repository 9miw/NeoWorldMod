package com.ambition.neoworld.dungeon.structure;

import java.util.List;
import java.util.Set;

import com.ambition.neoworld.dungeon.definition.DungeonDefinition;

import net.minecraft.core.BlockPos;

public final class DungeonTemplateLayouts {
    private DungeonTemplateLayouts() {
    }

    public static DungeonTemplateLayout from(DungeonDefinition definition) {
        if (definition.structureId() == null) {
            throw new IllegalArgumentException("Dungeon has no linked structure: " + definition.id());
        }
        return new DungeonTemplateLayout(
                definition.id(),
                List.of(new DungeonTemplatePiece(definition.structureId(), BlockPos.ZERO)),
                Set.of("player_spawn", "boss_spawn", "reward_chest", "exit")
        );
    }
}
