package com.ambition.neoworld.registry;

import java.util.function.Supplier;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.fashion.data.FashionData;
import com.ambition.neoworld.title.model.PlayerTitleData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// คลาสสำหรับลงทะเบียน Data Attachments (ระบบเก็บข้อมูลผูกกับ Entity/Player ใน NeoForge)
public class ModAttachments {
    // Deferred Register สำหรับ Attachment Types ภายใต้ namespace "neoworld"
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NeoWorld.MODID);

    public static final Supplier<AttachmentType<com.ambition.neoworld.activity.ActivityData>> ACTIVITY_DATA =
            ATTACHMENT_TYPES.register("activity_data", () -> AttachmentType
                    .builder(com.ambition.neoworld.activity.ActivityData::new)
                    .serialize(com.ambition.neoworld.activity.ActivityData.CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<PlayerTitleData>> TITLE_DATA = ATTACHMENT_TYPES.register(
            "title_data",
            () -> AttachmentType.builder(PlayerTitleData::new)
                    .serialize(PlayerTitleData.CODEC)
                    .copyOnDeath()
                    .build()
    );

    // ข้อมูลของผู้เล่น (Player Data) บันทึกอัตโนมัติด้วย Codec และ copyOnDeath เพื่อไม่ให้ข้อมูลหายเมื่อตาย
    public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENT_TYPES.register(
            "player_data",
            () -> AttachmentType.builder(() -> new PlayerData())
                    .serialize(PlayerData.CODEC) // แปลงและบันทึกข้อมูลลงไฟล์เซฟ
                    .copyOnDeath()               // คัดลอกข้อมูลเมื่อผู้เล่นตายแล้วเกิดใหม่
                    .build()
    );

    public static final Supplier<AttachmentType<FashionData>> FASHION_DATA = ATTACHMENT_TYPES.register(
            "fashion_data",
            () -> AttachmentType.builder(() -> new FashionData())
                    .serialize(FashionData.CODEC)
                    .copyOnDeath()
                    .build()
    );
}
