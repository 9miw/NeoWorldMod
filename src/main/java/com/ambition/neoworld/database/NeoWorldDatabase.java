package com.ambition.neoworld.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.NeoWorld;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.neoforged.fml.loading.FMLPaths;

/** Shared database stored outside individual world saves. */
public final class NeoWorldDatabase {
    private static final int LATEST_SCHEMA_VERSION = 7;

    private static HikariDataSource dataSource;
    private static Config.DatabaseType databaseType;

    private NeoWorldDatabase() {
    }

    public static synchronized void initialize() {
        if (dataSource != null) return;

        Config.DatabaseType selectedType = Config.DATABASE_TYPE.get();
        HikariDataSource newDataSource = null;
        try {
            HikariConfig poolConfig = createPoolConfig(selectedType);
            newDataSource = new HikariDataSource(poolConfig);
            runMigrations(newDataSource, selectedType, Config.DATABASE_TABLE_PREFIX.get());

            dataSource = newDataSource;
            databaseType = selectedType;
            NeoWorld.LOGGER.info("NeoWorld {} database is ready", selectedType);
        } catch (IOException | SQLException | RuntimeException exception) {
            if (newDataSource != null) newDataSource.close();
            throw new IllegalStateException("Could not initialize the NeoWorld " + selectedType + " database", exception);
        }
    }

    public static Connection getConnection() throws SQLException {
        HikariDataSource currentDataSource = dataSource;
        if (currentDataSource == null) throw new IllegalStateException("NeoWorld database is not initialized");
        return currentDataSource.getConnection();
    }

