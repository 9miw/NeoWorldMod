package com.ambition.neoworld.shop;

import com.ambition.neoworld.data.PlayerData;
import java.util.Locale;

/** Stable menu IDs and independent wallet balances; no automatic currency conversion. */
public enum ShopCurrency {
    SILVER(0, "Silver", "\uE046"), DIAMOND(2, "Diamond", "\uE048");

    private final int id;
    private final String label, glyph;
    ShopCurrency(int id, String label, String glyph) { this.id=id; this.label=label; this.glyph=glyph; }
    public int id() { return id; }
    public String label() { return label; }
    public String glyph() { return glyph; }
    public String commandName() { return name().toLowerCase(Locale.ROOT); }
    public boolean enabled() { return true; }
    public static ShopCurrency fromId(int id) {
        return switch (id) { case 0 -> SILVER; case 2 -> DIAMOND;
            default -> throw new IllegalArgumentException("Invalid shop currency ID: " + id); };
    }
    public long balance(PlayerData wallet) {
        return switch (this) { case SILVER -> wallet.getSilver(); case DIAMOND -> wallet.getDiamond(); };
    }
    public void setBalance(PlayerData wallet, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative shop balance");
        switch (this) { case SILVER -> wallet.setSilver(amount); case DIAMOND -> wallet.setDiamond(amount); }
    }
}
