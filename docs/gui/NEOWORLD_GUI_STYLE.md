# NeoWorld GUI Style Guide

This is the default visual language for NeoWorld interfaces. New systems should reuse it so the mod feels like one product rather than a collection of unrelated screens.

## Visual Identity

- Use a dark charcoal and gunmetal base with layered silver-gray beveled borders.
- Use muted teal for primary actions, active controls, and selected information.
- Use brighter cyan-teal for hover feedback.
- Use gold sparingly for selected categories, important values, and small title accents.
- Use red only for destructive actions, errors, and the hovered close button.
- Keep the style crisp and pixel-art compatible with Minecraft. Disable texture blur and clamp GUI textures.
- Dim or blur the game world behind the interface, but never draw that effect over the GUI itself.

## Composition

- Center the main interface and let it occupy most of the usable screen without hiding every edge of the world.
- Use a raised title plaque centered above the main frame.
- Put navigation or category tabs near the top.
- Put search and compact tool controls below the tabs.
- Use a dense scannable grid or list for primary content.
- Place details and the primary action in a dedicated panel on the right when space permits.
- Put pagination at the bottom of the content area.
- Always provide a visible `X` close button in the upper-right corner. Escape should continue to close the screen.
- Preserve usable margins and prevent text, icons, or controls from overlapping at every supported GUI scale.

## Assets

- Design decorative frames and reusable control states as separate transparent PNG files.
- Keep runtime text, item icons, player names, balances, prices, counts, timers, and database content dynamic.
- Store game textures in `src/main/resources/assets/neoworld/textures/gui/`.
- Store design sources and previews in `docs/gui/`.
- Add `.png.mcmeta` with `blur: false` and `clamp: true` for GUI textures.
- Use a widget atlas for normal, hover, selected, disabled, primary, and destructive states.
- Do not bake a selected state into the base frame when selection can change at runtime.

## Interaction

- Every clickable control needs visible normal, hover, active/selected, and disabled feedback where applicable.
- Icon-only controls need a tooltip.
- Keep primary actions visually dominant but disable them when the action is unavailable.
- Confirmation dialogs must block interaction with the screen behind them.
- Search should debounce network requests.
- Filtering, sorting, and pagination for large datasets should run on the server.

## Market Reference

The market is the canonical reference implementation:

- Design preview: `docs/gui/market/01-inventory-grid.png`
- PNG frame source: `docs/gui/market/04-png-frame-source.png`
- Widget atlas source: `docs/gui/market/05-widget-atlas.png`
- Silver coin source: `docs/gui/market/06-silver-coin.png`
- Runtime frame: `src/main/resources/assets/neoworld/textures/gui/market/market_frame.png`
- Runtime widgets: `src/main/resources/assets/neoworld/textures/gui/market/market_widgets.png`
- Runtime coin: `src/main/resources/assets/neoworld/textures/gui/market/silver_coin.png`
- Screen implementation: `src/main/java/com/ambition/neoworld/client/gui/MarketScreen.java`

The market layout uses six icon categories, a search field, a compact sort control, a Silver balance, a 9x5 item grid, a right-side detail panel, pagination, a teal purchase action, and a close button.

## Implementation Notes

- Reuse existing Minecraft and NeoForge components before building replacements. Inventory interfaces should use `AbstractContainerMenu`, `AbstractContainerScreen`, and `Slot`, keeping vanilla item rendering, tooltips, cursor handling, dragging, splitting, hotkeys, and container synchronization.
- Apply NeoWorld decoration around those components. Use vanilla slot textures first; a new visual theme does not justify reimplementing slot mechanics.
- Reference-only item previews must be read-only on the server for pickup, placement, shift-click, swapping, throwing, cloning, drag distribution, and double-click collection. Never refund or drop preview copies. Add custom behavior only when an actual feature requirement needs it.

- Shared icons use `client/gui/common/NeoGuiIcons.java`, with PNGs under `textures/gui/common/icons/`. Reuse this set across systems when the action matches the icon.
- `NeoGuiIcons.icon(...)` uses `neoworld:gui_icons` (height 16, ascent 13); `NeoGuiIcons.label(..., label)` uses `neoworld:gui_controls` (height 10, ascent 9) for the glyph while keeping label text in the ordinary font. Do not use these glyphs as plain default-font characters.
- The approved shared icon preview and source are `docs/gui/common/neo-icons-approved-mockup.png` and `docs/gui/common/neo-icons-source.png`. The preview retains its original Party-room demonstration.

- Prefer reusable render helpers and texture regions over duplicating drawing logic.
- Scale layout coordinates from the source texture dimensions so the texture and hit boxes stay aligned.
- Keep click regions identical to their rendered bounds.
- Render the background once, then the custom frame and content, then widgets and tooltips. Do not call a parent render method that redraws blur over custom content.
- During iterative GUI work, use `compileJava` for quick verification. Reserve a full build for major milestones or final packaging.
