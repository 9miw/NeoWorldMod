package com.ambition.neoworld.dungeon.definition;

import net.minecraft.util.RandomSource;

public record DungeonLootRule(double chance, int minimum, int maximum) {
    public static final DungeonLootRule DEFAULT = new DungeonLootRule(100, 0, 0);

    public DungeonLootRule {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100
                || !((minimum == 0 && maximum == 0) || (minimum >= 1 && maximum >= minimum && maximum <= 99))) {
            throw new IllegalArgumentException("Invalid loot chance or amount range");
        }
    }

    public int roll(RandomSource random, int originalCount) {
        if (chance == 0 || (chance < 100 && random.nextDouble() * 100 >= chance)) return 0;
        return minimum == 0 ? originalCount : minimum + random.nextInt(maximum - minimum + 1);
    }
}
