package com.ambition.neoworld.title;

import com.ambition.neoworld.title.model.TitleDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Catalogue SQL only. The caller owns the transaction and publishes after commit. */
public final class JdbcTitleCatalogueRepository {
    private static final String IMPORT_KEY = "title_catalogue_imported_v1";
    private final String prefix;

    public JdbcTitleCatalogueRepository(String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z0-9_]{0,32}"))
            throw new IllegalArgumentException("Unsafe table prefix");
        this.prefix = prefix;
    }

    public static void createSchema(Connection connection, String prefix) throws SQLException {
        new JdbcTitleCatalogueRepository(prefix); // Validate identifiers even outside configured startup.
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %stitle_catalogue (
                        title_id VARCHAR(64) PRIMARY KEY,
                        title_name VARCHAR(64) NOT NULL,
                        display_text VARCHAR(64) NOT NULL,
                        description VARCHAR(512) NOT NULL,
                        category VARCHAR(32) NOT NULL,
                        rarity VARCHAR(32) NOT NULL,
                        requirement_text VARCHAR(512) NOT NULL,
                        color VARCHAR(7) NOT NULL,
                        effect VARCHAR(64) NOT NULL,
                        metric VARCHAR(64) NOT NULL,
                        target BIGINT NOT NULL,
                        sort_order INTEGER NOT NULL
                    )
                    """.formatted(prefix));
        }
    }

    /** Must run in a transaction; catalogue and marker commit together. */
    public List<TitleDefinition> initialize(Connection connection, Path legacyFile) throws SQLException, IOException {
        if (connection.getAutoCommit()) throw new SQLException("Catalogue import requires a transaction");
        try (var statement = connection.prepareStatement("SELECT metadata_value FROM " + prefix + "metadata WHERE metadata_key=?")) {
            statement.setString(1, IMPORT_KEY);
            try (var result = statement.executeQuery()) {
                if (result.next()) return load(connection);
            }
        }
        if (!load(connection).isEmpty()) throw new SQLException("Catalogue has rows but no import marker; refusing to overwrite");
        var titles = TitleRegistry.readCatalogue(legacyFile);
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix + "title_catalogue"
                + " (title_id,title_name,display_text,description,category,rarity,requirement_text,color,effect,metric,target,sort_order)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int order = 0;
            for (var title : titles) {
                statement.setString(1, title.id()); statement.setString(2, title.name());
                statement.setString(3, title.display()); statement.setString(4, title.description());
                statement.setString(5, title.category().name()); statement.setString(6, title.rarity().name());
                statement.setString(7, title.requirement()); statement.setString(8, title.color());
                statement.setString(9, title.effect()); statement.setString(10, title.condition().metric());
                statement.setLong(11, title.condition().target()); statement.setInt(12, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.prepareStatement("INSERT INTO " + prefix + "metadata (metadata_key,metadata_value) VALUES (?,?)")) {
            statement.setString(1, IMPORT_KEY); statement.setString(2, "1"); statement.executeUpdate();
        }
        return load(connection);
    }

    public List<TitleDefinition> load(Connection connection) throws SQLException {
        var titles = new ArrayList<TitleDefinition>();
        try (var statement = connection.prepareStatement("SELECT title_id,title_name,display_text,description,category,rarity,"
                + "requirement_text,color,effect,metric,target FROM " + prefix + "title_catalogue ORDER BY sort_order,title_id");
             var result = statement.executeQuery()) {
            while (result.next()) {
                if (titles.size() >= 1024) throw new SQLException("Title catalogue exceeds 1024 entries");
                titles.add(new TitleDefinition(result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), TitleDefinition.Category.valueOf(result.getString(5)),
                        TitleDefinition.Rarity.valueOf(result.getString(6)), result.getString(7), result.getString(8),
                        result.getString(9), new TitleDefinition.Condition(result.getString(10), result.getLong(11))));
            }
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid title catalogue in database", exception);
        }
        return List.copyOf(titles);
    }
}
