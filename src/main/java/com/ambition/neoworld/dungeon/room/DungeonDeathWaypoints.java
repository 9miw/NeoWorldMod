package com.ambition.neoworld.dungeon.room;

import com.ambition.neoworld.registry.ModDimensionRegistries;
import net.minecraft.core.GlobalPos;

public final class DungeonDeathWaypoints {
    private DungeonDeathWaypoints() {}

    public static boolean suppress(GlobalPos deathLocation) {
        return deathLocation != null
                && ModDimensionRegistries.DUNGEON_LEVEL.equals(deathLocation.dimension());
    }
}
