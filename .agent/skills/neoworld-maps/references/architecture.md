# Detailed Minecraft architecture

Read this for castle/building edits, usable interiors, and mockup-to-block architectural work. Read the current map's `MAP.md` first; this reference is a method, not an instruction to rebuild completed areas.

## Establish the actual design

- Inspect the reference image rather than treating an export of the existing building as the target. If the user rejected the shape or position, adding surface details to that same mass does not resolve the rejection.
- Use the user's chosen hero view to establish silhouette, depth order, round versus rectangular volumes, setbacks, and height hierarchy. AI-generated front/top/cutaway panels can disagree. Identify the discrepancy and use approved coordinates and dimensions for construction; do not silently average incompatible panels or claim pixel-perfect scale.
- Separate the building envelope from outer defenses, gardens, terrain support, and projecting trim. A stated core size need not contain every buttress or roof overhang. Record their bounds separately.
- Distinguish ground/court level, floor-block Y, standing height (player feet Y = floor block Y + 1), ceiling, wall-walk deck, roof, and flags. A lower arrival court needs stairs up to the building, not buried steps beneath a higher terrace.
- When northern land expansion is authorized, choose enough room for usable interiors and the intended depth before fitting the enclosing wall. Expand only the required support area, tie the rock mass into the existing island, and preserve neighboring zones and their routes.

## Build useful space before ornament

Reserve clear interiors, doors, stairs and landings before furnishing rooms. Setbacks, round towers, and several height levels should carry the intended castle silhouette before adding trim. Use stairs and slabs for plinths, arch shoulders, inverted corbels and caps where their actual shape helps; do not substitute an unrelated color simply because it provides a stair block.

At adjoining shells, check both wall thicknesses and every occupied floor. An opening through one face can remain blocked by the second wall, an overlapping tower, a counter or a railing. When changing levels, remove obsolete supporting surfaces above the new steps as well as opening headroom; placing a low stair under an old high floor does not create a usable flight.

Validate circulation from the existing approach through the court, main hall, side wings, tower rooms, upper floors, roof walks and preserved defenses that the task promises to connect. Include intermediate landings and floor penetrations. For framed windows, make the recess in the inner wall layer as intended; glass over a solid backing is not a view through the wall.

## Finish and inspect

- Break broad facades into deliberate bays: layered bases, projecting piers, recessed window surrounds, horizontal bands, brackets, parapets, heraldry and lighting. Furniture should describe the room's function while leaving the agreed circulation clear. Avoid adding unrelated gameplay/NPC integration merely because the room is furnished.
- Evaluate colors with actual Vanilla assets. A color-coded preview can conceal a palette mismatch: prismarine stairs appear green in Vanilla and are not blue-concrete stairs. Vanilla has no blue-concrete stair; use stepped blue blocks or propose a compatible alternative when blue is essential.
- Check contained water levels against the real basin rim. Validate interactive blocks and their block entities if used; decorative room labels alone do not prove functional storage or machinery.
- Keep preview data and metadata consistent with the final saved world after corrections. Distinguish current output from historical proposed layouts and mark superseded records clearly.
- Inspect the saved geometry in the main textured viewer as well as any isolated shape preview. State when a preview omits the island underside, uses material colors, or hides layers without creating cut caps. Save a useful framed screenshot.
- Half-block route graphs with point headroom are evidence of connectivity, not full player hitbox, water-flow, lighting or in-game verification. Report that limit and whether the game was actually tested.

## Current zone 07 records and helpers

Paths below are relative to the project. Confirm current records before relying on them; do not replay their editors for routine changes.

- `maps/starter_sky_land_spawn_v1/MAP.md`: authoritative history, approved current scope, and overall map progress.
- `docs/maps/zone07-blueprint/COMPLETION.md`: authoritative source for completed castle coordinates, floor levels, changed bounds, room layouts, verification, and backup references. Earlier `layout.json` and `blocks.json` are historical proposals/snapshots, not the current castle.
- `maps/starter_sky_land_spawn_v1/castle-completion-report.json`: saved-world completion and subsequent roof-color correction audit.
- `tools/maps/finish_governor_castle.cjs`, `finish_castle_blue_roofs.cjs`, and `build_front_defense.cjs`: guarded historical local editors; inspect for reusable mechanics, do not rerun to regenerate the castle.
- `tools/maps/viewer/server.cjs`: main saved-world viewer, usually port 8765. `tools/maps/serve_castle_blueprint.cjs`: separate read-only architectural preview, usually port 8766; current page `castle-complete-3d.html`. Verify running services and current LAN address before sharing links.
