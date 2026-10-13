# Approved HUD to Runtime Assets

Use for requests to split an approved HUD mockup into runtime PNGs and icon fonts.
Asset preparation does not authorize replacing the live HUD or network protocol.

1. Inspect the approved mockup and current shared icon registry. Reuse suitable
   shared icons, rather than generating competing artwork for the same meaning.
2. Generate clean bitmap parts from the reference with the image tool. Keep all
   text, timers, counts, health values and actual progress out of the artwork.
   Separate the translucent backing from the outer border (transparent center),
   dividers, empty bar track, full-length fill, and icons. Do not bake sockets or
   duplicate borders into the backing. HUDs do not need close buttons or input focus.
3. Verify the actual PNG alpha channel, dimensions, center and exterior pixels.
   A checkerboard preview or requested dimensions do not establish transparency
   or correct bounds. Never remove dark pixels by color key: they may be real art.
4. For requested asset slicing, retain the generated source and record measured
   source rectangles in a reproducible export helper. Crop disjoint parts, remove
   unused transparent margins and normalize with nearest-neighbor sampling.
   Do not assume equal atlas cells. Keep icon aspect ratio and consistent padding.
5. Establish logical GUI size independently of PNG resolution and GUI Scale.
   Record output sizes and placement rectangles. Bars must have separate track
   and fill: reveal fill by clipping its UV/width, not squeezing the entire image.
   Treat a decorated frame as fixed-aspect unless nine-slice regions are verified.
6. Put icon-only bitmap providers in a named font with documented private-use
   characters, height and ascent. Assign the font only to the glyph Component,
   keeping ordinary labels in Minecraft's default font. White preserves icon
   colors. Inspect current mappings to avoid collisions; do not modify default
   font or unrelated shared registrations for a feature-local icon.
7. Add blur:false/clamp:true metadata, validate JSON and texture links, inspect
   exported PNGs at intended scale, and compile according to project instructions.
   State separately whether the assets were wired into a HUD and tested in-game.

NeoWorld dungeon example: `docs/gui/dungeon/HUD_ASSETS.md`,
`docs/gui/dungeon/export-hud-assets.ps1`, runtime
`assets/neoworld/textures/gui/dungeon/hud/`, font `neoworld:dungeon_hud`.
Read these current project files before relying on their measurements or mappings.
