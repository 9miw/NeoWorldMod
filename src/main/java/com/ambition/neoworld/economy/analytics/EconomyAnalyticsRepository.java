package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.database.NeoWorldDatabase;
import java.sql.*;

/** Append-only analysis history; never the authority for player wallets. */
public final class EconomyAnalyticsRepository {
    private EconomyAnalyticsRepository() {}
    public record Event(String id, long time, String type, String player, int level, boolean admin,
                        String source, String reference, String flow, String currency,
                        long before, long after, long amount, String detail) {}
    public record Balance(String player, long observedAt, int level, boolean admin,
                          long silver, long gold, long diamond) {}

    public static void createOutboxSchema(Connection c, String prefix) throws SQLException {
        validatePrefix(prefix);
        try (var s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "economy_outbox_receipts ("
                    + "job_id VARCHAR(36) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    public static void createSchema(Connection c, Config.DatabaseType type, String prefix) throws SQLException {
        String text = type == Config.DatabaseType.H2 ? "CHARACTER LARGE OBJECT" : "LONGTEXT";
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "economy_events ("
                    + "id VARCHAR(36) PRIMARY KEY, occurred_at BIGINT NOT NULL, event_type VARCHAR(40) NOT NULL,"
                    + " player_id VARCHAR(36), player_level INTEGER NOT NULL, admin BOOLEAN NOT NULL,"
                    + " source VARCHAR(191) NOT NULL, reference_id VARCHAR(191) NOT NULL, flow VARCHAR(24) NOT NULL,"
                    + " currency VARCHAR(16) NOT NULL, before_balance BIGINT NOT NULL, after_balance BIGINT NOT NULL,"
                    + " amount BIGINT NOT NULL, detail " + text + " NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix + "idx_economy_time ON " + prefix
                    + "economy_events (occurred_at, event_type)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix + "idx_economy_player ON " + prefix
                    + "economy_events (player_id, occurred_at)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix + "idx_economy_reference ON " + prefix
                    + "economy_events (event_type, reference_id, occurred_at)");
            String columns = "player_id VARCHAR(36) NOT NULL, observed_at BIGINT NOT NULL, player_level INTEGER NOT NULL,"
                    + " admin BOOLEAN NOT NULL, silver BIGINT NOT NULL, gold BIGINT NOT NULL, diamond BIGINT NOT NULL";
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "economy_balances (" + columns + ", PRIMARY KEY(player_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "economy_daily_balances (snapshot_day VARCHAR(10) NOT NULL,"
                    + columns + ", PRIMARY KEY(snapshot_day,player_id))");
        }
    }

    public static void insert(Connection c, Event event) throws SQLException {
        insert(c, NeoWorldDatabase.tableName("economy_events"), event);
    }
    public static void insert(Connection c, String table, Event e) throws SQLException {
        // Explicit table is also used by isolated migration tests. Never accepts user-supplied SQL.
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new SQLException("Invalid analysis table");
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table
                + " (id,occurred_at,event_type,player_id,player_level,admin,source,reference_id,flow,currency,"
                + "before_balance,after_balance,amount,detail) SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?"
                + " WHERE NOT EXISTS (SELECT 1 FROM " + table + " WHERE id=?)")) {
            s.setString(1,e.id()); s.setLong(2,e.time()); s.setString(3,e.type()); s.setString(4,e.player());
            s.setInt(5,e.level()); s.setBoolean(6,e.admin()); s.setString(7,e.source()); s.setString(8,e.reference());
            s.setString(9,e.flow()); s.setString(10,e.currency()); s.setLong(11,e.before()); s.setLong(12,e.after());
            s.setLong(13,e.amount()); s.setString(14,e.detail()); s.setString(15,e.id()); s.executeUpdate();
        }
    }
    public static void balance(Connection c, Balance b, String day) throws SQLException {
        balance(c, Config.DATABASE_TABLE_PREFIX.get(), b, day);
    }
    static void balance(Connection c,String prefix,Balance b,String day) throws SQLException {
        validatePrefix(prefix);
        upsertBalance(c, prefix + "economy_balances", b, null);
        upsertBalance(c, prefix + "economy_daily_balances", b, day);
    }
    private static void upsertBalance(Connection c, String table, Balance b, String day) throws SQLException {
        String where = "player_id=?" + (day == null ? "" : " AND snapshot_day=?");
        try (PreparedStatement s = c.prepareStatement("UPDATE " + table
                + " SET observed_at=?,player_level=?,admin=?,silver=?,gold=?,diamond=? WHERE " + where + " AND observed_at<=?")) {
            s.setLong(1,b.observedAt()); s.setInt(2,b.level()); s.setBoolean(3,b.admin());
            s.setLong(4,b.silver()); s.setLong(5,b.gold()); s.setLong(6,b.diamond()); s.setString(7,b.player());
            if (day != null) s.setString(8,day);
            s.setLong(day == null ? 8 : 9,b.observedAt());
            if (s.executeUpdate() != 0) return;
        }
        // An outbox from an older run/server must never overwrite a newer observation.
        try (var s = c.prepareStatement("SELECT observed_at FROM " + table + " WHERE " + where)) {
            s.setString(1,b.player()); if (day != null) s.setString(2,day);
            try (var r = s.executeQuery()) { if (r.next()) return; }
        }
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table
                + " (observed_at,player_level,admin,silver,gold,diamond,player_id" + (day == null ? "" : ",snapshot_day")
                + ") VALUES (?,?,?,?,?,?,?" + (day == null ? "" : ",?") + ")")) {
            s.setLong(1,b.observedAt()); s.setInt(2,b.level()); s.setBoolean(3,b.admin());
            s.setLong(4,b.silver()); s.setLong(5,b.gold()); s.setLong(6,b.diamond()); s.setString(7,b.player());
            if (day != null) s.setString(8,day); s.executeUpdate();
        }
    }
    public static void carryDaily(Connection c, String day) throws SQLException {
        carryDaily(c,Config.DATABASE_TABLE_PREFIX.get(),day);
    }
    static void carryDaily(Connection c,String prefix,String day) throws SQLException {
        validatePrefix(prefix);
        String daily = prefix + "economy_daily_balances";
        String latest = prefix + "economy_balances";
        try (PreparedStatement s = c.prepareStatement("INSERT INTO " + daily
                + " (snapshot_day,player_id,observed_at,player_level,admin,silver,gold,diamond)"
                + " SELECT ?,b.player_id,b.observed_at,b.player_level,b.admin,b.silver,b.gold,b.diamond FROM " + latest
                + " b WHERE NOT EXISTS (SELECT 1 FROM " + daily + " d WHERE d.snapshot_day=? AND d.player_id=b.player_id)")) {
            s.setString(1,day); s.setString(2,day); s.executeUpdate();
        }
    }
    private static void validatePrefix(String prefix) throws SQLException {
        if(!prefix.matches("[A-Za-z_][A-Za-z0-9_]{0,31}"))throw new SQLException("Invalid analysis prefix");
    }
}
