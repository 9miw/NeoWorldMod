# Market Expansion Implementation Plan

> Execute inline using executing-plans. Scope approved by the user's continuation of the documented next market step. No server deployment.

Goal: Enlarge the current three market buildings to the surveyed dimensions, build separate overhanging roof envelopes and usable rooms, preserve current repairs and service points.

Spec: docs/maps/zone04-market-expansion-survey-20261031.md; accepted visual concept docs/maps/zone04-market-concept-20261031.png. These dimensions are agreed design metrics, not a claim that every AI-image pixel gives a block measurement.

Baseline: 3de64fce5d84eaea219f737a267a8863ccec7bea2c4449828b755fb82ad78f02. Patch latest saved world only; no restaging old builders.

- [x] Read current map, reference image and previous survey. Refresh read-only live service snapshots outside maps.
- [x] Create verifier, observe missing enlarged floors/mukh in current world, then create dry-run editor.
- [x] Merchant local31×29 centre70/197 rotated30°,901 floor cells. Retain ground103 and upper110, full main upper volume and forward wing, usable balcony/stairs, main roof maximum126, one-block roof projection independent of wall mask.
- [x] Food17×21 X94–110/Z177–197,357 cells; separate kitchen and sales; front gable ridge118, chimney121,19×23 roof envelope. Keep smith pathX111–113 clear below roofs.
- [x] Herbs main17×19 X115–131/Z179–197 plus external7×15 bayX132–138/Z182–196,428 cells; main roof121, lower glazed bay with independent eaves endingX139. Do not cut main-room area to make bay.
- [x] Scoped terrain supports/blending on existing land only. No void shoreline extension needed. Preserve natural trees, roadsX145+, north smith/stairsZ172, fountain and stalls. Ruling: allow western roof/awning end rows throughZ220 whereX<90; fountainX90–100/Z219–229 remains protected. Original surveyed body bounds endedZ218, which did not include every projecting eave.
- [x] Keep all existing barrel block entities and contents at exact positions; move only the three local cosmetic shop signs within their current chunks, preserving their text/data. Preserve live NPC points, homes, patrols and marker clearances; no entity movement or server writes.
- [x] Back up current map outside maps, check source hash again, apply guarded once-only scoped delta. Validate every saved block semantically, protected scope, exact floor dimensions, overhangs, paths/rooms/stairs and block entities.
- [x] Run node syntax, relevant verifier, native Minecraft reader and .\gradlew.bat compileJava. Independent final review and actual textured-viewer exterior/GOD/interior images. Record remaining visual differences and in-game limitation.

Files: tools/maps/expand_market_district.cjs; tools/maps/verify_market_expansion.cjs; maps/starter_sky_land_spawn_v1/market-expansion-v2-report.json; map-manifest.json; zones.json; MAP.md; region/r.0.0.mca. Report filename is not a new map identity.

Ruling: detailed preflight found an isolated five-block oak-leaf shrub below the earlier roof-height scan; moved it whole offset[-8,-2,0] to supported vacant ground, preserving block states. The five destination cells atX41–43 are explicit exceptions to the principal edit bounds.

Completed local construction and verification; visual acceptance is pending user review. Report docs/maps/zone04-market-expanded-20261031.md. Native3556921blocks/177states/30BE and compileJava succeeded. No deployment or in-game test.

