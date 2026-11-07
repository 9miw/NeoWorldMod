# Dragon Armor prototype plan

**Goal:** Approved black/gold fantasy dragon armor with crimson scales; helmet, chest/arms, leggings and boots.
**Architecture:** New Blender scene, copy the existing Epic Fight humanoid armature, preserve original scene and files. Rigid plates use explicit anatomical bone assignments. Material palette is packed as an atlas for later export.
**Tech Stack:** Blender bpy, existing Epic Fight rig.
**Spec:** User-approved design in this chat, 2026-10-26.

- [x] Inspect and copy source rig without changing its rest transforms.
- [x] Create four armor meshes, horns, dragon shoulder heads and chest emblem; save dragon_armor.blend and palette PNG.
- [x] Check every vertex weight, rest pose, finite geometry, and deformation in a temporary pose; restore rest pose.
- [x] Inspect front/three-quarter image, save preview and validation report; run compileJava after helper code creation.

Prototype only: no item registration or game-ready compatibility claim. No overwriting source files. Check knee/elbow clearance, all four parts, atlas references, and valid bone targets.

