# Shared GUI library — phase 1

## Organization

- `client/gui/common`: window lifecycle, logical layout, text roles, popup, icons and tooltips.
- `client/gui/widget`: buttons, close control, headings, progress, scrollbar and balance display.
- `client/gui/style`: existing palette and legacy render helpers. `NeoGuiRender.frame` remains Market-specific; do not use it as a universal frame.
- Feature packages retain their own data and actions. No existing screens were migrated.

## Window usage

Extend `NeoWindowScreen(Component title, Screen parent, int desiredWidth, int desiredHeight)`.
Implement `initContent()` to register widgets and `renderContent(...)` for dynamic content.
Use protected `window.contentX/Y/Width/Height()` for layout. Dimensions are Minecraft logical GUI pixels, not physical window pixels.

The shell centers/clamps on resize, renders the world background once, draws the existing Teal Smoke PNG with preserved corners, adds a close button with tooltip/narration and returns to the supplied parent on close/Escape. It does not pause the world. Widgets must fit the content bounds; the shell clips drawn content but does not automatically scroll or clip widget hitboxes. Small screens should hide/reflow unavailable controls as the showcase does.

`NeoCloseButton.create(x,y,size,onClose)` returns a vanilla-compatible `NeoButton`, using the existing popup PNG states. It is neutral normally and red on hover/focus.

`NeoText.wrapped(graphics,font,component,role,x,y,width,height)` preserves dynamic Components, wraps using the game font and limits visible lines to the available height. Roles: BODY, MUTED, EMPHASIS, ERROR, SUCCESS. Use existing `NeoTitlePlaque` / `NeoSectionHeading` for headings. There is no new text scaling or baked text.

## Preview

Run `/neogui` in client chat. This local command opens `NeoComponentShowcaseScreen`: window/close, Thai text roles, three button styles, progress and a popup action. No server state is changed. It is a first-phase sample, not a catalogue of every existing widget.

## Assets

Reuse `textures/gui/common/popup_teal_smoke.png` (1536×1024), `title_plaques.png`, `buttons.png` and `progress_soft_slim.png` with their existing sampling metadata. No new PNGs or changes to existing artwork. The shell is the existing Teal Smoke appearance; it is not a claim of matching the illustrative generic-frame mockup.

## Next recommendations

Named icon access, shared balance text and the first three legacy close-control migrations are now documented in [SHARED_REUSE.md](SHARED_REUSE.md).

1. Inspect the sample at 1280×720 with the actual GUI Scale; check Thai labels, keyboard focus, Escape and resizing.
2. Standardize input/search, tabs and list rows using vanilla controls and these shared colors.
3. Add empty/loading/error patterns and a separate HUD/scoreboard presentation layer when requested.
4. Migrate one feature screen at a time, retaining server authority and inventory behavior.

## Verification

Layout regression covers centered bounds and close bounds at logical viewport sizes from 1280×720 down to 1×1. Java compilation passes. Actual in-game appearance and interaction are not yet verified; no screenshot/mockup match is claimed.
