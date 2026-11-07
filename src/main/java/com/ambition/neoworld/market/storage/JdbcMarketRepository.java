package com.ambition.neoworld.market.storage;

import com.ambition.neoworld.market.MarketMath;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.MarketTransaction;
import com.ambition.neoworld.market.MarketTransactionPolicy;
import com.ambition.neoworld.market.model.MarketCategory;
import com.ambition.neoworld.market.model.MarketListing;
import com.ambition.neoworld.market.model.MarketManagementEntry;
import com.ambition.neoworld.market.model.MarketManagementPage;
import com.ambition.neoworld.market.model.MarketPage;
import com.ambition.neoworld.market.model.MarketSort;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.Locale;

import com.ambition.neoworld.database.NeoWorldDatabase;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** JDBC persistence boundary shared by H2 and MariaDB. */
public final class JdbcMarketRepository {
    private static final String LISTING_FEE_KEY = "listing_fee_silver";
    private static final String SALES_TAX_KEY = "sales_tax_rate_bps";

    private JdbcMarketRepository() {}

    public static MarketSettings settings() throws SQLException {
        try (Connection connection = MarketSql.connection()) {
            long listingFee = readSetting(connection, LISTING_FEE_KEY);
            long storedTax = readSetting(connection, SALES_TAX_KEY);
            if (listingFee < 0 || storedTax > Integer.MAX_VALUE
                    || !MarketMath.validRate((int) storedTax)) {
                throw new SQLException("Invalid market fee settings in database");
            }
            return new MarketSettings(listingFee, (int) storedTax);
        }
    }

    public static boolean configureFees(long listingFee, int taxRateBps) throws SQLException {
        if (listingFee < 0 || !MarketMath.validRate(taxRateBps)) return false;
        MarketSql.transaction(connection -> {
            var feeHistory = new com.google.gson.JsonObject();
            feeHistory.addProperty("old_listing_fee", readSetting(connection, LISTING_FEE_KEY));
            feeHistory.addProperty("old_tax_bps", readSetting(connection, SALES_TAX_KEY));
            feeHistory.addProperty("new_listing_fee", listingFee);
            feeHistory.addProperty("new_tax_bps", taxRateBps);
            writeSetting(connection, LISTING_FEE_KEY, listingFee);
            writeSetting(connection, SALES_TAX_KEY, taxRateBps);
            com.ambition.neoworld.economy.analytics.AnalyticsHistory.config(connection, "market_fees", "", feeHistory);
            return null;
        });
        return true;
    }

    public static long createListing(UUID sellerId, String sellerName, ItemStack item, long price,
                                     long createdAt, long expiresAt, HolderLookup.Provider registries)
            throws SQLException, IOException {
        return createListing(null, sellerId, sellerName, item, price, createdAt, expiresAt, registries);
    }

