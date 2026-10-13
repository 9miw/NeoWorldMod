---
name: neoworld-persistence
description: Add or change NeoWorldMod persistent player or feature data, Attachment integration, H2/MariaDB storage, transactions, or schema migrations. Use when implementing storage behavior, rather than for gameplay balancing or database administration.
---

# NeoWorld Persistence

## Read the owning path

Start with `src/main/java/com/ambition/neoworld/database/NeoWorldDatabase.java`, then the target service/repository and the relevant part of `Config.java`. Inspect `ModAttachments.java` and the relevant player data/lifecycle handler only when Attachment state is involved. Paths are relative to NeoWorldMod.

## Decide where the data belongs

- Attachment data is tied to loaded game objects. Shared database storage is outside individual world saves and can support offline queries and cross-dimension feature data.
- Determine the actual source of truth from the current owning service before changing storage. Do not assume all Attachments are disposable caches or all player fields are already database-backed.
- If both layers contain a value, define load, update, save, reconnect, and failure behavior so stale cached state cannot overwrite newer committed data.
- Keep money, stock, ownership, and other related feature changes atomic where the requested operation requires it. Reuse `NeoWorldDatabase.inTransaction` rather than adding an unrelated connection pool.
- Publish success and update client state only after the owning operation succeeds. Preserve existing failure handling and retry semantics.

## Change schema without losing existing data

- Add the next migration through the current migration dispatcher and schema version. Do not rewrite an applied migration to silently change existing installations.
- Retain schema history and account for both a fresh database and an upgrade from the supported prior schema.
- Use configured table prefixes consistently. SQL identifiers cannot be substituted through value placeholders: validate identifiers through the existing configuration rules and bind ordinary values with prepared statements.
- Check H2 and MariaDB compatibility for the SQL being changed. Do not claim MariaDB verification from an H2-only run.
- Database engines may commit DDL implicitly. Do not assume a Java transaction makes every migration rollback-safe; plan recovery for a partially applied migration when that is relevant.
- Keep secrets out of logs and skills. Changing database targets, moving live data, or removing tables requires separate task authorization.

## Verification

Inspect the existing regression harness and run `.\\gradlew.bat regressionTest` for behavioral changes within that harness. Use isolated databases for migration tests. Verify fresh initialization, upgrading existing data, relevant transaction failure paths, and cache synchronization when affected. Report the database engines and runtime paths actually tested.
