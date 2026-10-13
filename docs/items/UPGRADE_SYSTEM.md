# Upgrade server core

Implemented: per-stack level/failure metadata, server config, eligible-item tag, outcome rules, `UpgradeService.attempt`, and derived equipment combat modifiers. No player command, network request, table menu, or material drop source is connected yet.

## Costs and eligibility

Server config: `neoworld-upgrade.toml` in the world's server config directory. Target levels 1..15 have independent Gold and stone costs. Defaults: Gold = 100 * target level squared; stones = 1 for +1..+6, 2 for +7..+12, 3 for +13..+15. Currency is the existing player Gold balance, not physical coin items. Every accepted attempt spends its full cost, including a selected protection scroll, regardless of outcome. Rejected attempts spend nothing.

`data/neoworld/tags/item/upgradeable.json` opts in swords, axes, tridents, vanilla armor and NeoWorld equipment. Add custom Epic Fight items through this tag (explicit server opt-in) or `upgrade.extraAllowedItems` for recognized SwordItem/AxeItem/TridentItem/ArmorItem classes. Single-item, non-stackable equipment is required. Eligibility does not implement Epic Fight combat bonuses.

## Outcome and protection rules

The configured chances are success, unchanged, downgrade, reset-to-zero, totaling 100. Failure bonus defaults to one percentage point per stored failure, capped at five. It transfers probability from unchanged first, then downgrade, then reset. There are no level checkpoints; downgrade stops at zero.

Exactly one protection choice: none, downgrade scroll, or break scroll. Downgrade protection changes a downgrade into unchanged but does not prevent reset-to-zero. Break protection changes reset-to-zero into downgrade. The scroll is consumed on every accepted attempt. Success and an actual reset clear failure points; unchanged and downgrade increment them, including a protected break converted to downgrade. The counter saturates at Integer.MAX_VALUE.

## Future caller contract

Invoke `UpgradeService.attempt` on the server thread with distinct main-inventory indices (0..35), an independent server-owned copy of the equipment shown in the preview, and protection slot -1 when none is selected. The service compares the current stack against that snapshot, rechecks costs/materials/eligibility, rolls on the server, prepares an updated copy preserving enchantments and other components, charges Gold, consumes resources, and broadcasts inventory and balance changes.

Before exposing it through a table or packet, the caller must verify the menu session, table access/distance, player permissions, request identity, and that the preview snapshot belongs to that session. Do not pass an arbitrary client stack as the trusted preview. Return fresh preview data after each attempt. This service does not authorize remote table access by itself.

## Equipment bonuses

`upgrade.bonuses` config defaults:

| Key | Default | Meaning |
|---|---|---|
| `damagePercentPerLevel` | 3.0 | Percent of original weapon damage per level |
| `impactPerLevel` | 0.10 | Flat Epic Fight Impact per level |
| `armorPerLevel` | 0.20 | Flat Armor per level, per equipped armor piece |
| `stunArmorPerLevel` | 0.10 | Flat Epic Fight Stun Armor per level, per equipped armor piece |

Rates accept 0..100; zero disables that bonus. Original weapon damage is standard player base damage 1 plus the item's original main-hand damage modifiers, evaluated with their operations. It excludes enchantments, player effects, other event-added modifiers, and any style-specific weapon capability damage. Thus the NeoWorld example sword's base 50 gets +22.5 at +15. Weapon bonuses apply only in the main hand; armor bonuses apply only in each piece's proper slot. Explicitly tagged non-armor equipment without a main-hand attack-damage modifier gets no weapon bonus.

`UpgradeAttributeHandler` uses `ItemAttributeModifierEvent` on both logical sides. The installed Epic Fight build's `CapabilityItem.getAttributeModifiersAsWeapon` reads `ItemStack.getAttributeModifiers()` before adding capability modifiers, so this hook reaches its weapon attribute calculation. No capability, enchantment, or original attribute component is rewritten. Stable IDs under `neoworld:upgrade_bonus/` are removed and replaced per query, with distinct armor-slot IDs. A lower level changes the derived amount; +0 adds none. Configuration must be loaded before applying bonuses. After editing/reloading bonus config, re-equip affected gear to refresh the equipped attribute instances.

## Verification details

`UpgradeRulesRegressionTest` checks all 100 rolls for the +15 pity distribution, both scroll behaviors, success/reset counter clearing, zero level floor, counter overflow, full-bonus transfer, and invalid requests. `UpgradeBonusRegressionTest` checks percentage and flat bonus arithmetic, downgrade/reset amounts, four-piece armor totals, disabled rates, and invalid inputs. These are pure logic tests, not equipped-attribute or combat integration tests. Compile project Java with `compileJava`. Inventory payment, networking, equip/unequip, login/respawn, and Epic Fight combat still require in-game verification.
