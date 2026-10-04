package com.ambition.neoworld.market;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        try (Connection connection = NeoWorldDatabase.getConnection()) {
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
        NeoWorldDatabase.inTransaction(connection -> {
            writeSetting(connection, LISTING_FEE_KEY, listingFee);
            writeSetting(connection, SALES_TAX_KEY, taxRateBps);
            return null;
        });
        return true;
    }

    public static long createListing(UUID sellerId, String sellerName, ItemStack item, long price,
                                     long createdAt, long expiresAt, HolderLookup.Provider registries)
            throws SQLException, IOException {
        byte[] itemData = MarketItemCodec.encode(item, registries);
        try {
            return NeoWorldDatabase.inTransaction(connection -> {
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
        try (Connection connection = NeoWorldDatabase.getConnection();
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
        try (Connection connection = NeoWorldDatabase.getConnection()) {
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
        try (Connection connection = NeoWorldDatabase.getConnection()) {
            return findListing(connection, id, false, registries);
        }
    }

    public static PurchaseResult purchase(long listingId, UUID buyerId, long buyerBalance, long gameTime,
                                          int taxRateBps, HolderLookup.Provider registries) throws SQLException {
        return NeoWorldDatabase.inTransaction(connection -> {
            MarketListing listing = findListing(connection, listingId, true, registries).orElse(null);
            long pending = listing == null ? 0L : readPendingSilver(connection, listing.sellerId(), true);
            MarketTransactionPolicy.PurchaseCheck check = MarketTransactionPolicy.checkPurchase(
                    listing != null,
                    listing != null && listing.isExpired(gameTime),
                    listing != null && listing.sellerId().equals(buyerId),
                    buyerBalance, pending,
                    listing == null ? 0L : listing.priceSilver(), taxRateBps);
            if (check.result() != MarketResult.SUCCESS) return new PurchaseResult(check.result(), null);

            if (check.sellerProceeds() > 0) {
                addPendingSilver(connection, listing.sellerId(), check.sellerProceeds());
            }
            insertPendingItem(connection, buyerId, MarketItemCodec.encode(listing.item(), registries));
            deleteListing(connection, listing.id());
            recordTransaction(connection, MarketTransaction.Type.SOLD, listing.id(), listing.sellerId(), buyerId,
                    listing.priceSilver(), check.taxSilver(), gameTime);
            return new PurchaseResult(MarketResult.SUCCESS, listing);
        });
    }

    public static MarketResult cancel(long listingId, UUID sellerId, long gameTime,
                                      HolderLookup.Provider registries) throws SQLException {
        return NeoWorldDatabase.inTransaction(connection -> {
            MarketListing listing = findListing(connection, listingId, true, registries).orElse(null);
            if (listing == null || !listing.sellerId().equals(sellerId)) return MarketResult.LISTING_NOT_FOUND;
            insertPendingItem(connection, sellerId, MarketItemCodec.encode(listing.item(), registries));
            deleteListing(connection, listing.id());
            recordTransaction(connection, MarketTransaction.Type.CANCELLED, listing.id(), sellerId, null,
                    listing.priceSilver(), 0L, gameTime);
            return MarketResult.SUCCESS;
        });
    }

    public static int expireListings(long gameTime, HolderLookup.Provider registries) throws SQLException {
        return NeoWorldDatabase.inTransaction(connection -> {
            String sql = "SELECT id, seller_id, seller_name, item_nbt, price_silver, created_game_time,"
                    + " expires_game_time FROM " + table("market_listings")
                    + " WHERE expires_game_time <= ? FOR UPDATE";
            List<MarketListing> expired = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, gameTime);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) expired.add(readListing(result, registries));
                }
            }
            for (MarketListing listing : expired) {
                insertPendingItem(connection, listing.sellerId(), MarketItemCodec.encode(listing.item(), registries));
                deleteListing(connection, listing.id());
                recordTransaction(connection, MarketTransaction.Type.EXPIRED, listing.id(), listing.sellerId(), null,
                        listing.priceSilver(), 0L, gameTime);
            }
            return expired.size();
        });
    }

    public static long pendingSilver(UUID playerId) throws SQLException {
        try (Connection connection = NeoWorldDatabase.getConnection()) {
            return readPendingSilver(connection, playerId, false);
        }
    }

    public static boolean consumePendingSilver(UUID playerId, long amount) throws SQLException {
        if (amount <= 0) return false;
        return NeoWorldDatabase.inTransaction(connection -> {
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
        });
    }

    public static List<PendingItem> pendingItems(UUID playerId, HolderLookup.Provider registries)
            throws SQLException, IOException {
        String sql = "SELECT id, item_nbt FROM " + table("market_pending_items") + " WHERE player_id = ? ORDER BY id";
        try (Connection connection = NeoWorldDatabase.getConnection();
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

    public static void deletePendingItem(long id, UUID playerId) throws SQLException {
        try (Connection connection = NeoWorldDatabase.getConnection();
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
        try (Connection connection = NeoWorldDatabase.getConnection();
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

    private static long insertPendingItem(Connection connection, UUID playerId, byte[] itemData) throws SQLException {
        String sql = "INSERT INTO " + table("market_pending_items") + " (player_id, item_nbt) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerId.toString());
            statement.setBytes(2, itemData);
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
}
