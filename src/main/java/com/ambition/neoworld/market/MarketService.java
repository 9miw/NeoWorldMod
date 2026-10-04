package com.ambition.neoworld.market;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-thread transaction boundary for all market mutations. */
public final class MarketService {
    private MarketService() {}

    public static ListingResult createListing(ServerPlayer seller, int inventorySlot, int count,
                                              long priceSilver, long durationTicks) {
        if (seller == null || count <= 0) return new ListingResult(MarketResult.INVALID_ITEM, OptionalLong.empty());
        if (priceSilver <= 0) return new ListingResult(MarketResult.INVALID_PRICE, OptionalLong.empty());
        if (durationTicks <= 0) return new ListingResult(MarketResult.INVALID_DURATION, OptionalLong.empty());
        if (inventorySlot < 0 || inventorySlot >= seller.getInventory().getContainerSize()) {
            return new ListingResult(MarketResult.INVALID_ITEM, OptionalLong.empty());
        }
        ItemStack source = seller.getInventory().getItem(inventorySlot);
        if (source.isEmpty() || source.getCount() < count) {
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
                long id = JdbcMarketRepository.createListing(seller.getUUID(), seller.getGameProfile().getName(),
                        listedItem, priceSilver, now, now + durationTicks, seller.registryAccess());
                PlayerEconomyManager.syncToClient(seller);
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
            PlayerEconomyManager.syncToClient(seller);
            return new ListingResult(MarketResult.DATABASE_ERROR, OptionalLong.empty());
        }
    }

    public static MarketResult buy(ServerPlayer buyer, long listingId) {
        if (buyer == null) return MarketResult.LISTING_NOT_FOUND;
        PlayerData wallet = buyer.getData(ModAttachments.PLAYER_DATA);
        try {
            MarketListing observed = JdbcMarketRepository.listing(listingId, buyer.registryAccess()).orElse(null);
            if (observed == null) return MarketResult.LISTING_NOT_FOUND;
            if (observed.isExpired(buyer.serverLevel().getGameTime())) return MarketResult.LISTING_EXPIRED;
            if (observed.sellerId().equals(buyer.getUUID())) return MarketResult.CANNOT_BUY_OWN_LISTING;
            JdbcMarketRepository.MarketSettings settings = JdbcMarketRepository.settings();
            long balanceBefore = wallet.getSilver();
            if (!wallet.removeSilver(observed.priceSilver())) return MarketResult.INSUFFICIENT_FUNDS;

            JdbcMarketRepository.PurchaseResult purchase;
            try {
                purchase = JdbcMarketRepository.purchase(listingId, buyer.getUUID(), balanceBefore,
                        buyer.serverLevel().getGameTime(), settings.salesTaxRateBps(), buyer.registryAccess());
            } catch (SQLException exception) {
                refundSilver(wallet, observed.priceSilver(), buyer.getUUID());
                throw exception;
            }
            if (purchase.result() != MarketResult.SUCCESS) {
                refundSilver(wallet, observed.priceSilver(), buyer.getUUID());
                return purchase.result();
            }
            try {
                claimPendingItems(buyer);
            } catch (SQLException | IOException exception) {
                logDatabaseFailure("deliver purchased item; it remains pending", exception);
            }
            PlayerEconomyManager.syncToClient(buyer);
            return MarketResult.SUCCESS;
        } catch (SQLException | IOException exception) {
            logDatabaseFailure("buy listing", exception);
            PlayerEconomyManager.syncToClient(buyer);
            return MarketResult.DATABASE_ERROR;
        }
    }

    public static MarketResult cancel(ServerPlayer seller, long listingId) {
        if (seller == null) return MarketResult.LISTING_NOT_FOUND;
        try {
            MarketResult result = JdbcMarketRepository.cancel(listingId, seller.getUUID(),
                    seller.serverLevel().getGameTime(), seller.registryAccess());
            if (result == MarketResult.SUCCESS) {
                try {
                    claimPendingItems(seller);
                } catch (SQLException | IOException exception) {
                    logDatabaseFailure("deliver cancelled listing; it remains pending", exception);
                }
            }
            return result;
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

    public static MarketResult claim(ServerPlayer player) {
        if (player == null) return MarketResult.NOTHING_TO_CLAIM;
        PlayerData wallet = player.getData(ModAttachments.PLAYER_DATA);
        try {
            long silver = JdbcMarketRepository.pendingSilver(player.getUUID());
            List<JdbcMarketRepository.PendingItem> items = JdbcMarketRepository.pendingItems(
                    player.getUUID(), player.registryAccess());
            if (silver == 0 && items.isEmpty()) return MarketResult.NOTHING_TO_CLAIM;
            if (silver > 0) {
                if (!PlayerEconomyManager.deposit(wallet, CoinItem.CoinType.SILVER, silver)) {
                    return MarketResult.BALANCE_OVERFLOW;
                }
                try {
                    if (!JdbcMarketRepository.consumePendingSilver(player.getUUID(), silver)) {
                        wallet.removeSilver(silver);
                        return MarketResult.DATABASE_ERROR;
                    }
                } catch (SQLException exception) {
                    wallet.removeSilver(silver);
                    throw exception;
                }
            }
            claimPendingItems(player, items);
            PlayerEconomyManager.syncToClient(player);
            return MarketResult.SUCCESS;
        } catch (SQLException | IOException exception) {
            logDatabaseFailure("claim market deliveries", exception);
            PlayerEconomyManager.syncToClient(player);
            return MarketResult.DATABASE_ERROR;
        }
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

    private static void claimPendingItems(ServerPlayer player) throws SQLException, IOException {
        claimPendingItems(player, JdbcMarketRepository.pendingItems(player.getUUID(), player.registryAccess()));
    }

    private static void claimPendingItems(ServerPlayer player, List<JdbcMarketRepository.PendingItem> items)
            throws SQLException, IOException {
        for (JdbcMarketRepository.PendingItem pending : items) {
            ItemStack delivery = pending.item().copy();
            player.getInventory().add(delivery);
            if (delivery.isEmpty()) {
                JdbcMarketRepository.deletePendingItem(pending.id(), player.getUUID());
            } else {
                JdbcMarketRepository.replacePendingItem(
                        pending.id(), player.getUUID(), delivery, player.registryAccess());
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
