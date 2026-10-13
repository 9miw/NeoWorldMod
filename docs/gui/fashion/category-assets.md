# Fashion Category Assets

Stage 1 of the approved three-part wardrobe redesign covers the left category panel only.
The player preview, wardrobe panel and wardrobe tiles retain their existing assets.

Reference: `fashion-wardrobe-approved-mockup.png`.

## Runtime Textures

All textures are under `assets/neoworld/textures/gui/fashion/`:

| File | Dimensions | Purpose |
| --- | --- | --- |
| `fashion_category_panel.png` | 384 x 728 | Empty left panel and header separator |
| `fashion_category_row_normal.png` | 384 x 104 | Normal category row |
| `fashion_category_row_hover.png` | 384 x 104 | Hover or keyboard focus |
| `fashion_category_row_selected.png` | 384 x 104 | Selected category, gold edge |
| `fashion_category_row_disabled.png` | 384 x 104 | Disabled category row |

The generated source sheet's four alpha-bounded rows are exported separately with
nearest-neighbor sampling and identical target dimensions. Source bounds are printed
by `export-category-assets.ps1`; exports remove transparent sheet padding.

`FashionCategoryButton` uses 8-pixel source edges and nine-slice rendering. It inherits
Minecraft Button input, tooltip, sound and narration behavior. Icons, labels, equipped
names and chevrons are rendered dynamically by `FashionScreen`.

`fashion-category-assets-preview.png` composes the actual runtime PNG textures with
sample labels and reference icons for review. It is not an in-game screenshot and
its sample icons are not installed as runtime category icons.
