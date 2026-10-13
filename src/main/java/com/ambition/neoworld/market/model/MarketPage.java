package com.ambition.neoworld.market.model;

import java.util.List;

public record MarketPage(int page, int totalPages, long totalListings, List<MarketListing> listings) {
    public MarketPage {
        listings = List.copyOf(listings);
    }
}
