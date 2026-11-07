package com.ambition.neoworld.dungeon;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.database.NeoWorldDatabase;
import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import com.ambition.neoworld.dungeon.stats.DungeonClear;
import com.ambition.neoworld.dungeon.stats.DungeonResultOutbox;
import com.ambition.neoworld.dungeon.stats.DungeonResultRepository;
import com.ambition.neoworld.dungeon.stats.DungeonResultWriter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.h2.jdbcx.JdbcDataSource;

public final class DungeonResultsRegressionTest {
    public static void run() throws Exception {
        UUID a = UUID.randomUUID(), dead = UUID.randomUUID(), left = UUID.randomUUID();
        var clear = DungeonClear.completed(UUID.randomUUID(), "slime", DungeonRoomType.TRIO,
                900, 2100, 1_700_000_000_000L, List.of(a, dead, left), Set.of(left));
        check(clear.clearTicks() == 1200 && clear.creditedPlayers().equals(Set.of(a, dead)),
                "Clock excludes preparation; dead spectators count, departed members do not");
        var outbox = new DungeonResultOutbox();
        outbox.add(clear); outbox.add(clear);
        var restored = DungeonResultOutbox.load(outbox.save(new CompoundTag(), null), null);
        check(restored.pending(32).equals(List.of(clear)), "Pending results survive world save and deduplicate");
        restored.acknowledge(clear.runId());
        check(restored.pending(32).isEmpty(), "Only acknowledged commits leave the retry queue");
        retryWriter(clear);
        partialBatch(clear);
        var malformed = outbox.save(new CompoundTag(), null);
        malformed.getList("runs", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("ticks");
        try {
            DungeonResultOutbox.load(malformed, null);
            throw new AssertionError("Corrupted pending duration became a zero-second record");
        } catch (IllegalArgumentException expected) { }
        try {
            DungeonClear.completed(UUID.randomUUID(), "slime", DungeonRoomType.SOLO,
                    100, 99, 1, List.of(a), Set.of());
            throw new AssertionError("Negative duration accepted");
        } catch (IllegalArgumentException expected) { }

        for (String mode : new String[]{"", ";MODE=MariaDB"}) {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:dungeon_" + UUID.randomUUID() + mode + ";DB_CLOSE_DELAY=-1");
            var engine = mode.isEmpty() ? Config.DatabaseType.H2 : Config.DatabaseType.MARIADB;
            NeoWorldDatabase.runMigrations(source, engine, "test_");
            int currentSchema;
            try (var c = source.getConnection(); var s = c.createStatement();
                    var r = s.executeQuery("SELECT MAX(version) FROM test_schema_history")) {
                r.next(); currentSchema = r.getInt(1);
            }
            try (var c = source.getConnection()) {
                c.setAutoCommit(false);
                check(DungeonResultRepository.record(c, "test_dungeon_runs", clear), "First result inserted");
                c.commit();
                check(!DungeonResultRepository.record(c, "test_dungeon_runs", clear), "Retry does not count twice");
                c.commit();
                check(DungeonResultRepository.stats(c, "test_dungeon_runs", dead, "slime", DungeonRoomType.TRIO)
                        .successfulRuns() == 1, "Dead spectator receives one clear");
                check(DungeonResultRepository.stats(c, "test_dungeon_runs", left, "slime", DungeonRoomType.TRIO)
                        .successfulRuns() == 0, "Departed member receives no clear");
                try {
                    DungeonResultRepository.record(c, "test_dungeon_runs", new DungeonClear(clear.runId(),
                            clear.dungeonId(), clear.mode(), clear.clearTicks() + 1, clear.completedAtMillis(), clear.members()));
                    throw new AssertionError("Conflicting run retry accepted");
                } catch (java.sql.SQLException expected) { c.rollback(); }
                UUID brokenRun = UUID.randomUUID();
                try (var s = c.createStatement()) {
                    s.execute("ALTER TABLE test_dungeon_runs_players ADD CONSTRAINT reject_test_member CHECK "
                            + "(run_id <> '" + brokenRun + "' OR player_id <> '" + dead + "')"); c.commit();
                }
                try {
                    DungeonResultRepository.record(c, "test_dungeon_runs", new DungeonClear(brokenRun,
                            clear.dungeonId(), clear.mode(), clear.clearTicks(), clear.completedAtMillis(), clear.members()));
                    throw new AssertionError("Failed participant insert accepted");
                } catch (java.sql.SQLException expected) { c.rollback(); }
                try (var s = c.createStatement(); var r = s.executeQuery(
                        "SELECT COUNT(*) FROM test_dungeon_runs WHERE run_id='" + brokenRun + "'")) {
                    r.next(); check(r.getInt(1) == 0, "Participant write failure rolls back run header too");
                }
                try (var s = c.createStatement()) { s.execute("ALTER TABLE test_dungeon_runs_players DROP CONSTRAINT reject_test_member"); c.commit(); }
                var faster = DungeonClear.completed(UUID.randomUUID(), "slime", DungeonRoomType.TRIO,
                        5000, 5600, 1_700_000_000_500L, List.of(a, dead), Set.of());
                DungeonResultRepository.record(c, "test_dungeon_runs", faster); c.rollback();
                check(DungeonResultRepository.stats(c, "test_dungeon_runs", a, "slime", DungeonRoomType.TRIO)
                        .successfulRuns() == 1, "Rollback preserves previous count");
                DungeonResultRepository.record(c, "test_dungeon_runs", faster); c.commit();
                var stats = DungeonResultRepository.stats(c, "test_dungeon_runs", a, "slime", DungeonRoomType.TRIO);
                check(stats.successfulRuns() == 2 && stats.bestTicks().orElseThrow() == 600,
                        "Count and best time derive from committed history");
                DungeonResultRepository.record(c, "test_dungeon_runs", DungeonClear.completed(UUID.randomUUID(),
                        "slime", DungeonRoomType.SOLO, 0, 20, 2000, List.of(a), Set.of()));
                DungeonResultRepository.record(c, "test_dungeon_runs", DungeonClear.completed(UUID.randomUUID(),
                        "mine", DungeonRoomType.TRIO, 0, 10, 3000, List.of(a), Set.of())); c.commit();
                check(DungeonResultRepository.stats(c, "test_dungeon_runs", a, "slime", DungeonRoomType.TRIO)
                        .equals(stats), "Other dungeons and modes cannot contaminate best time");
                check(DungeonResultRepository.history(c, "test_dungeon_runs", a, "slime", DungeonRoomType.TRIO, 1, 0)
                        .getFirst().runId().equals(faster.runId()), "History pages contain real clear duration and run ID");
                check(DungeonResultRepository.stats(c, "test_dungeon_runs", UUID.randomUUID(), "slime", DungeonRoomType.TRIO)
                        .bestTicks().isEmpty(), "No clears has absent best time, not an unbeatable zero");
                concurrentRetry(source, a);
                try (var s = c.createStatement()) {
                    s.execute("DROP TABLE test_dungeon_runs_players");
                    s.execute("DROP TABLE test_dungeon_runs");
                    s.execute("DELETE FROM test_schema_history WHERE version>=14"); c.commit();
                }
            }
            NeoWorldDatabase.runMigrations(source, engine, "test_");
            NeoWorldDatabase.runMigrations(source, engine, "test_");
            try (var c = source.getConnection(); var s = c.createStatement()) {
                try (var r = s.executeQuery("SELECT MAX(version) FROM test_schema_history")) {
                    r.next(); check(r.getInt(1) == currentSchema, "v13 upgrade and repeated migration reach current schema");
                }
                try (var r = s.executeQuery("SELECT COUNT(*) FROM test_pvp_ratings")) {
                    check(r.next(), "Existing unrelated feature tables survive upgrade");
                }
            }
        }
        try (var c = java.sql.DriverManager.getConnection("jdbc:h2:mem:dungeon_prefix_" + UUID.randomUUID())) {
            DungeonResultRepository.createSchema(c, "_NW_");
            check(DungeonResultRepository.stats(c, "_NW_dungeon_runs", a, "slime", DungeonRoomType.SOLO)
                    .successfulRuns() == 0, "All configured safe prefix formats are supported");
        }
        System.out.println("Dungeon completion history, credit, migration and outbox regression checks passed");
    }

    private static void concurrentRetry(JdbcDataSource source, UUID player) throws Exception {
        var clear = DungeonClear.completed(UUID.randomUUID(), "concurrent", DungeonRoomType.SOLO,
                0, 100, 4000, List.of(player), Set.of());
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) jobs.add(workers.submit(() -> {
                start.await();
                try (var c = source.getConnection()) {
                    c.setAutoCommit(false);
                    try { DungeonResultRepository.record(c, "test_dungeon_runs", clear); c.commit(); }
                    catch (java.sql.SQLException conflict) {
                        c.rollback();
                        DungeonResultRepository.record(c, "test_dungeon_runs", clear); c.commit();
                    }
                }
                return null;
            }));
            start.countDown();
            for (var job : jobs) job.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        try (var c = source.getConnection()) {
            check(DungeonResultRepository.stats(c, "test_dungeon_runs", player, "concurrent", DungeonRoomType.SOLO)
                    .successfulRuns() == 1, "Concurrent commit/replay creates only one credited run");
        }
    }

