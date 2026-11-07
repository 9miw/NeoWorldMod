# Halloween Armor Implementation Plan

**Goal:** Model the approved pumpkin knight mockup as four Epic Fight-skinned armor pieces.
**Architecture:** Copy the verified canonical humanoid rig; new scene and files only. Author -Y-facing geometry, preserve identity mesh transforms, export with the verified Epic Fight Y reflection plus reversed triangle winding. Keep all exports staged under this folder.
**Tech Stack:** Blender 5.2 bpy, installed Epic Fight JSON exporter, 256x256 nearest-filtered pixel atlas.
**Spec:** reference_mockup.png and user confirmation, 2026-10-30. Dark steel, orange pumpkin rims, purple ragged cloth, green vines, amber carved eyes. Helmet, layered chest/arms, leggings, boots.

- [x] Build layered pumpkin helmet, carved shoulder faces, V-bordered breastplate, belt, tassets and boots with anatomy-specific weights.
- [x] Check all vertex weights, mesh transforms, finite data, normalized normals and UVs; compare rig rest transforms to installed Epic Fight biped.
- [x] Save front/back previews and temporary bent-limb pose preview; restore rest pose.
- [x] Export four Epic Fight JSON meshes and texture into export/assets/neoworld; verify arrays, joint IDs and references.
- [x] Run compileJava and report actual verification scope. Game item registration and runtime testing are outside this model-authoring scope.

Review focus: front/back convention, arm/knee clearance, no accidentally baked animation, textures packed plus on disk, no changes to DragonArmor files.

