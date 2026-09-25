package com.ambition.neoworld.registry;

import com.ambition.neoworld.client.ClientEconomyData;
import com.ambition.neoworld.client.ClientLevelData;
import com.ambition.neoworld.network.LevelUpCelebrationPayload;
import com.ambition.neoworld.network.SyncEconomyPayload;
import com.ambition.neoworld.network.SyncLevelPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// คลาสลงทะเบียน Network Payloads สำหรับ NeoForge 1.21.1
public class ModPayloads {

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // ซิงค์เลเวลและ EXP จาก Server ไป Client
        registrar.playToClient(
                SyncLevelPayload.TYPE,
                SyncLevelPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ClientLevelData.update(payload.level(), payload.exp(), payload.maxExp());
                })
        );

        // ส่งสัญญาณเฉลิมฉลองการเลเวลอัปจาก Server ไป Client
        registrar.playToClient(
                LevelUpCelebrationPayload.TYPE,
                LevelUpCelebrationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ClientLevelData.onLevelUpCelebration(payload.newLevel());
                })
        );

        // ซิงค์ข้อมูลยอดเงินระบบ Economy (Silver, Gold, Diamond) จาก Server ไป Client
        registrar.playToClient(
                SyncEconomyPayload.TYPE,
                SyncEconomyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ClientEconomyData.update(payload.silver(), payload.gold(), payload.diamond());
                })
        );
    }
}
