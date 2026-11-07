# Dungeon Completion History

## Statistic Definitions

- Only boss victory creates a completion. Failure, timeout, voluntary exit and cleanup do not.
- Timing starts after successful structure placement and all entry teleports, when the run becomes ACTIVE. It includes the initial three-second wait and waits between waves/boss.
- Timing stops when the server detects all boss-phase enemies dead, before reward generation or returning spectators. Reward collection and exit time are excluded.
- `clearTicks` is elapsed simulation time: divide by `20.0` for seconds. Low TPS makes real wall-clock duration longer; it does not change this statistic.
- `completedAtMillis` is a UTC Unix timestamp in milliseconds, for history ordering, not the duration clock.
- The starting roster is retained. A player is credited exactly when absent from the run's `returned` set at victory. Dead spectators count; players already returned do not. Disconnect alone does not mark a participant returned in the existing Dungeon runtime, so an unreturned disconnected participant still receives credit if the remaining team wins.
- Each credited player receives the same team clear duration. Statistics are separated by dungeon ID and room mode (`SOLO`, `TRIO`, `RAID`), not by number of surviving players.
- No historical clears are fabricated/backfilled. No EXP, money, rewards, ranking formula or ranking UI is changed.

## Storage And Delivery

Migration **14** adds the tables below, using the configured prefix. Later migrations from other systems are preserved; this change does not reset the database schema to 14.

- `<prefix>dungeon_runs`: `run_id` UUID primary key, `dungeon_id` (up to 191 characters), `room_mode`, `clear_ticks`, `completed_at_ms`.
- `<prefix>dungeon_runs_players`: `run_id`, `player_id` UUID, `credited`; primary key `(run_id, player_id)`, foreign key to the run.

Run and roster commit together through `NeoWorldDatabase.inTransaction`. An identical replay is a no-op; conflicting data with an existing run ID is rejected, not overwritten. Concurrent insert conflicts are rolled back and safely replayed. Count and best time derive from committed history, so no separate counter can drift or double-increment.

`DungeonResults.completed(server, clear)` snapshots the already-completed result into the Overworld SavedData outbox `data/neoworld_dungeon_results_pending.dat`. A server-owned writer submits at most 32 immutable records in one asynchronous batch. Successful records are acknowledged on the server thread; failed records remain pending and retry after 600 ticks. A failed member of a batch does not discard other records. Startup loads pending records automatically.

The writer waits up to five seconds for its existing batch at server stop, before normal database shutdown. Unacknowledged rows remain replayable; a late successful database commit cannot count twice.

Outbox persistence follows normal world saving. This is not a synchronous crash-proof disk journal: a hard crash before either database commit or world save can lose a just-completed result. Successful gameplay does not wait for database completion, so freshly completed runs may take time to appear in reads. Back up both the shared database and world pending data together.

## API For Ranking Integration

Package: `com.ambition.neoworld.dungeon.stats`.

```java
DungeonResultRepository.Stats stats = DungeonResultRepository.stats(playerUuid, dungeonId, roomType);
long count = stats.successfulRuns();
OptionalLong best = stats.bestTicks();

List<DungeonResultRepository.History> page = DungeonResultRepository.history(
        playerUuid, dungeonId, roomType, 20, 0);
```

These are **blocking JDBC calls**: use the existing ranking I/O executor, not the server tick/client render thread. Return results to the server thread before accessing players or sending packets. No client payload accepts submitted scores.

- `Stats.successfulRuns()`: committed credited run count for the exact UUID/dungeon/mode.
- `Stats.bestTicks()`: minimum credited clear duration, or `OptionalLong.empty()` for a player without clears. Do not interpret absence as a zero-second best.
- `History`: run ID, dungeon ID, mode, elapsed ticks, completion timestamp. History includes credited clears only, newest first, then run ID as deterministic tie-breaker.
- History `limit` is 1..100 and `offset` is nonnegative.
- Connection/table-name overloads exist for isolated regression tests and transaction-owning callers. The `record(Connection, ...)` caller must commit/rollback the entire operation; normal callers use `record(DungeonClear)`.
- No leaderboard ordering/scoring formula is introduced. Rank/tie rules and UI wiring remain with the ranking feature.

## Files And Verification

- `DungeonClear`: immutable roster, credit snapshot, tick duration.
- `DungeonResultRepository`: transactional history and count/min/history reads.
- `DungeonResultOutbox`: versioned SavedData retry queue.
- `DungeonResultWriter`: asynchronous bounded batches, retry/backoff and acknowledgements.
- `DungeonResults`: server lifecycle and completion submission.
- `DungeonRoomManager`: actual start tick and one-time boss-victory hook before rewards.

Verified with `compileJava`, `dungeonBrowserRegressionTest` and `regressionTest`. Isolated H2 tests cover its default and MariaDB-compatibility modes, duplicate/concurrent replays, conflicting run data, participant-insert rollback, missing best time, filtering/pagination, safe table prefixes, v13-to-current migration, outbox save/load, partial batches, retries and stop acknowledgements. These are not live MariaDB tests or in-game victory/restart tests. No production deployment or data migration was run.
