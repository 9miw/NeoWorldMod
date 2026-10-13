package com.ambition.neoworld.market.model;

import net.minecraft.world.item.ItemStack;

/** One active or completed row in the seller's market management screen. */
public record MarketManagementEntry(
        long recordId,
        long listingId,
        Status status,
        ItemStack item,
        int quantity,
        long unitPriceSilver,
        long proceedsSilver,
        long updatedGameTime,
        long expiresGameTime
) {
    public MarketManagementEntry {
        item = item.copy();
    }

    @Override
    public ItemStack item() {
        return item.copy();
    }

    public enum Status {
        ACTIVE,
        PENDING,
        CLAIMED
    }
}
