# Cumulative player EXP

Update 2026-10-30: user confirmed server has never opened and does not need tracking dates. Removed the timestamp field, getter and login initialization; cumulative EXP policies and ranking API remain unchanged. The earlier timestamp design below is historical.

User approved: separate persistent cumulative EXP; count admin addexp and grants at level cap; preserve total when level/current EXP resets; legacy totals start at zero without estimated backfill. Do not change ranking GUI or sort order.

- [x] Establish a failing test against the existing player codec (missing cumulative EXP).
- [x] Add non-negative optional codec fields and getters in PlayerData. Preserve old constructors and legacy saves.
- [x] Count grants once in PlayerData.addExp, before progression costs. Share progression through PlayerLevelManager.applyExp; retain server effects and synchronization.
- [x] Initialize legacy tracking timestamp only at login/first positive award; leave offline reads pure.
- [x] Add tests for level cap, administrative reset preservation, saturation, old saves and compressed offline round trips.
- [x] Document schema, approved policies and integration API in docs/PLAYER_TOTAL_EXP.md.
- [x] Run compileJava, dedicated EXP regression and existing checks; review scope and report in-game verification as pending.

Verification: dedicated EXP regression initially failed because the old codec had no cumulative total. Final `compileJava` and `playerExperienceRegressionTest` passed, including the compressed offline-save test. Scoped review found no important issues. `check` was attempted; its database regression fails because DatabaseRegressionTest expects schema 13 while NeoWorldDatabase declares schema 14. This work does not change database migrations or their tests. Other reported runners (monster EXP, fashion, player profile, party, killer, trade and PvP) passed. In-game verification has not been performed.
