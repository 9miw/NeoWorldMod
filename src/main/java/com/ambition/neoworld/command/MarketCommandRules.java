package com.ambition.neoworld.command;

/** Pure calculations shared by market command parsing and regression checks. */
public final class MarketCommandRules {
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private MarketCommandRules() {}

    public static long durationTicks(int minutes) {
        return minutes > 0 ? minutes * TICKS_PER_MINUTE : -1L;
    }

    public static int totalPages(int itemCount, int pageSize) {
        if (itemCount < 0 || pageSize <= 0) return -1;
        return itemCount == 0 ? 1 : (itemCount - 1) / pageSize + 1;
    }

    public static int clampPage(int requestedPage, int totalPages) {
        if (requestedPage < 1 || totalPages < 1) return -1;
        return Math.min(requestedPage, totalPages);
    }
}