    public static <T> T inTransaction(SqlTransaction<T> transaction) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = transaction.execute(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) throw sqlException;
                throw new SQLException("NeoWorld database transaction failed", exception);
            }
        }
    }

    public static synchronized void shutdown() {
        HikariDataSource currentDataSource = dataSource;
        if (currentDataSource == null) return;

        if (databaseType == Config.DatabaseType.H2) {
            try (Connection connection = currentDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException exception) {
                NeoWorld.LOGGER.error("Could not shut down the NeoWorld H2 database cleanly", exception);
            }
        }

        currentDataSource.close();
        dataSource = null;
        databaseType = null;
    }

    private static HikariConfig createPoolConfig(Config.DatabaseType type) throws IOException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("NeoWorld-Database");
        config.setMaximumPoolSize(Config.DATABASE_POOL_SIZE.get());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(Duration.ofSeconds(Config.DATABASE_CONNECTION_TIMEOUT_SECONDS.get()).toMillis());

        if (type == Config.DatabaseType.H2) {
            Path databasePath = FMLPaths.CONFIGDIR.get().resolve(NeoWorld.MODID).resolve("neoworld");
            Files.createDirectories(databasePath.getParent());
            config.setDriverClassName("org.h2.Driver");
            config.setJdbcUrl("jdbc:h2:file:" + databasePath.toAbsolutePath().toString().replace('\\', '/')
                    + ";DB_CLOSE_ON_EXIT=FALSE");
            config.setUsername("sa");
            config.setPassword("");
        } else {
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setJdbcUrl(createMariaDbUrl());
            config.setUsername(Config.MARIADB_USERNAME.get());
            config.setPassword(Config.MARIADB_PASSWORD.get());
        }
        return config;
    }

    private static String createMariaDbUrl() {
        String sslMode = Config.MARIADB_USE_SSL.get() ? "verify-full" : "disable";
        return "jdbc:mariadb://" + Config.MARIADB_HOST.get() + ':' + Config.MARIADB_PORT.get()
                + '/' + Config.MARIADB_DATABASE.get()
                + "?sslMode=" + sslMode
                + "&characterEncoding=" + Config.MARIADB_CHARACTER_ENCODING.get();
    }

    static void runMigrations(DataSource source, Config.DatabaseType type, String tablePrefix) throws SQLException {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                createSchemaTable(connection, tablePrefix);
                int currentVersion = readSchemaVersion(connection, tablePrefix);
                if (currentVersion > LATEST_SCHEMA_VERSION) {
                    throw new SQLException("Database schema " + currentVersion
                            + " is newer than this mod supports (" + LATEST_SCHEMA_VERSION + ")");
                }
                for (int version = currentVersion + 1; version <= LATEST_SCHEMA_VERSION; version++) {
                    applyMigration(connection, type, tablePrefix, version);
                    recordMigration(connection, tablePrefix, version);
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private static void createSchemaTable(Connection connection, String tablePrefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %sschema_history (
                        version INTEGER PRIMARY KEY,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(tablePrefix));
        }
    }

    private static int readSchemaVersion(Connection connection, String tablePrefix) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COALESCE(MAX(version), 0) FROM " + tablePrefix + "schema_history")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void applyMigration(Connection connection, Config.DatabaseType type, String tablePrefix, int version)
            throws SQLException {
        switch (version) {
            case 1 -> {
                String valueType = type == Config.DatabaseType.H2 ? "CHARACTER LARGE OBJECT" : "LONGTEXT";
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS %smetadata (
                                metadata_key VARCHAR(191) PRIMARY KEY,
                                metadata_value %s NOT NULL,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            )
                            """.formatted(tablePrefix, valueType));
                }
            }
            case 2 -> createMarketSchema(connection, type, tablePrefix);
            case 3 -> addMarketQueryColumns(connection, tablePrefix);
            case 4 -> createTitleSchema(connection, tablePrefix);
            case 5 -> addMarketSaleClaimColumns(connection, type, tablePrefix);
            case 6 -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "pvp_ratings (player_id VARCHAR(36) PRIMARY KEY, player_name VARCHAR(64) NOT NULL, rating INTEGER NOT NULL, wins INTEGER NOT NULL, losses INTEGER NOT NULL)");
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "pvp_results (match_id VARCHAR(36) PRIMARY KEY, player_a VARCHAR(36) NOT NULL, player_b VARCHAR(36) NOT NULL, score_a DOUBLE NOT NULL, reason VARCHAR(128) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
                }
            }
            case 7 -> {
                try(Statement statement=connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "pvp_results_details (match_id VARCHAR(36) PRIMARY KEY, before_a INTEGER NOT NULL, after_a INTEGER NOT NULL, before_b INTEGER NOT NULL, after_b INTEGER NOT NULL)");
                }
            }
            default -> throw new SQLException("Unknown NeoWorld database migration: " + version);
        }
    }

    private static void createMarketSchema(Connection connection, Config.DatabaseType type, String prefix)
            throws SQLException {
        String identity = type == Config.DatabaseType.H2
                ? "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"
                : "BIGINT AUTO_INCREMENT PRIMARY KEY";
        String blob = type == Config.DatabaseType.H2 ? "BINARY LARGE OBJECT" : "LONGBLOB";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(("""
                    CREATE TABLE IF NOT EXISTS %smarket_listings (
                        id %s,
                        seller_id VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(64) NOT NULL,
                        item_nbt %s NOT NULL,
                        price_silver BIGINT NOT NULL,
                        created_game_time BIGINT NOT NULL,
                        expires_game_time BIGINT NOT NULL
                    )
                    """).formatted(prefix, identity, blob));
            statement.executeUpdate(("""
                    CREATE TABLE IF NOT EXISTS %smarket_pending_items (
                        id %s,
                        player_id VARCHAR(36) NOT NULL,
                        item_nbt %s NOT NULL
                    )
                    """).formatted(prefix, identity, blob));
            statement.executeUpdate(("""
                    CREATE TABLE IF NOT EXISTS %smarket_pending_balances (
                        player_id VARCHAR(36) PRIMARY KEY,
                        silver BIGINT NOT NULL
                    )
                    """).formatted(prefix));
            statement.executeUpdate(("""
                    CREATE TABLE IF NOT EXISTS %smarket_transactions (
                        id %s,
                        transaction_type VARCHAR(16) NOT NULL,
                        listing_id BIGINT NOT NULL,
                        seller_id VARCHAR(36) NOT NULL,
                        buyer_id VARCHAR(36),
                        price_silver BIGINT NOT NULL,
                        tax_silver BIGINT NOT NULL,
                        game_time BIGINT NOT NULL
                    )
                    """).formatted(prefix, identity));
            statement.executeUpdate(("""
                    CREATE TABLE IF NOT EXISTS %smarket_settings (
                        setting_key VARCHAR(64) PRIMARY KEY,
                        setting_value BIGINT NOT NULL
                    )
                    """).formatted(prefix));
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix
                    + "idx_market_listing_expiry ON " + prefix + "market_listings (expires_game_time)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix
                    + "idx_market_pending_owner ON " + prefix + "market_pending_items (player_id)");
        }
    }

    private static void addMarketQueryColumns(Connection connection, String prefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_listings ADD COLUMN IF NOT EXISTS item_id VARCHAR(191) NOT NULL DEFAULT ''");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_listings ADD COLUMN IF NOT EXISTS item_name VARCHAR(255) NOT NULL DEFAULT ''");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_listings ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'MATERIALS'");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix
                    + "idx_market_category_price ON " + prefix + "market_listings (category, price_silver, id)");
        }
    }

    private static void addMarketSaleClaimColumns(Connection connection, Config.DatabaseType type, String prefix)
            throws SQLException {
        String blob = type == Config.DatabaseType.H2 ? "BINARY LARGE OBJECT" : "LONGBLOB";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS item_nbt " + blob);
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS quantity INTEGER NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS unit_price_silver BIGINT NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS proceeds_silver BIGINT NOT NULL DEFAULT 0");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS claimed BOOLEAN NOT NULL DEFAULT TRUE");
            statement.executeUpdate("ALTER TABLE " + prefix
                    + "market_transactions ADD COLUMN IF NOT EXISTS claimed_game_time BIGINT NOT NULL DEFAULT 0");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS " + prefix
                    + "idx_market_sales_owner ON " + prefix
                    + "market_transactions (seller_id, transaction_type, claimed, game_time)");
        }
    }

    public static String tableName(String suffix) {
        if (suffix == null || !suffix.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Unsafe NeoWorld table suffix");
        }
        return Config.DATABASE_TABLE_PREFIX.get() + suffix;
    }

    private static void createTitleSchema(Connection connection, String prefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %stitle_players (
                        player_id VARCHAR(36) PRIMARY KEY,
                        equipped_title VARCHAR(64) NOT NULL DEFAULT '',
                        show_title BOOLEAN NOT NULL DEFAULT TRUE,
                        show_effects BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """.formatted(prefix));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %stitle_unlocks (
                        player_id VARCHAR(36) NOT NULL,
                        title_id VARCHAR(64) NOT NULL,
                        acquired_at BIGINT NOT NULL,
                        PRIMARY KEY (player_id, title_id),
                        FOREIGN KEY (player_id) REFERENCES %stitle_players(player_id)
                    )
                    """.formatted(prefix, prefix));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %stitle_progress (
                        player_id VARCHAR(36) NOT NULL,
                        metric VARCHAR(64) NOT NULL,
                        amount BIGINT NOT NULL,
                        PRIMARY KEY (player_id, metric),
                        FOREIGN KEY (player_id) REFERENCES %stitle_players(player_id)
                    )
                    """.formatted(prefix, prefix));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %stitle_discoveries (
                        player_id VARCHAR(36) NOT NULL,
                        metric VARCHAR(64) NOT NULL,
                        discovery_id VARCHAR(128) NOT NULL,
                        PRIMARY KEY (player_id, metric, discovery_id),
                        FOREIGN KEY (player_id) REFERENCES %stitle_players(player_id)
                    )
                    """.formatted(prefix, prefix));
        }
    }

    private static void recordMigration(Connection connection, String tablePrefix, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + tablePrefix + "schema_history (version) VALUES (?)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T execute(Connection connection) throws Exception;
    }
}
