---
name: neoworld-armor
description: Create, update, or diagnose NeoWorld armor sets compatible with Epic Fight, including Blender transforms and skinning, worn mesh JSON, textures, and inventory icons. Use neoworld-items alongside this skill for item registration or attributes; exclude unrelated fashion accessories and GUI layout.
---

# NeoWorld Armor

## Scope and authorization

- Follow the project's AGENTS.md and the user's current authorization before modifying code or assets. A diagnosis alone does not authorize changes. Explain the concrete change before asking for any required confirmation; keep confirmed authorization across turns.
- Do not automatically compile, build, package, install a JAR, or launch the game. Do those only when requested. Perform relevant read-only resource checks and distinguish them from runtime verification.
- Keep armor worn meshes, inventory icons, and Blender source files distinct. A request to change an icon should not modify the worn mesh or armor attributes.
- Preserve existing registry IDs unless the user requests renaming. For an approved rename, update item registration, creative tab, translations, datagen, item model, worn mesh filename, and relevant export helpers. Explain that old saved items do not migrate automatically.

## Inspect the actual input

- Inspect only the supplied files and their relevant references. Do not assume a previous export or similarly named Blender template is the current source.
- Identify whether JSON contains Epic Fight `vertices` or item-model `elements`/a custom `loader`. Do not put one format in the other's resource directory.
- Record source vertex counts, texture references, coordinate bounds, and used joint IDs. Compare the source, active resource, processed resource, and running artifact only when needed to diagnose stale output.
- Use the installed Epic Fight and NeoForge dependency versions to verify behavior. In the version used by this project, worn armor resolves from `assets/<namespace>/animmodels/armor/<item_id>.json`; verify this again if dependencies change.

## Blender and worn armor

- Use a Blender version capable of opening the source file. A newer .blend may fail in Blender 3.6; do not interpret that alone as file corruption.
- Inspect mesh local coordinates and `matrix_world`, parent inverse, rotation, scale, modifiers, rig rest matrices, current Pose, and vertex groups before altering coordinates.
- The installed Minecraft JSON Exporter has exported `vertex.co` without mesh object transforms. Verify the actual exporter code before relying on that behavior. A mesh rotated 90 degrees in Object Mode can look correct in Blender while exporting incorrect local coordinates.
- Prefer fixing the Blender source: apply necessary mesh transforms while preserving world geometry, retain one valid Armature modifier, and export in Rest Pose. A modifier with no target is inactive; do not claim that it causes double deformation.
- Preserve originals and save a separate `_fixed.blend` unless the user authorizes overwriting. Avoid changing unrelated reference objects, animations, or shared rig data.
- Match rig joint names, hierarchy, ordering, and rest transforms to the installed Epic Fight humanoid rig. Export weight joint indices using that canonical ordering; do not assume vertex-group order is always correct.
- Verify every exported vertex has valid deform-bone weights. A helmet normally follows Head; chest armor may include torso and both arms; leggings and boots may include both legs. Do not invent weights from position alone when a source rig is available.
- Export each equipped armor item as a separate mesh JSON. Multiple body parts may share one item mesh and use separate bone weights.
- Do not export an animated Pose with Armature deformation baked into the mesh; Epic Fight applies its own deformation at runtime. Inspect modifier settings before choosing whether to apply other modifiers.
- The project's Epic Fight loader converts Blender coordinates with an X rotation of -90 degrees. When a verified input uses Y-up coordinates already, conversion to Blender coordinates is `(x,y,z) -> (x,-z,y)` for positions and normals. Do not apply this universally, rotate twice, or guess an offset; diagnose the source space first.
- Check array sizes, vertex/normal/index bounds, influence counts and joint/weight references. Preserve UVs, topology and skinning unless changing them is part of the task. Verify transform round trips or world-coordinate invariance as appropriate.
- Worn texture can be set through `render_properties.texture_path` in the currently installed Epic Fight loader. Resource paths must be lowercase and point to an existing PNG. Inspect intended material/texture rather than guessing from an item's name.

## Inventory icons

- Preserve supplied geometry, UVs, display transforms and intended texture variant. The Duelist worn armor and its inventory icons have intentionally used different texture variants; do not silently replace one with the other.
- For Minecraft 1.21.1 vanilla element models, rotation is single-axis with angles -45, -22.5, 0, 22.5 or 45 degrees. Newer Blockbench exports may contain arbitrary angles or x/y/z rotations that this parser rejects.
- If an icon is purple/black, read the relevant model-loading error before assuming a missing texture. Unsupported rotation can reject the entire model even when its file and texture exist.
- For arbitrary rotations, NeoForge's installed OBJ loader is an available solution. Verify its local API/schema, bake element rotations around their original pivots into OBJ vertices, and preserve face orientation, UV rotation, display transforms, and texture references.
- An OBJ item JSON uses `loader: neoforge:obj` and an existing namespaced `model` resource. Check relative `mtllib` resolution, MTL material names, `map_Kd`, and UV flipping. Do not copy `elements` with unsupported rotations into a vanilla item model.
- Validate OBJ face indices, finite coordinates, normals/winding, UVs and model/material/texture paths. Omit zero-area faces only when they have no renderable area; retain genuine planar geometry.

## Project integration and checks

- Use neoworld-items for ArmorItem registration and the current Minecraft armor API. Reuse established materials/stats unless the user requests different behavior.
- Update ModItems, ModCreativeTabs, ModLanguageProvider and the relevant English/Thai resource translations. Inspect the current merged-language workflow instead of introducing duplicate output resources.
- Existing project helpers live in `docs/epicfight tool/` and `docs/model/Duelist/`. Read a relevant helper before reusing it: some have hardcoded Blender versions, source filenames, dependency versions or texture choices. Run only the requested pieces; do not regenerate unrelated artifacts.
- Verify paths and translations without a build by default. When a build is explicitly requested, also verify the final JAR contains the intended resource bytes and no obsolete renamed resources.
- Report the resulting item IDs/files and what was checked. Never claim the armor fits or animates correctly in-game based solely on JSON parsing, Blender alignment or a successful build.
