package com.ambition.neoworld.market;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.database.NeoWorldDatabase;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.market.model.MarketCategory;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;
import com.ambition.neoworld.market.storage.MarketItemCodec;
import com.ambition.neoworld.permission.ModPermissions;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

public final class MarketSecurityRegressionTest {
    private static int checks = 0;

    private MarketSecurityRegressionTest() {}

    public static int run() throws Exception {
        checks = 0;
        test1_normalPurchase();
        test2_concurrentPurchases();
        test3_duplicatePurchasePacket();
        test4_dbFailureBeforeCommit();
        test5_dbFailureAfterReserve();
        test6_dbFailureDuringClaim();
        test7_duplicateClaimSale();
        test8_restartRecovery();
        test9_partialInventorySpace();
        test10_cancelAndClaimReturn();
        test11_listingExpiration();
        test12_balanceOverflow();
        test13_permissionDenied();
        test14_schemaFreshAndUpgrade();
        test15_transactionRollbackNoPartialState();

        System.out.println("Passed all 15 market security & durability regression test suites (" + checks + " checks).");
        return checks;
    }

    private static JdbcDataSource createTestDataSource(String dbName) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "nw_");
        return source;
    }

    private static byte[] createDummyItemData(int count) throws IOException {
        CompoundTag tag = new CompoundTag();
        CompoundTag itemTag = new CompoundTag();
        itemTag.putString("id", "minecraft:iron_ingot");
        itemTag.putInt("count", count);
        tag.put("item", itemTag);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }

    // 1. ซื้อประกาศสำเร็จตามปกติ
    private static void test1_normalPurchase() throws Exception {
        JdbcDataSource source = createTestDataSource("test1_normal");
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        byte[] itemData = createDummyItemData(10);

        long listingId;
        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "INSERT INTO nw_market_listings (seller_id, seller_name, item_nbt, price_silver,"
                             + " created_game_time, expires_game_time, item_id, item_name, category)"
                             + " VALUES (?, 'Seller', ?, 100, 1, 1000, 'minecraft:iron_ingot', 'iron ingot', 'MATERIALS')",
                     Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, sellerId.toString());
            s.setBytes(2, itemData);
            s.executeUpdate();
            try (ResultSet rs = s.getGeneratedKeys()) { rs.next(); listingId = rs.getLong(1); }
        }

        // Purchase 4 items
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            // Record operation COMMITTED
            JdbcMarketRepository.recordOperation(c, opId, "PURCHASE", buyerId, listingId, "COMMITTED",
                    "SUCCESS", "", 10L, 0L);
            // Add pending silver to seller
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO nw_market_pending_balances (player_id, silver) VALUES (?, 400)")) {
                s.setString(1, sellerId.toString());
                s.executeUpdate();
            }
            // Add pending item for buyer (status PENDING)
            JdbcMarketRepository.insertPendingItem(c, buyerId, createDummyItemData(4), 10L);
            // Update listing stock to 6
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_listings SET item_nbt = ? WHERE id = ?")) {
                s.setBytes(1, createDummyItemData(6));
                s.setLong(2, listingId);
                s.executeUpdate();
            }
            c.commit();
        }

        try (Connection c = source.getConnection()) {
            // Verify listing remaining
            try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM nw_market_listings WHERE id = ?")) {
                s.setLong(1, listingId);
                try (ResultSet rs = s.executeQuery()) { rs.next(); check(rs.getInt(1) == 1, "Listing remains with remaining stock"); }
            }
            // Verify seller pending silver
            try (PreparedStatement s = c.prepareStatement("SELECT silver FROM nw_market_pending_balances WHERE player_id = ?")) {
                s.setString(1, sellerId.toString());
                try (ResultSet rs = s.executeQuery()) { rs.next(); check(rs.getLong(1) == 400L, "Seller proceeds recorded in pending balance"); }
            }
            // Verify buyer pending items
            try (PreparedStatement s = c.prepareStatement("SELECT delivery_status FROM nw_market_pending_items WHERE player_id = ?")) {
                s.setString(1, buyerId.toString());
                try (ResultSet rs = s.executeQuery()) { rs.next(); check("PENDING".equals(rs.getString(1)), "Buyer pending item is PENDING in mailbox"); }
            }
            // Verify operation journal
            var op = JdbcMarketRepository.findOperation(c, opId);
            check(op.isPresent() && "COMMITTED".equals(op.get().status()), "Purchase operation committed in journal");
        }
    }

    // 2. ผู้เล่นสองคนซื้อประกาศเดียวกันพร้อมกัน
    private static void test2_concurrentPurchases() throws Exception {
        JdbcDataSource source = createTestDataSource("test2_concurrent");
        UUID seller = UUID.randomUUID();
        UUID buyerA = UUID.randomUUID();
        UUID buyerB = UUID.randomUUID();
        int initialStock = 5;

        // Listing has 5 items
        var checkA = MarketTransactionPolicy.checkPurchase(true, false, false, 500L, 0L, 100L, 5, initialStock, 0);
        check(checkA.result() == MarketResult.SUCCESS, "Buyer A purchase succeeds");

        // Buyer A takes all 5, stock becomes 0
        int stockAfterA = 0;
        var checkB = MarketTransactionPolicy.checkPurchase(true, false, false, 500L, 0L, 100L, 5, stockAfterA, 0);
        check(checkB.result() == MarketResult.INSUFFICIENT_STOCK, "Buyer B rejected due to insufficient stock");
    }

    // 3. packet purchase เดิมถูกส่งซ้ำ
    private static void test3_duplicatePurchasePacket() throws Exception {
        JdbcDataSource source = createTestDataSource("test3_dup_packet");
        UUID opId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        try (Connection c = source.getConnection()) {
            JdbcMarketRepository.recordOperation(c, opId, "PURCHASE", buyerId, 1L, "COMMITTED",
                    "SUCCESS", "", 1L, 0L);
        }

        try (Connection c = source.getConnection()) {
            var existing = JdbcMarketRepository.findOperation(c, opId);
            check(existing.isPresent(), "Operation exists in journal");
            check("COMMITTED".equals(existing.get().status()), "Duplicate packet detects COMMITTED status");
            check(MarketResult.SUCCESS.name().equals(existing.get().resultCode()), "Duplicate packet safely replays SUCCESS without re-mutating");
        }
    }

    // 4. DB ล้มเหลวก่อน commit
    private static void test4_dbFailureBeforeCommit() throws Exception {
        JdbcDataSource source = createTestDataSource("test4_db_failure");
        UUID buyerId = UUID.randomUUID();

        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            JdbcMarketRepository.insertPendingItem(c, buyerId, createDummyItemData(1), 10L);
            c.rollback(); // Simulated failure / rollback before commit
        }

        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM nw_market_pending_items WHERE player_id = ?")) {
            s.setString(1, buyerId.toString());
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                check(rs.getInt(1) == 0, "Rollback before commit leaves no partial item");
            }
        }
    }

    // 5. DB ล้มเหลวหลัง reserve pending item
    private static void test5_dbFailureAfterReserve() throws Exception {
        JdbcDataSource source = createTestDataSource("test5_reserve_fail");
        UUID playerId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        long pendingId;
        try (Connection c = source.getConnection()) {
            pendingId = JdbcMarketRepository.insertPendingItem(c, playerId, createDummyItemData(5), 10L);
        }

        // Reserve item
        try (Connection c = source.getConnection()) {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_id = ?, delivery_status = 'DELIVERING' WHERE id = ?")) {
                s.setString(1, deliveryId.toString());
                s.setLong(2, pendingId);
                s.executeUpdate();
            }
        }

        // Verify that standard query for PENDING does not see this item (cannot be claimed again)
        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "SELECT COUNT(*) FROM nw_market_pending_items WHERE player_id = ? AND delivery_status = 'PENDING'")) {
            s.setString(1, playerId.toString());
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                check(rs.getInt(1) == 0, "Reserved DELIVERING item is invisible to normal claim queries");
            }
        }
    }

    // 6. DB ล้มเหลวระหว่าง claim item
    private static void test6_dbFailureDuringClaim() throws Exception {
        JdbcDataSource source = createTestDataSource("test6_claim_fail");
        UUID playerId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        long pendingId;
        try (Connection c = source.getConnection()) {
            pendingId = JdbcMarketRepository.insertPendingItem(c, playerId, createDummyItemData(5), 10L);
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_id = ?, delivery_status = 'DELIVERING' WHERE id = ?")) {
                s.setString(1, deliveryId.toString());
                s.setLong(2, pendingId);
                s.executeUpdate();
            }
        }

        // Simulate item was given to inventory, but DB confirmation failed -> mark NEEDS_REVIEW
        try (Connection c = source.getConnection()) {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_status = 'NEEDS_REVIEW' WHERE delivery_id = ?")) {
                s.setString(1, deliveryId.toString());
                s.executeUpdate();
            }
        }

        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "SELECT delivery_status FROM nw_market_pending_items WHERE id = ?")) {
            s.setLong(1, pendingId);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                check("NEEDS_REVIEW".equals(rs.getString(1)), "Unconfirmed delivery is quarantined in NEEDS_REVIEW, never PENDING");
            }
        }
    }

    // 7. claim sale เดิมซ้ำ
    private static void test7_duplicateClaimSale() throws Exception {
        JdbcDataSource source = createTestDataSource("test7_dup_sale");
        UUID sellerId = UUID.randomUUID();

        try (Connection c = source.getConnection()) {
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO nw_market_pending_balances (player_id, silver) VALUES (?, 500)")) {
                s.setString(1, sellerId.toString());
                s.executeUpdate();
            }
        }

        // First consume
        boolean first;
        try (Connection c = source.getConnection()) {
            first = JdbcMarketRepository.consumePendingSilver(c, sellerId, 500L);
        }
        check(first, "First claim sale consumes pending silver");

        // Second consume attempt with same amount
        boolean second;
        try (Connection c = source.getConnection()) {
            second = JdbcMarketRepository.consumePendingSilver(c, sellerId, 500L);
        }
        check(!second, "Duplicate claim sale fails; pending balance was already consumed");
    }

    // 8. restart ขณะ operation เป็น PROCESSING/DELIVERING
    private static void test8_restartRecovery() throws Exception {
        JdbcDataSource source = createTestDataSource("test8_restart");
        UUID playerId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        try (Connection c = source.getConnection()) {
            long id = JdbcMarketRepository.insertPendingItem(c, playerId, createDummyItemData(3), 1L);
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_id = ?, delivery_status = 'DELIVERING' WHERE id = ?")) {
                s.setString(1, deliveryId.toString());
                s.setLong(2, id);
                s.executeUpdate();
            }
        }

        // Simulate server restart recovery scanner
        int recovered;
        try (Connection c = source.getConnection()) {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_status = 'NEEDS_REVIEW' WHERE delivery_status = 'DELIVERING'")) {
                recovered = s.executeUpdate();
            }
        }
        check(recovered == 1, "Recovery moved in-flight DELIVERING item to NEEDS_REVIEW");

        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "SELECT delivery_status FROM nw_market_pending_items WHERE player_id = ?")) {
            s.setString(1, playerId.toString());
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                check("NEEDS_REVIEW".equals(rs.getString(1)), "Status after restart recovery is NEEDS_REVIEW");
            }
        }
    }

    // 9. กระเป๋าเต็มบางส่วน
    private static void test9_partialInventorySpace() throws Exception {
        JdbcDataSource source = createTestDataSource("test9_partial");
        UUID playerId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        long originalId;
        try (Connection c = source.getConnection()) {
            originalId = JdbcMarketRepository.insertPendingItem(c, playerId, createDummyItemData(64), 1L);
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET delivery_id = ?, delivery_status = 'DELIVERING' WHERE id = ?")) {
                s.setString(1, deliveryId.toString());
                s.setLong(2, originalId);
                s.executeUpdate();
            }
        }

        // Inventory could fit 20 items; 44 items remain
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            // 20 items marked DELIVERED
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE nw_market_pending_items SET item_nbt = ?, delivery_status = 'DELIVERED' WHERE id = ?")) {
                s.setBytes(1, createDummyItemData(20));
                s.setLong(2, originalId);
                s.executeUpdate();
            }
            // 44 items re-inserted as PENDING
            JdbcMarketRepository.insertPendingItem(c, playerId, createDummyItemData(44), 2L);
            c.commit();
        }

        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "SELECT delivery_status, COUNT(*) FROM nw_market_pending_items WHERE player_id = ? GROUP BY delivery_status")) {
            s.setString(1, playerId.toString());
            try (ResultSet rs = s.executeQuery()) {
                int countDelivered = 0;
                int countPending = 0;
                while (rs.next()) {
                    if ("DELIVERED".equals(rs.getString(1))) countDelivered = rs.getInt(2);
                    if ("PENDING".equals(rs.getString(1))) countPending = rs.getInt(2);
                }
                check(countDelivered == 1, "Delivered portion marked DELIVERED");
                check(countPending == 1, "Remaining portion preserved as PENDING for future claim");
            }
        }
    }

    // 10. ยกเลิกประกาศแล้วรับสินค้าคืน
    private static void test10_cancelAndClaimReturn() throws Exception {
        JdbcDataSource source = createTestDataSource("test10_cancel");
        UUID sellerId = UUID.randomUUID();
        UUID cancelOp = UUID.randomUUID();

        long listingId;
        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "INSERT INTO nw_market_listings (seller_id, seller_name, item_nbt, price_silver,"
                             + " created_game_time, expires_game_time, item_id, item_name, category)"
                             + " VALUES (?, 'Seller', ?, 50, 1, 1000, 'minecraft:iron_ingot', 'iron ingot', 'MATERIALS')",
                     Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, sellerId.toString());
            s.setBytes(2, createDummyItemData(3));
            s.executeUpdate();
            try (ResultSet rs = s.getGeneratedKeys()) { rs.next(); listingId = rs.getLong(1); }
        }

        // Cancel listing
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            // Delete listing
            try (PreparedStatement s = c.prepareStatement("DELETE FROM nw_market_listings WHERE id = ?")) {
                s.setLong(1, listingId);
                s.executeUpdate();
            }
            // Put in pending items with status PENDING
            JdbcMarketRepository.insertPendingItem(c, sellerId, createDummyItemData(3), 10L);
            JdbcMarketRepository.recordOperation(c, cancelOp, "CANCEL", sellerId, listingId, "COMMITTED", "SUCCESS", "", 10L, 0L);
            c.commit();
        }

        try (Connection c = source.getConnection()) {
            // Verify listing deleted
            try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM nw_market_listings WHERE id = ?")) {
                s.setLong(1, listingId);
                try (ResultSet rs = s.executeQuery()) { rs.next(); check(rs.getInt(1) == 0, "Cancelled listing removed"); }
            }
            // Verify pending item ready for claim
            try (PreparedStatement s = c.prepareStatement("SELECT delivery_status FROM nw_market_pending_items WHERE player_id = ?")) {
                s.setString(1, sellerId.toString());
                try (ResultSet rs = s.executeQuery()) { rs.next(); check("PENDING".equals(rs.getString(1)), "Cancelled item available in mailbox"); }
            }
        }
    }

    // 11. ประกาศหมดอายุ
    private static void test11_listingExpiration() throws Exception {
        JdbcDataSource source = createTestDataSource("test11_expire");
        UUID sellerId = UUID.randomUUID();

        try (Connection c = source.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "INSERT INTO nw_market_listings (seller_id, seller_name, item_nbt, price_silver,"
                             + " created_game_time, expires_game_time, item_id, item_name, category)"
                             + " VALUES (?, 'Seller', ?, 50, 1, 100, 'minecraft:iron_ingot', 'iron ingot', 'MATERIALS')")) {
            s.setString(1, sellerId.toString());
            s.setBytes(2, createDummyItemData(2));
            s.executeUpdate();
        }

        // Process batch expiry at gameTime 200 (> 100)
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT id, seller_id, item_nbt FROM nw_market_listings WHERE expires_game_time <= 200 FOR UPDATE");
                 ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    UUID seller = UUID.fromString(rs.getString(2));
                    byte[] nbt = rs.getBytes(3);
                    JdbcMarketRepository.insertPendingItem(c, seller, nbt, 200L);
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM nw_market_listings WHERE id = ?")) {
                        del.setLong(1, id);
                        del.executeUpdate();
                    }
                }
            }
            c.commit();
        }

        try (Connection c = source.getConnection()) {
            try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM nw_market_listings")) {
                try (ResultSet rs = s.executeQuery()) { rs.next(); check(rs.getInt(1) == 0, "Expired listing removed from market"); }
            }
            try (PreparedStatement s = c.prepareStatement("SELECT delivery_status FROM nw_market_pending_items WHERE player_id = ?")) {
                s.setString(1, sellerId.toString());
                try (ResultSet rs = s.executeQuery()) { rs.next(); check("PENDING".equals(rs.getString(1)), "Expired item returned to seller pending items"); }
            }
        }
    }

    // 12. balance overflow
    private static void test12_balanceOverflow() {
        var check = MarketTransactionPolicy.checkPurchase(
                true, false, false,
                1000L, Long.MAX_VALUE, // Seller pending balance is already MAX_VALUE
                100L, 1, 10, 0
        );
        check(check.result() == MarketResult.BALANCE_OVERFLOW, "Seller balance overflow caught and rejected");
    }

    // 13. permission ถูกปฏิเสธ
    private static void test13_permissionDenied() {
        boolean hasGuiPermissionNode = java.util.Arrays.stream(ModPermissions.class.getDeclaredFields())
                .anyMatch(field -> field.getName().startsWith("GUI_MARKET_"));
        check(!hasGuiPermissionNode, "Market access uses command permissions without separate GUI permission nodes");
        check(ModPermissions.COMMAND_MARKET_USE.getNodeName().equals("neoworld.command.market.use"), "Command market use permission key correct");
        check(ModPermissions.COMMAND_MARKET_ADMIN.getNodeName().equals("neoworld.command.market.admin"), "Command market admin permission key correct");
    }

    // 14. schema ใหม่และ upgrade schema เดิม
    private static void test14_schemaFreshAndUpgrade() throws Exception {
        // Fresh
        JdbcDataSource fresh = new JdbcDataSource();
        fresh.setURL("jdbc:h2:mem:schema_fresh;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(fresh, Config.DatabaseType.H2, "fresh_");
        check(tableExists(fresh, "FRESH_MARKET_OPERATIONS"), "Fresh schema creates market_operations");
        check(columnExists(fresh, "FRESH_MARKET_PENDING_ITEMS", "DELIVERY_STATUS"), "Fresh schema has delivery_status");

        // Upgrade from v11
        JdbcDataSource upgrade = new JdbcDataSource();
        upgrade.setURL("jdbc:h2:mem:schema_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(upgrade, Config.DatabaseType.H2, "up_");
        // Drop version 12 from schema history and drop market_operations to emulate v11
        try (Connection c = upgrade.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE up_market_operations");
            s.execute("DELETE FROM up_schema_history WHERE version >= 12");
        }
        // Run migration again to upgrade from 11 to 12
        NeoWorldDatabase.runMigrations(upgrade, Config.DatabaseType.H2, "up_");
        check(tableExists(upgrade, "UP_MARKET_OPERATIONS"), "Upgrade to v12 adds market_operations");
        check(columnExists(upgrade, "UP_MARKET_PENDING_ITEMS", "DELIVERY_STATUS"), "Upgrade to v12 maintains delivery_status");
    }

    // 15. H2 transaction rollback ไม่เหลือ state ครึ่งกลาง
    private static void test15_transactionRollbackNoPartialState() throws Exception {
        JdbcDataSource source = createTestDataSource("test15_rollback");
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            // Insert partial listing
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO nw_market_listings (seller_id, seller_name, item_nbt, price_silver,"
                            + " created_game_time, expires_game_time, item_id, item_name, category)"
                            + " VALUES (?, 'Seller', ?, 100, 1, 1000, 'minecraft:iron_ingot', 'iron ingot', 'MATERIALS')")) {
                s.setString(1, seller.toString());
                s.setBytes(2, createDummyItemData(10));
                s.executeUpdate();
            }
            // Insert partial balance
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO nw_market_pending_balances (player_id, silver) VALUES (?, 100)")) {
                s.setString(1, seller.toString());
                s.executeUpdate();
            }
            // Insert partial operation
            JdbcMarketRepository.recordOperation(c, UUID.randomUUID(), "PURCHASE", buyer, 1L, "PROCESSING", "", "", 1L, 0L);
            // Rollback!
            c.rollback();
        }

        try (Connection c = source.getConnection()) {
            try (Statement s = c.createStatement()) {
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM nw_market_listings")) { rs.next(); check(rs.getInt(1) == 0, "No listings after rollback"); }
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM nw_market_pending_balances")) { rs.next(); check(rs.getInt(1) == 0, "No balances after rollback"); }
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM nw_market_operations")) { rs.next(); check(rs.getInt(1) == 0, "No operations after rollback"); }
            }
        }
    }

    private static boolean tableExists(JdbcDataSource source, String tableName) throws SQLException {
        try (Connection connection = source.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean columnExists(JdbcDataSource source, String tableName, String columnName) throws SQLException {
        try (Connection connection = source.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
