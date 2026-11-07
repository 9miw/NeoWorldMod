package com.ambition.neoworld.market.service;

import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.model.MarketListing;
import com.ambition.neoworld.market.model.MarketManagementPage;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server transaction boundary for market mutations and state-machine item delivery. */
public final class MarketService {
    private MarketService() {}

    public static ListingResult createListing(ServerPlayer seller, int inventorySlot, int count,
                                              long priceSilver, long durationTicks) {
        return createListing(UUID.randomUUID(), seller, inventorySlot, count, priceSilver, durationTicks);
    }

    public static ListingResult createListing(UUID operationId, ServerPlayer seller, int inventorySlot, int count,
                                              long priceSilver, long durationTicks) {
        if (seller == null || count <= 0) return new ListingResult(MarketResult.INVALID_ITEM, OptionalLong.empty());
        if (priceSilver <= 0) return new ListingResult(MarketResult.INVALID_PRICE, OptionalLong.empty());
        if (durationTicks <= 0) return new ListingResult(MarketResult.INVALID_DURATION, OptionalLong.empty());
        if (inventorySlot < 0 || inventorySlot >= seller.getInventory().getContainerSize()) {
            return new ListingResult(MarketResult.INVALID_ITEM, OptionalLong.empty());
        }
        ItemStack source = seller.getInventory().getItem(inventorySlot);
        return createListing(operationId, seller, source, count, priceSilver, durationTicks);
    }

    public static ListingResult createListing(ServerPlayer seller, ItemStack source, int count,
                                              long priceSilver, long durationTicks) {
        return createListing(UUID.randomUUID(), seller, source, count, priceSilver, durationTicks);
    }

    public static ListingResult createListing(UUID operationId, ServerPlayer seller, ItemStack source, int count,
                                              long priceSilver, long durationTicks) {
        return MarketDurability.execute(seller, operationId, "CREATE_LISTING", source,
                () -> createListingUnchecked(operationId, seller, source, count, priceSilver, durationTicks));
    }

