package com.ambition.neoworld.market;

import com.ambition.neoworld.economy.CurrencyMath;

/** Pure validation used before a purchase mutates either wallet or market data. */
public final class MarketTransactionPolicy {
    private MarketTransactionPolicy() {}

    public static PurchaseCheck checkPurchase(boolean listingExists, boolean expired, boolean ownListing,
                                              long buyerBalance, long sellerPendingSilver,
                                              long unitPriceSilver, int quantity, int availableQuantity,
                                              int taxRateBps) {
        if (!listingExists) return failure(MarketResult.LISTING_NOT_FOUND);
        if (expired) return failure(MarketResult.LISTING_EXPIRED);
        if (ownListing) return failure(MarketResult.CANNOT_BUY_OWN_LISTING);
        if (quantity <= 0) return failure(MarketResult.INVALID_QUANTITY);
        if (quantity > availableQuantity) return failure(MarketResult.INSUFFICIENT_STOCK);
        long totalPrice = CurrencyMath.total(unitPriceSilver, quantity);
        if (totalPrice <= 0) return failure(MarketResult.INVALID_PRICE);
        long tax = MarketMath.percentage(totalPrice, taxRateBps);
        long proceeds = MarketMath.sellerProceeds(totalPrice, taxRateBps);
        if (tax < 0 || proceeds < 0) {
            return failure(MarketResult.INVALID_RATE);
        }
        if (buyerBalance < totalPrice) {
            return failure(MarketResult.INSUFFICIENT_FUNDS);
        }
        if (proceeds > 0 && !CurrencyMath.canAdd(sellerPendingSilver, proceeds)) {
            return failure(MarketResult.BALANCE_OVERFLOW);
        }
        return new PurchaseCheck(MarketResult.SUCCESS, totalPrice, proceeds, tax);
    }

    private static PurchaseCheck failure(MarketResult result) {
        return new PurchaseCheck(result, 0L, 0L, 0L);
    }

    public record PurchaseCheck(MarketResult result, long totalPriceSilver, long sellerProceeds, long taxSilver) {}
}
