---
name: neoworld-bitmap-titles
description: Create or adapt decorative bitmap fonts for NeoWorld Minecraft Title overlays, including victory, defeat, fight-start and countdown artwork, PNG extraction and server-side Title integration. Use for center-screen Title typography; ordinary GUI text and general-purpose Thai font design belong elsewhere.
---

# NeoWorld Bitmap Titles

Create reusable decorated Title glyphs while keeping scores, player names,
rating changes and timers as dynamic Subtitle text. Fixed words may be whole
glyphs when the user requests illustrated lettering; distinguish this from
building a complete alphabet font.

## Start from the owning feature

- Locate NeoWorldMod; the usual checkout is `C:/Users/miw/Documents/NeoWorldMod`.
  Follow its current `AGENTS.md` and read `docs/gui/NEOWORLD_GUI_STYLE.md`.
- Apply the relevant `neoworld-gui` guidance. Use `imagegen` for new illustrated
  bitmap artwork or art revisions when available; simple resource wiring does
  not need image generation.
- Inspect the feature's current Title sender, font JSON and existing artwork.
  Reuse an approved style rather than redesigning it. A preview-only request
  does not authorize runtime integration; preserve the project's approval rules.
- For several style options, make a clearly labeled comparison sheet and retain
  the selected reference. Do not assume Arena Gold is the choice for every task.

## Prepare usable glyph assets

- Keep the approved reference and production source under `docs/gui/<feature>/`.
  Request true transparent backgrounds for runtime artwork, no scene backdrop,
  subtitles, scores or sample player information. Keep Thai diacritics legible
  and clear of crowns, laurels and other ornaments.
- Treat generated sheet dimensions and grid spacing as unverified. Inspect
  actual pixel dimensions, alpha and gaps; measure disjoint crop rectangles.
  A requested equal grid is not evidence of the returned layout.
- Use reproducible extraction for approved artwork. Crop each glyph to its
  measured alpha bounds, retain transparent padding, and scale with nearest
  neighbor. Never remove dark pixels by color key: outlines and shadows use them.
  Clean alpha fringes when appropriate for the crisp approved art.
- Normalize related countdown digits to the same canvas and visual size so
  they do not jump during playback. Keep each label's aspect ratio intact.
- Export a checkerboard preview from the actual runtime PNGs. Inspect for
  clipped Thai marks, neighboring sprite fragments and asymmetric margins.

## Install and connect

- PNGs go under `src/main/resources/assets/neoworld/textures/font/<feature>/`;
  named font providers go under `assets/neoworld/font/<feature>.json`.
  Use `.png.mcmeta` with `blur:false` and `clamp:true`.
- Inspect existing mappings before assigning private-use glyphs. Keep the
  feature font separate from `minecraft:default`; do not change unrelated
  font registries. A provider's `file` is `neoworld:font/<feature>/<name>.png`,
  relative to `textures/`, and `chars` contains the actual assigned character.
- Choose bitmap `height` and `ascent` from the intended logical Title size.
  Vanilla Title rendering scales the font by four; window pixels and logical
  GUI pixels differ. Use the actual GUI Scale and scaled window dimensions,
  not an assumed scale. Preserve room for the separate Subtitle baseline.
- Create a small common/server-safe Component helper. Assign the named font
  only to the glyph and use white text color to preserve colored artwork.
  Do not import client-only classes into server gameplay code.
- Replace only the intended Title Components. Keep gameplay timing, authoritative
  countdowns, sound event timing, Subtitle updates and readable chat messages.
  Draws or unsupported values may retain ordinary text unless included in scope.
- Existing vanilla Title packets can carry the styled Component without a new
  payload. Do not add a protocol or client renderer merely to display a glyph.

## Verify and hand off

- Validate JSON, every texture reference, distinct glyph mappings, actual alpha
  bounds, transparent margins and sampling metadata. Check title width against
  supported scaled window widths and the Title/Subtitle separation.
- Run `compileJava` after project code changes and the relevant existing feature
  regression task. Do not run a full build/package unless requested. A skill
  documentation-only update does not need the game compiled.
- Compare an actual game screenshot with the approved design at the same aspect
  ratio and documented GUI Scale when available. Offline previews and successful
  compilation do not verify appearance in Minecraft. State the verified stage
  explicitly and link the actual installed-assets preview.

## Existing Arena Gold example

Read these project files only when extending or reproducing that implementation:

- `docs/gui/pvp/arena-gold/README.md`: approved reference, generation prompt,
  measured layout and verification limits.
- `docs/gui/pvp/arena-gold/export.py` and `manifest.json`: reproducible extraction
  and measured crops. Their coordinates are specific to that source image.
- `src/main/resources/assets/neoworld/font/pvp_titles.json`: named font
  `neoworld:pvp_titles`; U+E400 victory, E401 defeat, E402–E406 digits 5–1,
  E407 fight. Existing providers use height 18 and ascent 14.
- `src/main/java/com/ambition/neoworld/pvp/room/PvpTitles.java`: server-safe
  Components; `PvpRoomManager.java` owns Title event integration.

Arena Gold uses gold/ivory lettering with crown/laurel victory ornaments,
crimson/bronze defeat artwork, and gold countdown rings with red side gems.
The existing digit canvases are 128 x 96; word images are 256 pixels wide.
Treat these as measured example values, not universal requirements.
