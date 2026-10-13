package com.ambition.neoworld.database;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.title.JdbcTitleRepository;
import com.ambition.neoworld.title.TitleRegistry;
import com.ambition.neoworld.title.TitlePlayerState;
import org.h2.jdbcx.JdbcDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public final class TitleStorageRegressionTest {
    private static int checks;
    private TitleStorageRegressionTest() {}
    public static int run() throws Exception {
        checks = 0;
        catalogue();
        guiPages();
        payload();
        storage(false);
        storage(true);
        upgrade();
        return checks;
    }
    private static void guiPages() {
        var state = new TitlePlayerState(java.util.Map.of("traveler", 123L), "traveler", true, false,
                java.util.Map.of("ores_mined", 320L), java.util.Map.of());
        var id = UUID.randomUUID();
        var all = new com.ambition.neoworld.network.title.TitleGuiRequestPayload(id,
                com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.QUERY, "", 0, "", 0, true, false);
        var page = com.ambition.neoworld.network.title.TitleGuiPages.build(all, state, TitleRegistry.all());
        check(page.entries().size() == 5 && page.totalCount() == 12 && page.filteredCount() == 12
                && page.unlockedCount() == 1 && page.equipped().equals("traveler") && !page.showEffects(),
                "GUI page preserves server collection and settings");
        var miner = page.entries().stream().filter(e -> e.id().equals("novice_miner")).findFirst().orElseThrow();
        check(!miner.unlocked() && miner.progress() == 320 && miner.target() == 500, "GUI displays stored progress without granting titles");
        for (int filter = 1; filter <= 3; filter++) {
            var request = new com.ambition.neoworld.network.title.TitleGuiRequestPayload(id,
                    com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.QUERY, "", filter, "", 0, true, false);
            var filtered = com.ambition.neoworld.network.title.TitleGuiPages.build(request, state, TitleRegistry.all());
            check(filtered.filteredCount() == 4 && filtered.entries().size() == 4, "server filters general rare humorous categories");
        }
        var request = new com.ambition.neoworld.network.title.TitleGuiRequestPayload(id,
                com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.QUERY, "", 0, "นักขุด", 999, true, false);
        var searched = com.ambition.neoworld.network.title.TitleGuiPages.build(request, state, TitleRegistry.all());
        check(searched.page() == 0 && searched.filteredCount() == 1 && searched.entries().getFirst().id().equals("novice_miner"),
                "Thai search and out-of-range pagination handled on server");
        var absent = new com.ambition.neoworld.network.title.TitleGuiRequestPayload(id,
                com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.QUERY, "", 0, "no_such_title", 0, true, false);
        check(com.ambition.neoworld.network.title.TitleGuiPages.build(absent, state, TitleRegistry.all()).entries().isEmpty(),
                "empty search returns an empty page");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.ambition.neoworld.network.title.TitleGuiRequestPayload.STREAM_CODEC.encode(buffer, request);
            check(com.ambition.neoworld.network.title.TitleGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(request),
                    "GUI request round trip");
            buffer.clear();
            com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC.encode(buffer, page);
            check(com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC.decode(buffer).equals(page),
                    "GUI page round trip including Thai metadata and ownership");
            buffer.clear(); buffer.writeUUID(id); buffer.writeUtf(""); buffer.writeVarInt(6);
            boolean oversized = false;
            try { com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC.decode(buffer); }
            catch (IllegalArgumentException expected) { oversized = true; }
            check(oversized, "oversized wire page rejected before allocation");
        } finally { buffer.release(); }
        boolean invalid = false;
        try { new com.ambition.neoworld.network.title.TitleGuiRequestPayload(id,
                com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.EQUIP, "invalid id", 4, "", -1, true, false); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "invalid GUI request fields rejected");
    }
    private static void payload() {
        var id = UUID.randomUUID();
        var packet = new com.ambition.neoworld.network.TitleStatePayload(id, "traveler", "นักเดินทาง", 0x112233);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.ambition.neoworld.network.TitleStatePayload.STREAM_CODEC.encode(buffer, packet);
            check(com.ambition.neoworld.network.TitleStatePayload.STREAM_CODEC.decode(buffer).equals(packet),
                    "title payload round trip preserves UUID Thai name and custom color");
        } finally { buffer.release(); }
        com.ambition.neoworld.client.ClientTitleData.update(packet);
        var updated = new com.ambition.neoworld.network.TitleStatePayload(id, "traveler", "ชื่อใหม่", 0x445566);
        com.ambition.neoworld.client.ClientTitleData.update(updated);
        check(com.ambition.neoworld.client.ClientTitleData.title(id).orElseThrow().equals(updated),
                "client uses server name and color without local catalogue");
        com.ambition.neoworld.client.ClientTitleData.update(com.ambition.neoworld.network.TitleStatePayload.empty(id));
        check(com.ambition.neoworld.client.ClientTitleData.title(id).isEmpty(), "empty payload removes stale title");
        com.ambition.neoworld.client.ClientTitleData.update(packet);
        com.ambition.neoworld.client.ClientTitleData.reset();
        check(com.ambition.neoworld.client.ClientTitleData.title(id).isEmpty(), "disconnect clears client title cache");
        boolean rejected = false;
        try { new com.ambition.neoworld.network.TitleStatePayload(id, "traveler", "x".repeat(65), 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "oversized network title rejected");
    }
    private static void catalogue() throws Exception {
        Path directory = Files.createTempDirectory("neoworld-title-test-");
        Path file = directory.resolve("titles.json");
        try {
            TitleRegistry.load(file);
            check(Files.exists(file) && TitleRegistry.all().size() == 12, "default JSON created with 12 titles");
            String original = Files.readString(file);
            Files.writeString(file, original.replaceAll(",\"display\":\"[^\"]*\"", ""));
            TitleRegistry.load(file);
            check(TitleRegistry.all().stream().allMatch(t -> t.display().equals(t.name())),
                    "legacy catalogue without display falls back to name");
            Files.writeString(file, original.replace("นักเดินทาง", "นักเดินทางทดสอบ").replace("#73D8CD", "#112233"));
            TitleRegistry.load(file);
            check(TitleRegistry.find("traveler").orElseThrow().name().equals("นักเดินทางทดสอบ")
                    && TitleRegistry.find("traveler").orElseThrow().rgb() == 0x112233, "name/color reload uses same stable ID");
            Files.writeString(file, original.replace("\"display\":\"นักเดินทาง\"", "\"display\":\"✦ ผู้เดินทาง ✦\"")
                    .replace("#73D8CD", "#112233"));
            TitleRegistry.load(file);
            var title = TitleRegistry.find("traveler").orElseThrow();
            check(title.name().equals("นักเดินทาง") && title.display().equals("✦ ผู้เดินทาง ✦"),
                    "display reload changes display independently from name and ID");
            var request = new com.ambition.neoworld.network.title.TitleGuiRequestPayload(UUID.randomUUID(),
                    com.ambition.neoworld.network.title.TitleGuiRequestPayload.Action.QUERY, "", 0, "traveler", 0, true, false);
            var state = new TitlePlayerState(java.util.Map.of("traveler", 123L), "traveler", true, false,
                    java.util.Map.of(), java.util.Map.of());
            var customPage = com.ambition.neoworld.network.title.TitleGuiPages.build(request, state, TitleRegistry.all());
            check(customPage.entries().getFirst().name().equals(title.name())
                    && customPage.entries().getFirst().display().equals(title.display()), "GUI retains distinct name and preview display");
            var displayBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                    net.minecraft.core.RegistryAccess.EMPTY);
            try {
                com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC.encode(displayBuffer, customPage);
                check(com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC.decode(displayBuffer).equals(customPage),
                        "GUI codec round trip preserves distinct decorated display");
            } finally { displayBuffer.release(); }
            for (String invalid : new String[]{"{", "null", "[null]", original.replace("novice_miner", "traveler"),
                    original.replace("#73D8CD", "bad"), original.replace("GENERAL", "INVALID"),
                    original.replace("\"target\":5", "\"target\":0"),
                    original.replace("\"display\":\"นักเดินทาง\"", "\"display\":\"\""),
                    original.replace("\"display\":\"นักเดินทาง\"", "\"display\":\"" + "x".repeat(65) + "\""),
                    original.replace("\"display\":\"นักเดินทาง\"", "\"display\":\"bad\\nline\"")}) {
                Files.writeString(file, invalid);
                boolean rejected = false;
                try { TitleRegistry.load(file); } catch (java.io.IOException expected) { rejected = true; }
                check(rejected && TitleRegistry.find("traveler").orElseThrow().rgb() == 0x112233,
                        "invalid reload preserves previous catalogue");
            }
            Files.writeString(file, original);
            TitleRegistry.load(file);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
    private static void storage(boolean mariaMode) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:title_storage_" + mariaMode + ";DB_CLOSE_DELAY=-1" + (mariaMode ? ";MODE=MariaDB" : ""));
        NeoWorldDatabase.runMigrations(source, mariaMode ? Config.DatabaseType.MARIADB : Config.DatabaseType.H2, "titles_");
        var repository = new JdbcTitleRepository("titles_");
        var id = UUID.randomUUID();
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            var imported = repository.loadOrImport(connection, id, Set.of("traveler", "unknown_legacy"), "traveler", 123L);
            connection.commit();
            check(imported.unlocked().get("traveler") == 123L && imported.unlocked().containsKey("unknown_legacy")
                    && imported.equipped().equals("traveler"), "legacy import keeps unknown IDs and equipped title");
            repository.grant(connection, id, "traveler", 999L);
            connection.commit();
            check(repository.load(connection, id).unlocked().get("traveler") == 123L, "duplicate grant preserves acquisition date");
            repository.revoke(connection, id, "traveler");
            connection.commit();
            check(repository.load(connection, id).equipped().isEmpty(), "revoking equipped title clears equip atomically");
            var again = repository.loadOrImport(connection, id, Set.of("traveler", "old_title"), "traveler", 555L);
            check(!again.unlocked().containsKey("traveler") && !again.unlocked().containsKey("old_title"),
                    "reconnect cannot restore revoked title from old Attachment");
            boolean rejected = false;
            try { repository.equip(connection, id, "traveler"); } catch (SQLException expected) { rejected = true; }
            connection.rollback();
            check(rejected && repository.load(connection, id).equipped().isEmpty(), "reject locked equip");
            repository.grant(connection, id, "novice_miner", 321L);
            repository.equip(connection, id, "novice_miner");
            repository.settings(connection, id, false, false);
            repository.progress(connection, id, "ores_mined", 320L);
            repository.discover(connection, id, "explored_areas", "neoworld:forest");
            repository.discover(connection, id, "explored_areas", "neoworld:forest");
            repository.discover(connection, id, "explored_areas", "neoworld:desert");
            connection.commit();
            var saved = repository.load(connection, id);
            check(!saved.showTitle() && !saved.showEffects() && saved.progress().get("ores_mined") == 320L,
                    "settings and progress persist");
            check(saved.progress().get("explored_areas") == 2L && saved.discoveries().get("explored_areas").size() == 2,
                    "discovery IDs prevent duplicate progress");
            var allTitles = TitleRegistry.all().stream().map(title -> title.id()).toList();
            repository.grantAll(connection, id, allTitles, 777L);
            var allUnlocked = repository.load(connection, id);
            check(allUnlocked.unlocked().keySet().containsAll(allTitles)
                    && allUnlocked.equipped().equals(saved.equipped())
                    && allUnlocked.unlocked().get("novice_miner") == 321L
                    && allUnlocked.progress().equals(saved.progress()) && !allUnlocked.showTitle(),
                    "unlock all preserves equipped title dates progress and settings");
            repository.grantAll(connection, id, allTitles, 888L);
            check(repository.load(connection, id).equals(allUnlocked), "unlock all is idempotent");
            connection.rollback();
            check(repository.load(connection, id).equals(saved), "unlock all rollback leaves no partial grants");
            boolean invalidBatch = false;
            try { repository.grantAll(connection, id, java.util.List.of("traveler", "invalid id"), 999L); }
            catch (IllegalArgumentException expected) { invalidBatch = true; }
            check(invalidBatch && repository.load(connection, id).equals(saved), "invalid bulk grant rejected before writing");
            repository.grantAll(connection, id, allTitles, 777L);
            connection.commit();
            check(repository.load(connection, id).equals(allUnlocked), "unlock all persists after commit");
            saved = allUnlocked;
            repository.revoke(connection, id, "novice_miner");
            repository.progress(connection, id, "ores_mined", 999L);
            connection.rollback();
            check(repository.load(connection, id).equals(saved), "rollback restores unlocked/equipped/progress together");
            boolean negative = false;
            try { repository.progress(connection, id, "ores_mined", -1); }
            catch (IllegalArgumentException expected) { negative = true; }
            check(negative && repository.load(connection, id).progress().get("ores_mined") == 320L, "negative progress rejected");
            connection.commit();
        }
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            var reloaded = new JdbcTitleRepository("titles_").load(connection, id);
            check(reloaded.equipped().equals("novice_miner") && reloaded.unlocked().get("novice_miner") == 321L,
                    "new repository and connection read committed state");
            repository.equip(connection, id, "traveler");
            connection.commit();
            var changed = repository.load(connection, id);
            check(changed.equipped().equals("traveler") && changed.unlocked().equals(reloaded.unlocked())
                    && changed.progress().equals(reloaded.progress()), "player changes title without changing collection or progress");
            repository.equip(connection, id, "");
            connection.commit();
            var cleared = repository.load(connection, id);
            check(cleared.equipped().isEmpty() && cleared.unlocked().equals(reloaded.unlocked())
                    && cleared.showTitle() == reloaded.showTitle(), "player clears equipped title without revoking titles or settings");
            boolean missing = false;
            try { repository.grant(connection, UUID.randomUUID(), "traveler", 1L); }
            catch (SQLException expected) { missing = true; }
            check(missing, "offline changes require completed initial import");
            connection.rollback();
        }
        boolean unsafePrefix = false;
        try { new JdbcTitleRepository("titles_; DROP TABLE x"); } catch (IllegalArgumentException expected) { unsafePrefix = true; }
        check(unsafePrefix, "unsafe table prefix rejected");
    }
    private static void upgrade() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:title_upgrade;DB_CLOSE_DELAY=-1");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "upgrade_");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO upgrade_metadata(metadata_key, metadata_value) VALUES ('existing', 'keep')");
            statement.executeUpdate("INSERT INTO upgrade_market_pending_balances(player_id, silver) VALUES ('old-player', 500)");
            statement.executeUpdate("DROP TABLE upgrade_title_discoveries");
            statement.executeUpdate("DROP TABLE upgrade_title_progress");
            statement.executeUpdate("DROP TABLE upgrade_title_unlocks");
            statement.executeUpdate("DROP TABLE upgrade_title_players");
            statement.executeUpdate("DELETE FROM upgrade_schema_history WHERE version IN (4, 5, 6, 7)");
            // Emulate interrupted DDL with one pre-existing new table and schema still at v3.
            statement.executeUpdate("CREATE TABLE upgrade_title_players(player_id VARCHAR(36) PRIMARY KEY,"
                    + " equipped_title VARCHAR(64) NOT NULL DEFAULT '', show_title BOOLEAN NOT NULL DEFAULT TRUE,"
                    + " show_effects BOOLEAN NOT NULL DEFAULT TRUE)");
        }
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "upgrade_");
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "upgrade_");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT MAX(version) FROM upgrade_schema_history")) {
                result.next(); check(result.getInt(1) == 7, "v3 upgrade and interrupted migration retry reach v7");
            }
            try (var result = statement.executeQuery("SELECT metadata_value FROM upgrade_metadata WHERE metadata_key = 'existing'")) {
                result.next(); check(result.getString(1).equals("keep"), "upgrade preserves old metadata");
            }
            try (var result = statement.executeQuery("SELECT silver FROM upgrade_market_pending_balances WHERE player_id = 'old-player'")) {
                result.next(); check(result.getLong(1) == 500L, "upgrade preserves market balances");
            }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
