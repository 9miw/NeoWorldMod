# Guild Forecourt Implementation Plan

> **For agentic workers:** Use executing-plans to implement inline. One independent final review.

**Goal:** Build the approved compass forecourt concept in the existing city, preserving adjacent districts and routes.

**Architecture:** Guarded scoped voxel patch against current region hash, reusing typed NBT patch helpers. Independent saved-world verifier compares all semantic cells against backup and checks routes, monument, support and decoration.

**Tech Stack:** Node CommonJS, Anvil NBT, existing WebGL viewer, Gradle compileJava/native reader.

**Spec:** docs/maps/zone06-forecourt-design.md; approved generated concept exec-b4bad79a-0ece-49ea-b259-ddac72c5e2f9.png.

## Constraints and decisions

- User approved concept and implementation 2026-11-01. Keep 39×35 footprint X225–263/Z207–241; floor107, entry stair unchanged. Main laneX240–248 remains clear.
- Monument5×5 relocated from244/224 to257/233 with all original states and support; no NPC/waypoint movement. Small pergolas8×9 include beam/eave envelopes.
- Colors/materials follow approved compass concept; generated image is not a measured blueprint. Preserve existing buildings, bridge, city road, trees and server. No deployment.
- Source SHA2564ae29206247c08e05c537b98de5c88355164d3a86a48219ba4718c86e823e166. Back up outsidemaps. Restaging limited to this task's own latest hash and baseline, never other edits.
- Narrow mask Y100–119 for terrain supports/decor only. Abort on block entities, trees or unknown materials in clearing volume. Existing side paths and all NPC reserve columns kept accessible.

## Tasks

- [x] Survey saved blocks/site; save concept reference; create independent checks, observe old map failure for new floor mosaic.
- [x] Implement tools/maps/build_guild_forecourt.cjs and tools/maps/verify_guild_forecourt.cjs with dry-run/apply guards. Check saved net diff, monument state preservation, support/headroom, 9-wide lane, pergola3-wide access, lights/plant supports and all existing approach routes.
- [x] Apply backup-guarded scoped patch, run syntax/geometry/native checks and compileJava.
- [ ] Inspect actual exterior/top views, resolve visible deviations, request independent review, record final dimensions/hash/backup/limitations inMAP.md and report.

## Review focus

- Pergola roof outside core must remain inside edit mask; no furniture on entry lane or side bypasses.
- Terrain support must join existing ground, never float at oval corners.
- Preserve30 existing block entities andlevel.dat, all unrelated semantic blocks, source monument typed states.
- Keep original stairs and external route elevations. Saved-world/render validation does not imply in-game collision/nightlight testing.

Ruling: Preserve original49-voxel memorial rather than generated-image replacement. Final saved geometry checks/native/compile/review passed. Final visual snapshot remains pending: user physically stopped Computer Use withEscape. Do not label intermediate image as final.