    private static void partialBatch(DungeonClear first) {
        var outbox = new DungeonResultOutbox();
        outbox.add(first);
        for (int i = 0; i < 32; i++) outbox.add(new DungeonClear(UUID.randomUUID(), first.dungeonId(),
                first.mode(), first.clearTicks(), first.completedAtMillis(), first.members()));
        var tasks = new java.util.ArrayList<Runnable>();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var writer = new DungeonResultWriter(outbox, tasks::add, clear -> {
            writes.incrementAndGet();
            if (clear.runId().equals(first.runId())) throw new java.sql.SQLException("Single result rejected");
        }, (id, error) -> {});
        writer.tick(0); tasks.removeFirst().run(); writer.tick(1);
        check(writes.get() == 32 && outbox.pending(100).size() == 2,
                "Batch is bounded to 32; one failure does not discard or block successful batch members");
        var recovered = DungeonResultOutbox.load(outbox.save(new CompoundTag(), null), null);
        var restarted = new DungeonResultWriter(recovered, tasks::add, clear -> {}, (id, error) -> {});
        restarted.tick(0); tasks.removeFirst().run(); restarted.stop(); restarted.tick(1);
        check(recovered.pending(32).isEmpty() && tasks.isEmpty(), "Restart replays pending records and stop acknowledges completed writes");
        writer.stop();
    }

    private static void retryWriter(DungeonClear clear) {
        var outbox = new DungeonResultOutbox();
        outbox.add(clear);
        var tasks = new java.util.ArrayList<Runnable>();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var errors = new java.util.ArrayList<UUID>();
        var writer = new DungeonResultWriter(outbox, tasks::add, result -> {
            if (attempts.getAndIncrement() == 0) throw new java.sql.SQLException("Temporary database outage");
        }, (id, error) -> errors.add(id));
        writer.tick(0);
        writer.tick(1);
        check(tasks.size() == 1 && attempts.get() == 0, "Only one async batch; no JDBC on server thread");
        tasks.removeFirst().run(); writer.tick(2);
        check(!outbox.pending(32).isEmpty() && errors.equals(List.of(clear.runId())),
                "Failed commit retains original immutable result and reports it");
        writer.tick(3);
        check(tasks.isEmpty(), "Database failure backs off rather than retrying every tick");
        writer.tick(602); tasks.removeFirst().run(); writer.tick(603);
        check(outbox.pending(32).isEmpty() && attempts.get() == 2, "Successful retry acknowledges original result once");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
