# Nearby player actions

Aim at another player and press G (rebindable under Controls / NeoWorld).
The compact non-pausing menu captures that player's UUID and name once.
X or Escape closes it. Minecraft's existing crosshair entity hit result is used;
this does not extend the crosshair targeting range to eight blocks.

Each action sends a fresh request UUID plus the captured target UUID. The server
resolves the sender from network context and checks online status, alive/non-spectator
state, same dimension and a maximum distance of eight blocks. Requests are throttled
to one per five ticks per sender. Replies only affect the matching pending request
in the matching open screen. Pending controls time out after 200 client ticks;
timeouts never retry a mutation automatically.

- Profile exposes only the target's account name and level from PLAYER_DATA.
- Trade calls the existing TradeManager.request, retaining its eligibility,
  cooldown, invitation popup and server-opened vanilla container flow.
- Party invite checks the existing dungeon-use permission and reuses the existing
  invitation rules/notification path. A party must already exist and the sender
  must be its leader. This menu does not create parties automatically.

Uses Minecraft Screen/KeyMapping, NeoButton, NeoPopupScreen, shared theme colors
and the existing PNG title plaque. No new database, inventory logic or assets.
The additive payloads use the current protocol registration; both clients and
server require a build containing them.

Runtime checklist: two players; rebind G; change aim after opening; move beyond
eight blocks, change dimension, logout or die before clicking; profile level;
trade accept/decline and return; party leader/non-leader/full/busy/permission
rejection; delayed replies after closing/reopening; X/Escape, GUI scales and long
names. Compilation and regression checks do not replace this in-game verification.
