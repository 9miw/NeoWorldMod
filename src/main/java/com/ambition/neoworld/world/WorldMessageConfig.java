package com.ambition.neoworld.world;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned templates for world reset notifications. */
public final class WorldMessageConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("ข้อความแจ้งเตือนระบบจัดการโลก แก้ไขบนเซิร์ฟเวอร์",
                "ข้อความว่างจะปิดการแจ้งเตือนนั้น โดยไม่เปลี่ยนการทำงานของระบบรีโลก").push("messages");
    }

    public static final ModConfigSpec.ConfigValue<String> PREFIX = BUILDER
            .comment("คำนำหน้าข้อความทุกข้อความ ใส่ช่องว่างท้ายหากต้องการ")
            .define("prefix", "[จัดการโลก] ");
    public static final ModConfigSpec.ConfigValue<String> WARNING_5_MINUTES = BUILDER
            .define("warning5Minutes", "โลกนี้จะรีเซ็ตในอีก 5 นาที กรุณาเก็บของและออกจากโลก");
    public static final ModConfigSpec.ConfigValue<String> WARNING_3_MINUTES = BUILDER
            .define("warning3Minutes", "โลกนี้จะรีเซ็ตในอีก 3 นาที กรุณาเก็บของและออกจากโลก");
    public static final ModConfigSpec.ConfigValue<String> WARNING_30_SECONDS = BUILDER
            .define("warning30Seconds", "โลกนี้จะรีเซ็ตในอีก 30 วินาที กรุณาเก็บของและออกจากโลก");
    public static final ModConfigSpec.ConfigValue<String> COUNTDOWN = BUILDER
            .comment("ใช้ {seconds} แทนจำนวนวินาทีในช่วงนับถอยหลัง 10 ถึง 1")
            .define("countdown", "โลกนี้จะรีเซ็ตในอีก {seconds} วินาที");
    public static final ModConfigSpec.ConfigValue<String> PREPARING = BUILDER
            .define("preparing", "เตรียมรีโลกนี้ กรุณาเก็บของและออกจากโลก");
    public static final ModConfigSpec.ConfigValue<String> STARTING = BUILDER
            .define("starting", "กำลังรีโลกนี้ ย้ายผู้เล่นออกจากโลก");
    public static final ModConfigSpec.ConfigValue<String> SUCCESS = BUILDER
            .define("success", "รีโลกเรียบร้อยแล้ว สามารถกลับเข้าได้");
    public static final ModConfigSpec.ConfigValue<String> FAILURE = BUILDER
            .define("failure", "รีโลกไม่สำเร็จ ปิดการเข้าโลกชั่วคราว กรุณาแจ้งแอดมิน");

    public static final ModConfigSpec SPEC;
    static {
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private WorldMessageConfig() {}

    public static String message(ModConfigSpec.ConfigValue<String> template) {
        String text = template.get();
        return text.isBlank() ? "" : PREFIX.get() + text;
    }

    public static String warning(long seconds) {
        if (seconds == 300) return message(WARNING_5_MINUTES);
        if (seconds == 180) return message(WARNING_3_MINUTES);
        if (seconds == 30) return message(WARNING_30_SECONDS);
        return message(COUNTDOWN).replace("{seconds}", Long.toString(seconds));
    }
}
