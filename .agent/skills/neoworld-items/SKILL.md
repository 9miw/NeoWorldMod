---
name: neoworld-items
description: Add or change NeoWorldMod items, ItemStack Data Components, required-level restrictions, tooltips, or Vanilla/Epic Fight attributes. Use for item behavior and registration; cosmetic GUI layout belongs to neoworld-gui.
---

# NeoWorld Items

## Follow existing registration

Read the target item and its entry in `src/main/java/com/ambition/neoworld/registry/ModItems.java`. For custom stack data, inspect `registry/ModDataComponents.java`. For level gating, inspect `level/ItemLevelEventHandler.java` and only the relevant configuration. Paths are relative to NeoWorldMod.

Use the project's current Minecraft/NeoForge and Epic Fight APIs. Preserve existing registry IDs unless renaming is explicitly part of the task.

## Data and attributes

- Store per-stack values with the existing Data Component pattern. Persistent codecs and network codecs must agree on type and meaning; validate values at the owning command/action boundary as well.
- Reuse existing components for coin value, EXP, required level, and fashion slots rather than duplicating the same concept.
- For required level, the current ItemStack component takes precedence over the item configuration. Preserve that precedence unless the requested behavior changes it.
- Distinguish final attributes from modifiers. Sword damage includes player base damage and tier contribution; attack speed has a player base plus the item modifier. Inspect the actual construction before assigning values.
- For Epic Fight, inspect the installed dependency's attributes, modifier helpers, equipment slot groups, and weapon capability. Do not promise that a Vanilla attribute alone produces a particular animation or combo.
- Change translation/model/texture references only as required for the requested item.

## Level gating and lifecycle

A tooltip explains restrictions; server events enforce them. For a gating change, check the relevant use path: air/block/entity interaction, attack/damage, mining, or armor equip.

The current handler preserves block interaction while blocking the under-level item, throttles denial messages, and removes invalid armor on the server tick. Inventory armor placement may therefore appear briefly before removal. Preserve item counts when returning armor; use the existing inventory-then-drop fallback if space is unavailable.

Check offhand, level decreases, login/respawn, and passive attributes when those paths are affected. State any remaining gap instead of claiming every equipment path is blocked. Do not expand an ordinary item addition into a rewrite of the gating system.

## Item icons from an approved mockup

Preserve the approved artwork when extracting inventory icons; do not regenerate it just to split a sheet. Cropping and nearest-neighbor resizing are asset preparation. Preserve RGBA transparency and inspect the actual exported icons at inventory scale; a preview's apparent background is not evidence of its alpha channel.

For NeoWorld's upgrade set, read [references/upgrade-items.md](references/upgrade-items.md) when changing its materials, atlas, or registration. Keep the placed block model separate from its inventory icon and state any visual difference. Registration and icons alone do not implement upgrade consumption, protection, or an interactive menu.

## Item tooltips and bitmap icons

For tooltip design or implementation, read [references/item-tooltips.md](references/item-tooltips.md). Use `neoworld-gui` alongside this skill when changing tooltip frames or visual assets. Present item details and actual usage with separate bitmap icon glyphs and dynamic text; keep gameplay enforcement in the existing server-side behavior.

## Verification checks

Use `.\\gradlew.bat compileJava` after Java edits and relevant regression checks for behavior changes. If resources changed, verify resource processing and the item's references. Runtime checks should cover the changed restriction and an unrestricted control case, plus full inventory or combat integration when relevant. Separate compilation from in-game verification.
