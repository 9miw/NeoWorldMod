package com.ambition.neoworld.killer;

/** Server policy shared by combat, persistence, and presentation. */
public final class KillerRules {
    public static final int SIN_PER_KILL = 100;
    public static final int DECAY_TICKS = 12_000;
    public static final int DECAY_SIN = 10;
    public static final int DEFENSE_TICKS = 1_200;
    public static final int CREDIT_TICKS = 600;

    private KillerRules() {}

    public static boolean isRed(int sin) { return sin > 0; }

    public static int addKillSin(int sin) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, sin) + SIN_PER_KILL);
    }

    public static long deathPenalty(long silver) {
        return Math.min(Math.max(0L, silver) / 20L, 1_000L);
    }

    public static int sinForKill(boolean victimIsRed, boolean isSelfDefense, boolean isArenaOrCanceled) {
        if (victimIsRed || isSelfDefense || isArenaOrCanceled) return 0;
        return SIN_PER_KILL;
    }
}
