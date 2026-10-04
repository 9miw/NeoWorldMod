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

    public enum DatabaseType {
        H2,
        MARIADB
    }

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

    static {
        BUILDER.comment(
                "การตั้งค่าฐานข้อมูลกลางของ NeoWorld ซึ่งทุกโลกในเซิร์ฟเวอร์ใช้ร่วมกัน",
                "H2 เก็บข้อมูลใน config/neoworld/neoworld.mv.db และไม่ต้องติดตั้งฐานข้อมูลแยก",
                "MARIADB เหมาะกับข้อมูลจำนวนมากหรือการแชร์ข้อมูลระหว่างหลายเซิร์ฟเวอร์",
                "การเปลี่ยนชนิดฐานข้อมูลจะไม่ย้ายข้อมูลเดิมให้อัตโนมัติ"
        ).push("database");
    }

    public static final ModConfigSpec.EnumValue<DatabaseType> DATABASE_TYPE = BUILDER
            .comment(
                    "ชนิดฐานข้อมูลที่ต้องการใช้",
                    "H2 = ฐานข้อมูลไฟล์ภายในเครื่องนี้ (ค่าแนะนำสำหรับเซิร์ฟเวอร์เดียว)",
                    "MARIADB = ฐานข้อมูลภายนอกที่หลายเซิร์ฟเวอร์เชื่อมต่อร่วมกันได้",
                    "ค่าที่ใช้ได้: H2 หรือ MARIADB"
            )
            .defineEnum("type", DatabaseType.H2);

    public static final ModConfigSpec.IntValue DATABASE_POOL_SIZE = BUILDER
            .comment(
                    "จำนวน connection สูงสุดที่เปิดพร้อมกัน",
                    "เพิ่มค่าเมื่อมีงานฐานข้อมูลพร้อมกันมาก แต่จะใช้ทรัพยากรและ connection ของฐานข้อมูลเพิ่ม",
                    "ช่วงที่ใช้ได้: 2-64, ค่าเริ่มต้น: 16"
            )
            .defineInRange("poolSize", 16, 2, 64);

    public static final ModConfigSpec.IntValue DATABASE_CONNECTION_TIMEOUT_SECONDS = BUILDER
            .comment(
                    "เวลาสูงสุดเป็นวินาทีที่รอ connection ก่อนแจ้งว่าการเชื่อมต่อล้มเหลว",
                    "ช่วงที่ใช้ได้: 1-60 วินาที, ค่าเริ่มต้น: 10"
            )
            .defineInRange("connectionTimeoutSeconds", 10, 1, 60);

    public static final ModConfigSpec.ConfigValue<String> DATABASE_TABLE_PREFIX = BUILDER
            .comment(
                    "คำนำหน้าชื่อตารางทั้งหมดของ NeoWorld เช่น nw_metadata",
                    "ใช้ได้เฉพาะตัวอักษรอังกฤษ ตัวเลข และขีดล่าง ความยาวไม่เกิน 32 ตัวอักษร"
            )
            .define("table-prefix", "nw_", Config::isSafeTablePrefix);

    static {
        BUILDER.pop();
        BUILDER.comment(
                "ค่าการเชื่อมต่อ MariaDB ใช้เมื่อ database.type = MARIADB เท่านั้น",
                "ต้องสร้าง database และบัญชีผู้ใช้ใน MariaDB ไว้ก่อน มอดจะสร้างตารางภายในให้อัตโนมัติ",
                "ตัวอย่าง: host=127.0.0.1, port=3306, database=neoworld, username=neoworld"
        ).push("mariadb");
    }

    public static final ModConfigSpec.ConfigValue<String> MARIADB_HOST = BUILDER
            .comment("ชื่อโฮสต์หรือ IP ของเครื่อง MariaDB เช่น 127.0.0.1 หรือ db.example.com")
            .define("host", "127.0.0.1", Config::isNonBlankString);

    public static final ModConfigSpec.IntValue MARIADB_PORT = BUILDER
            .comment("พอร์ตของ MariaDB โดยค่ามาตรฐานคือ 3306")
            .defineInRange("port", 3306, 1, 65535);

    public static final ModConfigSpec.ConfigValue<String> MARIADB_DATABASE = BUILDER
            .comment(
                    "ชื่อ database ที่สร้างไว้แล้วใน MariaDB",
                    "ใช้ได้เฉพาะตัวอักษรอังกฤษ ตัวเลข ขีดกลาง และขีดล่าง ความยาวไม่เกิน 64 ตัวอักษร"
            )
            .define("database", "neoworld", Config::isSafeDatabaseName);

    public static final ModConfigSpec.ConfigValue<String> MARIADB_USERNAME = BUILDER
            .comment("ชื่อบัญชี MariaDB ที่มีสิทธิ์สร้างและแก้ไขตารางใน database ที่กำหนด")
            .define("username", "neoworld", Config::isNonBlankString);

    public static final ModConfigSpec.ConfigValue<String> MARIADB_PASSWORD = BUILDER
            .comment(
                    "รหัสผ่านของบัญชี MariaDB",
                    "ไฟล์นี้มีรหัสผ่านแบบข้อความปกติ ควรจำกัดสิทธิ์การอ่านไฟล์ในเครื่องเซิร์ฟเวอร์"
            )
            .define("password", "");

    public static final ModConfigSpec.BooleanValue MARIADB_USE_SSL = BUILDER
            .comment(
                    "เปิดใช้ TLS พร้อมตรวจสอบ certificate และ hostname ของ MariaDB",
                    "แนะนำให้ใช้ true เมื่อเชื่อมต่อฐานข้อมูลผ่านเครือข่ายภายนอก",
                    "เซิร์ฟเวอร์ MariaDB ต้องมี certificate ที่เชื่อถือได้เมื่อเปิดใช้งาน"
            )
            .define("useSsl", false);

    public static final ModConfigSpec.ConfigValue<String> MARIADB_CHARACTER_ENCODING = BUILDER
            .comment(
                    "Character encoding ที่ส่งให้ MariaDB JDBC driver",
                    "ค่าเริ่มต้น: utf8"
            )
            .define("characterEncoding", "utf8", Config::isSafeCharacterEncoding);

    static {
        BUILDER.pop();
    }

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

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean isSafeDatabaseName(Object value) {
        return value instanceof String text && text.matches("[A-Za-z0-9_-]{1,64}");
    }

    private static boolean isSafeTablePrefix(Object value) {
        return value instanceof String text && text.matches("[A-Za-z_][A-Za-z0-9_]{0,31}");
    }

    private static boolean isSafeCharacterEncoding(Object value) {
        return value instanceof String text && text.matches("[A-Za-z0-9_-]{1,32}");
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
