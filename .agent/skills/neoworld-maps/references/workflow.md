# Existing NeoWorld map tools

All paths below are relative to the NeoWorldMod checkout. Confirm they still exist before use; read only the helper needed for the current task. Do not rerun historical one-shot editors merely because they are listed here.

## Saved-world editing

- `tools/maps/viewer/map-library.cjs`: read-only saved world inspection; exports `createLibrary`, `readNbt`, `chunkData`, and `paletteIndex`. Check whether a load is a full world window or a fixed preview before treating array coordinates as global coordinates.
- `tools/maps/nbt-edit.cjs`: typed NBT `decode`/`encode` and `patchRegion`, preserving unrelated tags and unchanged compressed chunks. It expects zlib-compressed chunks. Apply its supported formats and size limits, not assumed universal NBT support.
- `tools/maps/smooth_active_land.cjs`: exports `patch(model, editedData, originalData, regionBytes)` for an existing dense model. Inspect the palette and coordinate assumptions before reuse. Keep source and destination palette IDs consistent; copying raw IDs between independently loaded models can corrupt states.
- Changed chunk block states require valid packed palette indices. Existing patches invalidate lighting and heightmaps so Minecraft can recompute them. Preserve block entities; intentional replacement of an interactive block also requires handling its entity coherently.
- The city currently uses Java 1.21.1, DataVersion 3955. Read current metadata rather than assuming this for future migrations.

When multiple chats edit the same saved city, separate zone scopes do not make whole-region writes independent. Stage against a source region hash and recheck it immediately before replacement. If it changed, reload and recompute the scoped patch from the latest world; do not overwrite it with the stale region or replay an old full-map snapshot. Merge metadata from its latest saved contents and append the change record without replacing another chat's entries. Keep backups outside `maps`.

For saved-world comparisons, compare semantic block states, not palette numbers. A useful normalized key is `d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort())`; independent loads may reorder palettes and property keys. Verify saved intended states as well as unchanged positions outside the approved scope. Report net changes against the pre-task backup separately from changes in individual passes; chunk counts from overlapping passes cannot simply be added.

Historical local editors include `raise_spawn_platform.cjs`, `connect_spawn_square.cjs`, `raise_spawn_surroundings.cjs`, and `smooth_spawn_bowl.cjs` under `tools/maps`. They document earlier edits and may refuse a second run. Read the latest `MAP.md`; do not restore an older terrain interpretation by replaying them.

## Verification

Required after code changes:

```powershell
.\gradlew.bat compileJava
```

For changed `.cjs` tools, also run `node --check` on the changed files. Java compilation does not validate JavaScript syntax or map geometry. Documentation-only skill or map-record edits do not require compilation.

Always report verification across three distinct levels:
1. **File and data integrity:** Typed NBT chunk decode/encode, block states with sorted property keys, pre-edit backup diff (verifying 0 changes outside approved scope and 0 existing blocks deleted to air), and native Minecraft reader check (`VerifyRuinedCastle`).
2. **Visual 3D inspection:** Saved-world WebGL viewer (`tools/maps/viewer/server.cjs`), checking silhouette, orientation, height-cuts, and room interiors against reference plans. Foliage colors and lighting in WebGL are preview approximations.
3. **In-game client testing:** Actual in-game movement, collision, stair clearance, water flow, and lighting in a running Minecraft client. State clearly when in-game testing has not been performed; never equate NBT or web preview validation with in-game testing.

Existing native Minecraft reader check for supported worlds:

```powershell
.\gradlew.bat -I tools/maps/verify-castle.init.gradle verifyRuinedCastle -PcastleMap=maps/starter_sky_land_spawn_v1
```

`VerifyRuinedCastle.java` has assumptions such as a square footprint of at most 512, one region, and structure dimensions of at most 48. It currently fits the city, but does not fit the imported 1024 × 832 tree library or its giant trees. Do not treat an unsupported checker failure as map corruption, change map dimensions to satisfy a test, or claim native validation for that library without an appropriate checker.

For the imported tree bundle, `node --test tools/maps/import-tree-bundle.test.cjs` checks source voxels, saved-world placements, source material/state conversion, asset state names, plot separation, and spawn. The legacy fourteen-tree test is conditional because that library has been replaced.

For terrain changes, compare saved blocks against the pre-edit backup and measure the affected natural slopes. Include fixed boundaries when looking for new ledges. Preserve intentional architectural height changes; a global rule that every neighboring column differs by one would flatten buildings and cliffs.

