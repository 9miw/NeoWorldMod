# Wardrobe Window Shell

The outer shell replaces the legacy combined `fashion_frame.png` at runtime.
The old asset is retained for reference. No dynamic text or icons are baked into
the new shell, title plaque or close-button PNGs.

- `fashion_shell.png`: 860 x 444. Eight surrounding slices use 24-pixel source
  edges rendered at 6 GUI pixels; the center is filled by code with charcoal.
- `fashion_title_plaque.png`: 480 x 112. Positioned at logical (154, 0), 122 x 28.
- `fashion_close_normal.png` and `fashion_close_hover.png`: 64 x 64.
  The close button is 16 x 16 with a dynamic X and a Thai tooltip.

The body begins at logical y=14, so the title plaque overlaps its upper edge.
Existing panels begin at y=34 and remain within the silver outer border.
`export-shell-assets.ps1` reproduces the bounded PNG exports.
`preview-shell-assets.ps1` assembles the runtime frame with the previous panel
previews and an illustrative reference character. This is not an in-game capture.
