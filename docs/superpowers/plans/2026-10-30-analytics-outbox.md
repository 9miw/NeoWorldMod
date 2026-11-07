# Durable analytics outbox implementation plan

**Approved goal:** Persist queued economy telemetry on disk and retry delivery without duplicates after restart; wallet/business transactions keep their existing behavior.
**Architecture:** Replace opaque lambdas with versioned Event/Balance/CarryDaily jobs. Atomic, fsynced JSON spool files under config/neoworld/analytics-outbox survive process restart. A single file lock prevents concurrent consumers. Migration 16 adds SQL job receipts committed with telemetry effects; files are acknowledged only after SQL commit. Pending jobs remain on shutdown/DB failure. Older balance observations must not overwrite newer SQL snapshots.

- [x] Observe a failing schema-16 assertion before migration.
- [x] Implement typed jobs and checksummed file outbox, recovery and exclusive lock.
- [x] Add receipts and commit/retry worker; replace all lambda submissions.
- [x] Test restart, SQL rollback/ambiguous commit, file acknowledgement gaps, malformed files, concurrent append, and receipt replay for all job kinds.
- [x] Update report wording and schema tests; compileJava and run related regression checks; focused review.

Constraints: Preserve applied migrations 1–15. Use the shared SQL pool, no extra database dependency. No wallet rollback or automatic live data migration. Accepted work must be durable before offer returns. Disk failure can still prevent acceptance and must be counted/logged; never silently drop old work or clear it on start. File-spool fsync runs on the submitting thread and may add I/O latency. Real MariaDB and Minecraft process-kill testing remain separate runtime checks.

Review rulings: Retry outbox initialization after a retiring writer/external lock becomes available; cancel initialization retries when stopping. Do not interrupt an ambiguous SQL commit or release its file lock from another thread. pending=-1 reports unavailable storage rather than an empty queue. Accepted files are verified/fsynced and atomically renamed, but parent-directory persistence is not guaranteed on Windows: the supported failure model is process termination/restart, not host/power failure. Corrupt jobs remain on disk and block delivery with an error until reviewed. Receipt retention must cover any possible spool replay; no automatic pruning is introduced.

Verification: `compileJava regressionTest shopRegressionTest` succeeds. 31 durable-outbox checks exercise production file/SQL delivery classes with file-backed H2, including concurrent 320-job acceptance, exactly-once SQL effects after lost commit acknowledgement, daily-carry receipt replay, stale balance protection, checksum rejection and stop/start lock recovery. Migration tests cover fresh initialization, v15-to-v16 upgrade and interrupted receipt DDL. Related checks: 43 economy, 241 database (includes outbox checks), 35 market-security, 191 player-journal, 119 shop and 3 wallet compatibility. H2 MariaDB compatibility mode is covered by the existing schema harness; real MariaDB and in-game runtime are not verified. Fault tests intentionally log offline/lock errors.

Operational data: Back up config/neoworld/analytics-outbox with the SQL database. Only successfully committed files are acknowledged; shutdown leaves unsent files intact. Failed disk acceptance is counted via dropped/lost metrics and logged, so reports can still be incomplete when the spool itself is unavailable. Historical events that existed only in the previous RAM queue cannot be reconstructed.
