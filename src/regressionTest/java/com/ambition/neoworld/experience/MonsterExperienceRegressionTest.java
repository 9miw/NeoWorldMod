package com.ambition.neoworld.experience;

public final class MonsterExperienceRegressionTest {
    public static void main(String[] args) throws java.io.IOException {
        configurableRules();
        monsterLevels();
        configTemplate();
        for (int count = 2; count <= 6; count++) {
            for (long total : new long[] {1, 100, 101, Long.MAX_VALUE}) {
                long distributed = 0;
                long first = MonsterExperienceRules.partyShare(total, count, 0);
                for (int i = 0; i < count; i++) {
                    long share = MonsterExperienceRules.partyShare(total, count, i);
                    check(share <= first && first - share <= 1, "Party shares differ by at most one EXP");
                    distributed = Math.addExact(distributed, share);
                }
                check(distributed == total, "Party distribution must conserve total EXP");
            }
        }
        MonsterExperienceRules.Combat combat = new MonsterExperienceRules.Combat();
        check(!combat.isEligible(20, 100), "No combat must not award EXP");
        combat.recordDamage(20, 16, 100);
        combat.recordDamage(16, 10, 110);
        check(combat.isEligible(20, 110), "Player damage must combine to meet the 50 percent threshold");
        check(combat.isEligible(20, 410), "Exactly 15 seconds must remain eligible");
        check(!combat.isEligible(20, 411), "Damage older than 15 seconds must not award EXP");
        check(!combat.isEligible(20, 109), "A backwards clock must not award EXP");

        MonsterExperienceRules.Combat finisher = new MonsterExperienceRules.Combat();
        finisher.recordDamage(1, 0, 100);
        check(!finisher.isEligible(20, 100), "Finishing a machine-damaged mob must credit only one health point");
        MonsterExperienceRules.Combat overkill = new MonsterExperienceRules.Combat();
        overkill.recordDamage(1, -100, 100);
        check(!overkill.isEligible(20, 100), "Overkill below zero health must not inflate contribution");
        MonsterExperienceRules.Combat below = new MonsterExperienceRules.Combat();
        below.recordDamage(20, 10.1, 100);
        check(!below.isEligible(20, 100), "Less than half of max health must be rejected");
        combat.recordDamage(10, 10, 411);
        check(!combat.isEligible(20, 411), "An absorbed or blocked hit must not refresh combat");
        combat.recordDamage(20, 19, 412);
        check(!combat.isEligible(20, 412), "A new fight must not reuse expired damage credit");
        combat.recordDamage(Double.NaN, 0, 413);
        check(!combat.isEligible(20, 413), "Invalid health must not create combat credit");
        check(!combat.isEligible(0, 413) && !combat.isEligible(Double.NaN, 413),
                "Invalid maximum health must not award EXP");
        MonsterExperienceRules.Combat melee = new MonsterExperienceRules.Combat();
        melee.recordDamage(20, 0, 500);
        check(melee.isEligible(20, 500), "A legitimate one-hit kill must qualify");

        var zombie = MonsterExperienceRules.reward("minecraft:zombie", 1, false);
        check(zombie.minimum() == 80 && zombie.maximum() == 120, "Zombie must retain its approved range");
        var spawner = MonsterExperienceRules.reward("minecraft:zombie", 1, true);
        check(spawner.minimum() == 20 && spawner.maximum() == 30, "Spawner EXP must be quartered");
        var tiny = MonsterExperienceRules.reward("minecraft:slime", 1, true);
        check(tiny.minimum() == 1 && tiny.maximum() == 2, "Small quarter rewards must remain valid positive integers");
        check(MonsterExperienceRules.reward("minecraft:slime", 4, false).maximum() == 120,
                "Large slimes must use their size-specific range");
        check(MonsterExperienceRules.reward("minecraft:magma_cube", 2, false).minimum() == 50,
                "Medium magma cubes must use their size-specific range");
        for (String excluded : new String[]{"minecraft:vex", "minecraft:warden", "minecraft:cow", "custom:zombie"}) {
            check(!MonsterExperienceRules.reward(excluded, 1, false).hasReward(),
                    "Excluded and unknown mobs must not award EXP: " + excluded);
        }
        System.out.println("Monster EXP regression checks passed");
    }

