package com.ambition.neoworld.registry;

import java.util.function.Supplier;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// คลาสสำหรับลงทะเบียน Data Attachments (ระบบเก็บข้อมูลผูกกับ Entity/Player ใน NeoForge)
public class ModAttachments {
    // Deferred Register สำหรับ Attachment Types ภายใต้ namespace "neoworld"
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NeoWorld.MODID);

    // ข้อมูลของผู้เล่น (Player Data) บันทึกอัตโนมัติด้วย Codec และ copyOnDeath เพื่อไม่ให้ข้อมูลหายเมื่อตาย
    public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENT_TYPES.register(
            "player_data",
            () -> AttachmentType.builder(() -> new PlayerData())
                    .serialize(PlayerData.CODEC) // แปลงและบันทึกข้อมูลลงไฟล์เซฟ
                    .copyOnDeath()               // คัดลอกข้อมูลเมื่อผู้เล่นตายแล้วเกิดใหม่
                    .build()
    );
}
