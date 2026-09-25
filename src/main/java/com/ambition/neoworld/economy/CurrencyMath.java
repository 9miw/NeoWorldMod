package com.ambition.neoworld.economy;

// คำนวณมูลค่าเงินโดยปฏิเสธค่าผิดปกติและจำนวนที่เกินขอบเขต long
public final class CurrencyMath {
    private CurrencyMath() {}

    public static long total(long value, int count) {
        return value > 0 && count > 0 && value <= Long.MAX_VALUE / count
                ? value * count : -1L;
    }

    public static boolean canAdd(long balance, long amount) {
        return balance >= 0 && amount > 0 && amount <= Long.MAX_VALUE - balance;
    }

    public static boolean canAfford(long balance, long first, long second) {
        return balance >= 0 && first >= 0 && second >= 0
                && first <= balance && second <= balance - first;
    }
}
