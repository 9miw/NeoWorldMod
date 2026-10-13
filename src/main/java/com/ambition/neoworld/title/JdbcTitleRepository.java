package com.ambition.neoworld.title;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Caller owns the transaction. Lock the profile before modifying its child rows. */
public final class JdbcTitleRepository {
    private final String prefix;
    public JdbcTitleRepository(String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z_][a-zA-Z0-9_]{0,31}"))
            throw new IllegalArgumentException("Unsafe table prefix");
        this.prefix = prefix;
    }
    public TitlePlayerState load(Connection connection, UUID playerId) throws SQLException {
        String equipped;
        boolean showTitle, showEffects;
        try (var statement = connection.prepareStatement("SELECT equipped_title, show_title, show_effects FROM "
                + prefix + "title_players WHERE player_id = ? FOR UPDATE")) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) return null;
                equipped = result.getString(1);
                showTitle = result.getBoolean(2);
                showEffects = result.getBoolean(3);
            }
        }
        Map<String, Long> unlocked = readValues(connection, playerId, "title_unlocks", "title_id", "acquired_at");
        Map<String, Long> progress = readValues(connection, playerId, "title_progress", "metric", "amount");
        Map<String, Set<String>> discoveries = new HashMap<>();
        try (var statement = connection.prepareStatement("SELECT metric, discovery_id FROM "
                + prefix + "title_discoveries WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                while (result.next()) discoveries.computeIfAbsent(result.getString(1), ignored -> new HashSet<>())
                        .add(result.getString(2));
            }
        }
        return new TitlePlayerState(unlocked, equipped, showTitle, showEffects, progress, discoveries);
    }
    private Map<String, Long> readValues(Connection connection, UUID id, String table, String key, String value)
            throws SQLException {
        Map<String, Long> values = new HashMap<>();
        try (var statement = connection.prepareStatement("SELECT " + key + ", " + value + " FROM "
                + prefix + table + " WHERE player_id = ?")) {
            statement.setString(1, id.toString());
            try (var result = statement.executeQuery()) {
                while (result.next()) values.put(result.getString(1), result.getLong(2));
            }
        }
        return values;
    }
    public TitlePlayerState loadOrImport(Connection connection, UUID id, Set<String> legacy, String equipped,
            long importedAt) throws SQLException {
        var existing = load(connection, id);
        if (existing != null) return existing; // Presence is the one-time import marker, even after revocation.
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix
                + "title_players (player_id, equipped_title) VALUES (?, ?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, "");
            statement.executeUpdate();
        }
        for (var title : legacy) {
            key(title);
            grant(connection, id, title, importedAt);
        }
        if (legacy.contains(equipped)) equip(connection, id, equipped);
        return load(connection, id);
    }
    public void grant(Connection connection, UUID id, String title, long acquiredAt) throws SQLException {
        key(title);
        var state = require(connection, id);
        if (state.unlocked().containsKey(title)) return;
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix
                + "title_unlocks (player_id, title_id, acquired_at) VALUES (?, ?, ?)")) {
            statement.setString(1, id.toString()); statement.setString(2, title); statement.setLong(3, acquiredAt);
            statement.executeUpdate();
        }
    }
    public void revoke(Connection connection, UUID id, String title) throws SQLException {
        key(title);
        var state = require(connection, id);
        if (state.equipped().equals(title)) equip(connection, id, "");
        try (var statement = connection.prepareStatement("DELETE FROM " + prefix
                + "title_unlocks WHERE player_id = ? AND title_id = ?")) {
            statement.setString(1, id.toString()); statement.setString(2, title); statement.executeUpdate();
        }
    }
    public void grantAll(Connection connection, UUID id, java.util.List<String> titles, long acquiredAt)
            throws SQLException {
        for (var title : titles) key(title);
        var state = require(connection, id);
        var seen = new HashSet<>(state.unlocked().keySet());
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix
                + "title_unlocks (player_id, title_id, acquired_at) VALUES (?, ?, ?)")) {
            for (var title : titles) {
                if (!seen.add(title)) continue;
                statement.setString(1, id.toString());
                statement.setString(2, title);
                statement.setLong(3, acquiredAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    public void equip(Connection connection, UUID id, String title) throws SQLException {
        if (!title.isEmpty()) key(title);
        var state = require(connection, id);
        if (!title.isEmpty() && !state.unlocked().containsKey(title)) throw new SQLException("Title is not unlocked");
        try (var statement = connection.prepareStatement("UPDATE " + prefix
                + "title_players SET equipped_title = ? WHERE player_id = ?")) {
            statement.setString(1, title); statement.setString(2, id.toString()); statement.executeUpdate();
        }
    }
    public void settings(Connection connection, UUID id, boolean showTitle, boolean showEffects) throws SQLException {
        require(connection, id);
        try (var statement = connection.prepareStatement("UPDATE " + prefix
                + "title_players SET show_title = ?, show_effects = ? WHERE player_id = ?")) {
            statement.setBoolean(1, showTitle); statement.setBoolean(2, showEffects);
            statement.setString(3, id.toString()); statement.executeUpdate();
        }
    }
    public void progress(Connection connection, UUID id, String metric, long amount) throws SQLException {
        key(metric);
        if (amount < 0) throw new IllegalArgumentException("Negative progress");
        var state = require(connection, id);
        boolean exists = state.progress().containsKey(metric);
        String sql = exists ? "UPDATE " + prefix + "title_progress SET amount = ? WHERE player_id = ? AND metric = ?"
                : "INSERT INTO " + prefix + "title_progress (amount, player_id, metric) VALUES (?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount); statement.setString(2, id.toString()); statement.setString(3, metric);
            statement.executeUpdate();
        }
    }
    public void discover(Connection connection, UUID id, String metric, String discovery) throws SQLException {
        key(metric);
        if (discovery == null || discovery.isBlank() || discovery.length() > 128)
            throw new IllegalArgumentException("Invalid discovery ID");
        var state = require(connection, id);
        if (state.discoveries().getOrDefault(metric, Set.of()).contains(discovery)) return;
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix
                + "title_discoveries (player_id, metric, discovery_id) VALUES (?, ?, ?)")) {
            statement.setString(1, id.toString()); statement.setString(2, metric); statement.setString(3, discovery);
            statement.executeUpdate();
        }
        progress(connection, id, metric, state.discoveries().getOrDefault(metric, Set.of()).size() + 1L);
    }
    private TitlePlayerState require(Connection connection, UUID id) throws SQLException {
        var state = load(connection, id);
        if (state == null) throw new SQLException("Player has not completed the title import yet");
        return state;
    }
    private static void key(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid title key");
    }
}
