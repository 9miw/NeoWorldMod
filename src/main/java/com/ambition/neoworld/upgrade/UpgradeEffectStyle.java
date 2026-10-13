package com.ambition.neoworld.upgrade;

/** Shared palette and tier decisions; no client classes. */
public record UpgradeEffectStyle(int color, int interval, boolean runes, boolean trail) {
    public static UpgradeEffectStyle forLevel(int level) {
        if (level < 1 || level > 15) return new UpgradeEffectStyle(0, 0, false, false);
        if (level <= 3) return new UpgradeEffectStyle(0xEAF4FF, 12, false, false);
        if (level <= 6) return new UpgradeEffectStyle(0x42CFFF, 9, false, false);
        if (level <= 9) return new UpgradeEffectStyle(0xBD70FF, 7, true, false);
        if (level <= 12) return new UpgradeEffectStyle(0xFFCE55, 5, true, true);
        if (level <= 14) return new UpgradeEffectStyle(0xFF6540, 4, true, true);
        return new UpgradeEffectStyle(0xFFE793, 3, true, true);
    }
    public float red() { return ((color >> 16) & 255) / 255F; }
    public float green() { return ((color >> 8) & 255) / 255F; }
    public float blue() { return (color & 255) / 255F; }
}
