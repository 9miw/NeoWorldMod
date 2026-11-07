# Market durability implementation plan

**Goal:** Recover listing, purchase and claim operations without repeating wallet/inventory effects.
**Approved scope:** Market journal, player checkpoint, schema 15, login recovery and fault tests. Shop remains unchanged.
**Architecture:** The market SQL scope makes existing repository calls join one transaction. The transaction commits business rows and an after-image together. Server-thread code saves the player with an operation checkpoint and verifies the checkpoint on disk before acknowledging the journal. Login replays an unacknowledged after-image only when its checkpoint is absent. No success is published before verification. Database/save ambiguity terminates the player session and leaves recovery evidence.
**Tech stack:** Java 21, JDBC/H2/MariaDB, NeoForge NBT, existing regression runner.

- [x] Test schema 15 and migration before implementation (observed expected assertion failure).
- [x] Implement market-only transaction scope and durable journal, with rollback, replay and save-failure fault tests.
- [x] Integrate server-thread player snapshots, verified saves and login recovery.
- [x] Handle sell-menu escrow remainder without losing items outside the player save.
- [x] Run compileJava, regressionTest and shopRegressionTest; review scoped changes.

Constraints: Preserve existing migrations; no live database changes or full build. Block gameplay after uncertain persistence. Do not overwrite acknowledged player state on retry. Retain owner/type checks on operation IDs. Test H2 file restart independently of player NBT; H2 MariaDB mode is not a real MariaDB test.

Ruling: Synchronous market mutations on the server thread avoid gameplay/save races while introducing this persistence boundary. This may increase tick latency; a later asynchronous design needs explicit player-state reservation.
Ruling: Tests from the previous phase describe the old independent boundary; retain their explicit known-gap labeling, and add separate tests of the new coordinator.

Review rulings: Preserve checkpoints in NeoForge PlayerPersisted across death/End-return clones. On integrated-owner login, compare the loaded marker with the latest journal even if acknowledged, because level.dat can lag behind playerdata. Partition journal replay by stable world UUID in `<world>/neoworld/market-world-id`; back up that file with the world and database. Quarantine immediately on ambiguity and bypass combat-logout retention for that session so no gameplay follows an uncertain save. Restore before-image if SQL outcome lookup is unavailable. H2 uses WRITE_DELAY=0 so commit flushes before player fsync. MariaDB requires durable server-side commit settings.

Migration 15 is new and unreleased in this task; prior intermediate journal layouts existed only in isolated test directories. Existing released migrations 1–14 are unchanged. Fresh migration, v14 upgrade and interrupted-DDL retry are covered. Applied journal after-images are retained for owner reconciliation and operation idempotency; future retention must preserve the latest per-world player entry and duplicate-operation evidence.

Validation: compileJava succeeds; 43 economy, 205 database, 35 legacy market-security, 99 production-coordinator fault/restart checks, and 121 shop checks pass. H2 file-backed fault tests and H2 MariaDB compatibility mode only. No real MariaDB, Minecraft runtime/mixin execution or process-kill test has been performed. Additional tick latency is possible because durable market writes/save verification run synchronously on the server thread.
