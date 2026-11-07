---
name: neoworld-maps
description: Design, create, edit, import, and inspect NeoWorld Minecraft maps, terrain, spawn areas, and reusable tree assets. Use for mockup-to-block work and the local 3D map preview; exclude unrelated gameplay systems and Minecraft GUI screens.
---

# NeoWorld Maps

Work in the NeoWorldMod checkout. Read its applicable `AGENTS.md` and the target map's `MAP.md` before choosing dimensions or editing blocks. Current map records and the user's latest instructions take precedence over old generators, archived drafts, and examples in this skill.

## Choose the right operation

- For design or mockup requests, deliver the requested analysis or image first. A concept is not authorization to generate a world.
- For map edits, patch the saved world in place. The active city is `maps/starter_sky_land_spawn_v1`; the separately authorized reusable tree library is `maps/tree_library`. Keep their identities stable. Create another map only when explicitly requested.
- For detailed buildings, castles, usable interiors, or mismatches between a mockup and built architecture, read [references/architecture.md](references/architecture.md). Current castle coordinates and records reside in `docs/maps/zone07-blueprint/COMPLETION.md`, while `MAP.md` tracks overall progress; `layout.json` and `blocks.json` are historical proposals.
- For reusable tree assets, placing trees into the city, island landscaping, and external schematics, read [references/workflow.md](references/workflow.md). Tree placement records and landscape audits reside in `maps/starter_sky_land_spawn_v1/ISLAND-LANDSCAPING.md`.
- For preview work, use the existing read-only viewer. Do not add map-merging or composition controls. Read [references/workflow.md](references/workflow.md) for the current tools and limitations.

Honor the project's code-change approval rules. Explain proposed code changes and obtain explicit confirmation before creating or changing code. Inspection and documentation can proceed without code approval; documentation-only edits do not require `compileJava`. Existing confirmation covers the agreed scope; proceed to completion without re-confirming repeatedly.

## Translate a mockup into terrain and buildings

Inspect the actual reference image. Establish orientation, island outline, relative zone positions, widths, heights, routes, and available interior space before adding details. A perspective picture has no reliable block scale: distinguish measured constraints, agreed dimensions, and estimates. Do not claim a 1:1 match without an agreed scale and visual comparison.

For this sky-town design, compare the arrival area with the reference at `tools/maps/viewer/reference-plan/reference.jpg`. The low spawn terrace is recessed into higher surrounding land, opens toward the front island edge, and climbs toward the city. Raising only a paved square on retaining walls does not create that landform. Treat this as the current design's requirement, not a rule for every map.

Plan a small set of local coordinates and sections through important elevation changes. Distinguish floor block Y from player feet Y (feet Y = floor block Y + 1). Connect roads to the surrounding land; shape the terrain mass before decorating it. Keep useful building interiors and walking routes rather than using solid decorative shells when functional rooms are requested.

For smooth terrain, use a transition wide enough for the agreed height difference, soften both its start and finish, and check neighboring surface heights. Check natural slopes separately from intentional cliffs and architecture. A circular outline with a vertical drop is still a hard pit. Preserve the voxel style; do not promise a continuous mesh surface.

## Edit and verify the saved map

- Define the local edit bounds and protected structures. Inspect actual blocks, including manual edits. Back up the affected map outside active `maps` before mutation.
- Guard against concurrent edits: before applying region writes, check the source region hash. If it changed, reload and re-stage the scoped patch from the latest world to avoid overwriting concurrent work with stale region data.
- Reuse typed NBT and region-patching helpers. Preserve unrelated chunks, tags, block entities, and properties. Initial world generators are provenance, not routine edit commands.
- Stage calculations before writing. Preserve the island footprint unless widening it is requested. Clear obsolete blocks only within the approved edit and avoid burying trees, lamps, stairs, or entrances.
- Verify the saved result against the backup: only intended voxels changed; protected structures and spawn clearance remain valid. For routes, account for actual stair orientation, half-block surfaces, landings, and two-block headroom. For slopes, measure relevant neighbor-height jumps.
- After code changes run `.\gradlew.bat compileJava` and inspect the result. Do not run a full build/package by default. Run relevant map validation within its supported dimensions; passing compilation alone does not validate a world. Documentation-only edits do not require compilation.
- Inspect actual 3D blocks after refreshing the viewer. Compare the relevant shape and elevation with the reference; conceptual images do not prove the saved geometry. Save an updated preview when appearance changes.
- Update the target `MAP.md` with the current dimensions, coordinates, levels, finished/pending work, backup, and verification. Mark superseded design facts clearly. Reuse existing reports where appropriate instead of accumulating map versions.

Clearly distinguish three verification levels: (1) file-level NBT/JSON data integrity, (2) 3D WebGL viewer geometry and material inspection, and (3) real in-game client verification. Report the concrete change, a useful preview/link, verification outcome, and whether in-game testing occurred. Do not equate a web preview or NBT validation with successful in-game testing.
