package com.ambition.neoworld.upgrade;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.ArrayList;
import java.util.List;

/** Server upgrade rules and costs. */
public final class UpgradeConfig {
    public record Chances(int success, int unchanged, int downgrade, int reset) {
        public Chances {
            if (success < 0 || unchanged < 0 || downgrade < 0 || reset < 0
                    || success > 100 || unchanged > 100 || downgrade > 100 || reset > 100
                    || success + unchanged + downgrade + reset != 100) {
                throw new IllegalArgumentException("Outcome percentages must be 0..100 and sum to 100");
            }
        }

        public static Chances parse(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Expected four outcome percentages");
            return new Chances(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        }
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final List<ModConfigSpec.ConfigValue<String>> LEVEL_CHANCES = new ArrayList<>();
    private static final List<ModConfigSpec.LongValue> GOLD_COSTS = new ArrayList<>();
    private static final List<ModConfigSpec.IntValue> STONE_COSTS = new ArrayList<>();
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_ALLOWED_ITEMS;
    public static final ModConfigSpec.IntValue FAILURE_BONUS_PER_POINT;
    public static final ModConfigSpec.IntValue FAILURE_BONUS_CAP;
    public static final ModConfigSpec.DoubleValue DAMAGE_PERCENT_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue IMPACT_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ARMOR_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue STUN_ARMOR_PER_LEVEL;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("upgrade");
        EXTRA_ALLOWED_ITEMS = BUILDER.comment("Additional weapon/armor IDs allowed alongside neoworld:upgradeable. Use the tag to opt in custom Epic Fight equipment.")
                .defineListAllowEmpty("extraAllowedItems", List.<String>of(), () -> "minecraft:diamond_sword",
                        value -> value instanceof String id && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
        FAILURE_BONUS_PER_POINT = BUILDER.comment("Success bonus in percentage points per failure.")
                .defineInRange("failureBonusPerPoint", 1, 0, 100);
        FAILURE_BONUS_CAP = BUILDER.comment("Maximum success bonus; reset failure points on success or break.")
                .defineInRange("failureBonusCap", 5, 0, 100);
        BUILDER.push("chances");
        for (int level = 1; level <= UpgradeData.MAX_LEVEL; level++) {
            String defaults = level <= 3 ? "90,10,0,0" : level <= 6 ? "65,25,10,0"
                    : level <= 9 ? "40,25,25,10" : level <= 12 ? "20,15,40,25"
                    : level == 13 ? "10,10,40,40" : level == 14 ? "7,8,40,45" : "5,5,40,50";
            LEVEL_CHANCES.add(BUILDER.comment("Target +" + level
                    + ": success, unchanged, downgrade by one, break to zero. Must sum to 100.")
                    .define("level" + level, defaults, UpgradeConfig::validChances));
        }
        BUILDER.pop();
        BUILDER.push("costs");
        for (int level = 1; level <= UpgradeData.MAX_LEVEL; level++) {
            GOLD_COSTS.add(BUILDER.defineInRange("goldForLevel" + level, 100L * level * level, 1L, Long.MAX_VALUE));
            STONE_COSTS.add(BUILDER.defineInRange("stonesForLevel" + level, level <= 6 ? 1 : level <= 12 ? 2 : 3, 1, 64));
        }
        BUILDER.pop();
        BUILDER.push("bonuses");
        DAMAGE_PERCENT_PER_LEVEL = BUILDER.comment("Percent of unupgraded main-hand weapon damage per level; excludes enchantments and player effects.")
                .defineInRange("damagePercentPerLevel", 3.0, 0.0, 100.0);
        IMPACT_PER_LEVEL = BUILDER.comment("Flat Epic Fight impact per weapon upgrade level.")
                .defineInRange("impactPerLevel", 0.10, 0.0, 100.0);
        ARMOR_PER_LEVEL = BUILDER.comment("Flat armor per upgrade level on each equipped armor piece.")
                .defineInRange("armorPerLevel", 0.20, 0.0, 100.0);
        STUN_ARMOR_PER_LEVEL = BUILDER.comment("Flat Epic Fight stun armor per upgrade level on each equipped armor piece.")
                .defineInRange("stunArmorPerLevel", 0.10, 0.0, 100.0);
        BUILDER.pop();
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private UpgradeConfig() {}

    private static boolean validChances(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            Chances.parse(text);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static Chances chances(int targetLevel) {
        if (targetLevel < 1 || targetLevel > UpgradeData.MAX_LEVEL) {
            throw new IllegalArgumentException("Target level must be 1..15");
        }
        return Chances.parse(LEVEL_CHANCES.get(targetLevel - 1).get());
    }

    public static int failureBonus(int failures) {
        if (failures < 0) throw new IllegalArgumentException("Failures cannot be negative");
        return (int) Math.min((long) failures * FAILURE_BONUS_PER_POINT.get(), FAILURE_BONUS_CAP.get());
    }

    public static long goldCost(int targetLevel) {
        chances(targetLevel);
        return GOLD_COSTS.get(targetLevel - 1).get();
    }

    public static int stoneCost(int targetLevel) {
        chances(targetLevel);
        return STONE_COSTS.get(targetLevel - 1).get();
    }
}
