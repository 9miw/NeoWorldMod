package com.ambition.neoworld.market;

import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/** Immutable snapshot of an item offered on the server market. */
public record MarketListing(
        long id,
        UUID sellerId,
        String sellerName,
        ItemStack item,
        long priceSilver,
        long createdAtGameTime,
        long expiresAtGameTime
) {
    public MarketListing {
        item = item.copy();
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= expiresAtGameTime;
    }
}
