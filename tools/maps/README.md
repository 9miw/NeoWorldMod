# Ruined castle map tools

Generate a new, non-existing output folder with Node.js:

```text
node tools/maps/create_ruined_castle.cjs
```

Default output: `maps/ruined_castle_v1`. Pass another destination as the first argument for a new revision; the generator refuses existing output folders.

Verify the default deliverable with Minecraft's native NBT/Anvil reader:

```text
.\gradlew.bat compileJava -I tools/maps/verify-castle.init.gradle verifyRuinedCastle
```

Validation checks the generator metadata fields, all 100 chunks and packed block palettes, 150 x 150 footprint, empty container positions, and unobstructed spawn. The generator independently checks that each of the nine room interiors is reachable from spawn. Registry bootstrap and in-game lighting/rendering require a running NeoForge instance and are not covered by the headless validator. The PNG is a simple material-colour projection of actual blocks, not an in-game screenshot.

The map is architecture only: five combat rooms, two treasure rooms with empty chests, central boss courtyard and safe entry plaza. No mobs, spawners, reward loot tables, scripted gates or command blocks. The forge's bright channels are glass over shroomlight, without flowing lava. Use the map's README for import and teleport commands.
