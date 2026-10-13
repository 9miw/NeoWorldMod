# Ranked match confirmation

After matchmaking selects two queued players, both receive the approved
`pvp-match-confirm-mockup-v1.png` confirmation screen. Actual local/remote skin
and equipped item preview copies stand on either side, with names, Elo Rating,
numeric rank, a VS label and a server-owned 30-second countdown.

The Ready button accepts the current offer UUID for the acting player only.
Acceptance is irreversible for that offer. Repeated clicks cannot accept for the
other player, and acceptance at/after the deadline is rejected. Both acceptance
flags must be true before creating a ranked room, allocating an arena or teleporting.
Ranks load asynchronously; an unavailable rank displays `—`, while a player without
a completed ranked win/loss displays `ยังไม่มีอันดับ`.

At deadline:
- Each unready player receives a 60-second ranked queue suspension, with no Elo change.
- A ready, online, eligible player returns automatically to the original arena queue,
  retaining its original entry time and cached matchmaking Rating.
- If neither accepted, both are suspended and neither is requeued.

Disconnecting during an offer cancels it immediately and suspends the disconnected
player for 60 seconds. The other eligible online player returns to their queue.
Unfinished offers are void on server shutdown without penalties. Confirmed arena
startup failure likewise produces no penalty or Rating change; the players can
rejoin after the arena is available.

X/Escape dismisses the screen without accepting or resetting the server timer.
Use `/neow pvp`, `/neoworld pvp` or the Hub PvP button to reopen the same current
offer. Closing the screen while unready does not exempt a player from timeout.
K remains queue-only: pressing it after a match offer cannot accept, cancel or
surrender an offer/match. The small search HUD is hidden while an offer is pending.

Suspensions use wall-clock expiry in `<world>/data/neoworld_pvp_queue_penalties.dat`
and survive relogging and normal save/restart. Offers and their queues remain
transient. Abrupt process failure before a world save is not transactionally covered.
Protocol version 12 requires matching client/server code.

The PNG frame has no characters, controls or text baked in; previews are rendered
with Minecraft's inventory entity renderer and separate NeoButton widgets.
Ready status and button labels reuse the shared NeoGuiIcons READY/REFRESH glyphs.
Unconfirmed players show a continuously rotating eight-segment pixel spinner.
The static stone pedestals have separate animated reflection/glow layers, with
teal emphasis after server-confirmed readiness. VS lettering has a shadow and
teal side accents; the timer turns red in its last five seconds. Shared buttons
provide hover/focus/selected/disabled artwork without baking states into the frame.
Compilation and isolated model/payload/penalty tests are available through
`compileJava`, `pvpRegressionTest`, `regressionTest`. In-game two-player UI behavior,
skin/equipment rendering with other mods and dedicated-server loading are still
runtime verification tasks.
The standalone harness validates closed-offer/acceptance codecs and rejects invalid
countdown headers. It cannot bootstrap the NeoForge item registry, so active-offer
round trips containing equipment still require a fully loaded game environment.
