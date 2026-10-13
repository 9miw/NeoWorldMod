# Ranked 1vs1

Defeat now uses Spectator and a five-second break. Final results use native
Minecraft Title/Subtitle after the score transaction commits; see
[ROUND_RESULTS.md](ROUND_RESULTS.md).

Commands use either `/neow` or `/neoworld`:

- `/neow pvp queue arena_1`: enter the ranked queue for this registered arena.
- `/neow pvp unqueue`: leave the queue before a match is assigned.
- `/neow pvp rank`: your Elo Rating, wins and losses.
- `/neow pvp top`: top 10 players with at least one ranked win/loss.
- `/neow pvp room leave`: surrender an assigned match.

Private `room create/join` matches never change Rating. Ranked opponents are
selected by the server; players cannot directly join a ranked room. Queue entry
requires a valid arena, no current room, no pending recovery, and a player outside
Creative/Spectator mode. Players in the queue select the same arena. Matching
starts within 100 Rating and expands by 50 every 20 seconds to a maximum of 600.
The same pair cannot be rematched for 10 minutes during this server session.
This cooldown resets on restart and does not prevent alternate-account farming.

Matched players first have a 30-second acceptance window. Both must press Ready;
unready players receive a 60-second queue suspension and ready players return to
their original queue without Elo changes. See
[MATCH_CONFIRM_USAGE.md](../gui/pvp/MATCH_CONFIRM_USAGE.md).
Accepted matches start with a 3-second countdown, using actual gear and
attributes. First to two round wins wins the match. Each round resets health,
effects, absorption, fire and position; inventory, durability and consumables
are not refunded. Round timeout is 3 minutes: compare percentage of health,
equal percentages draw the round. The overall 10-minute limit compares round
wins, with an equal score yielding a match draw.

Initial Rating is 1000. Elo uses K=32 and expectation based on Rating difference;
equally rated players gain/lose 16 for a match win/loss. A draw uses score 0.5.
Top ranking sorts by Rating, then wins, then UUID for deterministic tie-breaking.
The `rank` command shows Rating and W/L; it does not report a numeric position
outside the top 10. A draw alone does not qualify a new player for the leaderboard.

After the first round begins, surrender or leaving the bounds/dimension forfeits
the match. Disconnecting after combat has started forfeits immediately; there is
no reconnect grace period. Later disconnections cannot reverse a final result.
Cancelling before the
first round or stopping the server voids unfinished matches without Rating loss.

Schema migration 6 adds `<prefix>pvp_ratings` and `<prefix>pvp_results` to the
configured shared database. Both player ratings and the match UUID are committed
in one transaction. Repeating a saved match UUID cannot award points twice.
On database failure, the match pauses while saving retries every 5 seconds.
Final results awaiting commit are also saved in the world's
`neoworld_pvp_pending_results.dat` and retried after restart. Normal autosave
governs this outbox; abrupt process/storage failure before a world save can lose
an uncommitted result. Unfinished matches are not resumed across restarts.

Verification: `compileJava`, `pvpRegressionTest`, `regressionTest`. Tests cover Elo,
best-of-three, forfeits, pending-result serialization, idempotence, transaction
rollback and schema upgrades using isolated H2 databases, including MariaDB
compatibility mode. Actual MariaDB and two-player in-game behavior require runtime
verification. Queue management has no GUI at this stage.
