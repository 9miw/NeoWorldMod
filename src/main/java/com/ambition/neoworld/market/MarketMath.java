package com.ambition.neoworld.market;

/** Overflow-safe fee calculations. Rates use basis points: 100 = 1%. */
public final class MarketMath {
    public static final int MAX_RATE_BPS = 10_000;

    private MarketMath() {}

    public static boolean validRate(int rateBps) {
        return rateBps >= 0 && rateBps <= MAX_RATE_BPS;
    }

    public static long percentage(long amount, int rateBps) {
        if (amount < 0 || !validRate(rateBps)) return -1L;
        long whole = amount / MAX_RATE_BPS;
        long remainder = amount % MAX_RATE_BPS;
        return whole * rateBps + remainder * rateBps / MAX_RATE_BPS;
    }

    public static long sellerProceeds(long price, int taxRateBps) {
        long tax = percentage(price, taxRateBps);
        return tax < 0 ? -1L : price - tax;
    }
}
