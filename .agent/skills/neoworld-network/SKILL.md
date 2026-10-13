---
name: neoworld-network
description: Add or change NeoWorldMod NeoForge network payloads, codecs, handlers, and client synchronization. Use for packet wiring and stale client state; GUI appearance belongs to neoworld-gui.
---

# NeoWorld Network

## Locate the current implementation

Paths below are relative to the NeoWorldMod repository. Read only the target payload and handler, its caller, and `src/main/java/com/ambition/neoworld/registry/ModPayloads.java`. Use the installed NeoForge version in the project, not an API remembered from another version.

Keep payload records in `network/` and registration in `ModPayloads`. Short client handlers can remain inline; substantial feature actions belong in the existing feature handler/controller. Do not reorganize the network package as part of ordinary packet work.

## Implement the requested action

- Identify direction and ownership before choosing `playToClient` or `playToServer`. The server owns money, inventory, permissions, party membership, and persistent gameplay state.
- Match the payload record, TYPE ID, STREAM_CODEC field order/types, registration, and sender. Bound variable-length input according to the feature's actual limits.
- Resolve the acting player from the server context. Do not trust client-supplied identity, prices, balances, ownership, or eligibility.
- Validate quantities, IDs, permissions, and current availability on the server. A disabled client button is not enforcement.
- Inspect the current threading convention. Use `context.enqueueWork` where the handler needs main-thread game access; do not move blocking database work onto the game thread without checking the existing service design.
- Keep client references from being eagerly loaded on a dedicated server. Follow the project's existing side separation and verify newly introduced class references.
- Update the existing client state/controller and visible screen after a reply. When a popup is open, inspect how the current controller resolves the underlying screen.
- Clear feature caches on disconnect where applicable. Preserve selection by stable ID across data refreshes; handle removed IDs.
- Review `PROTOCOL_VERSION` when the wire format changes. Follow the project's compatibility policy; do not blindly bump it for unrelated edits.

## Verification

For Java changes, use `.\\gradlew.bat compileJava`; run relevant regression checks when state or validation changes. For changed codecs, verify meaningful round trips and rejected inputs where useful. When runtime testing is available, check the requested action, rejection path, reconnect, and dedicated-server loading when affected. State any unverified runtime behavior.
