# Fashion Tooltip Assets

- `tooltip-icons-source.png`: transparent icon sheet derived from the approved concept.
- `export-tooltip-icons.ps1`: measures alpha bounds per cell and exports padded 32 x 32 icons with nearest-neighbor resizing.
- `preview-tooltip-icons.ps1`: displays actual exports at 64, 32 and 10 pixels, not an in-game screenshot.
- Runtime font: `neoworld:fashion_tooltip`, action glyphs E200-E207 and slot glyphs E208-E20D, height 10 / ascent 8. This manually maintained registry is separate from the general font generator's three registries.
- `tooltip-slot-icons-source.png`: six category icons (head, face, back, body, legs, feet). Run the export and preview scripts with `-Slots` to process this sheet without replacing the existing action icons.
- Slot PNGs are `slot_head`, `slot_face`, `slot_back`, `slot_body`, `slot_legs`, `slot_feet` in the runtime tooltip texture folder, respectively mapped to E208-E20D. `FashionTooltip.slotIcon` explicitly covers every `FashionSlot`.
- Runtime frame reuses `fashion_shell.png` with nine-slicing. No text or item information is baked into it.
- `FashionTooltip` adds localized content on the client for `SimpleFashionItem` and its GeckoLib subclass. Existing lore, attributes and advanced tooltip lines remain after the inserted content.
- Ownership uses the synchronized client wardrobe. No tooltip handler sends packets or consumes items.
- The NeoForge color hook supplies the final vanilla tooltip position; text wrapping, component rendering and positioning remain with Minecraft.

Visual comparison in Minecraft is still required, especially Thai combining marks, GUI scales, screen edges and third-party tooltip modifications. The exported icons use 32-pixel source textures to retain the approved artwork's detail; they render at the font's smaller height.
