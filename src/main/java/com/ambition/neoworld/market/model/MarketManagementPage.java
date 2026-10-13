package com.ambition.neoworld.market.model;

import java.util.List;

/** Server-side page of a seller's active listings and completed sales. */
public record MarketManagementPage(
        int page,
        int totalPages,
        long totalEntries,
        long pendingSilver,
        List<MarketManagementEntry> entries
) {
    public MarketManagementPage {
        entries = List.copyOf(entries);
    }
}
