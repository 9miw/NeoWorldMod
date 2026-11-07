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
        currencyRetirement();
        int checks = 0;
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:neoworld_regression;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");

        check(tableExists(source, "TEST_MARKET_LISTINGS"), "market listings table created");
        checks++;
        check(tableExists(source, "TEST_MARKET_PENDING_ITEMS"), "pending items table created");
        checks++;
        check(schemaVersion(source) == 17, "database migrated to schema version 17");
        checks++;
        check(tableExists(source, "TEST_ECONOMY_OUTBOX_RECEIPTS"), "durable analytics receipts created");
        checks++;
        check(tableExists(source, "TEST_MARKET_PLAYER_JOURNAL"), "market player journal created");
        checks++;
        check(tableExists(source, "TEST_DUNGEON_RUNS") && tableExists(source, "TEST_DUNGEON_RUNS_PLAYERS"),
                "v14 dungeon runs and roster tables created");
        checks++;
        check(columnExists(source, "TEST_SHOP_TRANSACTIONS", "STATUS")
                && columnExists(source, "TEST_SHOP_TRANSACTIONS", "TX_SEQ")
                && columnExists(source, "TEST_SHOP_TRANSACTIONS", "BALANCE_BEFORE")
                && columnExists(source, "TEST_SHOP_TRANSACTIONS", "BALANCE_AFTER"),
                "shop transaction recovery columns created");
        checks++;
        check(tableExists(source, "TEST_MARKET_OPERATIONS"), "market operations table created");
        checks++;
        check(columnExists(source, "TEST_MARKET_PENDING_ITEMS", "DELIVERY_ID")
                && columnExists(source, "TEST_MARKET_PENDING_ITEMS", "DELIVERY_STATUS"),
                "market pending item delivery columns created");
        checks++;
        check(tableExists(source, "TEST_SHOP_STORES") && tableExists(source, "TEST_SHOP_OFFERS")
                && tableExists(source, "TEST_SHOP_TRANSACTIONS"), "shop catalog and audit schema created");
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
        check(tableExists(mariaCompatibility, "MARIA_MARKET_OPERATIONS"),
                "MariaDB-compatible market operations table created");
        checks++;
        check(columnExists(mariaCompatibility, "MARIA_MARKET_PENDING_ITEMS", "DELIVERY_STATUS"),
                "MariaDB-compatible pending item delivery status column created");
        checks++;
        check(columnExists(mariaCompatibility, "MARIA_MARKET_TRANSACTIONS", "CLAIMED")
                && columnExists(mariaCompatibility, "MARIA_MARKET_TRANSACTIONS", "ITEM_NBT"),
                "MariaDB-compatible sale claim columns created");
        checks++;
        check(columnExists(mariaCompatibility, "MARIA_SHOP_TRANSACTIONS", "STATUS")
                && columnExists(mariaCompatibility, "MARIA_SHOP_TRANSACTIONS", "TX_SEQ"),
                "MariaDB-compatible shop recovery columns created");
        checks++;
        analyticsUpgrade();
        checks += 3;
        dungeonUpgrade();
        checks += 4;
        journalUpgrade();
        checks += 4;
        outboxUpgrade();
        checks += 4;
        return checks + TitleCatalogueRegressionTest.run() + TitleStorageRegressionTest.run() + PvpRatingRegressionTest.run()
                + com.ambition.neoworld.economy.analytics.EconomyAnalyticsRegressionTest.run();
    }
    private static void currencyRetirement() throws Exception {
        for (boolean maria : new boolean[]{false,true}) {
            var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:retired_currency_"+maria+";DB_CLOSE_DELAY=-1"+(maria?";MODE=MariaDB":""));
            var type=maria?Config.DatabaseType.MARIADB:Config.DatabaseType.H2;
            NeoWorldDatabase.runMigrations(source,type,"test_");
            try(var c=source.getConnection();var s=c.createStatement()) {
                s.execute("INSERT INTO test_shop_stores(id,name) VALUES (1,'Legacy')");
                s.execute("INSERT INTO test_shop_offers(id,shop_id,item_nbt,buy_price,sell_price,category,currency) VALUES (1,1,X'0102',100,10,'MATERIALS','GOLD')");
                s.execute("INSERT INTO test_shop_transactions(id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt,currency,total_amount,status) VALUES ('old','00000000-0000-0000-0000-000000000001',1,1,'SELL',1,0,X'0102','GOLD',10,'COMMITTED')");
                s.execute("INSERT INTO test_shop_transactions(id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt,currency,total_amount,status) VALUES ('pending','00000000-0000-0000-0000-000000000001',1,1,'SELL',1,0,X'0102','GOLD',10,'PENDING')");
                s.execute("DELETE FROM test_schema_history WHERE version=17");
            }
            NeoWorldDatabase.runMigrations(source,type,"test_");
            try(var c=source.getConnection();var s=c.createStatement()) {
                try(var r=s.executeQuery("SELECT currency,buy_price,sell_price FROM test_shop_offers WHERE id=1")) {
                    r.next();check(r.getString(1).equals("SILVER")&&r.getLong(2)==100&&r.getLong(3)==10,"active catalog moves to Silver without repricing");
                }
                try(var r=s.executeQuery("SELECT currency,total_amount,status FROM test_shop_transactions WHERE id='old'")) {
                    r.next();check(r.getString(1).equals("GOLD")&&r.getLong(2)==10&&r.getString(3).equals("COMMITTED"),"old audit retains retired currency and amount");
                }
                try(var r=s.executeQuery("SELECT currency,status FROM test_shop_transactions WHERE id='pending'")) {
                    r.next();check(r.getString(1).equals("GOLD")&&r.getString(2).equals("NEEDS_REVIEW"),"pending retired operations never replay against Silver");
                }
                s.execute("DELETE FROM test_schema_history WHERE version=17");
            }
            NeoWorldDatabase.runMigrations(source,type,"test_");
            try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM test_economy_events WHERE source='currency_retirement'")) {
                r.next();check(r.getInt(1)==1,"retirement retry preserves single catalog history record");
            }
            try(var c=source.getConnection();var s=c.createStatement()) {
                s.execute("UPDATE test_shop_offers SET currency='GOLD' WHERE id=1");
                s.execute("INSERT INTO test_shop_offers(id,shop_id,item_nbt,buy_price,sell_price,category,currency) VALUES (2,1,X'0102',200,150,'MATERIALS','SILVER')");
                s.execute("DELETE FROM test_schema_history WHERE version=17");
            }
            var itemTag=new net.minecraft.nbt.CompoundTag();
            itemTag.putString("id","minecraft:stone"); itemTag.putInt("count",1);
            var wrapper=new net.minecraft.nbt.CompoundTag(); wrapper.put("item",itemTag);
            var bytes=new java.io.ByteArrayOutputStream(); net.minecraft.nbt.NbtIo.writeCompressed(wrapper,bytes);
            byte[] first=bytes.toByteArray(),second=first.clone(); second[4]^=1; // Different gzip metadata, identical NBT.
            try(var c=source.getConnection();var s=c.prepareStatement("UPDATE test_shop_offers SET item_nbt=? WHERE id=?")) {
                s.setBytes(1,first);s.setLong(2,1);s.executeUpdate();
                s.setBytes(1,second);s.setLong(2,2);s.executeUpdate();
            }
            try { NeoWorldDatabase.runMigrations(source,type,"test_");throw new AssertionError("Conflicting migration must fail"); }
            catch(java.sql.SQLException expected) { check(expected.getMessage().contains("arbitrage"),"decoded NBT identity catches conflicts despite different compressed bytes"); }
            try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT currency FROM test_shop_offers WHERE id=1")) {
                r.next();check(r.getString(1).equals("GOLD"),"arbitrage conflict preserves unconverted catalog");
            }
        }
    }
    private static void outboxUpgrade() throws Exception {
        var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:outbox_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("INSERT INTO test_metadata(metadata_key,metadata_value) VALUES ('keep','v15-data')");
            s.execute("DROP TABLE test_economy_outbox_receipts");
            s.execute("DELETE FROM test_schema_history WHERE version>=16");
        }
        check(schemaVersion(source)==15,"analytics outbox fixture starts at v15");
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        check(schemaVersion(source)==17 && tableExists(source,"TEST_ECONOMY_OUTBOX_RECEIPTS"),"v16 creates receipt table");
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT metadata_value FROM test_metadata WHERE metadata_key='keep'")) {
            check(r.next() && r.getString(1).equals("v15-data"),"v16 preserves previous feature data");
        }
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("INSERT INTO test_economy_outbox_receipts(job_id) VALUES ('keep-receipt')");
            s.execute("DELETE FROM test_schema_history WHERE version>=16");
        }
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        check(schemaVersion(source)==17 && rowCount(source,"test_economy_outbox_receipts")==1,
                "interrupted v16 DDL retries without losing receipts");
    }

    private static void journalUpgrade() throws Exception {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:journal_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("INSERT INTO test_metadata(metadata_key,metadata_value) VALUES ('keep','v14-data')");
            s.execute("DROP TABLE test_market_player_journal");
            s.execute("DELETE FROM test_schema_history WHERE version>=15");
        }
        check(schemaVersion(source) == 14, "journal upgrade fixture starts at v14");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        check(schemaVersion(source) == 17 && columnExists(source, "TEST_MARKET_PLAYER_JOURNAL", "WORLD_ID")
                && columnExists(source, "TEST_MARKET_PLAYER_JOURNAL", "JOURNAL_SEQ"), "v15 adds scoped ordered player journal");
        try (var c = source.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT metadata_value FROM test_metadata WHERE metadata_key='keep'")) {
            check(r.next() && r.getString(1).equals("v14-data"), "journal migration preserves existing data");
        }
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("DROP INDEX test_idx_market_journal_player");
            s.execute("DELETE FROM test_schema_history WHERE version>=15");
        }
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        try (var c = source.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT COUNT(*) FROM test_schema_history WHERE version=15")) {
            check(r.next() && r.getInt(1) == 1, "interrupted v15 DDL retries existing journal table");
        }
    }

    private static void dungeonUpgrade() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:dungeon_v13_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        try (var c = source.getConnection(); var s = c.createStatement()) {
            s.execute("INSERT INTO test_metadata(metadata_key,metadata_value) VALUES ('keep','v13-data')");
            s.execute("DROP TABLE test_dungeon_runs_players");
            s.execute("DROP TABLE test_dungeon_runs");
            s.execute("DELETE FROM test_schema_history WHERE version>=14");
        }
        check(schemaVersion(source) == 13, "upgrade fixture starts at v13");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        check(schemaVersion(source) == 17 && tableExists(source, "TEST_DUNGEON_RUNS")
                && tableExists(source, "TEST_DUNGEON_RUNS_PLAYERS"), "v13 upgrade reaches v17 and creates both dungeon tables");
        try (var c = source.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT metadata_value FROM test_metadata WHERE metadata_key='keep'")) {
            check(r.next() && r.getString(1).equals("v13-data"), "v14 upgrade preserves existing data");
        }
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "test_");
        check(schemaVersion(source) == 17, "migration dispatcher can run again through v17");
    }

    private static void analyticsUpgrade() throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:analytics_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        try(var c=source.getConnection();var s=c.createStatement()) {
            s.execute("INSERT INTO test_metadata(metadata_key,metadata_value) VALUES ('keep','original')");
            s.execute("DROP TABLE test_economy_daily_balances");s.execute("DROP TABLE test_economy_balances");s.execute("DROP TABLE test_economy_events");
            s.execute("DELETE FROM test_schema_history WHERE version>=10");
        }
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        check(schemaVersion(source)==17 && tableExists(source,"TEST_ECONOMY_EVENTS")
                && tableExists(source,"TEST_DUNGEON_RUNS_PLAYERS"),"v9 upgrade reaches v14 including dungeon roster");
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT metadata_value FROM test_metadata WHERE metadata_key='keep'")) {
            r.next();check(r.getString(1).equals("original"),"analytics upgrade preserves existing feature data");
        }
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("DELETE FROM test_schema_history WHERE version>=10");}
        NeoWorldDatabase.runMigrations(source,Config.DatabaseType.H2,"test_");
        check(schemaVersion(source)==17,"interrupted analytics migration safely retries existing tables");
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
