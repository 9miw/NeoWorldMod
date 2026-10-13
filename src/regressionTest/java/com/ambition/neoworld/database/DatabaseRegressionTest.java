package com.ambition.neoworld.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.h2.jdbcx.JdbcDataSource;

import com.ambition.neoworld.Config;

public final class DatabaseRegressionTest {
    private DatabaseRegressionTest() {}

    public static int run() throws Exception {
        int checks = 0;
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:neoworld_regression;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");

        check(tableExists(source, "TEST_MARKET_LISTINGS"), "market listings table created");
        checks++;
        check(tableExists(source, "TEST_MARKET_PENDING_ITEMS"), "pending items table created");
        checks++;
        check(schemaVersion(source) == 7, "database migrated to schema version 7");
        checks++;
        check(columnExists(source, "TEST_MARKET_LISTINGS", "ITEM_ID")
                && columnExists(source, "TEST_MARKET_LISTINGS", "CATEGORY"),
                "market query columns created");
        checks++;
        check(columnExists(source, "TEST_MARKET_TRANSACTIONS", "ITEM_NBT")
                && columnExists(source, "TEST_MARKET_TRANSACTIONS", "PROCEEDS_SILVER")
                && columnExists(source, "TEST_MARKET_TRANSACTIONS", "CLAIMED"),
                "market sale claim columns created");
        checks++;

        long generatedId;
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO test_market_listings
                     (seller_id, seller_name, item_nbt, price_silver, created_game_time, expires_game_time)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "00000000-0000-0000-0000-000000000001");
            statement.setString(2, "RegressionSeller");
            statement.setBytes(3, new byte[]{1});
            statement.setLong(4, 100L);
            statement.setLong(5, 1L);
            statement.setLong(6, 2L);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                check(keys.next(), "listing generated key returned");
                generatedId = keys.getLong(1);
            }
        }
        checks++;
        check(generatedId > 0, "listing generated ID is positive");
        checks++;

        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO test_market_pending_balances (player_id, silver) VALUES (?, ?)")) {
                statement.setString(1, "00000000-0000-0000-0000-000000000002");
                statement.setLong(2, 500L);
                statement.executeUpdate();
            }
            connection.rollback();
        }
        check(rowCount(source, "test_market_pending_balances") == 0, "market transaction rollback preserves state");
        checks++;

        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO test_schema_history (version) VALUES (99)")) {
            statement.executeUpdate();
        }
        boolean rejectedNewerSchema = false;
        try {
            NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        } catch (SQLException expected) {
            rejectedNewerSchema = true;
        }
        check(rejectedNewerSchema, "newer database schema rejected");
        checks++;

        JdbcDataSource mariaCompatibility = new JdbcDataSource();
        mariaCompatibility.setURL("jdbc:h2:mem:neoworld_maria;MODE=MariaDB;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(mariaCompatibility, Config.DatabaseType.MARIADB, "maria_");
        check(tableExists(mariaCompatibility, "MARIA_MARKET_LISTINGS"), "MariaDB-compatible market schema created");
        checks++;
        check(tableExists(mariaCompatibility, "MARIA_MARKET_TRANSACTIONS"),
                "MariaDB-compatible transaction table created");
        checks++;
        check(columnExists(mariaCompatibility, "MARIA_MARKET_TRANSACTIONS", "CLAIMED")
                && columnExists(mariaCompatibility, "MARIA_MARKET_TRANSACTIONS", "ITEM_NBT"),
                "MariaDB-compatible sale claim columns created");
        checks++;
        return checks + TitleStorageRegressionTest.run() + PvpRatingRegressionTest.run();
    }

    private static boolean tableExists(JdbcDataSource source, String tableName) throws SQLException {
        try (Connection connection = source.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static int schemaVersion(JdbcDataSource source) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT MAX(version) FROM test_schema_history");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static boolean columnExists(JdbcDataSource source, String tableName, String columnName)
            throws SQLException {
        try (Connection connection = source.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private static int rowCount(JdbcDataSource source, String table) throws SQLException {
        try (Connection connection = source.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