    public static long createListing(UUID operationId, UUID sellerId, String sellerName, ItemStack item, long price,
                                     long createdAt, long expiresAt, HolderLookup.Provider registries)
            throws SQLException, IOException {
        byte[] itemData = MarketItemCodec.encode(item, registries);
        try {
            return MarketSql.transaction(connection -> {
                if (operationId != null) {
                    Optional<MarketOperationRecord> existing = findOperation(connection, operationId);
                    if (existing.isPresent()) {
                        return existing.get().listingId();
                    }
                }
                String sql = "INSERT INTO " + table("market_listings")
                        + " (seller_id, seller_name, item_nbt, price_silver, created_game_time, expires_game_time,"
                        + " item_id, item_name, category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                long id;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, sellerId.toString());
                    statement.setString(2, sellerName);
                    statement.setBytes(3, itemData);
                    statement.setLong(4, price);
                    statement.setLong(5, createdAt);
                    statement.setLong(6, expiresAt);
                    statement.setString(7, BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                    statement.setString(8, item.getHoverName().getString().toLowerCase(Locale.ROOT));
                    statement.setString(9, MarketCategory.classify(item).name());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Database did not return a market listing ID");
                        id = keys.getLong(1);
                    }
                }
                recordTransaction(connection, MarketTransaction.Type.LISTED, id, sellerId, null,
                        price, 0L, createdAt);
                com.ambition.neoworld.economy.analytics.AnalyticsHistory.market(connection, "LISTED", id, sellerId, null,
                        item, price, 0, 0, createdAt, registries);
                if (operationId != null) {
                    recordOperation(connection, operationId, "CREATE_LISTING", sellerId, id,
                            "COMMITTED", MarketResult.SUCCESS.name(), "", createdAt, createdAt);
                }
                return id;
            });
        } catch (SQLException exception) {
            if (exception.getCause() instanceof IOException ioException) throw ioException;
            throw exception;
        }
    }

    public static List<MarketListing> listings(HolderLookup.Provider registries) throws SQLException, IOException {
        String sql = "SELECT id, seller_id, seller_name, item_nbt, price_silver, created_game_time, expires_game_time"
                + " FROM " + table("market_listings") + " ORDER BY id";
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<MarketListing> listings = new ArrayList<>();
            while (result.next()) listings.add(readListing(result, registries));
            return listings;
        }
    }

    public static MarketPage query(int requestedPage, int pageSize, MarketCategory category, String search,
                                   MarketSort sort, long gameTime, HolderLookup.Provider registries)
            throws SQLException, IOException {
        int safePageSize = Math.max(1, Math.min(36, pageSize));
        String normalizedSearch = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        if (normalizedSearch.length() > 64) normalizedSearch = normalizedSearch.substring(0, 64);
        String where = " WHERE expires_game_time > ?"
                + (category == MarketCategory.ALL ? "" : " AND category = ?")
                + (normalizedSearch.isEmpty() ? "" : " AND (LOWER(item_name) LIKE ? OR LOWER(item_id) LIKE ?"
                        + " OR LOWER(seller_name) LIKE ?)");
        try (Connection connection = MarketSql.connection()) {
            long total;
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM " + table("market_listings") + where)) {
                bindQueryFilters(count, category, normalizedSearch, gameTime);
                try (ResultSet result = count.executeQuery()) {
                    result.next();
                    total = result.getLong(1);
                }
            }
            int totalPages = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                    total == 0 ? 1 : ((total - 1) / safePageSize) + 1));
            int page = Math.max(1, Math.min(requestedPage, totalPages));
            long offset = (long) (page - 1) * safePageSize;
            String sql = "SELECT id, seller_id, seller_name, item_nbt, price_silver, created_game_time,"
                    + " expires_game_time FROM " + table("market_listings") + where
                    + " ORDER BY " + orderBy(sort) + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = bindQueryFilters(statement, category, normalizedSearch, gameTime);
                statement.setInt(index++, safePageSize);
                statement.setLong(index, offset);
                try (ResultSet result = statement.executeQuery()) {
                    List<MarketListing> listings = new ArrayList<>();
                    while (result.next()) listings.add(readListing(result, registries));
                    return new MarketPage(page, totalPages, total, listings);
                }
            }
        }
    }

    public static Optional<MarketListing> listing(long id, HolderLookup.Provider registries)
            throws SQLException, IOException {
        try (Connection connection = MarketSql.connection()) {
            return findListing(connection, id, false, registries);
        }
    }

    public static MarketManagementPage managementPage(UUID sellerId, int requestedPage, int pageSize,
                                                        int taxRateBps, HolderLookup.Provider registries)
            throws SQLException, IOException {
        int safePageSize = Math.max(1, Math.min(20, pageSize));
        String seller = sellerId.toString();
        try (Connection connection = MarketSql.connection()) {
            long total;
            String countSql = "SELECT (SELECT COUNT(*) FROM " + table("market_listings")
                    + " WHERE seller_id = ?) + (SELECT COUNT(*) FROM " + table("market_transactions")
                    + " WHERE seller_id = ? AND transaction_type = 'SOLD' AND item_nbt IS NOT NULL)";
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                count.setString(1, seller);
                count.setString(2, seller);
                try (ResultSet result = count.executeQuery()) {
                    result.next();
                    total = result.getLong(1);
                }
            }
            int totalPages = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                    total == 0 ? 1 : ((total - 1) / safePageSize) + 1));
            int page = Math.max(1, Math.min(requestedPage, totalPages));
            long offset = (long) (page - 1) * safePageSize;
            String sql = "SELECT record_id, listing_id, row_status, item_nbt, quantity, unit_price_silver,"
                    + " proceeds_silver, updated_game_time, expires_game_time FROM ("
                    + " SELECT id AS record_id, id AS listing_id, 'ACTIVE' AS row_status, item_nbt,"
                    + " 0 AS quantity, price_silver AS unit_price_silver, 0 AS proceeds_silver,"
                    + " created_game_time AS updated_game_time, expires_game_time FROM "
                    + table("market_listings") + " WHERE seller_id = ?"
                    + " UNION ALL SELECT id AS record_id, listing_id,"
                    + " CASE WHEN claimed THEN 'CLAIMED' ELSE 'PENDING' END AS row_status, item_nbt,"
                    + " quantity, unit_price_silver, proceeds_silver, game_time AS updated_game_time,"
                    + " 0 AS expires_game_time FROM " + table("market_transactions")
                    + " WHERE seller_id = ? AND transaction_type = 'SOLD' AND item_nbt IS NOT NULL"
                    + ") market_rows ORDER BY updated_game_time DESC, record_id DESC LIMIT ? OFFSET ?";
            List<MarketManagementEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, seller);
                statement.setString(2, seller);
                statement.setInt(3, safePageSize);
                statement.setLong(4, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ItemStack item = MarketItemCodec.decode(result.getBytes("item_nbt"), registries);
                        MarketManagementEntry.Status status = MarketManagementEntry.Status.valueOf(
                                result.getString("row_status"));
                        int quantity = status == MarketManagementEntry.Status.ACTIVE
                                ? item.getCount() : result.getInt("quantity");
                        long unitPrice = result.getLong("unit_price_silver");
                        long proceeds = result.getLong("proceeds_silver");
                        if (status == MarketManagementEntry.Status.ACTIVE) {
                            long gross = com.ambition.neoworld.economy.CurrencyMath.total(unitPrice, quantity);
                            proceeds = gross < 0 ? 0L : MarketMath.sellerProceeds(gross, taxRateBps);
                        }
                        entries.add(new MarketManagementEntry(
                                result.getLong("record_id"), result.getLong("listing_id"), status,
                                item, quantity, unitPrice, Math.max(0L, proceeds),
                                result.getLong("updated_game_time"), result.getLong("expires_game_time")));
                    }
                }
            }
            return new MarketManagementPage(page, totalPages, total, readPendingSilver(connection, sellerId, false),
                    entries);
        }
    }

    public static PurchaseResult purchase(long listingId, UUID buyerId, int quantity, long buyerBalance,
                                          long gameTime, int taxRateBps, HolderLookup.Provider registries)
            throws SQLException {
        return purchase(null, listingId, buyerId, quantity, buyerBalance, gameTime, taxRateBps, registries);
    }

    public static PurchaseResult purchase(UUID operationId, long listingId, UUID buyerId, int quantity,
                                          long buyerBalance, long gameTime, int taxRateBps,
                                          HolderLookup.Provider registries)
            throws SQLException {
        return MarketSql.transaction(connection -> {
            if (operationId != null) {
                Optional<MarketOperationRecord> existing = findOperation(connection, operationId);
                if (existing.isPresent()) {
                    MarketOperationRecord op = existing.get();
                    MarketResult res = MarketResult.valueOf(op.resultCode());
                    return new PurchaseResult(res, null);
                }
            }

            MarketListing listing = findListing(connection, listingId, true, registries).orElse(null);
            long pending = listing == null ? 0L : readPendingSilver(connection, listing.sellerId(), true);
            MarketTransactionPolicy.PurchaseCheck check = MarketTransactionPolicy.checkPurchase(
                    listing != null,
                    listing != null && listing.isExpired(gameTime),
                    listing != null && listing.sellerId().equals(buyerId),
                    buyerBalance, pending,
                    listing == null ? 0L : listing.priceSilver(), quantity,
                    listing == null ? 0 : listing.item().getCount(), taxRateBps);
            if (check.result() != MarketResult.SUCCESS) {
                if (operationId != null) {
                    recordOperation(connection, operationId, "PURCHASE", buyerId, listingId,
                            "FAILED", check.result().name(), "", gameTime, gameTime);
                }
                return new PurchaseResult(check.result(), null);
            }

            if (check.sellerProceeds() > 0) {
                addPendingSilver(connection, listing.sellerId(), check.sellerProceeds());
            }
            ItemStack purchasedItem = listing.item().copyWithCount(quantity);
            insertPendingItem(connection, buyerId, MarketItemCodec.encode(purchasedItem, registries), gameTime);
            int remainingQuantity = listing.item().getCount() - quantity;
            if (remainingQuantity == 0) {
                deleteListing(connection, listing.id());
            } else {
                updateListingItem(connection, listing.id(),
                        MarketItemCodec.encode(listing.item().copyWithCount(remainingQuantity), registries));
            }
            recordSaleTransaction(connection, listing.id(), listing.sellerId(), buyerId, purchasedItem,
                    listing.priceSilver(), check.totalPriceSilver(), check.taxSilver(), check.sellerProceeds(),
                    gameTime, registries);
            if (operationId != null) {
                recordOperation(connection, operationId, "PURCHASE", buyerId, listingId,
                        "COMMITTED", MarketResult.SUCCESS.name(), "", gameTime, 0L);
            }
            return new PurchaseResult(MarketResult.SUCCESS, listing);
        });
    }

    public static MarketResult cancel(long listingId, UUID sellerId, long gameTime,
                                      HolderLookup.Provider registries) throws SQLException {
        return cancel(null, listingId, sellerId, gameTime, registries);
    }

    public static MarketResult cancel(UUID operationId, long listingId, UUID sellerId, long gameTime,
                                      HolderLookup.Provider registries) throws SQLException {
        return MarketSql.transaction(connection -> {
            if (operationId != null) {
                Optional<MarketOperationRecord> existing = findOperation(connection, operationId);
                if (existing.isPresent()) {
                    if (!existing.get().playerId().equals(sellerId) || !existing.get().operationType().equals("CANCEL")
                            || existing.get().listingId() != listingId) return MarketResult.DATABASE_ERROR;
                    return MarketResult.valueOf(existing.get().resultCode());
                }
            }
            MarketListing listing = findListing(connection, listingId, true, registries).orElse(null);
            if (listing == null || !listing.sellerId().equals(sellerId)) {
                if (operationId != null) {
                    recordOperation(connection, operationId, "CANCEL", sellerId, listingId,
                            "FAILED", MarketResult.LISTING_NOT_FOUND.name(), "", gameTime, gameTime);
                }
                return MarketResult.LISTING_NOT_FOUND;
            }
            insertPendingItem(connection, sellerId, MarketItemCodec.encode(listing.item(), registries), gameTime);
            deleteListing(connection, listing.id());
            recordTransaction(connection, MarketTransaction.Type.CANCELLED, listing.id(), sellerId, null,
                    listing.priceSilver(), 0L, gameTime);
            com.ambition.neoworld.economy.analytics.AnalyticsHistory.market(connection, "CANCELLED", listing.id(), sellerId, null,
                    listing.item(), listing.priceSilver(), 0, 0, gameTime, registries);
            if (operationId != null) {
                recordOperation(connection, operationId, "CANCEL", sellerId, listing.id(),
                        "COMMITTED", MarketResult.SUCCESS.name(), "", gameTime, 0L);
            }
            return MarketResult.SUCCESS;
        });
    }

    public static int expireListings(long gameTime, HolderLookup.Provider registries) throws SQLException {
        int totalExpired = 0;
        int batch;
        do {
            batch = expireListingsBatch(gameTime, 100, registries);
            totalExpired += batch;
        } while (batch == 100);
        return totalExpired;
    }

    public static int expireListingsBatch(long gameTime, int batchSize, HolderLookup.Provider registries)
            throws SQLException {
        int safeBatch = Math.max(1, Math.min(100, batchSize));
        return MarketSql.transaction(connection -> {
            String sql = "SELECT id, seller_id, seller_name, item_nbt, price_silver, created_game_time,"
                    + " expires_game_time FROM " + table("market_listings")
                    + " WHERE expires_game_time <= ? ORDER BY id LIMIT ? FOR UPDATE";
            List<MarketListing> expired = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                statement.setInt(2, safeBatch);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) expired.add(readListing(result, registries));
                }
            }
            for (MarketListing listing : expired) {
                insertPendingItem(connection, listing.sellerId(), MarketItemCodec.encode(listing.item(), registries), gameTime);
                deleteListing(connection, listing.id());
                recordTransaction(connection, MarketTransaction.Type.EXPIRED, listing.id(), listing.sellerId(), null,
                        listing.priceSilver(), 0L, gameTime);
                com.ambition.neoworld.economy.analytics.AnalyticsHistory.market(connection, "EXPIRED", listing.id(), listing.sellerId(), null,
                        listing.item(), listing.priceSilver(), 0, 0, gameTime, registries);
                recordOperation(connection, UUID.randomUUID(), "EXPIRE", listing.sellerId(), listing.id(),
                        "COMMITTED", MarketResult.SUCCESS.name(), "", gameTime, gameTime);
            }
            return expired.size();
        });
    }

    public static long pendingSilver(UUID playerId) throws SQLException {
        try (Connection connection = MarketSql.connection()) {
            return readPendingSilver(connection, playerId, false);
        }
    }

    public static OptionalLong claimableSale(long transactionId, UUID sellerId) throws SQLException {
        String sql = "SELECT proceeds_silver FROM " + table("market_transactions")
                + " WHERE id = ? AND seller_id = ? AND transaction_type = 'SOLD' AND claimed = FALSE";
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, transactionId);
            statement.setString(2, sellerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    public static boolean claimSale(long transactionId, UUID sellerId, long amount, long gameTime)
            throws SQLException {
        return claimSale(null, transactionId, sellerId, amount, gameTime);
    }

    public static boolean claimSale(UUID operationId, long transactionId, UUID sellerId, long amount, long gameTime)
            throws SQLException {
        if (amount <= 0) return false;
        return MarketSql.transaction(connection -> {
            if (operationId != null) {
                Optional<MarketOperationRecord> existing = findOperation(connection, operationId);
                if (existing.isPresent()) {
                    return "COMMITTED".equals(existing.get().status()) || "COMPLETED".equals(existing.get().status());
                }
            }
            String update = "UPDATE " + table("market_transactions")
                    + " SET claimed = TRUE, claimed_game_time = ?"
                    + " WHERE id = ? AND seller_id = ? AND transaction_type = 'SOLD'"
                    + " AND claimed = FALSE AND proceeds_silver = ?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setLong(1, gameTime);
                statement.setLong(2, transactionId);
                statement.setString(3, sellerId.toString());
                statement.setLong(4, amount);
                if (statement.executeUpdate() != 1) return false;
            }
            if (!consumePendingSilver(connection, sellerId, amount)) {
                throw new SQLException("Pending market balance changed while claiming sale " + transactionId);
            }
            if (operationId != null) {
                recordOperation(connection, operationId, "CLAIM_SALE", sellerId, transactionId,
                        "COMMITTED", MarketResult.SUCCESS.name(), "", gameTime, gameTime);
            }
            return true;
        });
    }

    public static boolean claimAllSales(UUID sellerId, long amount, long gameTime) throws SQLException {
        return claimAllSales(null, sellerId, amount, gameTime);
    }

    public static boolean claimAllSales(UUID operationId, UUID sellerId, long amount, long gameTime) throws SQLException {
        if (amount <= 0) return false;
        return MarketSql.transaction(connection -> {
            if (operationId != null) {
                Optional<MarketOperationRecord> existing = findOperation(connection, operationId);
                if (existing.isPresent()) {
                    return "COMMITTED".equals(existing.get().status()) || "COMPLETED".equals(existing.get().status());
                }
            }
            int updatedSales = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + table("market_transactions")
                            + " SET claimed = TRUE, claimed_game_time = ?"
                            + " WHERE seller_id = ? AND transaction_type = 'SOLD' AND claimed = FALSE")) {
                statement.setLong(1, gameTime);
                statement.setString(2, sellerId.toString());
                updatedSales = statement.executeUpdate();
            }
            if (!consumePendingSilver(connection, sellerId, amount)) {
                throw new SQLException("Pending market balance changed while claiming all sales for " + sellerId);
            }
            if (operationId != null) {
                recordOperation(connection, operationId, "CLAIM_ALL", sellerId, updatedSales,
                        "COMMITTED", MarketResult.SUCCESS.name(), "", gameTime, gameTime);
            }
            return true;
        });
    }

    public static boolean deleteClaimedSale(long transactionId, UUID sellerId) throws SQLException {
        String sql = "DELETE FROM " + table("market_transactions")
                + " WHERE id = ? AND seller_id = ? AND transaction_type = 'SOLD' AND claimed = TRUE";
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, transactionId);
            statement.setString(2, sellerId.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public static boolean consumePendingSilver(UUID playerId, long amount) throws SQLException {
        if (amount <= 0) return false;
        return MarketSql.transaction(connection -> consumePendingSilver(connection, playerId, amount));
    }

    public static List<PendingItem> pendingItems(UUID playerId, HolderLookup.Provider registries)
            throws SQLException, IOException {
        String sql = "SELECT id, item_nbt FROM " + table("market_pending_items")
                + " WHERE player_id = ? AND delivery_status = 'PENDING' ORDER BY id";
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PendingItem> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new PendingItem(result.getLong("id"),
                            MarketItemCodec.decode(result.getBytes("item_nbt"), registries)));
                }
                return items;
            }
        }
    }

    public static List<PendingItem> reservePendingItems(UUID playerId, UUID deliveryId, long gameTime,
                                                        HolderLookup.Provider registries)
            throws SQLException, IOException {
        return MarketSql.transaction(connection -> {
            String selectSql = "SELECT id, item_nbt FROM " + table("market_pending_items")
                    + " WHERE player_id = ? AND delivery_status = 'PENDING' FOR UPDATE";
            List<PendingItem> reserved = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                select.setString(1, playerId.toString());
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        long id = result.getLong("id");
                        ItemStack item = MarketItemCodec.decode(result.getBytes("item_nbt"), registries);
                        reserved.add(new PendingItem(id, item));
                    }
                }
            }
            if (reserved.isEmpty()) return reserved;

            String updateSql = "UPDATE " + table("market_pending_items")
                    + " SET delivery_id = ?, delivery_status = 'DELIVERING', reserved_game_time = ?, updated_game_time = ?"
                    + " WHERE id = ? AND delivery_status = 'PENDING'";
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (PendingItem item : reserved) {
                    update.setString(1, deliveryId.toString());
                    update.setLong(2, gameTime);
                    update.setLong(3, gameTime);
                    update.setLong(4, item.id());
                    int affected = update.executeUpdate();
                    if (affected != 1) {
                        throw new SQLException("Could not reserve pending item " + item.id() + ", affected rows: " + affected);
                    }
                }
            }
            return reserved;
        });
    }

    public static boolean completePendingItemDelivery(long id, UUID deliveryId, long gameTime) throws SQLException {
        return MarketSql.transaction(connection -> {
            String sql = "UPDATE " + table("market_pending_items")
                    + " SET delivery_status = 'DELIVERED', updated_game_time = ?"
                    + " WHERE id = ? AND delivery_id = ? AND delivery_status = 'DELIVERING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                statement.setLong(2, id);
                statement.setString(3, deliveryId.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public static boolean updatePendingItemPartial(long id, UUID deliveryId, ItemStack deliveredItem,
                                                   ItemStack remainingItem, long gameTime,
                                                   HolderLookup.Provider registries)
            throws SQLException, IOException {
        byte[] deliveredBytes = MarketItemCodec.encode(deliveredItem, registries);
        byte[] remainingBytes = MarketItemCodec.encode(remainingItem, registries);
        return MarketSql.transaction(connection -> {
            String updateSql = "UPDATE " + table("market_pending_items")
                    + " SET item_nbt = ?, delivery_status = 'DELIVERED', updated_game_time = ?"
                    + " WHERE id = ? AND delivery_id = ? AND delivery_status = 'DELIVERING'";
            UUID playerId;
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setBytes(1, deliveredBytes);
                update.setLong(2, gameTime);
                update.setLong(3, id);
                update.setString(4, deliveryId.toString());
                if (update.executeUpdate() != 1) return false;
            }
            String findSql = "SELECT player_id FROM " + table("market_pending_items") + " WHERE id = ?";
            try (PreparedStatement find = connection.prepareStatement(findSql)) {
                find.setLong(1, id);
                try (ResultSet rs = find.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Pending item not found: " + id);
                    playerId = UUID.fromString(rs.getString("player_id"));
                }
            }
            insertPendingItem(connection, playerId, remainingBytes, gameTime);
            return true;
        });
    }

    public static void revertPendingItemToPending(long id, UUID deliveryId, long gameTime) throws SQLException {
        MarketSql.transaction(connection -> {
            String sql = "UPDATE " + table("market_pending_items")
                    + " SET delivery_id = NULL, delivery_status = 'PENDING', reserved_game_time = 0, updated_game_time = ?"
                    + " WHERE id = ? AND delivery_id = ? AND delivery_status = 'DELIVERING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                statement.setLong(2, id);
                statement.setString(3, deliveryId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public static void markPendingItemsReview(UUID deliveryId, long gameTime) throws SQLException {
        MarketSql.transaction(connection -> {
            String sql = "UPDATE " + table("market_pending_items")
                    + " SET delivery_status = 'NEEDS_REVIEW', updated_game_time = ?"
                    + " WHERE delivery_id = ? AND delivery_status = 'DELIVERING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                statement.setString(2, deliveryId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public static int recoverInFlightDeliveries(long gameTime) throws SQLException {
        return MarketSql.transaction(connection -> {
            String sql = "UPDATE " + table("market_pending_items")
                    + " SET delivery_status = 'NEEDS_REVIEW', updated_game_time = ?"
                    + " WHERE delivery_status = 'DELIVERING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                return statement.executeUpdate();
            }
        });
    }

    public static Optional<MarketOperationRecord> findOperation(Connection connection, UUID operationId)
            throws SQLException {
        String sql = "SELECT operation_id, operation_type, player_id, listing_id, status, result_code,"
                + " error_info, created_game_time, completed_game_time FROM " + table("market_operations")
                + " WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new MarketOperationRecord(
                        UUID.fromString(result.getString("operation_id")),
                        result.getString("operation_type"),
                        UUID.fromString(result.getString("player_id")),
                        result.getLong("listing_id"),
                        result.getString("status"),
                        result.getString("result_code"),
                        result.getString("error_info"),
                        result.getLong("created_game_time"),
                        result.getLong("completed_game_time")
                ));
            }
        }
    }

    public static void recordOperation(Connection connection, UUID operationId, String type, UUID playerId,
                                       long listingId, String status, String resultCode, String errorInfo,
                                       long createdGameTime, long completedGameTime) throws SQLException {
        String sql = "INSERT INTO " + table("market_operations")
                + " (operation_id, operation_type, player_id, listing_id, status, result_code, error_info,"
                + " created_game_time, completed_game_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationId.toString());
            statement.setString(2, type);
            statement.setString(3, playerId.toString());
            statement.setLong(4, listingId);
            statement.setString(5, status);
            statement.setString(6, resultCode != null ? resultCode : "");
            statement.setString(7, errorInfo != null ? errorInfo : "");
            statement.setLong(8, createdGameTime);
            statement.setLong(9, completedGameTime);
            statement.executeUpdate();
        }
    }

    public static void updateOperation(Connection connection, UUID operationId, String status,
                                       String resultCode, String errorInfo, long completedGameTime)
            throws SQLException {
        String sql = "UPDATE " + table("market_operations")
                + " SET status = ?, result_code = ?, error_info = ?, completed_game_time = ?"
                + " WHERE operation_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, resultCode != null ? resultCode : "");
            statement.setString(3, errorInfo != null ? errorInfo : "");
            statement.setLong(4, completedGameTime);
            statement.setString(5, operationId.toString());
            statement.executeUpdate();
        }
    }

    public static void markOperationCompleted(UUID operationId, long completedGameTime) throws SQLException {
        MarketSql.transaction(connection -> {
            updateOperation(connection, operationId, "COMPLETED", null, null, completedGameTime);
            return null;
        });
    }

    public static void deletePendingItem(long id, UUID playerId) throws SQLException {
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + table("market_pending_items") + " WHERE id = ? AND player_id = ?")) {
            statement.setLong(1, id);
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    public static void replacePendingItem(long id, UUID playerId, ItemStack item,
                                          HolderLookup.Provider registries) throws SQLException, IOException {
        byte[] data = MarketItemCodec.encode(item, registries);
        try (Connection connection = MarketSql.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + table("market_pending_items") + " SET item_nbt = ? WHERE id = ? AND player_id = ?")) {
            statement.setBytes(1, data);
            statement.setLong(2, id);
            statement.setString(3, playerId.toString());
            statement.executeUpdate();
        }
    }

    private static Optional<MarketListing> findListing(Connection connection, long id, boolean lock,
                                                       HolderLookup.Provider registries)
            throws SQLException, IOException {
        String sql = "SELECT id, seller_id, seller_name, item_nbt, price_silver, created_game_time, expires_game_time"
                + " FROM " + table("market_listings") + " WHERE id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readListing(result, registries)) : Optional.empty();
            }
        }
    }

    private static MarketListing readListing(ResultSet result, HolderLookup.Provider registries)
            throws SQLException, IOException {
        return new MarketListing(
                result.getLong("id"), UUID.fromString(result.getString("seller_id")),
                result.getString("seller_name"), MarketItemCodec.decode(result.getBytes("item_nbt"), registries),
                result.getLong("price_silver"), result.getLong("created_game_time"),
                result.getLong("expires_game_time"));
    }

    private static long readSetting(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT setting_value FROM " + table("market_settings") + " WHERE setting_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private static void writeSetting(Connection connection, String key, long value) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table("market_settings") + " SET setting_value = ? WHERE setting_key = ?")) {
            update.setLong(1, value);
            update.setString(2, key);
            if (update.executeUpdate() == 1) return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table("market_settings") + " (setting_key, setting_value) VALUES (?, ?)")) {
            insert.setString(1, key);
            insert.setLong(2, value);
            insert.executeUpdate();
        }
    }

    private static long readPendingSilver(Connection connection, UUID playerId, boolean lock) throws SQLException {
        String sql = "SELECT silver FROM " + table("market_pending_balances")
                + " WHERE player_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private static void addPendingSilver(Connection connection, UUID playerId, long amount) throws SQLException {
        String updateSql = "UPDATE " + table("market_pending_balances")
                + " SET silver = silver + ? WHERE player_id = ? AND silver <= ?";
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            update.setLong(1, amount);
            update.setString(2, playerId.toString());
            update.setLong(3, Long.MAX_VALUE - amount);
            if (update.executeUpdate() == 1) return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table("market_pending_balances") + " (player_id, silver) VALUES (?, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, amount);
            insert.executeUpdate();
        }
    }

    public static boolean consumePendingSilver(Connection connection, UUID playerId, long amount)
            throws SQLException {
        String update = "UPDATE " + table("market_pending_balances")
                + " SET silver = silver - ? WHERE player_id = ? AND silver >= ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, amount);
            statement.setString(2, playerId.toString());
            statement.setLong(3, amount);
            if (statement.executeUpdate() != 1) return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table("market_pending_balances") + " WHERE player_id = ? AND silver = 0")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
        return true;
    }

    private static long insertPendingItem(Connection connection, UUID playerId, byte[] itemData) throws SQLException {
        return insertPendingItem(connection, playerId, itemData, 0L);
    }

    public static long insertPendingItem(Connection connection, UUID playerId, byte[] itemData, long gameTime) throws SQLException {
        String sql = "INSERT INTO " + table("market_pending_items")
                + " (player_id, item_nbt, delivery_status, updated_game_time) VALUES (?, ?, 'PENDING', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, itemData);
            statement.setLong(3, gameTime);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Database did not return a pending item ID");
                return keys.getLong(1);
            }
        }
    }

    private static void deleteListing(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table("market_listings") + " WHERE id = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Market listing changed during transaction: " + id);
        }
    }

    private static void updateListingItem(Connection connection, long id, byte[] itemData) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + table("market_listings") + " SET item_nbt = ? WHERE id = ?")) {
            statement.setBytes(1, itemData);
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Market listing changed during transaction: " + id);
            }
        }
    }

    private static void recordTransaction(Connection connection, MarketTransaction.Type type, long listingId,
                                          UUID sellerId, UUID buyerId, long price, long tax, long gameTime)
            throws SQLException {
        String sql = "INSERT INTO " + table("market_transactions")
                + " (transaction_type, listing_id, seller_id, buyer_id, price_silver, tax_silver, game_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setLong(2, listingId);
            statement.setString(3, sellerId.toString());
            statement.setString(4, buyerId == null ? null : buyerId.toString());
            statement.setLong(5, price);
            statement.setLong(6, tax);
            statement.setLong(7, gameTime);
            statement.executeUpdate();
        }
    }

    private static void recordSaleTransaction(Connection connection, long listingId, UUID sellerId, UUID buyerId,
                                              ItemStack item, long unitPrice, long totalPrice, long tax,
                                              long proceeds, long gameTime, HolderLookup.Provider registries)
            throws SQLException, IOException {
        com.ambition.neoworld.economy.analytics.AnalyticsHistory.market(connection, "SOLD", listingId, sellerId, buyerId,
                item, unitPrice, totalPrice, tax, gameTime, registries);
        String sql = "INSERT INTO " + table("market_transactions")
                + " (transaction_type, listing_id, seller_id, buyer_id, price_silver, tax_silver, game_time,"
                + " item_nbt, quantity, unit_price_silver, proceeds_silver, claimed, claimed_game_time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, 0)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MarketTransaction.Type.SOLD.name());
            statement.setLong(2, listingId);
            statement.setString(3, sellerId.toString());
            statement.setString(4, buyerId.toString());
            statement.setLong(5, totalPrice);
            statement.setLong(6, tax);
            statement.setLong(7, gameTime);
            statement.setBytes(8, MarketItemCodec.encode(item, registries));
            statement.setInt(9, item.getCount());
            statement.setLong(10, unitPrice);
            statement.setLong(11, proceeds);
            statement.executeUpdate();
        }
    }

    private static String table(String suffix) {
        return NeoWorldDatabase.tableName(suffix);
    }

    private static int bindQueryFilters(PreparedStatement statement, MarketCategory category, String search,
                                        long gameTime) throws SQLException {
        int index = 1;
        statement.setLong(index++, gameTime);
        if (category != MarketCategory.ALL) statement.setString(index++, category.name());
        if (!search.isEmpty()) {
            String pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            statement.setString(index++, pattern);
            statement.setString(index++, pattern);
            statement.setString(index++, pattern);
        }
        return index;
    }

    private static String orderBy(MarketSort sort) {
        return switch (sort) {
            case PRICE_ASC -> "price_silver ASC, id ASC";
            case PRICE_DESC -> "price_silver DESC, id DESC";
            case NEWEST -> "id DESC";
            case EXPIRING -> "expires_game_time ASC, id ASC";
        };
    }

    public record MarketSettings(long listingFeeSilver, int salesTaxRateBps) {}
    public record PendingItem(long id, ItemStack item) {}
    public record PurchaseResult(MarketResult result, MarketListing listing) {}
    public record MarketOperationRecord(UUID operationId, String operationType, UUID playerId, long listingId, String status, String resultCode, String errorInfo, long createdGameTime, long completedGameTime) {}
}
