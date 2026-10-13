---
name: neoworld-gui
description: Create, redesign, or fix Minecraft NeoForge GUI screens for the NeoWorld mod using its established frame, widget, interaction, and responsive-layout conventions. Use for NeoWorld screen code, GUI PNG assets, mockups, or visual mismatches between a mockup and the game.
---

# NeoWorld GUI

Build NeoWorld interfaces as a consistent product rather than isolated screens.

## Start With Project Context

1. Locate the NeoWorld project and read `docs/gui/NEOWORLD_GUI_STYLE.md` before proposing or changing a GUI.
2. Inspect only the target screen, its controller/payloads, the relevant runtime textures, and the closest canonical screen. Use the Market GUI as the default implementation reference.
3. Inspect the current in-game screenshot and approved mockup when the task is visual correction. Treat them as evidence of actual and intended behavior.
4. Respect repository approval instructions before creating or editing files.

For layout, asset, rendering, interaction, and verification details, read
[references/neoworld-gui-workflow.md](references/neoworld-gui-workflow.md).

## Core Invariants

- Use existing Minecraft/NeoForge components first. For real inventory interaction, use `AbstractContainerMenu`, `AbstractContainerScreen`, and `Slot`; retain vanilla rendering, tooltips, cursor, drag/split/hotkey behavior, and container synchronization. Apply NeoWorld styling around them. Custom mechanics need a concrete feature requirement; see the workflow's Game Components First section.

- Keep decorative frame art separate from reusable widget states.
- When turning an approved HUD mockup into PNGs and bitmap fonts, follow [HUD asset extraction](references/hud-asset-extraction.md): separate backing, transparent border, tracks/fills and icons; measure alpha bounds before export.
- Keep text, player data, item icons, prices, counts, timers, and other dynamic values out of baked backgrounds.
- Reuse the namespaced bitmap fonts `neoworld:icons`, `neoworld:currency`, and `neoworld:ranks` through `NeoBitmapFonts` for their registered glyphs. They no longer belong to `minecraft:default`; see the workflow reference for mappings and reusable examples. The separate `NeoGuiIcons` set uses its own font IDs.
- Use the shared `NeoGuiIcons` set for suitable leader/member, invite, kick, ready, leave, disband and refresh icons across features, including mockups. Keep glyphs separate from frame/button PNGs and ordinary labels; consult the workflow's bitmap icon section for font IDs, mappings and sizing.
- Scale render coordinates and matching hitboxes from one logical source coordinate system.
- Every interactive element must visibly represent normal, hover, selected or active, and disabled states when applicable.
- Prefer the shared `NeoButton` (extends Minecraft `Button`) for new buttons. Reuse its PNG states and vanilla input behavior; see the workflow reference for construction and screen integration. Migrate existing controls only within the approved task scope.
- Prefer the shared `NeoPopupScreen` for acknowledgement and countdown notifications. Keep callbacks client-side and send validated payloads for server-owned actions; see the workflow reference for its APIs and lifecycle.
- Render modal overlays on an explicit Z layer above parent `ItemStack`, entity previews, widgets, and tooltips. Flush the parent batch before translating the modal pose; call order alone does not guarantee correct depth.
- Render the world background once, then frame, dynamic content, widgets, and tooltips. Do not blur over the finished GUI.
- Preserve visible margins and prevent clipping or overlap at supported GUI scales.
- Keep the server authoritative for gameplay and persistent data. A screen should display client payload data and send explicit actions rather than mutate game state locally.

## Deliverables

For shared progress indicators, follow the approved PNG-based Soft Slim design in the workflow reference: chamfered silver frame assets matching the mockup, separate track/fill, configurable size and color, loading effects, and optional percentage text. Check implementation and asset availability before using a shared class or API.

Store runtime textures under `src/main/resources/assets/neoworld/textures/gui/` and design previews under `docs/gui/`. Add `.png.mcmeta` files with `blur: false` and `clamp: true` for crisp GUI textures.

Compile with `gradlew.bat compileJava` after Java changes. For meaningful visual changes, verify an in-game screenshot against the approved mockup before declaring the layout finished. Report any visual verification that could not be performed.

