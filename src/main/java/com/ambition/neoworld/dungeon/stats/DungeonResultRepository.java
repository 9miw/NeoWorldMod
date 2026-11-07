package com.ambition.neoworld.dungeon.stats;

import com.ambition.neoworld.database.NeoWorldDatabase;
import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** Blocking JDBC API: call from an I/O worker, never a ranking screen/server tick. */
public final class DungeonResultRepository {
    private DungeonResultRepository() {}

    public record Stats(long successfulRuns, OptionalLong bestTicks) {}
    public record History(UUID runId, String dungeonId, DungeonRoomType mode, long clearTicks, long completedAtMillis) {}

    public static void createSchema(Connection connection, String prefix) throws SQLException {
        String runs = table(prefix + "dungeon_runs");
        try (var s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + runs + " (run_id VARCHAR(36) PRIMARY KEY,"
                    + " dungeon_id VARCHAR(191) NOT NULL, room_mode VARCHAR(16) NOT NULL,"
                    + " clear_ticks BIGINT NOT NULL, completed_at_ms BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + runs + "_players (run_id VARCHAR(36) NOT NULL,"
                    + " player_id VARCHAR(36) NOT NULL, credited BOOLEAN NOT NULL, PRIMARY KEY (run_id,player_id),"
                    + " FOREIGN KEY (run_id) REFERENCES " + runs + "(run_id))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS " + runs + "_lookup ON " + runs
                    + " (dungeon_id,room_mode,completed_at_ms)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS " + runs + "_player_lookup ON " + runs
                    + "_players (player_id,credited,run_id)");
        }
    }

    public static boolean record(DungeonClear clear) throws SQLException {
        return NeoWorldDatabase.inTransaction(c -> record(c, NeoWorldDatabase.tableName("dungeon_runs"), clear));
    }

    /** Caller owns the transaction: the run and its entire roster must commit together. */
    public static boolean record(Connection c, String runs, DungeonClear clear) throws SQLException {
        table(runs);
        try (var q = c.prepareStatement("SELECT dungeon_id,room_mode,clear_ticks,completed_at_ms FROM " + runs
                + " WHERE run_id=?")) {
            q.setString(1, clear.runId().toString());
            try (var rows = q.executeQuery()) {
                if (rows.next()) {
                    var members = new ArrayList<DungeonClear.Member>();
                    try (var players = c.prepareStatement("SELECT player_id,credited FROM " + runs + "_players WHERE run_id=?")) {
                        players.setString(1, clear.runId().toString());
                        try (var r = players.executeQuery()) {
                            while (r.next()) members.add(new DungeonClear.Member(UUID.fromString(r.getString(1)), r.getBoolean(2)));
                        }
                    }
                    var stored = new DungeonClear(clear.runId(), rows.getString(1), DungeonRoomType.valueOf(rows.getString(2)),
                            rows.getLong(3), rows.getLong(4), members);
                    if (!stored.equals(clear)) throw new SQLException("Conflicting dungeon result for run " + clear.runId());
                    return false;
                }
            }
        }
        try (var insert = c.prepareStatement("INSERT INTO " + runs
                + " (run_id,dungeon_id,room_mode,clear_ticks,completed_at_ms) VALUES (?,?,?,?,?)")) {
            insert.setString(1, clear.runId().toString());
            insert.setString(2, clear.dungeonId());
            insert.setString(3, clear.mode().name());
            insert.setLong(4, clear.clearTicks());
            insert.setLong(5, clear.completedAtMillis());
            insert.executeUpdate();
        }
        try (var insert = c.prepareStatement("INSERT INTO " + runs + "_players (run_id,player_id,credited) VALUES (?,?,?)")) {
            for (var member : clear.members()) {
                insert.setString(1, clear.runId().toString());
                insert.setString(2, member.playerId().toString());
                insert.setBoolean(3, member.credited());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return true;
    }

    public static Stats stats(UUID player, String dungeon, DungeonRoomType mode) throws SQLException {
        try (var c = NeoWorldDatabase.getConnection()) {
            return stats(c, NeoWorldDatabase.tableName("dungeon_runs"), player, dungeon, mode);
        }
    }

    public static Stats stats(Connection c, String runs, UUID player, String dungeon, DungeonRoomType mode) throws SQLException {
        try (var q = c.prepareStatement("SELECT COUNT(*),MIN(r.clear_ticks)" + filter(runs))) {
            bind(q, player, dungeon, mode);
            try (var rows = q.executeQuery()) {
                rows.next();
                long count = rows.getLong(1), best = rows.getLong(2);
                return new Stats(count, rows.wasNull() ? OptionalLong.empty() : OptionalLong.of(best));
            }
        }
    }

    public static List<History> history(UUID player, String dungeon, DungeonRoomType mode, int limit, int offset) throws SQLException {
        try (var c = NeoWorldDatabase.getConnection()) {
            return history(c, NeoWorldDatabase.tableName("dungeon_runs"), player, dungeon, mode, limit, offset);
        }
    }

    public static List<History> history(Connection c, String runs, UUID player, String dungeon,
            DungeonRoomType mode, int limit, int offset) throws SQLException {
        if (limit < 1 || limit > 100 || offset < 0) throw new IllegalArgumentException("Invalid history page");
        try (var q = c.prepareStatement("SELECT r.run_id,r.dungeon_id,r.room_mode,r.clear_ticks,r.completed_at_ms"
                + filter(runs) + " ORDER BY r.completed_at_ms DESC,r.run_id ASC LIMIT ? OFFSET ?")) {
            bind(q, player, dungeon, mode);
            q.setInt(4, limit); q.setInt(5, offset);
            var result = new ArrayList<History>();
            try (var rows = q.executeQuery()) {
                while (rows.next()) result.add(new History(UUID.fromString(rows.getString(1)), rows.getString(2),
                        DungeonRoomType.valueOf(rows.getString(3)), rows.getLong(4), rows.getLong(5)));
            }
            return List.copyOf(result);
        }
    }

    private static String filter(String runs) {
        table(runs);
        return " FROM " + runs + " r JOIN " + runs + "_players p ON r.run_id=p.run_id"
                + " WHERE p.player_id=? AND p.credited=TRUE AND r.dungeon_id=? AND r.room_mode=?";
    }

    private static void bind(java.sql.PreparedStatement q, UUID player, String dungeon, DungeonRoomType mode) throws SQLException {
        q.setString(1, player.toString()); q.setString(2, dungeon); q.setString(3, mode.name());
    }

    private static String table(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Unsafe dungeon results table");
        }
        return value;
    }
}
