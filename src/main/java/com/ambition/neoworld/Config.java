package com.ambition.neoworld;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// คลาสตัวอย่างสำหรับจัดการ Config (TOML) ช่วยให้การตั้งค่าเป็นระเบียบ
// แสดงตัวอย่างการใช้งาน NeoForge ModConfigSpec API
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // รายการข้อความ (String) ที่จะถูกมองเป็น ResourceLocation ของไอเทม
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    // กำหนดเลเวลขั้นต่ำของไอเทม รูปแบบ: "modid:item=level"
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_LEVEL_REQUIREMENTS = BUILDER
            .comment("Minimum NeoWorld level required to use items. Format: modid:item=level")
            .defineListAllowEmpty("itemLevelRequirements", List.of("minecraft:diamond_sword=10"), () -> "", Config::validateItemLevelRequirement);

    static final ModConfigSpec SPEC = BUILDER.build();

    // เมธอดตรวจสอบว่าชื่อไอเทมในลิสต์เป็น ResourceLocation ที่มีอยู่จริงในเกมหรือไม่
    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    private static boolean validateItemLevelRequirement(final Object obj) {
        if (!(obj instanceof String value)) {
            return false;
        }

        String[] parts = value.split("=", 2);
        if (parts.length != 2) {
            return false;
        }

        try {
            int level = Integer.parseInt(parts[1].trim());
            return level > 0 && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(parts[0].trim()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static int getRequiredLevel(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        for (String entry : ITEM_LEVEL_REQUIREMENTS.get()) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            try {
                if (ResourceLocation.parse(parts[0].trim()).equals(itemId)) {
                    return Math.max(1, Integer.parseInt(parts[1].trim()));
                }
            } catch (IllegalArgumentException ignored) {
                // Config validation should catch this; skip bad entries defensively.
            }
        }
        return 0;
    }
}
