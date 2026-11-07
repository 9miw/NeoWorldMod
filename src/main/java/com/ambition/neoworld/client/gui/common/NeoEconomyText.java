package com.ambition.neoworld.client.gui.common;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shared presentation only; callers provide their own snapshot of formatted amounts. */
public final class NeoEconomyText {
    public enum Order { SILVER_FIRST, DIAMOND_FIRST }
    private NeoEconomyText() {}

    public static MutableComponent amount(NeoBitmapIcon currency, String amount) {
        int color = switch (currency) {
            case CURRENCY_SILVER_COIN -> 0xFFFFFF;
            case CURRENCY_DIAMOND_COIN -> 0x55FFFF;
            default -> throw new IllegalArgumentException("Not a currency icon: " + currency);
        };
        return Component.empty().append(currency.component().withStyle(style -> style.withColor(0xFFFFFF)))
                .append(Component.literal(Objects.requireNonNull(amount)).withStyle(style -> style.withColor(color)));
    }

    public static MutableComponent balances(String silver, String diamond, Order order) {
        Objects.requireNonNull(order);
        Component s = amount(NeoBitmapIcon.CURRENCY_SILVER_COIN, silver);
        Component d = amount(NeoBitmapIcon.CURRENCY_DIAMOND_COIN, diamond);
        return Component.empty().append(order == Order.SILVER_FIRST ? s : d)
                .append(" ").append(order == Order.SILVER_FIRST ? d : s);
    }
}
