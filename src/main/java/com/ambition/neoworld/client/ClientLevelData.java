package com.ambition.neoworld.client;

import com.ambition.neoworld.level.LevelFormula;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

// คลาสเก็บข้อมูลเลเวลชั่วคราวฝั่ง Client สำหรับเรนเดอร์ HUD
public class ClientLevelData {
    private static final int DEFAULT_LEVEL = 1;
    private static final long DEFAULT_EXP = 0L;
    private static final long DEFAULT_MAX_EXP = 100L;

    private static int level = DEFAULT_LEVEL;
    private static long exp = DEFAULT_EXP;
    private static long maxExp = DEFAULT_MAX_EXP;

    public static int getLevel() {
        return level;
    }

    public static long getExp() {
        return exp;
    }

    public static long getMaxExp() {
        return maxExp;
    }

    public static float getProgressRatio() {
        if (level >= LevelFormula.MAX_LEVEL) {
            return 1.0f;
        }
        if (maxExp <= 0) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (float) exp / (float) maxExp));
    }

    public static void update(int newLevel, long newExp, long newMaxExp) {
        level = newLevel;
        exp = newExp;
        maxExp = newMaxExp;
    }

    public static void reset() {
        level = DEFAULT_LEVEL;
        exp = DEFAULT_EXP;
        maxExp = DEFAULT_MAX_EXP;
    }

    public static void onLevelUpCelebration(int newLevel) {
        level = newLevel;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            // เล่นเสียง Level-up
            mc.level.playSound(
                    mc.player,
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );

            // แสดง Title / Subtitle กลางจอ
            mc.gui.setTitle(Component.literal("§6§l★ LEVEL UP! ★"));
            mc.gui.setSubtitle(Component.literal("§eเลเวลตัวละครของคุณเพิ่มเป็น §a§l" + newLevel));
            mc.gui.setTimes(10, 60, 20); // fade in 10 ticks, stay 60 ticks, fade out 20 ticks
        }
    }
}
