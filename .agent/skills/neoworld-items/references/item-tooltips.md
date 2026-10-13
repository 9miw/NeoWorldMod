# Item Tooltips

## Approved fashion direction

The approved fashion tooltip concept uses a charcoal background, thin layered silver beveled frame with stepped corners, gold item name, white/gray descriptions, teal usage instructions, and green owned-state feedback. Keep it a compact passive hover tooltip, not a screen: no close button or action buttons.

Reference image: `../assets/fashion-tooltip-approved-mockup.png`. This is an enlarged concept, not a runtime screenshot or a production sprite atlas. The preview's 16 x 16 caption describes the intended icon design grid; verify actual exported dimensions before registration.

Use this composition for fashion items; adapt content to other item types rather than claiming all items unlock a wardrobe:

1. Dynamic item name and short item-type description.
2. Icon-led details such as fashion slot or actual restrictions.
3. Usage section describing the real input, consumed amount, and destination/menu.
4. Ownership or duplicate-use note when the feature supplies that state.

For the current fashion behavior, explain that right-clicking while holding the item unlocks it in the wardrobe, consumes one item on successful unlock, and does not consume a duplicate. Equipping happens from the fashion screen. Verify these facts against current code before reusing them. Do not claim armor stats, bonuses, trade restrictions, or rendering compatibility without implementation evidence.

## Separate assets and content

- Keep decorative frame PNG, icon PNGs/bitmap-font providers, item render, and text as separate layers. Never bake item names, quantities, instructions, or owned status into a background.
- Intended icon meanings are fashion, slot/back, right-click, wardrobe, unlock, consumption, owned/check, and information. Show the mouse's right button distinctly. Other slots need their own appropriate icon or a neutral fallback, not the back icon for every item.
- Reuse existing registered art when its meaning and appearance match. Inspect `NeoBitmapFonts`, `NeoGuiIcons`, and their registries before allocating glyphs. Do not replace shared mappings to match one item.
- New icons should have real RGBA transparency, crisp readable silhouettes at tooltip scale, consistent padding and baseline, and nearest-neighbor scaling. Extract approved art when viable; inspect alpha and remove sheet captions/grid backing rather than registering entire mockup cells.

## Font registration and rendering

- Existing named families are `neoworld:icons`, `neoworld:currency`, and `neoworld:ranks`, used through `NeoBitmapFonts`. Shared GUI actions use `NeoGuiIcons` and separate GUI font IDs. Consult current files for mappings; never insert private-use icons into `minecraft:default`.
- For new tooltip glyphs, select an unused range after checking all relevant registries and helpers. Extend an appropriate family or introduce a dedicated namespaced family only when needed. If a generator owns the registry, update its source too so regeneration preserves the additions.
- A bitmap provider needs the correct texture path, `chars`, `height`, and `ascent`. A 16 x 16 source image does not require a 16-pixel text-line height. Align glyphs with the actual tooltip font and Thai text; allow headroom for combining marks.
- Style only the icon component with its font, then append separately styled/translatable labels. Keep styled `Component` values through width measurement, wrapping, and rendering; `getString()` drops font selection. White tint preserves the original icon colors.
- Prefer the existing item tooltip hook and Minecraft tooltip wrapping/positioning. Custom framing must be scoped to approved items, preserve vanilla/other-mod tooltip content and advanced information, and remain above item rendering without screen-edge clipping.
- Resolve ownership only from synchronized client data when available. Missing player/world or unavailable data must not be shown as confirmed ownership. Do not query the server or mutate player state while rendering a tooltip.

## Verification

Check texture references, glyph collisions, provider dimensions/baselines, transparent backgrounds, and generator consistency. Inspect long Thai names, wrapping, GUI scales, screen edges, owned/unowned states, and tooltips with no player context. Preserve actual use behavior and existing attributes.

After code changes run `.\gradlew.bat compileJava`; do not package automatically. Report whether in-game visual verification was performed. A concept image is not evidence that font assets or runtime tooltips are implemented. Documentation-only skill edits do not require compilation.
