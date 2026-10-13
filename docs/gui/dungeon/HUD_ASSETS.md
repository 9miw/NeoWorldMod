# Dungeon HUD Assets

Prepared from `hud-approved-mockup.png`. These resources are not yet connected to
the live HUD; the existing scoreboard remains unchanged. No in-game QA yet.

## Layers and Sizes

Use a 220x160 logical GUI canvas at the top right, with an 8-unit margin.
These are GUI units, not screen pixels. GUI Scale 2 makes it 440x320 pixels.
For small viewports, apply a uniform scale to the whole composition; do not
independently scale text and frame. This frame is not a verified nine-slice.

All PNGs live in `assets/neoworld/textures/gui/dungeon/hud/`.

| PNG | Source size | Logical destination (x, y, width, height) |
| --- | --- | --- |
| background.png | 424x290 | 4, 10, 212, 145 |
| frame.png | 440x320 | 0, 0, 220, 160 |
| divider.png | 400x20 | 10, 91, 200, 10 |
| bar_track.png | 400x36 | 10, 68, 200, 18 |
| wave_fill.png | 384x16 | 14, 73, 192, 8 |
| boss_fill.png | 384x16 | 14, 73, 192, 8 |
| clock.png | 32x32 | Use named bitmap font below |

Draw backing first (multiply alpha by 0.75 for translucent gameplay), border,
divider/track, clipped fill, then text and icons. The frame center is transparent.
Fill images represent full bars: clip destination width AND sampled UV width to
the same progress ratio. Do not stretch the full fill into a shortened rectangle.
Wave segmentation can be added at runtime from wave count, never baked into art.

Suggested text anchors: title center (110,20), status center (110,36), wave/boss
row (14,55), time label/value (40,112)/(204,112), members (40,136)/(204,136).
Right-align values. Keep text inside x=14..206; measure localized text and use
shorter labels or a compact layout when needed. Clock/member icons start at
x=16 on their row; tune baseline with the actual font renderer before release.
Do not show both the Wave and Boss variant at once. Time limit 0 means unlimited.

## Bitmap Font

Font ID: `neoworld:dungeon_hud`. Providers: height 12, ascent 11.

| Character | Meaning | Texture |
| --- | --- | --- |
| `\uE110` | Time remaining | `gui/dungeon/hud/clock.png` |
| `\uE101` | Living members | Shared `gui/common/icons/member.png` |

The member texture is intentionally reused, not duplicated. This font is icon-only:
apply it to the glyph Component, not the entire text label. Render glyphs white to
preserve PNG colors. It does not register characters in Minecraft's default font.

```java
Component.literal("\uE110").withStyle(style -> style
    .withFont(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_hud"))
    .withColor(0xFFFFFF));
```

## Re-export

The measured source rectangles are in `export-hud-assets.ps1`. The source is
`hud-assets-source.png` (1536x1024 RGBA). Run from the repository root:

```powershell
& docs/gui/dungeon/export-hud-assets.ps1 -Source docs/gui/dungeon/hud-assets-source.png
```

The helper crops approved disjoint regions, uses alpha bounds for the border,
backing, track and icon, and trims the glow outside fill regions. It normalizes
using nearest-neighbor sampling and preserves clock padding. Re-measure regions
if the source sheet changes. PNG metadata disables blur and enables clamp.

Validate JSON references and transparent pixels after export; compilation alone
does not validate resource-pack loading or how the font looks in-game.
