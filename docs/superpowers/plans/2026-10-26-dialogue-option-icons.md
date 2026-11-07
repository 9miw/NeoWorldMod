# Dialogue option icons

Approved: replace the leading chevron with an icon selected from the actual `DialogOption.optionType` (0 close, 1 dialogue, 2 disabled, 3 NPC role, 4 command), with disabled visual feedback. Keep row hitboxes and selection behavior unchanged.

1. Add a pure `DialogueOptionKind` mapping and regression checks for all five installed types plus unknown values. Unknown values use a neutral speech icon, never guessed actions.
2. Reuse existing glyphs/assets: NeoGuiIcons.LEAVE, dialogue E009, lock E015, NeoSystemIcons.PROFILE and item/protection_scroll.png. No new glyph registration or duplicate PNGs.
3. Add a small feature renderer fitting every icon into the same 10×10 logical slot. Preserve aspect ratio and restore render color. Move labels to x+20 and match wrap width; retain whole-row hitboxes, existing option IDs and playback gating. Synthetic Continue uses the speech icon.
4. Run mapping, layout and playback regressions plus compileJava. Check resource dimensions/references and report live in-game appearance separately.

Completed: type mapping, shared-asset renderer, wrap inset integration, source preview inspection, compileJava and all three focused regression suites. No in-game screenshot after restart is available.