    private static ListingResult createListingUnchecked(UUID operationId, ServerPlayer seller, ItemStack source,
            int count, long priceSilver, long durationTicks) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(seller, "market_listing_fee", "", "SINK")) {
            if (seller == null || count <= 0) return new ListingResult(MarketResult.INVALID_ITEM, OptionalLong.empty());
            if (priceSilver <= 0) return new ListingResult(MarketResult.INVALID_PRICE, OptionalLong.empty());
            if (durationTicks <= 0) return new ListingResult(MarketResult.INVALID_DURATION, OptionalLong.empty());
            if (source == null || source.isEmpty() || source.getCount() < count) {
                return new ListingResult(MarketResult.INVENTORY_CHANGED, OptionalLong.empty());
            }
            long now = seller.serverLevel().getGameTime();
            if (durationTicks > Long.MAX_VALUE - now) {
                return new ListingResult(MarketResult.INVALID_DURATION, OptionalLong.empty());
            }

            PlayerData wallet = seller.getData(ModAttachments.PLAYER_DATA);
            try {
                long listingFee = JdbcMarketRepository.settings().listingFeeSilver();
                if (listingFee > 0 && !wallet.removeSilver(listingFee)) {
                    return new ListingResult(MarketResult.INSUFFICIENT_FUNDS, OptionalLong.empty());
                }
                ItemStack listedItem = source.copyWithCount(count);
                source.shrink(count);
                try {
                    long id = JdbcMarketRepository.createListing(operationId, seller.getUUID(), seller.getGameProfile().getName(),
                            listedItem, priceSilver, now, now + durationTicks, seller.registryAccess());
                    return new ListingResult(MarketResult.SUCCESS, OptionalLong.of(id));
                } catch (SQLException | IOException exception) {
                    source.grow(count);
                    if (listingFee > 0 && !wallet.addSilver(listingFee)) {
                        NeoWorld.LOGGER.error("Could not refund listing fee to {}", seller.getUUID());
                    }
                    throw exception;
                }
            } catch (SQLException | IOException exception) {
                logDatabaseFailure("create listing", exception);
                return new ListingResult(MarketResult.DATABASE_ERROR, OptionalLong.empty());
            }
        }
    }

    public static MarketResult buy(ServerPlayer buyer, long listingId) {
        return buy(UUID.randomUUID(), buyer, listingId, 0, true);
    }

    public static MarketResult buy(ServerPlayer buyer, long listingId, int quantity) {
        return buy(UUID.randomUUID(), buyer, listingId, quantity, false);
    }

    public static MarketResult buy(UUID operationId, ServerPlayer buyer, long listingId, int quantity) {
        return buy(operationId, buyer, listingId, quantity, false);
    }

    private static MarketResult buy(UUID operationId, ServerPlayer buyer, long listingId, int requestedQuantity, boolean buyAll) {
        return MarketDurability.execute(buyer, operationId, "PURCHASE", null,
                () -> new ListingResult(buyUnchecked(operationId, buyer, listingId, requestedQuantity, buyAll), OptionalLong.empty())).result();
    }

    private static MarketResult buyUnchecked(UUID operationId, ServerPlayer buyer, long listingId, int requestedQuantity, boolean buyAll) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(buyer, "market_purchase", Long.toString(listingId), "TRANSFER")) {
            if (buyer == null) return MarketResult.LISTING_NOT_FOUND;
            if (!buyAll && requestedQuantity <= 0) return MarketResult.INVALID_QUANTITY;
            PlayerData wallet = buyer.getData(ModAttachments.PLAYER_DATA);
            try {
                MarketListing observed = JdbcMarketRepository.listing(listingId, buyer.registryAccess()).orElse(null);
                if (observed == null) return MarketResult.LISTING_NOT_FOUND;
                if (observed.isExpired(buyer.serverLevel().getGameTime())) return MarketResult.LISTING_EXPIRED;
                if (observed.sellerId().equals(buyer.getUUID())) return MarketResult.CANNOT_BUY_OWN_LISTING;
                int quantity = buyAll ? observed.item().getCount() : requestedQuantity;
                if (quantity > observed.item().getCount()) return MarketResult.INSUFFICIENT_STOCK;
                long totalPrice = CurrencyMath.total(observed.priceSilver(), quantity);
                if (totalPrice <= 0) return MarketResult.INVALID_PRICE;
                JdbcMarketRepository.MarketSettings settings = JdbcMarketRepository.settings();
                long balanceBefore = wallet.getSilver();
                if (!wallet.removeSilver(totalPrice)) return MarketResult.INSUFFICIENT_FUNDS;

                JdbcMarketRepository.PurchaseResult purchase;
                try {
                    purchase = JdbcMarketRepository.purchase(operationId, listingId, buyer.getUUID(), quantity, balanceBefore,
                            buyer.serverLevel().getGameTime(), settings.salesTaxRateBps(), buyer.registryAccess());
                } catch (SQLException exception) {
                    refundSilver(wallet, totalPrice, buyer.getUUID());
                    throw exception;
                }
                if (purchase.result() != MarketResult.SUCCESS) {
                    refundSilver(wallet, totalPrice, buyer.getUUID());
                    return purchase.result();
                }
                // Decoupled from auto-delivery: item safely resides in market mailbox (pending items)
                return MarketResult.SUCCESS;
            } catch (SQLException | IOException exception) {
                logDatabaseFailure("buy listing", exception);
                return MarketResult.DATABASE_ERROR;
            }
        }
    }

    public static MarketResult cancel(ServerPlayer seller, long listingId) {
        return cancel(UUID.randomUUID(), seller, listingId);
    }

    public static MarketResult cancel(UUID operationId, ServerPlayer seller, long listingId) {
        if (seller == null) return MarketResult.LISTING_NOT_FOUND;
        try {
            // Decoupled from auto-delivery: cancelled item safely resides in pending items until claimed
            return JdbcMarketRepository.cancel(operationId, listingId, seller.getUUID(),
                    seller.serverLevel().getGameTime(), seller.registryAccess());
        } catch (SQLException exception) {
            logDatabaseFailure("cancel listing", exception);
            return MarketResult.DATABASE_ERROR;
        }
    }

    public static int expireListings(MinecraftServer server) {
        try {
            return JdbcMarketRepository.expireListings(server.overworld().getGameTime(), server.registryAccess());
        } catch (SQLException exception) {
            logDatabaseFailure("expire listings", exception);
            return 0;
        }
    }

    public static int expireListingsBatch(MinecraftServer server, int batchSize) {
        try {
            return JdbcMarketRepository.expireListingsBatch(server.overworld().getGameTime(), batchSize,
                    server.registryAccess());
        } catch (SQLException exception) {
            logDatabaseFailure("expire listings batch", exception);
            return 0;
        }
    }

    public static MarketResult claim(ServerPlayer player) {
        return claim(UUID.randomUUID(), player);
    }

    public static MarketResult claim(UUID operationId, ServerPlayer player) {
        return MarketDurability.execute(player, operationId, "CLAIM_ALL", null,
                () -> new ListingResult(claimUnchecked(operationId, player), OptionalLong.empty())).result();
    }

    private static MarketResult claimUnchecked(UUID operationId, ServerPlayer player) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(player, "market_claim", "all", "TRANSFER")) {
            if (player == null) return MarketResult.NOTHING_TO_CLAIM;
            PlayerData wallet = player.getData(ModAttachments.PLAYER_DATA);
            long gameTime = player.serverLevel().getGameTime();
            try {
                long silver = JdbcMarketRepository.pendingSilver(player.getUUID());
                UUID deliveryId = UUID.randomUUID();
                List<JdbcMarketRepository.PendingItem> reservedItems = JdbcMarketRepository.reservePendingItems(
                        player.getUUID(), deliveryId, gameTime, player.registryAccess());

                if (silver == 0 && reservedItems.isEmpty()) return MarketResult.NOTHING_TO_CLAIM;

                // 1. Process Silver sales claim
                if (silver > 0) {
                    if (!PlayerEconomyManager.deposit(wallet, CoinItem.CoinType.SILVER, silver)) {
                        for (JdbcMarketRepository.PendingItem reserved : reservedItems) {
                            JdbcMarketRepository.revertPendingItemToPending(reserved.id(), deliveryId, gameTime);
                        }
                        return MarketResult.BALANCE_OVERFLOW;
                    }
                    try {
                        if (!JdbcMarketRepository.claimAllSales(operationId, player.getUUID(), silver, gameTime)) {
                            wallet.removeSilver(silver);
                            for (JdbcMarketRepository.PendingItem reserved : reservedItems) {
                                JdbcMarketRepository.revertPendingItemToPending(reserved.id(), deliveryId, gameTime);
                            }
                            return MarketResult.DATABASE_ERROR;
                        }
                    } catch (SQLException exception) {
                        wallet.removeSilver(silver);
                        for (JdbcMarketRepository.PendingItem reserved : reservedItems) {
                            JdbcMarketRepository.revertPendingItemToPending(reserved.id(), deliveryId, gameTime);
                        }
                        throw exception;
                    }
                }

                // 2. Deliver reserved items under strict state machine
                deliverReservedItems(player, deliveryId, reservedItems, gameTime);

                return MarketResult.SUCCESS;
            } catch (SQLException | IOException exception) {
                logDatabaseFailure("claim market deliveries", exception);
                return MarketResult.DATABASE_ERROR;
            }
        }
    }

    public static MarketResult claimSale(ServerPlayer player, long transactionId) {
        return claimSale(UUID.randomUUID(), player, transactionId);
    }

    public static MarketResult claimSale(UUID operationId, ServerPlayer player, long transactionId) {
        return MarketDurability.execute(player, operationId, "CLAIM_SALE", null,
                () -> new ListingResult(claimSaleUnchecked(operationId, player, transactionId), OptionalLong.empty())).result();
    }

    private static MarketResult claimSaleUnchecked(UUID operationId, ServerPlayer player, long transactionId) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(player, "market_claim", "sale:" + transactionId, "TRANSFER")) {
            if (player == null || transactionId <= 0) return MarketResult.NOTHING_TO_CLAIM;
            PlayerData wallet = player.getData(ModAttachments.PLAYER_DATA);
            long gameTime = player.serverLevel().getGameTime();
            try {
                OptionalLong amount = JdbcMarketRepository.claimableSale(transactionId, player.getUUID());
                if (amount.isEmpty() || amount.getAsLong() <= 0) return MarketResult.NOTHING_TO_CLAIM;
                long silver = amount.getAsLong();
                if (!PlayerEconomyManager.deposit(wallet, CoinItem.CoinType.SILVER, silver)) {
                    return MarketResult.BALANCE_OVERFLOW;
                }
                try {
                    if (!JdbcMarketRepository.claimSale(operationId, transactionId, player.getUUID(), silver, gameTime)) {
                        wallet.removeSilver(silver);
                        return MarketResult.NOTHING_TO_CLAIM;
                    }
                } catch (SQLException exception) {
                    wallet.removeSilver(silver);
                    throw exception;
                }
                return MarketResult.SUCCESS;
            } catch (SQLException exception) {
                logDatabaseFailure("claim market sale", exception);
                return MarketResult.DATABASE_ERROR;
            }
        }
    }

    public static MarketResult deleteClaimedSale(ServerPlayer player, long transactionId) {
        if (player == null || transactionId <= 0) return MarketResult.LISTING_NOT_FOUND;
        try {
            return JdbcMarketRepository.deleteClaimedSale(transactionId, player.getUUID())
                    ? MarketResult.SUCCESS : MarketResult.LISTING_NOT_FOUND;
        } catch (SQLException exception) {
            logDatabaseFailure("delete claimed market sale", exception);
            return MarketResult.DATABASE_ERROR;
        }
    }

    public static MarketManagementPage managementPage(ServerPlayer player, int requestedPage, int pageSize)
            throws SQLException, IOException {
        JdbcMarketRepository.MarketSettings settings = JdbcMarketRepository.settings();
        return JdbcMarketRepository.managementPage(player.getUUID(), requestedPage, pageSize,
                settings.salesTaxRateBps(), player.registryAccess());
    }

    public static List<MarketListing> listings(MinecraftServer server) throws SQLException, IOException {
        return JdbcMarketRepository.listings(server.registryAccess());
    }

    public static long pendingSilver(ServerPlayer player) throws SQLException {
        return JdbcMarketRepository.pendingSilver(player.getUUID());
    }

    public static int pendingItemCount(ServerPlayer player) throws SQLException, IOException {
        return JdbcMarketRepository.pendingItems(player.getUUID(), player.registryAccess()).size();
    }

    // === Asynchronous Methods for Network and Background Execution ===

    public static void createListingAsync(ServerPlayer player, UUID operationId, ItemStack source, int count,
                                          long priceSilver, long durationTicks,
                                          Consumer<ListingResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(player.getUUID(), "CREATE_LISTING")) {
            callback.accept(new ListingResult(MarketResult.MARKET_BUSY, OptionalLong.empty()));
            return;
        }
        player.server.execute(() -> {
            ListingResult result;
            try {
                result = createListing(operationId, player, source, count, priceSilver, durationTicks);
            } finally {
                MarketDatabaseExecutor.release(player.getUUID());
            }
            player.server.execute(() -> callback.accept(result));
        });
    }

    public static void buyAsync(ServerPlayer buyer, UUID operationId, long listingId, int quantity,
                                Consumer<MarketResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(buyer.getUUID(), "PURCHASE")) {
            callback.accept(MarketResult.MARKET_BUSY);
            return;
        }
        buyer.server.execute(() -> {
            MarketResult result;
            try {
                result = buy(operationId, buyer, listingId, quantity);
            } finally {
                MarketDatabaseExecutor.release(buyer.getUUID());
            }
            buyer.server.execute(() -> callback.accept(result));
        });
    }

    public static void cancelAsync(ServerPlayer seller, UUID operationId, long listingId,
                                   Consumer<MarketResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(seller.getUUID(), "CANCEL")) {
            callback.accept(MarketResult.MARKET_BUSY);
            return;
        }
        seller.server.execute(() -> {
            MarketResult result;
            try {
                result = cancel(operationId, seller, listingId);
            } finally {
                MarketDatabaseExecutor.release(seller.getUUID());
            }
            seller.server.execute(() -> callback.accept(result));
        });
    }

    public static void claimAsync(ServerPlayer player, UUID operationId, Consumer<MarketResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(player.getUUID(), "CLAIM")) {
            callback.accept(MarketResult.MARKET_BUSY);
            return;
        }
        player.server.execute(() -> {
            MarketResult result;
            try {
                result = claim(operationId, player);
            } finally {
                MarketDatabaseExecutor.release(player.getUUID());
            }
            player.server.execute(() -> callback.accept(result));
        });
    }

    public static void claimSaleAsync(ServerPlayer player, UUID operationId, long transactionId,
                                      Consumer<MarketResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(player.getUUID(), "CLAIM_SALE")) {
            callback.accept(MarketResult.MARKET_BUSY);
            return;
        }
        player.server.execute(() -> {
            MarketResult result;
            try {
                result = claimSale(operationId, player, transactionId);
            } finally {
                MarketDatabaseExecutor.release(player.getUUID());
            }
            player.server.execute(() -> callback.accept(result));
        });
    }

    public static void deleteClaimedSaleAsync(ServerPlayer player, long transactionId,
                                             Consumer<MarketResult> callback) {
        if (!MarketDatabaseExecutor.tryAcquire(player.getUUID(), "DELETE_HISTORY")) {
            callback.accept(MarketResult.MARKET_BUSY);
            return;
        }
        player.server.execute(() -> {
            MarketResult result;
            try {
                result = deleteClaimedSale(player, transactionId);
            } finally {
                MarketDatabaseExecutor.release(player.getUUID());
            }
            player.server.execute(() -> callback.accept(result));
        });
    }

    private static void deliverReservedItems(ServerPlayer player, UUID deliveryId,
                                             List<JdbcMarketRepository.PendingItem> items, long gameTime)
            throws SQLException, IOException {
        for (JdbcMarketRepository.PendingItem pending : items) {
            ItemStack stackToDeliver = pending.item().copy();
            int originalCount = stackToDeliver.getCount();
            player.getInventory().add(stackToDeliver);
            int deliveredCount = originalCount - stackToDeliver.getCount();

            if (deliveredCount == originalCount) {
                try {
                    if (!JdbcMarketRepository.completePendingItemDelivery(pending.id(), deliveryId, gameTime)) {
                        JdbcMarketRepository.markPendingItemsReview(deliveryId, gameTime);
                    }
                } catch (SQLException exception) {
                    JdbcMarketRepository.markPendingItemsReview(deliveryId, gameTime);
                    throw exception;
                }
            } else if (deliveredCount > 0) {
                ItemStack deliveredStack = pending.item().copyWithCount(deliveredCount);
                ItemStack remainingStack = pending.item().copyWithCount(originalCount - deliveredCount);
                try {
                    if (!JdbcMarketRepository.updatePendingItemPartial(
                            pending.id(), deliveryId, deliveredStack, remainingStack, gameTime, player.registryAccess())) {
                        JdbcMarketRepository.markPendingItemsReview(deliveryId, gameTime);
                    }
                } catch (SQLException | IOException exception) {
                    JdbcMarketRepository.markPendingItemsReview(deliveryId, gameTime);
                    throw exception;
                }
            } else {
                JdbcMarketRepository.revertPendingItemToPending(pending.id(), deliveryId, gameTime);
            }
        }
    }

    private static void refundSilver(PlayerData wallet, long amount, java.util.UUID playerId) {
        if (!wallet.addSilver(amount)) {
            NeoWorld.LOGGER.error("Could not refund {} Silver to {} after a failed market purchase", amount, playerId);
        }
    }

    private static void logDatabaseFailure(String operation, Exception exception) {
        NeoWorld.LOGGER.error("Could not {} in the NeoWorld database", operation, exception);
    }

    public record ListingResult(MarketResult result, OptionalLong listingId) {}
}
