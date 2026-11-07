package com.ambition.neoworld.experience;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Display-only monster levels. */
public final class MonsterLevelRules {
    public static final List<String> DEFAULT_LEVELS = List.of(
            "minecraft:silverfish=1", "minecraft:endermite=1", "minecraft:zombie=3", "minecraft:spider=5",
            "minecraft:husk=7", "minecraft:skeleton=9", "minecraft:drowned=9", "minecraft:cave_spider=11",
            "minecraft:creeper=13", "minecraft:stray=15", "minecraft:bogged=15", "minecraft:phantom=17",
            "minecraft:pillager=17", "minecraft:witch=19", "minecraft:zombified_piglin=20", "minecraft:piglin=23",
            "minecraft:hoglin=25", "minecraft:zoglin=25", "minecraft:blaze=28", "minecraft:guardian=28",
            "minecraft:enderman=30", "minecraft:vindicator=30", "minecraft:wither_skeleton=33", "minecraft:ghast=33",
            "minecraft:breeze=35", "minecraft:shulker=35", "minecraft:evoker=40", "minecraft:piglin_brute=40",
            "minecraft:ravager=45", "minecraft:elder_guardian=50", "minecraft:wither=70", "minecraft:ender_dragon=80",
            "minecraft:warden=90", "minecraft:vex=30", "minecraft:slime@small=1", "minecraft:slime@medium=5",
            "minecraft:slime@large=10", "minecraft:magma_cube@small=10", "minecraft:magma_cube@medium=15",
            "minecraft:magma_cube@large=20");
    public static final Map<String, Integer> DEFAULTS = parse(DEFAULT_LEVELS);
    private MonsterLevelRules() {}

    public static Map<String, Integer> parse(List<? extends String> entries) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split("=", -1);
            if (parts.length != 2 || !parts[0].trim().matches("[a-z0-9_.-]+:[a-z0-9/._-]+(?:@(small|medium|large))?")) {
                throw new IllegalArgumentException("Expected entity_id=level, optionally with @small/@medium/@large");
            }
            int level = Integer.parseInt(parts[1].trim());
            if (level < 0 || level > 100) throw new IllegalArgumentException("Monster level must be 0..100");
            levels.put(parts[0].trim(), level);
        }
        return Map.copyOf(levels);
    }

    public static boolean validEntry(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            parse(List.of(text));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static int level(Map<String, Integer> levels, String entityId, int size) {
        String sizeKey = size <= 1 ? "small" : size == 2 ? "medium" : "large";
        return levels.getOrDefault(entityId + "@" + sizeKey, levels.getOrDefault(entityId, 0));
    }
}
