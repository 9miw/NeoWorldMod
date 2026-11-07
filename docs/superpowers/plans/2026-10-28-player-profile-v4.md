# Player Profile V4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver the approved four-tab profile using real player/PvP/skill data without hand slots or invented skill levels.
**Architecture:** Extend the existing bounded profile codec with a presentation snapshot. Capture gameplay on the server thread and resolve cached PvP asynchronously, without blocking the game loop. Retain the existing request identity and validation. Client equipment and preview use sanitized snapshot copies; UI selection/scroll is client-only.
**Tech Stack:** Java 21, NeoForge 1.21.1, installed Epic Fight/WoM, existing NeoButton/ProfileSkin/NeoScrollbar.
**Spec:** docs/gui/player-profile-v4-spec.md

## Global Constraints
- No weapon names or main/offhand equipment slots. No refresh button or effects tab.
- No fabricated skill levels or unsupported numbers. PvP failures display unavailable, not zero.
- Restrict EXP on the server to the profile owner.
- Preserve other chats' work. No commits, packaging or full build requested.
- Run compileJava after code edits; relevant codec regressions and resource checks before handoff.

## Review Focus
- Wrong-target or late async replies must not change a closed/different profile.
- Missing Epic Fight/PvP data must render explicit empty/unavailable states.
- Long Thai/item/skill text must fit or scroll without overlapping controls.
- Snapshot gear must exclude both hands and arbitrary container/custom item data.
- More equipped skills than the viewport must remain reachable with a shared scrollbar.

## Task 1: Data snapshot
Files: network/PlayerProfilePayload.java, network/player/ProfileDetails.java, network/player/PlayerProfileSnapshot.java, network/player/PlayerProfileService.java, command/player/ProfileCommand.java, network/player/PlayerActionNetworkHandler.java, registry/ModPayloads.java; existing network/PlayerProfileRegressionTest.java.
- [x] Add regression cases for details round-trip, PvP unavailable/unranked, skill bounds and owner EXP projection; run red.
- [x] Implement bounded immutable metadata, owner projection and sanitized 4 armor/6 fashion previews.
- [x] Capture actual equipped skill identities and supported values; attach cached asynchronous PvP results; preserve sender/target validity.
- [x] Compile and run playerProfileRegressionTest; record results.

## Task 2: Four-tab UI
Files: client/gui/player/PlayerProfileScreen.java, ProfileSkin.java; optional focused snapshot preview helper.
- [x] Use approved reference and existing PNG frame/inner/control art. Overview: HP/stamina/owner EXP, PvP cards, sin and separate shortcuts.
- [x] Combat: current supported grouped stats, no offhand placeholder. Equipment: only armor/fashion with selected-item details. Skills: real equipped list and scrollable selected description.
- [x] Fixed portrait/identity, four tabs, retained native button input and narration. Use registered skill textures and existing catalogue icons.
- [x] Compile, check resources/bounds and attempt available in-game verification without restarting the user's game.

## Task 3: Handoff
- [x] Review changes, run final compile and relevant regression once changes stop.
- [x] Update design notes and report exact verification limits.

## Execution ledger

- Data and UI implemented inline in the existing working tree, preserving unrelated changes. No commits were requested.
- Red/green: metadata/owner/PvP tests failed before ProfileDetails existed, then passed. Loading/snapshot tests failed before the follow-up policy existed, then passed.
- Ruling: reuse the already extracted V3 frame/control art and catalogue icons rather than generate duplicate fonts/assets; latest approved V4 image is pinned as the information-layout reference.
- Ruling: do not fabricate mod skill levels or dynamic tooltip arguments. Support real equipped identities and mod descriptions, plus verified numeric adapters for Critical Knowledge/Vampirize.
- Code review: fixed empty BACK shrinking and async PvP opening older screens. Follow-up read-only review found no remaining important issue in those fixes.
- Test limitation: attempting ItemStack bootstrap in the standalone regression exposed the absence of LoadingModList. Removed that attempted bootstrap; gear-slot policy is tested headlessly, while real ItemStack transport remains an in-game check.
- Verification: compileJava and playerProfileRegressionTest passed;16 sprite dimensions/metadata verified. In-game visual verification is pending because the active game was not restarted with the new class/schema changes. No claim of visual equivalence is made.
