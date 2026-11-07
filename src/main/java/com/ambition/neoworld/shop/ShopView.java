package com.ambition.neoworld.shop;

/** Directions are named from the shop's perspective; IDs are synchronized menu data. */
public enum ShopView {
    ALL, STORE_SELLS, STORE_BUYS;

    public boolean includes(long buyPrice, long sellPrice) {
        return switch (this) {
            case ALL -> buyPrice > 0 || sellPrice > 0;
            case STORE_SELLS -> buyPrice > 0;
            case STORE_BUYS -> sellPrice > 0;
        };
    }

    public static ShopView fromId(int id) {
        if (id < 0 || id >= values().length) throw new IllegalArgumentException("Invalid shop view");
        return values()[id];
    }
}
