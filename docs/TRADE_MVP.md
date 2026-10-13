# Player trade MVP

Start with `/trade <player>` or the player-action menu's Trade button. Both players
must be alive, in survival/adventure, in the same dimension, with container menus
closed and no carried cursor stack. Sending and keeping a pending request do not
require proximity: the recipient receives an accept/decline popup immediately.
The server checks the eight-block distance when accepting, before opening the
trade menus, and throughout the active trade. Only one pending request or session
per player is allowed.
Requests expire after 300 server ticks; successful requests have a 100-tick sender
cooldown. Each side can offer up to nine distinct main-inventory/hotbar slots and
nonnegative Silver. CoinItem stacks are excluded because economy converts them
automatically. Armor, offhand and crafting slots cannot be offered.

The player inventory uses Minecraft Slot/Container input, rendering, tooltips,
dragging, stack splitting and hotkeys. Shift-click an inventory slot to offer its
full stack. For a precise count, Ctrl-click the source slot, enter the quantity and
press Add. Click a local offered item to remove the reference. Preview slots are
server-protected and cannot be picked up, swapped, thrown, cloned or used in drag
distribution/double-click collection. Enter Silver and press Set money. Both players confirm, then the server
waits 60 ticks before transferring. Popup timers are display/interaction aids;
only server state completes a trade.

## Ownership and cancellation

Items and Silver stay with their owners while editing. Offers are copied server
snapshots, not escrow inventories. Every main-inventory or balance change clears
both confirmations and stops countdown; changed source slots are removed from the
offer. An unaffordable Silver offer is reset to zero. Closing the GUI, logout,
death, dimension changes, excessive distance, a container opening, or 100 ticks
without a GUI heartbeat cancels the session. No refund creates a second copy of
an item or balance. On death, ordinary Minecraft inventory/drop rules still apply.

The server resolves participants from authenticated network context. Session UUIDs,
offer revisions, explicit confirm/unconfirm actions, bounded slot/count input,
single server-thread ownership and per-player action throttling prevent stale
confirmation and duplicate commit. Cancellation is idempotent and bypasses the
action throttle. Both post-trade inventories and balances are planned before any
mutation. If either inventory cannot hold its delivery, nothing transfers. No
delivery is dropped into the world. Inventory item identity includes components.
The session is removed before publishing success. The server opens a registered
TradeMenu for both players; the client never creates a local replacement container.
Moving items or changing the real cursor stack invalidates both confirmations.
Confirmation requires both cursors to be empty. Vanilla menu removal returns the
real carried stack; preview containers are never refunded or dropped.

## Persistence boundary

This MVP introduces no database migration or escrow table. Silver remains in the
existing PlayerData Attachment, and inventory remains in vanilla player data.
The server-thread commit has rollback for ordinary mutation exceptions, but does
not provide crash atomicity across two player save files. An abrupt process/host
failure during save can leave players persisted at different transaction points.
Durable ownership, coordinated saves and replay/recovery require the separately
approved phase 3; a SQL journal alone is insufficient.

Payloads are additive; existing packet formats and protocol version 2 are retained.
Both server and clients need the build containing the required trade payloads.
No existing market or other-feature mutation path is replaced.

## Verification

`gradlew.bat tradeRegressionTest` covers inventory and Silver conservation,
capacity and overflow rejection, unchanged preflight input, component identity,
duplicate slots, stack limits, stale/repeated confirmations and countdown reset,
action codec round trips, and snapshot collection decode limits. Inventory policy
tests use immutable component identities independent of the NeoForge loader; they
do not replace real ItemStack codec and live session integration testing.

Before release, test two real clients against a dedicated server:

- Partial stacks, named/enchanted items, unstackable items, one-sided gifts and
  two-sided Silver exchanges; verify both inventories and balances after reconnect.
- Confirm, alter items/Silver, change inventory through another action, then
  deliver a stale confirm packet; neither side should remain confirmed.
- Confirm both, unconfirm or cancel during countdown; close with X and Escape,
  close a popup, logout/disconnect, die, change dimension and exceed eight blocks.
- Fill either inventory; check both failures and exchanges that free capacity.
- Accept near the request deadline; replay actions and commit packets; verify one
  transfer and no reopening from queued state after local cancellation.
- Check popup keyboard blocking, tooltip/item depth, long names, narrow windows
  and GUI scales. The shared PNG plaque/buttons and style colors are reused.
- Check native drag/split/number-key/drop/double-click behavior in the real inventory,
  and all those actions against both offer previews. Close while carrying items;
  they should return through vanilla menu removal without creating preview copies.

Live GUI/session behavior, full ItemStack snapshot codec round trips, and dedicated
server class loading remain unverified until those runtime checks are performed.
