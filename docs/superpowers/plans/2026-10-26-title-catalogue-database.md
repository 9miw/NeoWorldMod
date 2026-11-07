# Title catalogue database implementation plan

> Execute inline with executing-plans; preserve unrelated working-tree changes.

**Goal:** Make configured H2/MariaDB the authoritative title catalogue, preserving existing player title IDs.
**Architecture:** Schema 11 adds a relational catalogue. Import legacy JSON (or bundled defaults when absent) and an initialization marker in one transaction. Publish a validated immutable registry only after commit. Reload reads SQL asynchronously.
**Tech Stack:** Java 21, existing JDBC/Hikari pool, H2/MariaDB, existing regression harness.
**Spec:** User-approved scope in this conversation: one-time import, stable IDs, retain legacy JSON, seed new installations, database-only subsequent loads.

## Constraints and review focus
- No live database changes, full build, commits or unrelated feature work.
- Marker must distinguish an intentionally empty catalogue from a new installation.
- Invalid JSON/SQL data must not replace the active registry; rollback must not mark import complete.
- Preserve name/display, enum values, numeric targets, order and player data.
- Use validated prefixes and prepared value parameters. DDL retry uses IF NOT EXISTS.
- H2 MariaDB compatibility mode is not a real MariaDB server verification.

## Tasks
- [x] Add regression coverage for fresh seed, existing JSON import, restart with corrupt JSON, empty catalogue, failed import rollback, SQL reload validation, stable player data and v10 upgrade/retry.
- [x] Add `JdbcTitleCatalogueRepository` with `createSchema`, `initialize(Connection, Path)` and `load(Connection)`; schema 11 dispatches its DDL. Split JSON reading from atomic registry publication.
- [x] Initialize catalogue before player service starts; async admin reload reads SQL and preserves registry on failure.
- [x] Update storage documentation; run compileJava and regressionTest; inspect resource and diff checks.

## Verification ledger
`compileJava regressionTest` passed: 43 economy and 185 database checks. Tests used
isolated H2 databases and H2 MariaDB compatibility mode only. Real MariaDB and
in-game startup were not tested. Independent read-only review found no substantive
correctness issues. No live database, legacy JSON, commits or unrelated feature data
were modified; production import runs at the next server startup. Existing schema
version assertions were advanced to 11, preserving their prior upgrade coverage.
