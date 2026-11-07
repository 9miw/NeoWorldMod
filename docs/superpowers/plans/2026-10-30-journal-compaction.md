# Journal snapshot compaction

Approved scope: Reduce journal snapshot duplication; inspect ranking read-only and report findings.

Keep the newest applied snapshot per player/world and every unapplied snapshot. Replace only older applied BLOB payloads with an empty BLOB tombstone; retain operation IDs, owners, types, results and sequence so duplicate requests cannot execute again. Acknowledge and compact atomically. Run bounded background backfill on server startup for historical inactive players; no schema changes or manual live DB commands. Retained metadata continues to grow, but full inventory snapshots no longer grow per successful transaction.

- [x] Observe a failing two-operation retention assertion.
- [x] Implement scoped compaction and bounded startup backfill.
- [x] Verify pending recovery, latest owner recovery, duplicate requests and player/world isolation in H2 and H2 MariaDB compatibility mode.
- [x] Run compileJava, regressionTest and rankingRegressionTest; report ranking findings without modifying ranking code.

Physical database file size is not guaranteed to shrink immediately after BLOB compaction. No full database rebuild/vacuum, record deletion or analytics-receipt pruning is authorized in this change.

Validation: compileJava, regressionTest, rankingRegressionTest and shopRegressionTest pass. Journal suite includes 217 fault/restart checks, including 26 compaction checks across H2 and H2 MariaDB mode. Focused review found no correctness blocker in acknowledgement/compaction ordering, pending recovery or duplicate receipts. No in-game test, real MariaDB execution or live DB compaction was performed by the agent. Runtime startup backfill will process existing applied payloads in batches of 128 player/world groups.

Ranking read-only findings:
- RankingService captures online attachments on the server thread, merges offline player NBT asynchronously, and uses SQL for PvP. No independent wallet authority or additional Silver balance is created by ranking.
- RankingTable orders LEVEL by level then current EXP; displayed cumulative EXP does not change that order. SILVER counts the player's wallet, excluding market proceeds not yet claimed and physical coins.
- SavedRankingPlayers.read aborts the offline scan if any player file fails decoding. RankingService then replies with a load failure for the whole LEVEL/SILVER table. This is an intentional fail-closed policy, not silent partial ranking; operational handling/reporting for individual corrupt files could be improved in a separate authorized change.
- Offline NBT cache TTL is 30 seconds. Every refresh scans all player .dat files; it runs off-thread but scales with the number of saved players. Online attachment values supersede the cached offline entry.
- RankingAppearanceEvents captures appearance at the first login of a Bangkok calendar day. Visual equipment therefore may lag the currently worn equipment by design.
- Existing table, cache, appearance, layout and network/save regression suites pass; no ranking source was edited.
