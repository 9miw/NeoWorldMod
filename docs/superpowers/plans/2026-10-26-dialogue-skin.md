# NeoWorld Dialogue Skin Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan inline, task by task.

**Goal:** Implement approved dialogue concept 1 while preserving CustomNPCs gameplay behavior.

**Architecture:** Client Mixin into `GuiDialogInteract` replaces list rendering and hit-testing only. A renderer owns wrapped content and scrolling; a Minecraft-independent layout owns bounds and option visibility.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.248, CustomNPCs 1.21.1.20251230, PNG assets.

**Spec:** `docs/gui/dialogue/DESIGN.md`

## Global Constraints

- No dependency upgrades, server protocol changes, unrelated file changes or full build.
- Keep wheel/sub-GUI paths, hideNPC, disableEsc, sounds, IDs and server handlers.
- Use dynamic Thai text and PNG frame assets; preserve corners and matching hitboxes.
- Run compileJava after code edits; report in-game validation separately.

## Review Focus

- Small scaled windows must not overlap dialogue, options or close controls.
- Zero options and very long/multiline options must not select invalid entries.
- Mouse clicks outside the actual rows must never submit a response.
- Scroll and keyboard selection must reveal the actual selected option.
- Wheel, forced dialogue, hidden NPC and server close lifecycle must retain original semantics.

### Task 1: Layout and assets

Files: `client/gui/dialogue/DialogueLayout.java`, matching regression test, `docs/gui/dialogue/`, `textures/gui/dialogue/`.

- [x] Pin approved image and generate/export measured blank component PNGs.
- [x] Add focused layout regression assertions for bounds, hidden preview, hit-testing, zero/long lists and selection scroll.
- [x] Observe failure without implementation; implement layout and run green.

### Task 2: Client integration

Files: `client/gui/dialogue/NeoDialogueView.java`, `mixin/client/GuiDialogInteractMixin.java`, `neoworld.mixins.json`.

- [x] Render frame, actual NPC, plaque, formatted history and wrapped response rows from one layout.
- [x] Route list-mode clicks, keyboard and scrolling, reusing original selection and close methods.
- [x] Preserve wheel and sub-GUI paths; maintain append/reset and resize state.
- [x] Run compileJava and layout regression checks; inspect exact target bytecode against shadow/injection signatures.

### Task 3: Verification and handoff

- [x] Verify resource paths, PNG alpha/padding and sampling metadata.
- [x] Review only this change's files and resolve functional issues.
- [ ] If a running game is available, capture and compare with approved concept; otherwise explicitly leave in-game verification pending.
- [x] Record commands/results and remaining limitations in design notes; do not commit or push unrelated work.
