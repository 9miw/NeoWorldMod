package com.ambition.neoworld.market;

import java.util.UUID;

/** Audit entry for a completed market operation. */
public record MarketTransaction(
        long id,
        Type type,
        long listingId,
        UUID sellerId,
        UUID buyerId,
        long priceSilver,
        long taxSilver,
        long gameTime
) {
    public enum Type {
        LISTED,
        SOLD,
        CANCELLED,
        EXPIRED
    }
}
