# NeoWorld upgrade items

Paths below are relative to the NeoWorldMod repository.

## Approved atlas and extraction

The approved source is `docs/items/upgrade-items-preview.png`, an RGBA 1536x1024 atlas in a three-column, two-row grid. Each source cell is 512x512. Extract with `tools/split_upgrade_icons.py` using Python with Pillow. This script only exports icons and verifies existing item-model references; it does not edit Java, translations, or other block resources. Its preview of the actual 64x64 exports is `docs/items/upgrade-items-ingame-icons.png`.

Read the current source dimensions before changing this layout. Preserve the approved silhouettes and colors, alpha, and nearest-neighbor scaling. Do not rerun the old `build/create_upgrade_assets.py`: it relies on a machine-local generated-image path and inserts duplicate translation-provider entries on repeated execution.

| Cell (row, column) | Registry ID | Design / intended use |
|---|---|---|
| 1, 1 | `neoworld:upgrade_stone` | Cyan stone; target +1..+6 |
| 1, 2 | `neoworld:advanced_upgrade_stone` | Purple stone; target +7..+12 |
| 1, 3 | `neoworld:supreme_upgrade_stone` | Gold stone; target +13..+15 |
| 2, 1 | `neoworld:downgrade_protection_scroll` | Blue arrow scroll; intended to prevent a downgrade |
| 2, 2 | `neoworld:break_protection_scroll` | Red shield scroll; intended to convert reset-to-zero into a downgrade |
| 2, 3 | `neoworld:upgrade_table` | Workbench icon; registered BlockItem |

The five materials are currently simple items; the table is a simple block. Their intended behavior above is a design, not implemented consumption or protection logic.

## Integration and checks

- Icons: `src/main/resources/assets/neoworld/textures/item/<id>.png`, 64x64 RGBA.
- Item models: `assets/neoworld/models/item/<id>.json`, `minecraft:item/generated` with `layer0` pointing to `neoworld:item/<id>`.
- Registration: `registry/ModItems.java`, `registry/ModBlocks.java`, and entries in `registry/ModCreativeTabs.java` under `src/main/java/com/ambition/neoworld/`.
- Update both locales in `datagen/ModLanguageProvider.java` and the existing `src/generated/resources/assets/neoworld/lang/{en_us,th_th}.json`; do not invoke data generation merely to change names.
- The placed table currently uses stone legs, a gold worktop border, and a vanilla smithing-table top. It does not reproduce the atlas's anvil and crystal. Check `blockstates/upgrade_table.json`, `models/block/upgrade_table.json`, its block loot table, and pickaxe/stone-tool tags when changing the block.
- Verify all six model references and texture alpha, and inspect the exported-icon preview. After project code changes, run `compileJava` under the current AGENTS.md policy; report compilation separately from in-game verification.

Upgrade metadata already lives in `registry/ModDataComponents.java` (`upgrade_level`, `upgrade_failures`) with helpers, rules, and tooltip in `upgrade/`. Do not recreate these components or claim that Epic Fight bonuses or a working upgrade menu exist without inspecting their implementation.
