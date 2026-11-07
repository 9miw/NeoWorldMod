# Dungeon Results Implementation Plan

> Execute inline using executing-plans, with focused regression verification.

**Goal:** Persist successful clears and expose count/best-time queries without ranking UI changes.

**Architecture:** Immutable run/participant snapshots, transactional JDBC history, and an Overworld SavedData retry outbox. Read statistics from history, not a second mutable counter.

**Tech Stack:** Java 21, NeoForge SavedData/events, shared NeoWorldDatabase, H2/MariaDB SQL.

**Spec:** User-approved rules in this chat: start after placement and participant teleportation; finish on boss victory before rewards; dead spectators count; returned players do not; tick duration at 20 TPS. Only successful runs are recorded.

## Tasks
- [x] Write regression tests for snapshots, duplicate/retry writes, rollback, filtered count/min queries, migration upgrade and outbox serialization.
- [x] Add immutable completion model and JDBC repository with migration 14; preserve prior migrations and rows.
- [x] Add server-owned persistent retry queue, asynchronous bounded database submission and restart recovery.
- [x] Snapshot actual start tick and successful result in DungeonRoomManager before reward/return handling.
- [x] Run compileJava, focused Dungeon tests and shared database regression tests; document API and limits in docs/DUNGEON_RESULTS.md.

## Verification Ledger
- Inline implementation resumed after other feature work advanced the shared schema to 17. Migration 14 remains the Dungeon-owned migration; later migrations and their tests are preserved.
- New tests first failed for missing model/writer APIs, then passed after implementation. A safe-prefix test reproduced and fixed the initial lowercase-only table-name check.
- `compileJava dungeonBrowserRegressionTest regressionTest --no-parallel`: BUILD SUCCESSFUL. Shared regression output: 45 economy, 241 database, 35 market security checks; Dungeon completion/browser suites passed separately in the same invocation.
- No live MariaDB or in-game verification; no build/package, deployment, ranking UI change or production data migration.

## Constraints
- No ranking GUI, balance changes, currency changes, historical backfill or production deployment.
- UUID primary keys enforce idempotence; whole run and participant rows commit together.
- All SavedData/game access stays on server thread; worker receives immutable snapshots only.
- Queries are blocking JDBC APIs for future async ranking callers.
- Outbox uses normal world saves; abrupt process/disk failure before either world save or database commit can still lose a newly completed result.
- H2 MariaDB compatibility mode is not verification against a live MariaDB server.