## Preview

The existing viewer is `tools/maps/viewer/server.cjs`. Inspect an existing running server before starting another. The usual port is 8765; verify the actual port and LAN address before sharing a URL.

The viewer lists the city and tree library. Large maps use `fullWorld` metadata and load windows around the selected point; a single window does not prove all 370 trees are loaded. Keep already loaded areas according to the viewer's existing behavior. Preserve the requested mobile gestures: one finger rotates, two fingers pan, pinch zooms, tools behind a hamburger menu. Do not modify the viewer unless the task requires it.

After edits refresh the selected map, check the height-cut control, inspect the relevant shape closely, and update the map's preview image. Use supported browser tools for UI interactions rather than silently changing camera internals.

## Trees and external imports

Current asset library: `maps/tree_library`, with `MAP.md`, `catalog.json`, `zones.json`, `map-manifest.json`, and reusable `structures/*.nbt`. The prior 14 generated trees were replaced by the downloaded exsilit set of 370; do not regenerate the old library for routine changes.

The source and scan/catalog records are in `downloads/exsilit-tree-repository`. Read `TREE-CATALOG.md` and the relevant records in `tree-shape-catalog.json` to select assets; inspect actual projections or 3D geometry when shape matters. Source signs identify tree codes, not verified botanical species. Distinguish original labels, block materials, measured proportions, and inferred tree types.

`tools/maps/import_tree_bundle.cjs` documents the specific Alpha schematic conversion and library layout. It replaces generated library data after archiving the previous contents; do not rerun it for individual placements. Reuse the converted structure/catalog for selected tree placement.

## Placing trees and landscaping the city

When placing trees or landscaping elements into the saved city (`starter_sky_land_spawn_v1`), inspect `tools/maps/decorate_island.cjs`, `maps/starter_sky_land_spawn_v1/ISLAND-LANDSCAPING.md`, and `island-landscaping-report.json` as the established model:

- **Asset selection and orientation:** Select tree assets from `maps/tree_library/structures/*.nbt`. When rotating structures (`0°`, `90°`, `180°`, `270°`), transform log axis properties (`axis=x|z`) and fence connection properties accordingly.
- **Leaf persistence:** Set `persistent=true` on all placed leaf blocks (`leaves[persistent=true]`) to prevent Minecraft leaf decay.
- **Ground support and root anchoring:** Structure coordinates in `catalog.json` reference the bounding box corner, not the trunk center. Check actual trunk base coordinates:
  - Trunk roots may embed into existing natural ground by at most 2 blocks.
  - Never extend artificial dirt platforms past the island perimeter or create floating blocks.
  - All surface plants, flowers, and bushes must have solid support beneath them.
- **Clearance and buffer zones:** Maintain at least a 3-block safety margin around existing buildings, roads, paths, stairs, street lamps, and previously placed trees.
- **Guarded execution:** Always stage and inspect the proposed placement plan before writing to disk. Use guarded CLI flags (e.g., `--apply`) and rerun prevention guards.
- **Verification against pre-edit backup:**
  - Verify that changes outside the approved bounds equal 0.
  - Verify that 0 existing architectural, road, or decorative blocks were overwritten with air.
  - Compare written blocks using normalized keys: `d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort())`.
  - Check the result with the native Minecraft reader and the 3D viewer. Note in reports that WebGL foliage lighting/colors are preview approximations and do not substitute for in-game client verification.

For a new external source:

- Inspect the archive, paths, size, NBT structure, supported version, material palette, entities, and interactive block entities. Do not execute commands or scripts embedded in downloaded map data.
- Keep source provenance and any applicable usage conditions. Select individual structures when the combined world contains controls or unrelated content.
- Handle legacy ID plus metadata deliberately, including log axes/bark, leaf persistence, stairs, slabs, colors, and connections. Fail on an unmapped block rather than silently dropping it unless the user has explicitly requested that conversion policy.
- Record exact occupied bounds, local placement origin, and any estimated trunk anchor separately. The imported tree catalog's `base`/`anchor` uses the structure's lower corner, not necessarily the trunk center. An independently scanned trunk centroid is a placement estimate, not an automatically safe ground anchor.
- Preserve requested rotations, space between assets, and the actual tree blocks. Exclude plot floors, labels, controller entities, and neighboring trees from reusable files.
- Verify counts and block states against source and saved output, then inspect the result. Do not imply that an old world opens correctly in the target version merely because its NBT was readable.