    private static void configurableRules() {
        var settings = MonsterExperienceRules.settings(true, 25, 5, 0.5,
                java.util.List.of("minecraft:zombie=100,200", "minecraft:slime@small=8,16"));
        var combat = new MonsterExperienceRules.Combat();
        combat.recordDamage(20, 15, 100, settings);
        check(combat.isEligible(20, 200, settings), "Configured 25 percent and five-second boundary must apply");
        check(!combat.isEligible(20, 201, settings), "Configured combat timeout must apply");
        var reward = settings.reward("minecraft:zombie", 1, true);
        check(reward.minimum() == 50 && reward.maximum() == 100, "Configured rewards and spawner multiplier must apply");
        check(settings.reward("minecraft:slime", 1, false).maximum() == 16, "Size-specific overrides must apply");
        check(!settings.reward("minecraft:skeleton", 1, false).hasReward(), "Removing a reward entry must disable that mob");
        var noSpawner = MonsterExperienceRules.settings(true, 50, 15, 0,
                java.util.List.of("minecraft:zombie=80,120"));
        check(!noSpawner.reward("minecraft:zombie", 1, true).hasReward(), "Zero spawner multiplier must disable spawner drops");
        var disabled = MonsterExperienceRules.settings(false, 50, 15, 0.25,
                java.util.List.of("minecraft:zombie=80,120"));
        check(!disabled.reward("minecraft:zombie", 1, false).hasReward(), "Disabled system must not reward mobs");
        check(!combat.isEligible(20, 100, disabled), "Disabled system must reject existing combat credit");
        var empty = MonsterExperienceRules.settings(true, 50, 15, 0.25, java.util.List.of());
        check(!empty.reward("minecraft:zombie", 1, false).hasReward(), "Empty reward table must disable all drops");
        for (String invalid : new String[]{"zombie=1,2", "minecraft:zombie=200,100", "minecraft:zombie=-1,10",
                "minecraft:zombie=1", "minecraft:zombie=0,10", "minecraft:slime@huge=1,2", "minecraft:zombie=1,2,3"}) {
            try {
                MonsterExperienceRules.settings(true, 50, 15, 0.25, java.util.List.of(invalid));
                throw new AssertionError("Invalid reward must be rejected: " + invalid);
            } catch (IllegalArgumentException expected) {}
        }
        var explicitZero = MonsterExperienceRules.settings(true, 50, 15, 0.25,
                java.util.List.of("minecraft:zombie=0,0"));
        check(!explicitZero.reward("minecraft:zombie", 1, false).hasReward(), "A zero range must disable a mob");
        var zeroThreshold = MonsterExperienceRules.settings(true, 0, 15, 0.25,
                java.util.List.of("minecraft:zombie=80,120"));
        check(!new MonsterExperienceRules.Combat().isEligible(20, 0, zeroThreshold),
                "A zero threshold still requires real player damage");
        check(!MonsterExperienceRules.validRewardEntry(123), "Non-string config entries must be rejected");
        for (double invalid : new double[]{-1, 101, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                MonsterExperienceRules.settings(true, invalid, 15, 0.25, java.util.List.of());
                throw new AssertionError("Invalid threshold must be rejected");
            } catch (IllegalArgumentException expected) {}
            try {
                MonsterExperienceRules.settings(true, 50, 15, invalid, java.util.List.of());
                throw new AssertionError("Invalid multiplier must be rejected");
            } catch (IllegalArgumentException expected) {}
        }
    }

    private static void monsterLevels() {
        check(MonsterLevelRules.level(MonsterLevelRules.DEFAULTS, "minecraft:zombie", 1) == 3,
                "Zombie must display its approved level");
        check(MonsterLevelRules.level(MonsterLevelRules.DEFAULTS, "minecraft:warden", 1) == 90,
                "Levels must exist independently of EXP rewards");
        check(MonsterLevelRules.level(MonsterLevelRules.DEFAULTS, "minecraft:slime", 4) == 10,
                "Large slime must use the correct level");
        check(MonsterLevelRules.level(MonsterLevelRules.DEFAULTS, "minecraft:cow", 1) == 0,
                "Unlisted mobs must not gain a level label");
        var levels = MonsterLevelRules.parse(java.util.List.of("minecraft:zombie=12", "minecraft:slime=2",
                "minecraft:slime@large=18", "minecraft:warden=0"));
        check(MonsterLevelRules.level(levels, "minecraft:zombie", 1) == 12, "Configured levels must override defaults");
        check(MonsterLevelRules.level(levels, "minecraft:slime", 1) == 2, "Base level must cover unspecified sizes");
        check(MonsterLevelRules.level(levels, "minecraft:slime", 4) == 18, "Size override must take precedence");
        check(MonsterLevelRules.level(levels, "minecraft:warden", 1) == 0, "Zero level must hide a label");
        for (String invalid : new String[]{"zombie=3", "minecraft:zombie=-1", "minecraft:zombie=101",
                "minecraft:zombie=3=4", "minecraft:slime@huge=3"}) {
            try {
                MonsterLevelRules.parse(java.util.List.of(invalid));
                throw new AssertionError("Invalid monster level must be rejected: " + invalid);
            } catch (IllegalArgumentException expected) {}
        }
    }

    private static void configTemplate() throws java.io.IOException {
        try (var reader = java.nio.file.Files.newBufferedReader(
                java.nio.file.Path.of("docs/config/neoworld-monster-experience.toml"))) {
            var config = new com.electronwill.nightconfig.toml.TomlParser().parse(reader);
            // NeoForge also normalizes comments; validate values independently of comment wording.
            MonsterExperienceConfig.SPEC.correct(config, (action, path, oldValue, newValue) -> {
                throw new AssertionError("Example TOML value requires correction at " + path);
            }, null);
            check(MonsterExperienceConfig.SPEC.isCorrect(config), "Example TOML must satisfy the real NeoForge config spec");
            config.set("monsterExperience.minimumPlayerDamagePercent", -1.0);
            check(!MonsterExperienceConfig.SPEC.isCorrect(config), "Negative threshold must fail spec validation");
            MonsterExperienceConfig.SPEC.correct(config);
            check(((Number) config.get("monsterExperience.minimumPlayerDamagePercent")).doubleValue() == 0.0,
                    "NeoForge must clamp a negative threshold to the supported minimum");
            config.set("monsterExperience.rewards", java.util.List.of("minecraft:zombie=200,100"));
            check(!MonsterExperienceConfig.SPEC.isCorrect(config), "Reversed EXP ranges must fail spec validation");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
