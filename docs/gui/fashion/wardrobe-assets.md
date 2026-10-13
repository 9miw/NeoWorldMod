# Wardrobe Panel Assets

The right-hand wardrobe follows `fashion-wardrobe-approved-mockup.png`.
`export-wardrobe-assets.ps1` exports individually bounded PNG textures from the source sheets.
All text, item icons, ownership, pagination and equipped status remain dynamic.

| Runtime PNG | Dimensions | Purpose |
| --- | --- | --- |
| fashion_wardrobe_panel | 576 x 728 | Empty right panel with header separator |
| fashion_search_normal / fashion_search_focus | 512 x 48 | Search field states |
| fashion_search_icon | 16 x 16 | Separate magnifier glyph |
| fashion_wardrobe_detail | 512 x 100 | Selected item detail background |
| fashion_slot_normal / hover / selected / equipped | 128 x 128 | Square wardrobe tile states |

`FashionSlotButton` retains Minecraft button input, focus, sound and narration.
`FashionActionButton` reuses the thin search bevel with nine-slice rendering and
teal primary tint, matching the mockup's restrained action buttons.
Gold selection and the teal equipped strip can appear simultaneously. Tiles represent
unlocked entries, not transferable inventory stacks. Empty slots have no input widget.
The ordinary layout uses five columns and three rows. Smaller GUI viewports reduce
the row count and keep square tiles at least 22 pixels at supported Minecraft GUI sizes.
Page controls, item details, search and action buttons have distinct bounds.

`preview-wardrobe-assets.ps1` assembles the runtime PNGs at four times the default
logical size with illustrative labels and reference icons. Its output is an asset
review image, not an in-game screenshot. Existing 360-degree player rotation is retained.
