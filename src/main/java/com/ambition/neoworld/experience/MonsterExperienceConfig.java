package com.ambition.neoworld.experience;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned settings, baked once on load/reload rather than parsed per hit. */
public final class MonsterExperienceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue MINIMUM_PLAYER_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue COMBAT_WINDOW_SECONDS;
    public static final ModConfigSpec.DoubleValue SPAWNER_EXP_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REWARDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LEVELS;
    public static final ModConfigSpec.ConfigValue<String> PARTY_SHARED_EXP_DIMENSION;
    public static final ModConfigSpec SPEC;
    private static volatile MonsterExperienceRules.Settings current = MonsterExperienceRules.DEFAULT_SETTINGS;
    private static volatile Map<String, Integer> levels = MonsterLevelRules.DEFAULTS;
    private static volatile String partySharedExpDimension = "";

    static {
        BUILDER.comment("ระบบดรอปไอเทม NeoWorld EXP จากมอนสเตอร์ ไม่เปลี่ยน EXP ของ Minecraft").push("monsterExperience");
        ENABLED = BUILDER.comment("เปิดระบบดรอป EXP จากมอนสเตอร์; ไอเทม EXP ที่มีอยู่แล้วใช้ได้ตามเดิม")
                .define("enabled", true);
        MINIMUM_PLAYER_DAMAGE_PERCENT = BUILDER.comment("ดาเมจจากผู้เล่นรวมขั้นต่ำ เป็นเปอร์เซ็นต์ของเลือดสูงสุด",
                        "แม้ตั้งเป็น 0 ก็ยังต้องมีดาเมจจริงจากผู้เล่นอย่างน้อยหนึ่งครั้ง")
                .defineInRange("minimumPlayerDamagePercent", 50.0, 0.0, 100.0);
        COMBAT_WINDOW_SECONDS = BUILDER.comment("ระยะเวลาหลังดาเมจผู้เล่นครั้งล่าสุด; เกินช่วงนี้เริ่มสะสมดาเมจใหม่",
                        "ใช้เวลาในเกม 20 ticks ต่อวินาที")
                .defineInRange("combatWindowSeconds", 15, 1, 3600);
        SPAWNER_EXP_MULTIPLIER = BUILDER.comment("ตัวคูณ EXP สำหรับ SPAWNER และ TRIAL_SPAWNER: 0=ไม่ดรอป, 0.25=25%, 1=เต็ม",
                        "ตัวคูณมากกว่า 0 ปัดลง แต่ให้ขั้นต่ำ 1 EXP")
                .defineInRange("spawnerExpMultiplier", 0.25, 0.0, 100.0);
        REWARDS = BUILDER.comment("รายการ entity_id=EXPต่ำสุด,EXPสูงสุด; 0,0 หรือเอารายการออกเพื่อปิดชนิดนั้น",
                        "ปรับขนาด Slime/Magma Cube ด้วย @small, @medium, @large (ขนาด 1, 2, 4 ขึ้นไป)",
                        "เพิ่มรหัสมอนสเตอร์จากม็อดได้; ชนิดที่ไม่มีในรายการไม่ดรอป; รหัสซ้ำใช้รายการท้ายสุด")
                .defineListAllowEmpty("rewards", MonsterExperienceRules.DEFAULT_REWARDS,
                        () -> "minecraft:zombie=80,120", MonsterExperienceRules::validRewardEntry);
        LEVELS = BUILDER.comment("เลเวลที่แสดงเหนือหัวเท่านั้น ไม่เพิ่มเลือด ดาเมจ หรือเปลี่ยน EXP",
                        "รูปแบบ entity_id=level (1–100); 0 หรือเอารายการออกเพื่อซ่อนป้ายเลเวล",
                        "Slime/Magma Cube ใช้ @small, @medium, @large; รหัสซ้ำใช้รายการท้ายสุด")
                .defineListAllowEmpty("levels", MonsterLevelRules.DEFAULT_LEVELS,
                        () -> "minecraft:zombie=3", MonsterLevelRules::validEntry);
        PARTY_SHARED_EXP_DIMENSION = BUILDER.comment("Dimension ID for party EXP sharing. Leave empty to disable.")
                .define("partySharedExpDimension", "", value -> value instanceof String id
                        && (id.isEmpty() || id.contains(":") && ResourceLocation.tryParse(id) != null));
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private MonsterExperienceConfig() {}

    public static MonsterExperienceRules.Settings settings() { return current; }

    public static boolean sharesPartyExperience(ResourceLocation dimension) {
        return current.enabled()
                && !com.ambition.neoworld.registry.ModDimensionRegistries.DUNGEON_LEVEL.location().equals(dimension)
                && partySharedExpDimension.equals(dimension.toString());
    }

    public static int level(String entityId, int size) {
        return MonsterLevelRules.level(levels, entityId, size);
    }

    public static void registerListeners(IEventBus modEventBus) {
        modEventBus.addListener(MonsterExperienceConfig::onLoading);
        modEventBus.addListener(MonsterExperienceConfig::onReloading);
        modEventBus.addListener(MonsterExperienceConfig::onUnloading);
    }

    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) bake();
    }

    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) bake();
    }

    public static void onUnloading(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            current = MonsterExperienceRules.DEFAULT_SETTINGS;
            levels = MonsterLevelRules.DEFAULTS;
            partySharedExpDimension = "";
        }
    }

    private static void bake() {
        current = MonsterExperienceRules.settings(ENABLED.get(), MINIMUM_PLAYER_DAMAGE_PERCENT.get(),
                COMBAT_WINDOW_SECONDS.get(), SPAWNER_EXP_MULTIPLIER.get(), REWARDS.get());
        levels = MonsterLevelRules.parse(LEVELS.get());
        partySharedExpDimension = PARTY_SHARED_EXP_DIMENSION.get();
    }
}
