# Loot box configuration commands

Final approved commands use held boxes instead of IDs: `/neow lootbox create` initializes an unconfigured held loot box with an automatic internal ID; `add <weight>` takes an offhand box and main-hand reward/count/components; `count <draws>`, `list`, `remove <1-based index>`, `give <players> <count>` resolve the configured box from the main hand then offhand. All require permission level 2. Internal IDs are validated lowercase ASCII, 1–48 characters. Definitions belong to this world's Overworld SavedData, not the economy database. Box identity is a persistent/synchronized ItemStack component preserved by existing shops. Plain legacy boxes retain their old basic loot table.

- [x] Test weight boundaries, IDs, 3-of-8 unique draws, multi-reward inventory atomicity and pre-roll capacity.
- [x] Implement rules, definitions/SavedData and held-box command registration. Block malformed saved definitions and preserve their original NBT rather than allowing an empty writable fallback.
- [x] Integrate configured multi-rewards, bounded payload and compact reward grid/tooltips. Reserve capacity for configured draw count before rolling to prevent selective rerolls. No automatic shop offers/prices.
- [x] Compile Java, run loot box regressions and process resources. Document syntax, persistence and unverified in-game paths.

Final selection replaces the independent-chance draft: sequential weighted sampling without replacement. Eight configured entries with count=3 produce exactly three unique entries. Weights are 0–100 (zero disabled), not absolute hit percentages. At most 9 candidates, no duplicate item+components. Draw count defaults to 1, must not exceed positive-weight entries. An invalid edited definition cannot open. Conservative capacity needs one empty main-inventory slot per draw (the last main-hand box may free one). Format 2 stores draw counts; format 1 loads with count=1. SavedData persists with normal world saves and clean shutdown. Protocol 39 carries up to nine reward stacks. No automatic commits, full build or packaging.

Verification: `compileJava lootBoxRegressionTest lootBoxConfigurationRegressionTest processResources` succeeded. 234 rules/inventory checks and 33 configuration/NBT/packet/command-tree checks passed. Actual in-game commands, dedicated server, shop purchase and reward-grid appearance remain unverified.
