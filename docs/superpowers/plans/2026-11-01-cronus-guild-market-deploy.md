# Cronus Guild/Market Deployment Plan

**Goal:** Deploy pending154066 semantic blocks tocronus minecraft:overworld, preserving live server additions/NPC/player/mod data.

**Authorization:** User2026-11-01explicitly confirmed adding the delta tool and deploying all pending guild/market/court/repair work with backup and restart.

**Architecture:** Comparebefore-guild-campus baseline ddcd5502... with currentlocalb9b402...; three-way merge into a fresh stopped-server snapshot. Never upload the authoringworld as a replacement. Preserve all193serverblockentities and add30local entities only after collision checks. Noentity/player/customnpcs/dimensionfiles replaced.

**Tools:** stage_cronus_guild_market.cjs builds candidate/guards/conflict report; verify_cronus_guild_market.cjs independently validates full delta and unrelated cells, BEs, NPC points and all unchanged chunk tags. Native Minecraft reader validated local authoringworld. After codechangescompileJava.

- [x] Read-only live survey:154066deltas no blockconflicts,41CustomNPCs. RED verifier oldservermissingcompass.
- [x] Preflight candidate; alltargetstates, localBEcontents, existingBEtypedNBT, NPCsupports/headroom; syntax/compile and independentreview beforelivewrite.
- [x] Wait concurrent modmaintenance complete. Gracefulsave/stop, confirmexited, back up fullworld to timestampeddirectory outside services. Downloadstoppedregion/entities; restageagainstfreshbytes. Recordfullworldfilehashmanifest.
- [x] Uploadcandidateoutsideworld. Underdeploymentlock checkcontainerstillstopped/sourcehash/currentlocalhash andcandidatesha; atomicallyreplaceregiononly, sameowner/mode; compareworldfilemanifest changedonlyregion/r.0.0.mca. Rollbackregionfrombackup onvalidationfailure beforestart.
- [x] Startcontainer; waitserverReady (not merelyDocker running). Triggerfreshsave, downloadpostloadregion/entities; compare alltargetblocktypes,41NPCUUIDs andhomes/patrol, waypointids. Reportbackup/deployment/pendingin-gamevisualtest inMAP.md.

Ruling: Anothermaintenanceprocess stopped/restartedserver toinstallYUNGmods duringpreparation. Didnot interfere; waituntilitsreadybeforestartingourwindow. Noalterationofmods/configs inthisdeployment.

Completed: fullworld738filebackup/archive, offlinechangedonlyregion/r.0.0.mca. Candidate154066blocks95chunks, existing193BE+new30. Post-saveall154066targettypes/41UUIDsandNPCconfigurationverified. FinalserverReady15:15:04andSaved15:15:31Asia/Bangkok. FirstSIGTERMshutdownexceeded120seconds(exit137); recoveryarchivepreserved738files, worlddecoded1024chunks/NPC41, restartandexplicitflushsucceeded, consoleStop savedalldimensions/exited0. Finalbackupmadeaftercleanstopping. Originalunless-stoppedpolicyrestored. No in-gamevisualverification.
