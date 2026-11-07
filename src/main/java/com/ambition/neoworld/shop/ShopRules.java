package com.ambition.neoworld.shop;

/** Shared, overflow-safe validation; zero disables one side of an offer. */
public final class ShopRules {
    private ShopRules() {}

    public static boolean validPrices(long buy, long sell) {
        return buy >= 0 && sell >= 0 && (buy > 0 || sell > 0) && (buy == 0 || sell < buy);
    }

    public static boolean conflicts(long buy, long sell, ShopCurrency currency,
                                    long otherBuy, long otherSell, ShopCurrency otherCurrency) {
        return currency == otherCurrency && ((buy > 0 && otherSell >= buy)
                || (sell > 0 && otherBuy > 0 && sell >= otherBuy));
    }

    public static long total(long price, int quantity) {
        if (price <= 0 || quantity < 1 || quantity > 64) throw new IllegalArgumentException("Invalid shop quantity/price");
        return Math.multiplyExact(price, quantity);
    }

    public static String code(long id) {
        return Long.toString(id);
    }

    public static long balance(long before, long total, boolean sell) {
        if (before < 0 || total <= 0 || (!sell && before < total)) throw new IllegalArgumentException("Insufficient or invalid balance");
        return sell ? Math.addExact(before, total) : before - total;
    }
}
