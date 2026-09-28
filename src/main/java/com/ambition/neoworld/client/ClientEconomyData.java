package com.ambition.neoworld.client;

import java.text.NumberFormat;
import java.util.Locale;

// คลาสเก็บข้อมูลยอดเงินระบบ Economy ฝั่ง Client สำหรับเรนเดอร์ HUD
public class ClientEconomyData {
    private static long silver = 0;
    private static long gold = 0;
    private static long diamond = 0;

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

    public static long getSilver() {
        return silver;
    }

    public static long getGold() {
        return gold;
    }

    public static long getDiamond() {
        return diamond;
    }

    /**
     * แปลงตัวเลขยอดเงินให้อยู่ในรูปแบบย่อที่อ่านง่าย (K, M, B, T)
     * ตัวอย่าง: 950 -> "950", 1500 -> "1.5K", 25000 -> "25K", 2500000 -> "2.5M"
     */
    public static String formatCompact(long value) {
        if (value < 0) {
            return "-" + formatCompact(-value);
        }
        if (value < 1_000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return formatWithSuffix(value, 1_000.0, "K");
        }
        if (value < 1_000_000_000) {
            return formatWithSuffix(value, 1_000_000.0, "M");
        }
        if (value < 1_000_000_000_000L) {
            return formatWithSuffix(value, 1_000_000_000.0, "B");
        }
        return formatWithSuffix(value, 1_000_000_000_000.0, "T");
    }

    private static String formatWithSuffix(long value, double divisor, String suffix) {
        double formatted = value / divisor;
        if (formatted >= 100.0) {
            return String.format(Locale.US, "%.0f%s", formatted, suffix);
        } else {
            String s = String.format(Locale.US, "%.2f", formatted);
            if (s.endsWith(".0")) {
                s = s.substring(0, s.length() - 2);
            }
            return s + suffix;
        }
    }

    public static String formatFull(long value) {
        return NUMBER_FORMAT.format(value);
    }

    public static String getFormattedSilver() {
        return formatCompact(silver);
    }

    public static String getFormattedGold() {
        return formatCompact(gold);
    }

    public static String getFormattedDiamond() {
        return formatCompact(diamond);
    }

    public static String getCompactSilver() {
        return formatCompact(silver);
    }

    public static String getCompactGold() {
        return formatCompact(gold);
    }

    public static String getCompactDiamond() {
        return formatCompact(diamond);
    }

    public static String getFullSilver() {
        return NUMBER_FORMAT.format(silver);
    }

    public static String getFullGold() {
        return NUMBER_FORMAT.format(gold);
    }

    public static String getFullDiamond() {
        return NUMBER_FORMAT.format(diamond);
    }

    public static void update(long newSilver, long newGold, long newDiamond) {
        silver = newSilver;
        gold = newGold;
        diamond = newDiamond;
    }

    public static void reset() {
        silver = 0L;
        gold = 0L;
        diamond = 0L;
    }
}
