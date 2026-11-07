# YUNG structure mods deployment — 2026-11-01

Target: Minecraft 1.21.1, NeoForge (Cronus 21.1.248; launcher 21.1.250).

Installed release files:
- YungsApi-1.21.1-NeoForge-5.1.9.jar — https://modrinth.com/mod/yungs-api/version/2prKITKh
- YungsBetterStrongholds-1.21.1-NeoForge-5.1.3.jar — https://modrinth.com/mod/yungs-better-strongholds/version/8U0dIfSM
- YungsBetterDesertTemples-1.21.1-NeoForge-4.1.5.jar — https://modrinth.com/mod/yungs-better-desert-temples/version/GQ9iNWkI
- YungsBetterJungleTemples-1.21.1-NeoForge-3.1.2.jar — https://modrinth.com/mod/yungs-better-jungle-temples/version/P00i2hJn

Verified release/platform/game tags using Modrinth API, SHA512 against publisher hashes, and META-INF/neoforge.mods.toml inside all JARs. Strongholds and Desert Temples require API >=5.1.4; Jungle Temples >=5.1.3. API 5.1.9 satisfies all. Minecraft ranges [1.21,1.22), NeoForge >=21.0.0. No other required external dependency declared.

Installed locations:
- NeoWorldMod/run/mods and NeoWorldMod/run-miw/mods (external runtime mods; no build.gradle or Java source change).
- C:/Users/miw/Documents/NeoNebula/distro/servers/NeoForge-1.21.1/forgemods/required
- cronus:/home/miw/services/neoworld/mods
- cronus:/home/miw/services/ambition-api/public/distro/servers/NeoForge-1.21.1/forgemods/required

Ran NeoNebula npm start -- g distro successfully. Retained only the four new generated module entries alongside pre-existing local manifest entries. Published only these four entries into the live distribution.json, preserving all existing live modules and server metadata. Did not upload stale unrelated local mods or libraries.

Backup: cronus:/home/miw/backups/yungs-20261101/server-before.tar.gz (world, plugins, mods, config, defaultconfigs, customnpcs; gzip integrity passed), plus original live distribution.json. Local original launcher manifest: stage/yungs-20261101/before/nebula-distribution.json.

Verification:
- compileJava: BUILD SUCCESSFUL (no Java source changes).
- All 12 local installed copies match staged SHA512.
- All four public launcher downloads match manifest MD5.
- Server latest.log lists all four expected versions and Done (20.689s).
- NeoWorld MariaDB initialization completed after startup.
- No ERROR/FATAL entries found in the new latest.log at verification. Refmap WARN entries are present for the four mods; startup nevertheless completed.
- Existing world retained. No regeneration, deletion, or reset performed.
- In-game client launch and newly generated structure exploration have not been tested; startup success does not establish gameplay/balance compatibility.

Excluded: Better Mineshafts, Better Dungeons, Better Nether Fortresses, Better Ocean Monuments, Better Witch Huts.
