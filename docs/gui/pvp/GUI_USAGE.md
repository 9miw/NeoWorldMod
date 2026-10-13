# PvP selection GUI

Open with `/neow pvp` or `/neoworld pvp`, or select PvP in the NeoWorld Hub.
The screen follows `pvp-selection-mockup-v1.png` and displays actual server Rating
and numeric leaderboard position. A player without a ranked win/loss sees
`ยังไม่มีอันดับ`; database failure displays unavailable values rather than 1000/0.

- Create Room opens an arena chooser, then creates a private room. The room view
  displays its number and join command, member readiness, Ready/Unready, Start,
  and Leave controls. Opponents can join with the displayed command.
- Ranked opens an arena chooser and joins the existing ranked queue. Successfully
  joining closes the GUI and shows a compact middle-right queue HUD. Reopening the
  GUI while queued shows Cancel Queue. Matching or starting closes the GUI.
- Leaderboard shows the top 10. Numeric personal rank is calculated beyond Top 10
  with the same Rating/wins/UUID ordering as the leaderboard.
- GUI requests a snapshot once when opened, after explicit gameplay actions, and
  when opening the leaderboard. It does not poll or retry automatically and
  rejects replies for an old request. Reopen the GUI to refresh external changes.
  Gameplay actions are validated by the server manager. Rating reads run off the
  game thread through the existing database pool. Personal Rating/position cache
  lasts 30 seconds, coalesces overlapping requests per player and is invalidated
  after a committed match result. Requests reuse these values while the cache
  is valid. Display reads use ordinary SELECT, no FOR UPDATE,
  transaction or player insertion. A new player displays the initial 1000 until
  ranked participation creates their persistent row.
  Top 10 is requested only on the leaderboard page and uses one shared 30-second
  cache for all viewers. Failed reads back off for five seconds. Personal cache
  retains at most 512 entries and clears on server stop. Foreign database updates
  may remain cached until TTL; local committed match results invalidate all ranks.

Runtime assets: `textures/gui/pvp/selection_frame.png` (1672x941), `swords.png`
and `trophy.png` (1254x1254). Background, icons and shared NeoButton states are
separate. Text, skin, names and values are rendered dynamically. PNG sampling
disables blur and enables clamp. Coordinates and hitboxes share one scale factor.
No fonts are added. Protocol version 12 requires matching client/server mod code.
Ranked pairs now open [MATCH_CONFIRM_USAGE.md](MATCH_CONFIRM_USAGE.md) before entering
the arena; both players must accept within 30 seconds.

## Queue HUD and cancellation

The queue HUD is approximately scoreboard-sized: 116–154 GUI pixels wide, with
five compact lines, a translucent dark backing and thin silver border. It is
anchored at the right edge and vertically centered. It displays server-owned wait
time and Rating, plus `ยกเลิก [K]`. The hint follows the configured key binding.
Default K is registered in Controls under NeoWorld as Cancel ranked PvP queue.
K works during ordinary gameplay only; it does not run while typing chat or using
a screen. It sends a queue-only cancellation request, so a late key press after
matchmaking cannot leave a room or surrender. Queue state pushes every second
without database reads and clears on cancel, match, invalidation or disconnect.
The HUD is hidden with F1 and while a GUI is open. It has no clickable mouse button.

Compile and regression checks cover room state and payload round trips. Assets
were inspected for transparency and dimensions. Actual in-game screenshots at
different GUI scales, two-player interaction and dedicated-server loading remain
runtime verification steps; this document does not claim those checks passed.
