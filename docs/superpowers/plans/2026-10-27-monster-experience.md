# Monster Experience Implementation Plan

**Goal:** Drop contestable NeoWorld EXP for genuine player combat using the approved monster reward table.

**Architecture:** A Java-only rules class owns combat credit and reward ranges. A server event subscriber measures actual health loss, checks eligibility and adds an unowned EXP item to death drops. Existing pickup and Minecraft EXP behavior remain unchanged.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248.

**Spec:** Approved conversation: at least 50% maximum-health damage from players combined, last player damage within 15 seconds, spawner rewards 25%, Vex excluded; previous monster reward table.

## Constraints and review focus
- No one-hour leveling target; no world-entry changes.
- Ignore creative/spectator attackers and pets; attribute projectiles through their damage-source owner.
- Count actual health loss, not overkill or absorbed damage; reset credit after a 15-second combat gap.
- Preserve spawner origin through save/load, conversions and splits.
- Respect doMobLoot and canceled drops; no item ownership restriction.
- Unknown/modded entities get no automatic reward. Warden and Vex get none.

## Tasks
- [x] Add regression cases for 50% threshold, time boundary, multiple attackers, overkill, absorbed damage, combat reset, reward scaling and excluded entities; observe failure.
- [x] Implement `MonsterExperienceRules` and verify the rules with the regression runner.
- [x] Implement `MonsterExperienceEventHandler`: capture before/after health, use server-only events and persistent spawner markers, emit one EXP item using the approved ranges.
- [x] Register a dedicated regression task in `build.gradle`; run it, existing regression checks, `test` and `compileJava`. Review the scoped diff. Report in-game verification as pending.

Verification: initial regression failed at combined player damage as expected; implemented rules passed. `compileJava monsterExperienceRegressionTest test regressionTest` and then `compileJava monsterExperienceRegressionTest check` succeeded. `test` has no sources; regression runners executed. Check printed log-rotation errors from missing/locked debug logs but all runners passed. In-game verification remains pending.

## Follow-up: configurable EXP

User requested a configurable system. Added server config `neoworld-monster-experience.toml` with enabled, damage percentage, combat window, spawner multiplier and validated reward entries. Immutable settings are baked on load/reload and reset on unload. Added a complete TOML template and operator documentation.

Config regression initially failed at the custom 25%/five-second rule, then passed after implementation. Production config code compiled successfully. Template parsing and NeoForge value validation passed in `monsterExperienceRegressionTest -x compileJava`. Final `compileJava check` is blocked by concurrent changes outside EXP in market/shop code (including missing `MarketOperationRecord`, `ShopService.reconcile`, and an `insertPendingItem` signature mismatch). These files were not changed by this task. In-game verification remains pending.

## Follow-up: display-only monster levels

User chose level before name above the head, with no changes to health, damage or EXP. Added `MonsterLevelRules` for validated level configuration, server config `levels` entries with size overrides, and client-only `MonsterLevelNameTagRenderer`. It prefixes the original Component, keeps custom/localized names, respects visibility/team suppression and uses a 32-block range. Entity names and persistence are never changed.

Regression initially failed for the missing Zombie level, then passed after implementation. `compileJava monsterExperienceRegressionTest check` passed on the final code; earlier unrelated market compilation blockers are no longer present. Visual in-game verification remains pending.
