package com.ambition.neoworld.level;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.network.level.LevelUpCelebrationPayload;
import com.ambition.neoworld.network.level.SyncLevelPayload;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

// ตัวจัดการระบบเลเวลและค่าประสบการณ์ฝั่ง Server
public class PlayerLevelManager {
    /** Subtracts progression and cumulative EXP separately; returns levels lost. */
    public static int applyExpReduction(PlayerData data, long amount) {
        if (data == null || amount <= 0) return 0;
        int previousLevel = data.getLevel();
        int currentLevel = previousLevel;
        long currentExp = currentLevel >= LevelFormula.MAX_LEVEL ? 0 : data.getExp();
        long remaining = amount;
        while (remaining > currentExp && currentLevel > 1) {
            remaining -= currentExp;
            currentLevel--;
            currentExp = LevelFormula.getRequiredExpForNextLevel(currentLevel);
        }
        data.setLevel(currentLevel);
        data.setExp(remaining >= currentExp ? 0L : currentExp - remaining);
        data.removeTotalExp(amount);
        return previousLevel - currentLevel;
    }

    public static void removeExp(ServerPlayer player, long amount) {
        if (player == null || amount <= 0) return;
        applyExpReduction(player.getData(ModAttachments.PLAYER_DATA), amount);
        syncToClient(player);
        player.sendSystemMessage(Component.literal("§a[NeoWorld] §fลด EXP §e-" + amount
                + " §fแล้ว ปัจจุบันเลเวล §e" + player.getData(ModAttachments.PLAYER_DATA).getLevel()));
    }

    /**
     * เพิ่มค่าประสบการณ์ (EXP) ให้กับผู้เล่น และตรวจสอบการเลเวลอัป
     */
    public static void addExp(ServerPlayer player, long amount) {
        addExp(player, amount, true);
    }

    public static void addAdminExp(ServerPlayer player, long amount) {
        addExp(player, amount, true);
    }

    private static void addExp(ServerPlayer player, long amount, boolean countTotal) {
        if (amount <= 0 || player == null) return;

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        int gainedLevels = applyExp(data, amount, countTotal);
        if (gainedLevels > 0) onLevelUp(player, data.getLevel());

        syncToClient(player);
        if (gainedLevels > 0) {
            PacketDistributor.sendToPlayer(player, new LevelUpCelebrationPayload(data.getLevel()));
        }
    }

    /** Gameplay grants count toward cumulative EXP. Returns the number of levels gained. */
    public static int applyExp(PlayerData data, long amount) {
        return applyExp(data, amount, true);
    }

    public static int applyExp(PlayerData data, long amount, boolean countTotal) {
        if (data == null || amount <= 0) return 0;
        int previousLevel = data.getLevel();
        // Gameplay and administrative grants count once, including at level cap.
        data.addExp(amount, countTotal);
        int currentLevel = previousLevel;
        long currentExp = data.getExp();

        while (currentLevel < LevelFormula.MAX_LEVEL) {
            long requiredExp = LevelFormula.getRequiredExpForNextLevel(currentLevel);
            if (currentExp >= requiredExp) {
                currentExp -= requiredExp;
                currentLevel++;
            } else {
                break;
            }
        }

        // บันทึกค่าใหม่
        data.setLevel(currentLevel);
        data.setExp(currentLevel >= LevelFormula.MAX_LEVEL ? 0L : currentExp);

        return currentLevel - previousLevel;
    }

    /**
     * กำหนดเลเวลโดยตรง (สำหรับคำสั่ง Admin)
     */
    public static void setLevel(ServerPlayer player, int newLevel) {
        if (player == null) return;
        int clampedLevel = Math.max(1, Math.min(LevelFormula.MAX_LEVEL, newLevel));
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        data.setLevel(clampedLevel);
        data.setExp(0L);

        syncToClient(player);
        player.sendSystemMessage(Component.literal("§a[NeoWorld] §fระดับเลเวลของคุณถูกตั้งค่าเป็น: §e§l" + clampedLevel));
    }

    /**
     * ทำงานเมื่อผู้เล่นเลเวลอัป (เอฟเฟกต์แสง, เสียง, ประกาศ)
     */
    private static void onLevelUp(ServerPlayer player, int newLevel) {
        ServerLevel level = player.serverLevel();

        // 1. เล่นเสียงประกาศ
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );

        // 2. สร้างอนุภาคแสงรอบตัว
        level.sendParticles(
                ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                30,
                0.5, 0.8, 0.5,
                0.2
        );

        // 3. ส่งข้อความแชท
        player.sendSystemMessage(Component.literal("§6§l★ LEVEL UP! ★ §fยินดีด้วย! คุณเลเวล §a§l" + newLevel + " §fแล้ว"));
    }

    /**
     * ซิงค์ข้อมูลเลเวลและ EXP ไปยัง Client
     */
    public static void syncToClient(ServerPlayer player) {
        if (player == null) return;
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        long maxExp = LevelFormula.getRequiredExpForNextLevel(data.getLevel());
        PacketDistributor.sendToPlayer(player, new SyncLevelPayload(data.getLevel(), data.getExp(), maxExp));
    }
}
