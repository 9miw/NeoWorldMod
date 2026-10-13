# Private 1vs1 rooms

After defeat, the loser enters Spectator and the private-room result is displayed
for five seconds before returning both players. See [ROUND_RESULTS.md](ROUND_RESULTS.md).

Use `/neow` or `/neoworld`. Room commands are available to ordinary players.
Arena setup requires OP level 2. Save and register a complete arena first.

1. Owner: `/neow pvp room create arena_1` (returns a room number).
2. Opponent: `/neow pvp room join 1`.
3. Both: `/neow pvp room ready`.
4. Owner: `/neow pvp room start`.

Other commands:
- `/neow pvp room list`: rooms and their occupancy/state.
- `/neow pvp room info`: your room, owner and readiness.
- `/neow pvp room unready`: cancel readiness before starting.
- `/neow pvp room leave`: leave; after starting this ends the room for both players.
- `/neow pvp room cancel`: owner closes the room.

Joining/leaving resets readiness. Owner leaving closes the room. Each player can
belong to one room. Both players must be online, alive, unmounted, awake and
outside the PvP dimension when starting. Spawn points need empty feet/head cells
and solid floors. Do not enter while participating in another gameplay activity.

Starting places the arena in the PvP dimension, teleports players to A/B and gives
a 3-second countdown with damage disabled. The room ends on defeat, disconnect,
leaving the bounds/dimension, cancellation or after 10 minutes. Lethal damage is
cancelled to prevent death item/EXP drops. Original health and locations are
restored; other consumed combat resources are not refunded. This stage has no
round scoring or Rating in private rooms. Open the selection GUI with `/neow pvp`;
see [GUI_USAGE.md](../gui/pvp/GUI_USAGE.md). Ranked matchmaking and scoring
are described in [RANKED_USAGE.md](RANKED_USAGE.md).

Generated slots start at X/Z 1,000,000 and Y=32, with X spacing 1024. Reserve this
area for PvP: do not put permanent builds there. Up to 128 slots are available.
Templates are limited to 512 x 224 x 512 and 262144 total cells. Cleanup removes
2048 cells per tick; slots remain reserved until complete, including across
restarts. Cleanup pauses for regions with players. Participants cannot break,
place or interact with blocks after starting.

Rooms close on restart. Return points and cleanup regions persist in
`<world>/data/neoworld_pvp_recovery.dat`. Pending players return at login/respawn,
using the Overworld spawn if the original dimension is missing. Data is persisted
by normal world saves; abrupt process/storage failures between saves are not
transactionally covered.
