package com.ambition.neoworld.database;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.title.JdbcTitleCatalogueRepository;
import com.ambition.neoworld.title.JdbcTitleRepository;
import com.ambition.neoworld.title.TitleRegistry;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;

final class TitleCatalogueRegressionTest {
    static int run() throws Exception {
        int checks = 0;
        for (boolean maria : new boolean[]{false, true}) {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:title_catalogue_" + maria + ";DB_CLOSE_DELAY=-1" + (maria ? ";MODE=MariaDB" : ""));
            NeoWorldDatabase.runMigrations(source, maria ? Config.DatabaseType.MARIADB : Config.DatabaseType.H2, "cat_");
            var repository = new JdbcTitleCatalogueRepository("cat_");
            var directory = Files.createTempDirectory("title-catalogue-");
            var file = directory.resolve("titles.json");
            try (var c = source.getConnection()) {
                c.setAutoCommit(false);
                var seeded = repository.initialize(c, file); c.commit();
                check(seeded.size() == 12 && !Files.exists(file), "fresh seed uses defaults without creating legacy JSON"); checks++;
                check(seeded.equals(TitleRegistry.readCatalogue(file)), "all fields and original catalogue ordering survive SQL round trip"); checks++;
                var player = UUID.randomUUID();
                var players = new JdbcTitleRepository("cat_");
                players.loadOrImport(c, player, Set.of("traveler"), "traveler", 42L); c.commit();
                try (var s = c.createStatement()) { s.executeUpdate("UPDATE cat_title_catalogue SET display_text='✦ ใหม่ ✦', color='#112233' WHERE title_id='traveler'"); }
                c.commit();
                Files.writeString(file, "broken JSON");
                var reloaded = repository.initialize(c, file); c.commit();
                check(reloaded.getFirst().display().equals("✦ ใหม่ ✦") && reloaded.getFirst().rgb() == 0x112233,
                        "restart reads database and ignores corrupt legacy JSON"); checks++;
                check(players.load(c, player).equipped().equals("traveler") && players.load(c, player).unlocked().get("traveler") == 42L,
                        "catalogue migration preserves ownership acquisition date and equipped ID"); checks++;
                try (var s = c.createStatement()) { s.executeUpdate("DELETE FROM cat_title_catalogue"); } c.commit();
                check(repository.initialize(c, file).isEmpty(), "empty initialized catalogue is never reseeded"); checks++;
                try (var s = c.createStatement()) { s.executeUpdate("DELETE FROM cat_metadata WHERE metadata_key='title_catalogue_imported_v1'"); } c.commit();
                boolean invalid = false;
                try { repository.initialize(c, file); } catch (Exception expected) { invalid = true; c.rollback(); }
                check(invalid && repository.load(c).isEmpty(), "invalid initial JSON fails without partial rows"); checks++;
                String json = "[{\"id\":\"custom\",\"name\":\"ชื่อจริง\",\"display\":\"✦ แสดง ✦\",\"description\":\"คำอธิบาย\",\"category\":\"HUMOROUS\",\"rarity\":\"RARE\",\"requirement\":\"เงื่อนไข\",\"color\":\"#112233\",\"effect\":\"none\",\"condition\":{\"metric\":\"manual\",\"target\":0}}]";
                Files.writeString(file, json);
                repository.initialize(c, file); c.rollback();
                check(repository.load(c).isEmpty(), "rollback removes catalogue import rows and marker"); checks++;
                var imported = repository.initialize(c, file); c.commit();
                check(imported.size() == 1 && imported.getFirst().id().equals("custom") && imported.getFirst().display().equals("✦ แสดง ✦")
                        && Files.readString(file).equals(json), "custom legacy catalogue imported intact and file preserved"); checks++;
                check(imported.equals(TitleRegistry.readCatalogue(file)), "custom fields including category rarity condition and effects preserved"); checks++;
                TitleRegistry.replace(imported);
                try (var s = c.createStatement()) { s.executeUpdate("UPDATE cat_title_catalogue SET category='INVALID'"); } c.commit();
                boolean rejected = false;
                try { TitleRegistry.replace(repository.load(c)); } catch (SQLException expected) { rejected = true; }
                check(rejected && TitleRegistry.find("custom").isPresent(), "invalid SQL catalogue preserves active registry"); checks++;
            } finally { Files.deleteIfExists(file); Files.delete(directory); }
        }
        upgrade();
        return checks + 2;
    }
    private static void upgrade() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:catalogue_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "old_");
        var player = UUID.randomUUID();
        var players = new JdbcTitleRepository("old_");
        try (var c = source.getConnection(); var s = c.createStatement()) {
            c.setAutoCommit(false);
            players.loadOrImport(c, player, Set.of("traveler"), "traveler", 7L); c.commit();
            s.executeUpdate("DROP TABLE old_title_catalogue");
            s.executeUpdate("DELETE FROM old_schema_history WHERE version>=11"); c.commit();
        }
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "old_");
        try (var c = source.getConnection(); var s = c.createStatement()) {
            check(players.load(c, player).unlocked().get("traveler") == 7L, "v10 upgrade preserves old player data");
            s.executeUpdate("DELETE FROM old_schema_history WHERE version>=11");
        }
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "old_");
        try (var c = source.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT MAX(version) FROM old_schema_history")) {
            r.next(); check(r.getInt(1) == 17, "interrupted catalogue DDL safely retries through v17");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
