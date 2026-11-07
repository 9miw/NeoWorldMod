# Dragon Armor — Blender prototype

Approved design: black steel, antique gold, crimson scales; horned helmet, dragon shoulder heads and raised chest crest. Four separate meshes using the copied existing 20-bone Epic Fight rig. Original scene is retained in the new .blend; original source file was not overwritten.

Open dragon_armor.blend and use scene `Dragon Armor • prototype`. The rig is hidden for presentation; unhide Dragon_EpicFight_Rig to inspect or pose. Materials use packed dragon_palette.png; UVs address solid palette cells. This is a palette-colored low-poly prototype, not detailed painted texture art.

Checks: 1,150 vertices, zero unweighted vertices, finite coordinates; Head, Hand_R and Leg_L pose smoke test moved the expected parts and rest pose was restored. See validation.json. Front and three-quarter previews were visually inspected. compileJava: BUILD SUCCESSFUL (UP-TO-DATE).

Not yet exported to Epic Fight armor JSON, registered as items, or verified in Minecraft. The copied rig comes from the existing source and has not been re-compared against the installed Epic Fight JAR in this task. A complete bend/collision pass and exporter compatibility check remain before game integration. Helper expects a source scene containing an Epic Fight armature; it creates a new scene and saves a new project file.

References:
- https://epicfight-docs.readthedocs.io/Guides/page1/
- https://epicfight-docs.readthedocs.io/Guides/page2/
- https://epicfight-docs.readthedocs.io/Guides/Armor/3Darmor_page2/

## Pixel knight revision

User-approved reference: attached knight armor showcase screenshot, https://sketchfab.com/3d-models/knight-armor-weapons-showcase-962fe8d523614abc8492df90db0ab127 . Page returned HTTP 403; screenshot used for visual direction. No model or texture downloaded. Original procedural geometry and 160x32 nearest-filtered pixel atlas.

Open dragon_armor_pixel.blend, scene Dragon Armor • pixel knight. Four weighted parts, flat stepped plates, thick borders, three-tier shoulders, curved square-section crown horns. Original dragon_armor.blend remains unchanged. Texture is packed and also saved as dragon_pixel_atlas.png. Preview: dragon_pixel_preview.png. Validation: validation_pixel.json, 1,664 vertices, zero unweighted, pose smoke test passed and rest pose restored. compileJava BUILD SUCCESSFUL. Visually reviewed the three-quarter render against the supplied screenshot: follows its block shapes, layered rims and pixel shading, with the approved black/gold/crimson palette. This is a reference-inspired prototype, not an exact reproduction. Full bend clearance and in-game export/testing remain pending.
