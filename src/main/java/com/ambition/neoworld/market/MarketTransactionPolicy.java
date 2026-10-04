package com.ambition.neoworld.market;

import com.ambition.neoworld.economy.CurrencyMath;

/** Pure validation used before a purchase mutates either wallet or market data. */
public final class MarketTransactionPolicy {
    private MarketTransactionPolicy() {}

    public static PurchaseCheck checkPurchase(boolean listingExists, boolean expired, boolean ownListing,
                                              long buyerBalance, long sellerPendingSilver,
                                              long priceSilver, int taxRateBps) {
        if (!listingExists) return new PurchaseCheck(MarketResult.LISTING_NOT_FOUND, 0L, 0L);
        if (expired) return new PurchaseCheck(MarketResult.LISTING_EXPIRED, 0L, 0L);
        if (ownListing) return new PurchaseCheck(MarketResult.CANNOT_BUY_OWN_LISTING, 0L, 0L);
        long tax = MarketMath.percentage(priceSilver, taxRateBps);
        long proceeds = MarketMath.sellerProceeds(priceSilver, taxRateBps);
        if (priceSilver <= 0 || tax < 0 || proceeds < 0) {
            return new PurchaseCheck(MarketResult.INVALID_RATE, 0L, 0L);
        }
        if (buyerBalance < priceSilver) {
            return new PurchaseCheck(MarketResult.INSUFFICIENT_FUNDS, 0L, 0L);
        }
        if (proceeds > 0 && !CurrencyMath.canAdd(sellerPendingSilver, proceeds)) {
            return new PurchaseCheck(MarketResult.BALANCE_OVERFLOW, 0L, 0L);
        }
        return new PurchaseCheck(MarketResult.SUCCESS, proceeds, tax);
    }

    public record PurchaseCheck(MarketResult result, long sellerProceeds, long taxSilver) {}
}
