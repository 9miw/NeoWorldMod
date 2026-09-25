package com.ambition.neoworld.level;

// คลาสสูตรคำนวณ EXP และข้อมูลเพดานเลเวลของ NeoWorld
public class LevelFormula {
    public static final int MAX_LEVEL = 100;
    public static final int BASE_EXP = 100;
    public static final double EXP_EXPONENT = 1.5;

    /**
     * คำนวณค่า EXP ที่ต้องใช้ในการเลื่อนจากเลเวลปัจจุบันไปยังเลเวลถัดไป
     * สูตร: floor(100 * Level ^ 1.5)
     */
    public static long getRequiredExpForNextLevel(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) {
            return Long.MAX_VALUE; // เลเวลตันแล้ว ไม่ต้องการ EXP เพิ่ม
        }
        return (long) Math.floor(BASE_EXP * Math.pow(Math.max(1, currentLevel), EXP_EXPONENT));
    }

    /**
     * คำนวณอัตราส่วนความก้าวหน้า (0.0 ถึง 1.0) สำหรับวาดหลอด EXP บนหน้าจอ
     */
    public static float getExpProgressRatio(long currentExp, int currentLevel) {
        if (currentLevel >= MAX_LEVEL) {
            return 1.0f;
        }
        long required = getRequiredExpForNextLevel(currentLevel);
        if (required <= 0) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (float) currentExp / (float) required));
    }
}
