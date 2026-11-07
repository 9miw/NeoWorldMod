# Remove Gold Implementation Plan

> Execute inline with executing-plans, then perform one fresh code review. No commits or deployment requested.

**Goal:** Remove Gold from active gameplay, UI, commands, items and network while retaining old balances and transaction history without conversion.

**Approved spec:** User approved the scope proposed in this chat: remove Gold code/UI/commands/coins/recipes/references, move remaining active costs to Silver, preserve old balance/history as inaccessible legacy data, leave Silver/Diamond intact.

**Architecture:** Keep only legacy codec/database fields needed to preserve saves. Active enums contain Silver and Diamond; preserve Diamond shop wire ID 2 and reject retired ID 1. Bump network version with two-balance payload. Add migration 17 to archive/convert current Gold shop offers to Silver at unchanged numeric prices and quarantine pending Gold operations for review. Abort before conversion if merging currencies creates an arbitrage pair. No live database access.

**Tech Stack:** Java 21, NeoForge, existing JDBC migration dispatcher, Python icon catalogue generator.

## Tasks

- [x] Add failing coverage for the two active currencies and retired ID rejection.
- [x] Remove active Gold APIs/coin registration and client payload fields. Keep legacy balances read-only through serialization/checkpoints.
- [x] Move upgrade costs, commands, widgets and shop controls to Silver; preserve numerical costs and historical currency strings.
- [x] Add migration and regression coverage for preserved audit rows, converted offers, pending-operation quarantine and retry.
- [x] Remove coin assets/translations/font registration and regenerate/check the icon catalogue.
- [x] Compile and run economy/database/shop/checkpoint/GUI/network checks relevant to the final edits. Review remaining references and documentation.

## Review focus

- Legacy saves/checkpoints must retain inaccessible balances and never credit Silver.
- Old shop currency ID 1 must never be interpreted as Diamond or Silver.
- Gold audit records must remain unchanged; pending operations must not replay against Silver.
- Removed item registry IDs must never be remapped to Silver; old coin stacks are no longer registered. No live deployment is included.
- Upgrade UI/server must agree on Silver costs, and custom numeric settings must survive key migration where applicable.

## Execution ledger

Approved design already supplied and confirmed in chat; do not repeat approval flows. Cosmetic gold colors/vanilla gold materials are outside scope. Existing unrelated working-tree edits must remain intact.

Verified: compileJava, regressionTest, shopRegressionTest, sharedGuiReuseRegressionTest, activityRegressionTest and killerRegressionTest passed. Icon catalogue --check and five generator tests passed. Reviewer findings on semantic item comparison and help text resolved; no further actionable findings. Runtime comparison uses server registry; regression fixture compares structural NBT with distinct gzip headers. H2 and H2 MariaDB mode verified, not a live MariaDB server. No in-game/UI screenshot or live-server deployment performed.
