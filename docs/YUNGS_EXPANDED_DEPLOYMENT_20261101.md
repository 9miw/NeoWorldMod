# Expanded YUNG deployment — 2026-11-01

Installed all 14 requested YUNG projects for Minecraft 1.21.1 / NeoForge, plus required TerraBlender. Skipped: none. The initial four remain unchanged; this deployment adds ten YUNG mods and one dependency.

## Added files

- TerraBlender-neoforge-1.21.1-4.1.0.8.jar (beta) — https://modrinth.com/version/6e8GCrLb
- YungsBetterWitchHuts-1.21.1-NeoForge-4.1.1.jar (release) — https://modrinth.com/version/AvedwcIe
- YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar (release) — https://modrinth.com/version/D6aZn0Em
- YungsBetterCaves-1.21.1-NeoForge-3.1.6.jar (release) — https://modrinth.com/version/Feo6YOjN
- YungsBetterMineshafts-1.21.1-NeoForge-5.1.1.jar (release) — https://modrinth.com/version/Go3nbneL
- YungsCaveBiomes-1.21.1-NeoForge-3.1.1.jar (release) — https://modrinth.com/version/HYGqXWqQ
- YungsBetterEndIsland-1.21.1-NeoForge-3.1.2.jar (release) — https://modrinth.com/version/I52NZ1qK
- YungsBetterNetherFortresses-1.21.1-NeoForge-3.1.5.jar (release) — https://modrinth.com/version/iopJiJQp
- YungsExtras-1.21.1-NeoForge-5.1.1.jar (release) — https://modrinth.com/version/N2EpMhR7
- YungsBridges-1.21.1-NeoForge-5.1.1.jar (release) — https://modrinth.com/version/urkCzBf6
- YungsBetterOceanMonuments-1.21.1-NeoForge-4.1.2.jar (release) — https://modrinth.com/version/yFjEcj2g

## Validation and dependencies

All files verified against publisher SHA512 and tagged for NeoForge / Minecraft 1.21.1. Official project teams include YUNGNICKYOUNG; TerraBlender source is Glitchfiend/TerraBlender. Checked dependency ranges inside JAR META-INF/neoforge.mods.toml. Existing YUNG API 5.1.9 satisfies API requirements. Cave Biomes requires API >=5.1.6, GeckoLib >=4.7.6 and TerraBlender >=4.1.0.8; GeckoLib 4.9.2 already installed. TerraBlender 4.1.0.8 is an official beta for this game/loader. No server/game/loader version change.

## Installed paths

- NeoWorldMod/run/mods and run-miw/mods (external runtime mods; no Java/build.gradle edit).
- C:/Users/miw/Documents/NeoNebula/distro/servers/NeoForge-1.21.1/forgemods/required
- cronus:/home/miw/services/neoworld/mods
- cronus:/home/miw/services/ambition-api/public/distro/servers/NeoForge-1.21.1/forgemods/required

Generated module metadata with npm start -- g distro. Only 11 added module entries merged into previous local and live distribution.json. Existing unrelated module entries, launcher metadata and files preserved. All 33 local installed copies verified; all 11 public launcher downloads verified against manifest MD5. compileJava passed.

## Backup and startup

Backup: cronus:/home/miw/backups/yungs-expanded-20261101/server-before.tar.gz (world/plugins/mods/config/defaultconfigs/customnpcs; gzip integrity passed, 287 MB) and distribution.json. Local prior manifest: stage/yungs-expanded-20261101/before/nebula-distribution.json.

Server latest.log lists all 14 YUNG mods and TerraBlender, then Done (21.439s); NeoWorld MariaDB initialization completed. Save-all flush succeeded.

## Existing End

No reset command run; no world or End files deleted/regenerated. Existing End has 10 files. All 10 SHA256 hashes matched the pre-install backup after startup (including region and entity files). Baselines saved remotely as end-before-sha256.json and end-after-sha256.json in the backup directory.

First startup emitted Better End Island ERROR: key missing bei_ExtraDragonFight. Developer PrimaryLevelDataMixin.java on branch 1.21.1 logs a missing key then falls back to ExtraFightData.DEFAULT and writes the data on save. Verified saved level.dat now contains bei_ExtraDragonFight; server continued to Done. This is evidence of first-install initialization, not a clean error-free startup claim.

Developer guidance: https://modrinth.com/mod/yungs-better-end-island says replacing an existing central island requires /end_island reset. That operation was intentionally not performed under the instruction to preserve the world. Existing island is not claimed to have the redesigned structures.

No End overhaul/dragon mods from the developer incompatibility list found among server mod filenames; existing datapack folder names were inspected but their full contents were not audited. No in-game client, dragon-fight or new terrain/structure exploration test performed. Startup and file checks do not establish full gameplay or balance compatibility.

Excluded projects remain absent: Paxi, Travelers Titles, YUNGs Menu Tweaks, Ribbits, The First City.
