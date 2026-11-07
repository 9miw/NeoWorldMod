# Client quest navigation migration

User approved: server sends initial road graph, changed graph records, server-owned quest target and clear actions. Client chooses routes and generates visible ribbon/ring; server no longer periodically sends ribbon points.

1. Add bounded paged graph reset/delta, target and resync codecs; test atomic graph cache, revisions, delta removal and no-op updates.
2. Sync graph on login/change, retaining per-player revision and explicit resync support. Server continues to own quest state, selected NPC and rewards; send goal only when changed.
3. Share world collision helpers with client Level and move old 8-start/4-exit shortest-route policy to a common pathfinder. Use stored graph for unloaded roads, recheck loaded local obstacles. Preserve reverted routing policy.
4. Client caches graph and goal across ordinary ticks without a 2-second timeout, recalculates locally, and creates the same 24-block white/cyan ribbon and ring. Clear on stop/disconnect; hide outside target dimension or while dead/spectator.
5. Remove old server trail payload/TTL state, update protocol, tests and docs. Run compileJava, questNavigationRegressionTest and regressionTest. Report in-game/visual gaps explicitly.

No map changes, no quest/reward mutations and no full build/package.
