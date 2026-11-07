# Loot box: approved opening animation

Approved: buy a box from the existing shop, then right-click it. Start with option 2: shake, light through the lid, opening lid, and a rising reward. Retain option 1 (horizontal roulette) and option 3 (card flip) as future options; do not implement them yet.

Implementation choices: reuse the existing shop's held-item offer registration, a registered `neoworld:loot_box` item, a server-owned vanilla loot table, and a client-only presentation screen. No client claim request. Consumption and inventory delivery happen together before the cosmetic animation, so closing or disconnecting during the animation cannot forfeit the reward. Reject opening when the inventory cannot hold the reward. Cooldown prevents overlapping uses.

The user subsequently authorized arbitrary test rewards: use iron ingots ×3 at 60%, gold ingots ×2 at 30%, diamonds ×1 at 10%. Do not create automatic shop listings or prices. Each successful opening must produce exactly one nonempty reward stack within its stack limit. Require an empty main-inventory slot before rolling (or the last main-hand box freeing its own slot) so selective merge capacity cannot filter outcomes.

Approaches considered: roulette, opening chest, and card flip. The user selected opening chest first. Reuse the Vanilla chest texture for moving lid/body surfaces and the existing NeoWorld shared window; render labels and reward stacks dynamically. Inventory icon reuses the existing chest artwork. The approved motion reference is the chat preview `loot-box-opening.html` in the thread visualization directory.

Scope excludes pity, multi-open, new currencies, shop rewrites, and paid offers. Verify item/resource wiring, inventory edge cases, and compileJava. In-game appearance remains a separate verification stage.
