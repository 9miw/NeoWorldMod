package com.ambition.neoworld.upgrade;

/** Upgrade amounts depend on original equipment values, never the previous upgrade amount. */
public final class UpgradeBonusMath {
    private UpgradeBonusMath() {}

    public static double damage(double originalWeaponDamage, int level, double percentPerLevel) {
        validate(level, percentPerLevel);
        if (!Double.isFinite(originalWeaponDamage)) throw new IllegalArgumentException("Non-finite weapon damage");
        return Math.max(0.0, originalWeaponDamage) * level * percentPerLevel / 100.0;
    }

    public static double flat(int level, double perLevel) {
        validate(level, perLevel);
        return level * perLevel;
    }

    private static void validate(int level, double rate) {
        if (level < 0 || level > 15 || !Double.isFinite(rate) || rate < 0 || rate > 100) {
            throw new IllegalArgumentException("Invalid upgrade level or rate");
        }
    }
}
