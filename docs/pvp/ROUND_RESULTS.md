# Defeat, Spectator breaks and Minecraft titles

PvP lethal damage remains cancelled: no death screen or death item/EXP drops.
The defeated player switches to Spectator. Both players' damage is paused while
the room is in COUNTDOWN, and native Minecraft Title/Subtitle show the result and
a server-owned five-second countdown (100 ticks).

For a normal Ranked round, after five seconds the original game modes are restored,
both players teleport to A/B, health/effects reset, and the existing three-second
fight countdown starts. A health-percentage round timeout follows the same break;
a drawn round pauses both players without choosing a Spectator loser.

For a final result, Title shows `ยินดีด้วย! คุณชนะ`, `คุณแพ้การแข่งขัน` or `เสมอ`.
Subtitle includes each player's round score, actual Rating change, committed Rating
and the return countdown. The result is also copied to chat for later reading.
Private rooms finish on their first defeat and show a five-second result without
Rating. After final display, both return to their pre-PvP locations and modes.

Rating is displayed only after the result transaction commits. Migration 7 adds
`<prefix>pvp_results_details` with each side's before/after scores, committed with
the result and both player ratings. Retrying a match returns the original receipt;
later Rating changes do not change the report. Historical matches from schema 6
have no invented deltas. If the database is unavailable, the final room stays paused
and retries; the five-second result display begins after saving succeeds.

Recovery SavedData captures original game mode alongside position and health.
Returning or reconnecting after a restart restores mode and camera, preventing a
player being stranded in Spectator. Legacy recovery entries lacking a mode use
Survival if the player is still Spectator. Disconnecting after combat begins,
including during a round break, immediately forfeits the match; reconnecting
only restores the return location/mode after the completed result window.

Compilation and isolated regression tests cover pause boundaries, recovery-mode
serialization, idempotent score receipts, transaction rollback and schema upgrades.
H2 and H2 MariaDB compatibility mode were exercised; actual MariaDB, Minecraft
titles and two-player Spectator/camera behavior still require runtime verification.
