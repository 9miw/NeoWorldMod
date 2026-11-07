# Execution ledger — plan: docs/superpowers/plans/2026-10-27-killer.md

- Execution authorized by the user; working on branch `codex/killer-red-name` with the existing working tree preserved.
- Task 1: in progress.
- Task 1: complete — `killerRegressionTest compileJava` passed after implementing rules and pairwise combat tracker.
- Task 2: complete — KillerData attachment registered with copyOnDeath, KillerManager, and KillerEvents handling damage, death, ticks, and PvP arena exclusions.
- Task 3: complete — KillerStatePayload registered, ClientKillerData cache, KillerNameTagRenderer coloring name tag red without breaking titles, PlayerProfilePayload updated with killerSin, and PlayerProfileScreen displaying sin and decay info.
- Task 4: complete — docs/KILLER_SYSTEM.md written, killerRegressionTest, playerProfileRegressionTest, and compileJava passing.
- Review fixes authorized: isolate arena damage; defer death effects until final cancellation; move analytics observation around the debit.
- Fix tests RED: new lifecycle harness initially failed compilation for missing queue/arena/observation interfaces; after implementation `killerRegressionTest compileJava` passed.
- Fresh read-only review identified attacker logout before queue commit and canceled-then-real death within one tick. The latter was reproduced as an assertion failure before the fix. Logout now flushes all queued deaths before save/removal; canceled pending notifications can be replaced by a subsequent death.
- Source validation: NeoForge 21.1.248 PlayerList.remove fires logout before save/removal. AttachmentInternals copies player attachments at NORMAL clone priority; Killer clone handling now runs at LOWEST and redirects the pending charge to the replacement wallet.
- Added lifecycle regression tests for replacement wallet charging and duplicate suppression. In-game verification remains outstanding.
- Final verification: `.\gradlew.bat compileJava killerRegressionTest playerProfileRegressionTest pvpRegressionTest regressionTest --console=plain --max-workers=1` — BUILD SUCCESSFUL; lifecycle/rules, profile codec, PvP suites, 43 economy checks, 195 database checks, and 35 market security checks passed. Only terminal-feature warnings remained; no log-rotation errors in this serialized run.
- Task 1 RED: `killerRegressionTest compileJava` failed at regression compilation because KillerRules/KillerCombatTracker did not exist; main compilation passed. Initial sandbox Gradle cache access denied; rerun with approved elevation.
- Ruling: use the existing checkout on a separate branch rather than a clean worktree, because the current uncommitted project systems are integration dependencies. No commit or push is planned.
- Ruling: keep this progress record in docs instead of an ephemeral script workspace so uncommitted work remains resumable without running unrelated helper scripts.
