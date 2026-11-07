package com.ambition.neoworld.experience;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combat and reward policy, independent of Minecraft entity lifecycle. */
public final class MonsterExperienceRules {
    private static final Reward NONE = new Reward(0, 0);
    public static final List<String> DEFAULT_REWARDS = List.of(
            "minecraft:silverfish=10,20", "minecraft:endermite=10,20",
            "minecraft:zombie=80,120", "minecraft:spider=90,130", "minecraft:husk=100,140",
            "minecraft:skeleton=120,180", "minecraft:drowned=120,180", "minecraft:cave_spider=140,200",
            "minecraft:creeper=160,240", "minecraft:stray=180,260", "minecraft:bogged=180,260",
            "minecraft:pillager=180,260", "minecraft:piglin=180,260", "minecraft:phantom=200,300",
            "minecraft:witch=280,420", "minecraft:vindicator=300,450", "minecraft:wither_skeleton=300,450",
            "minecraft:evoker=600,900", "minecraft:piglin_brute=600,900", "minecraft:ravager=1000,1500",
            "minecraft:zombified_piglin=100,150", "minecraft:hoglin=250,350", "minecraft:zoglin=250,350",
            "minecraft:blaze=250,350", "minecraft:enderman=250,350", "minecraft:guardian=250,350",
            "minecraft:ghast=300,500", "minecraft:shulker=350,550", "minecraft:elder_guardian=2000,3000",
            "minecraft:breeze=400,600", "minecraft:wither=8000,12000", "minecraft:ender_dragon=15000,25000",
            "minecraft:slime@small=5,10", "minecraft:slime@medium=30,50", "minecraft:slime@large=80,120",
            "minecraft:magma_cube@small=10,20", "minecraft:magma_cube@medium=50,80", "minecraft:magma_cube@large=120,180",
            "minecraft:vex=0,0", "minecraft:warden=0,0");
    public static final Settings DEFAULT_SETTINGS = settings(true, 50, 15, 0.25, DEFAULT_REWARDS);

    private MonsterExperienceRules() {}

    static long partyShare(long total, int recipients, int index) {
        return total / recipients + (index < total % recipients ? 1 : 0);
    }

    public static Settings settings(boolean enabled, double percent, int seconds, double multiplier,
                                    List<? extends String> entries) {
        if (!Double.isFinite(percent) || percent < 0 || percent > 100 || seconds < 1 || seconds > 3600
                || !Double.isFinite(multiplier) || multiplier < 0 || multiplier > 100) {
            throw new IllegalArgumentException("Invalid monster EXP combat settings");
        }
        Map<String, Reward> rewards = new LinkedHashMap<>();
        for (String entry : entries) {
            Map.Entry<String, Reward> parsed = parseReward(entry);
            rewards.put(parsed.getKey(), parsed.getValue());
        }
        return new Settings(enabled, percent, seconds * 20L, multiplier, Map.copyOf(rewards));
    }

    public static boolean validRewardEntry(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            parseReward(text);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Map.Entry<String, Reward> parseReward(String entry) {
        String[] parts = entry.split("=", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Expected entity_id=minimum,maximum");
        String key = parts[0].trim();
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9/._-]+(?:@(small|medium|large))?")) {
            throw new IllegalArgumentException("Invalid entity ID or size: " + key);
        }
        String[] values = parts[1].split(",", -1);
        if (values.length != 2) throw new IllegalArgumentException("Expected minimum,maximum");
        long minimum = Long.parseLong(values[0].trim());
        long maximum = Long.parseLong(values[1].trim());
        if (minimum < 0 || maximum < minimum || minimum == 0 && maximum != 0) {
            throw new IllegalArgumentException("Use 1 <= minimum <= maximum, or 0,0 to disable");
        }
        return Map.entry(key, new Reward(minimum, maximum));
    }

    public static final class Settings {
        private final boolean enabled;
        private final double minimumDamagePercent;
        private final long combatWindowTicks;
        private final double spawnerMultiplier;
        private final Map<String, Reward> rewards;

        private Settings(boolean enabled, double minimumDamagePercent, long combatWindowTicks,
                         double spawnerMultiplier, Map<String, Reward> rewards) {
            this.enabled = enabled;
            this.minimumDamagePercent = minimumDamagePercent;
            this.combatWindowTicks = combatWindowTicks;
            this.spawnerMultiplier = spawnerMultiplier;
            this.rewards = rewards;
        }

        public boolean enabled() { return enabled; }

        public Reward reward(String entityId, int size, boolean spawner) {
            if (!enabled) return NONE;
            String sizeKey = size <= 1 ? "small" : size == 2 ? "medium" : "large";
            Reward base = rewards.getOrDefault(entityId + "@" + sizeKey, rewards.getOrDefault(entityId, NONE));
            if (!spawner || !base.hasReward()) return base;
            if (spawnerMultiplier == 0) return NONE;
            return new Reward(Math.max(1, (long) Math.floor(base.minimum() * spawnerMultiplier)),
                    Math.max(1, (long) Math.floor(base.maximum() * spawnerMultiplier)));
        }
    }

    public static final class Combat {
        private double playerDamage;
        private long lastHitTick;
        private boolean hasHit;

        public void recordDamage(double before, double after, long tick, Settings settings) {
            if (!settings.enabled || !Double.isFinite(before) || !Double.isFinite(after) || before <= 0) return;
            double lostHealth = before - Math.max(0, after);
            if (lostHealth <= 0) return;
            if (!hasHit || tick < lastHitTick || tick - lastHitTick > settings.combatWindowTicks) playerDamage = 0;
            playerDamage += lostHealth;
            lastHitTick = tick;
            hasHit = true;
        }
        public boolean isEligible(double maxHealth, long tick, Settings settings) {
            return settings.enabled && hasHit && Double.isFinite(maxHealth) && maxHealth > 0
                    && playerDamage >= maxHealth * (settings.minimumDamagePercent / 100.0)
                    && tick >= lastHitTick && tick - lastHitTick <= settings.combatWindowTicks;
        }
        public void recordDamage(double healthBefore, double healthAfter, long tick) {
            recordDamage(healthBefore, healthAfter, tick, DEFAULT_SETTINGS);
        }

        public boolean isEligible(double maxHealth, long tick) {
            return isEligible(maxHealth, tick, DEFAULT_SETTINGS);
        }
    }

    public record Reward(long minimum, long maximum) {
        public boolean hasReward() { return maximum > 0; }
    }

    public static Reward reward(String entityId, int size, boolean spawner) {
        return DEFAULT_SETTINGS.reward(entityId, size, spawner);
    }
}
