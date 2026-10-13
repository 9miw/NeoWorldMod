package com.ambition.neoworld.upgrade;

public final class UpgradeEffectRegressionTest {
    public static void main(String[] args) {
        for (int level : new int[] {-1, 0, 16}) {
            var style = UpgradeEffectStyle.forLevel(level);
            check(style.interval() == 0 && !style.runes() && !style.trail(), "invalid/reset levels must disable effects");
        }
        for (int level = 1; level <= 15; level++) {
            var style = UpgradeEffectStyle.forLevel(level);
            check(style.interval() >= 3, "particle emission must be bounded");
            check(style.runes() == (level >= 7), "rune tier boundary");
            check(style.trail() == (level >= 10), "trail tier boundary");
            check(style.red() >= 0 && style.red() <= 1 && style.green() >= 0 && style.green() <= 1
                    && style.blue() >= 0 && style.blue() <= 1, "normalized RGB");
        }
        check(UpgradeEffectStyle.forLevel(14).color() != UpgradeEffectStyle.forLevel(15).color(), "+15 must have distinct color");
        System.out.println("Upgrade effect tier checks passed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
