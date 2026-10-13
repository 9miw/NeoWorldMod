# NeoWorld GUI Workflow

## Contents

- Visual language
- Mockup and asset split
- Bitmap-font icons
- Game Components First
- Shared NeoButton
- Coordinate system
- Rendering order
- Interaction and networking
- Visual comparison checklist

## Visual Language

Use dark charcoal and gunmetal surfaces with layered silver-gray beveled borders. Muted teal marks primary actions and selection; brighter cyan-teal is hover feedback. Use gold for important values and small accents, and red only for destructive actions, errors, and close-button hover.

Favor dense, readable operational layouts. Use a raised centered title plaque, compact navigation and filters near the top, a scannable list or grid, a dedicated detail panel, and one visually dominant primary action. Always expose a close button and preserve Escape behavior.

## Mockup and Asset Split

Before implementation, identify which pixels are static and which values change at runtime.

Put these in the frame PNG:

- Outer shell and title-plaque decoration
- Fixed panel borders and separators
- Noninteractive ornaments
- Permanent empty sockets for controls

Put these in a widget atlas or render them as reusable controls:

- Buttons and tabs
- List rows and slots
- Scrollbar track and thumb
- Normal, hover, selected, disabled, primary, and destructive states

When a component PNG supplies its own complete background or border, leave that
socket out of the base frame so the two assets do not duplicate edges, shadows,
or inset surfaces. Market item slots should default to one uninterrupted item
area. Add a dedicated price or metadata compartment only when the approved
design explicitly calls for it; otherwise show price in the detail view.
When square slots are approved, make the texture source region, rendered card,
grid pitch, and click/hover rectangle square from the same computed dimension.
Verify the nontransparent pixel bounds before promoting a generated mockup to a
runtime atlas; image-generation output alone is not a dimensional guarantee.

Render these dynamically:

- All text and translated labels
- Item and entity icons
- Names, balances, prices, levels, modes, counts, timers, rewards, and status
- Selection, hover, loading, empty, error, and permission states

Never bake dynamic selection into the frame. Avoid placing a drawn slot grid in both the PNG and code because small scaling differences create visible misalignment.

## Bitmap-font icons

### Shared NeoGuiIcons

The approved shared set lives in the project, not in a Party-specific package:

- Java API: `src/main/java/com/ambition/neoworld/client/gui/common/NeoGuiIcons.java`
- Runtime PNGs and sampling metadata: `src/main/resources/assets/neoworld/textures/gui/common/icons/`
- Large icon font: `assets/neoworld/font/gui_icons.json`, ID `neoworld:gui_icons`, height 16 / ascent 13
- Compact control font: `assets/neoworld/font/gui_controls.json`, ID `neoworld:gui_controls`, height 10 / ascent 9
- Approved preview: `docs/gui/common/neo-icons-approved-mockup.png` (the original Party-room example demonstrates the shared set)
- Transparent source sheet: `docs/gui/common/neo-icons-source.png`
- Optional export helper: `docs/gui/common/export-gui-icons.ps1` (`-Source` sheet, `-Destination` icon directory)

| Constant | Java glyph | PNG | Meaning |
| --- | --- | --- | --- |
| `LEADER` | `\uE100` | `leader.png` | Gold crown, leader/owner |
| `MEMBER` | `\uE101` | `member.png` | Silver/teal person, member/player |
| `INVITE` | `\uE102` | `invite.png` | Person with teal plus, invite |
| `KICK` | `\uE103` | `kick.png` | Person with red minus, remove member |
| `READY` | `\uE104` | `ready.png` | Teal check shield, ready |
| `LEAVE` | `\uE105` | `leave.png` | Door with outward arrow, leave |
| `DISBAND` | `\uE106` | `disband.png` | Split red group, disband |
| `REFRESH` | `\uE107` | `refresh.png` | Silver circular arrows, refresh |

Use `NeoGuiIcons.icon(NeoGuiIcons.LEADER)` for a 16-high icon Component, and
`NeoGuiIcons.label(NeoGuiIcons.INVITE, "เชิญผู้เล่น")` for compact icon-plus-label
button text. Both select the required custom font for the glyph; the label keeps
the ordinary font. These codes are not registered in the default Minecraft font,
so a plain `Component.literal("\uE100")` will not select this artwork.
Reuse the set where its meaning fits; do not force group-specific symbols onto
unrelated actions or migrate other screens without task authorization.
The PNGs are 32x32 with transparent padding, not a guarantee of 32-pixel rendered
size. Preserve aspect ratio, sampling metadata and readable baseline spacing.
Resource links and compilation have been checked; in-game rendering still needs
visual verification. Read current JSON and Java before relying on these mappings.

### Existing Default-Font Icons

Use existing bitmap-font icons when designing suitable GUI actions, status labels,
balances and mockups. Inspect the actual glyph artwork before depicting it in a mockup;
do not replace it with an unrelated emoji or invented symbol.

The former default-font set is now separated into
`src/main/resources/assets/neoworld/font/icons.json` (E001–E01F),
`currency.json` (E046–E048), and `ranks.json` (E020–E045).
Bitmap provider `file` values such as `neoworld:font/icons/no.png` resolve to
`src/main/resources/assets/neoworld/textures/font/icons/no.png`.
Use font IDs `neoworld:icons`, `neoworld:currency`, and `neoworld:ranks` respectively.
These glyphs no longer extend `minecraft:default`. Each named font references the
default font for ordinary characters. Use `NeoBitmapFonts.text(...)` to assign only
glyph spans their named font while retaining default text and legacy colors. Check
the current registries rather than inventing mappings. `FontBitmapGenerator` writes
these three registries and preserves their mappings across runs.

Useful existing mappings:

| Font ID | Bitmap count | Glyph range | Usage |
| --- | --- | --- | --- |
| `neoworld:icons` | 31 | E001–E01F | General icons |
| `neoworld:currency` | 3 | E046–E048 | Silver, Gold, Diamond |
| `neoworld:ranks` | 38 | E020–E045 | Rank badges and rank icons |

The rank set includes admin, builder, dev, exemple, graphiste, helper, manager,
mod, mvp, mvp_plus, owner, player, staff, tiktok, twitch, vip, vip_plus and ytb;
some have `_icon` and `_v2` variants. Inspect the registry and artwork for the exact
code rather than calculating it from a name. Wide rank badges need their measured
glyph width; do not treat them as square control icons.

| Action or meaning | Java glyph | PNG basename |
| --- | --- | --- |
| Close / decline | `\uE01B` | `no.png` |
| Confirm / accept | `\uE01F` | `yes.png` |
| Settings | `\uE006` | `cog.png` |
| Alert | `\uE00C` | `exclamation.png` |
| Dialogue | `\uE009` | `dialogue.png` |
| Navigation / compass | `\uE007` | `compass.png` |
| Chest / storage | `\uE004` | `chest.png` |
| Locked | `\uE015` | `lock.png` |
| Money | `\uE01A` | `money.png` |
| Silver | `\uE046` | `silver_coin.png` |
| Gold | `\uE047` | `gold_coin.png` |
| Diamond | `\uE048` | `diamond_coin.png` |

The listed providers have `height: 9` and `ascent: 8`. Use `font.width` and the
actual glyph baseline for placement rather than assuming a square item-icon size.
Keep the icon dynamic, separate from the baked button/frame art and from its text label.
Draw through `Component` / `GuiGraphics.drawString`, or use a glyph as a `NeoButton`
message. Example:

```java
NeoButton close = addRenderableWidget(new NeoButton(
        Button.builder(NeoBitmapFonts.text("\uE01B"), button -> onClose())
                .bounds(x, y, 24, 20)
                .tooltip(Tooltip.create(Component.literal("ปิดหน้าต่าง"))))
        .style(NeoButton.Style.DESTRUCTIVE).textScrolling(false));
```

The helper is `com.ambition.neoworld.client.gui.common.NeoBitmapFonts` in
`src/main/java/com/ambition/neoworld/client/gui/common/NeoBitmapFonts.java`.
It provides `ICONS`, `CURRENCY`, `RANKS` and `text(String)`. Use styled components
for drawing, measuring, clipping and tooltips; converting them back with `getString()`
loses the font selection. Ordinary strings containing private-use glyphs will not
select these named fonts automatically.

```java
Component balance = NeoBitmapFonts.text("\uE046 1,250");
graphics.drawString(font, balance, x, y, 0xFFFFFF, false);
int balanceWidth = font.width(balance);

Component cog = Component.literal("\uE006")
        .withStyle(style -> style.withFont(NeoBitmapFonts.ICONS).withColor(0xFFFFFF));
```

For localized labels, style only the icon component, then append the translated
label with its own default-font style. `text(String)` also handles legacy `§` color
codes; do not make the whole label inherit an icon font unnecessarily. Existing
Market screens and economy HUD/widgets are reference integrations.
`NeoBitmapFonts` covers E001–E048, not the E100–E107 shared action icons: use
`NeoGuiIcons` for those, with `neoworld:gui_icons` (16/13 height/ascent) or
`neoworld:gui_controls` (10/9). Preserve that distinction across new systems.

Give icon-only controls descriptive tooltips and accessible narration, not just a
private-use glyph name. `textScrolling(false)` keeps the full glyph stationary and
allows overflow; provide enough spacing so it cannot overlap neighboring controls.
Default scrolling remains available for ordinary labels.

White text color preserves the bitmap's source colors; other colors multiply/tint
those colors rather than replacing them. Prefer widget-state PNG feedback for colored
icons, and verify tinted and disabled states for legibility before relying on them.
Do not recolor neighboring text accidentally with inherited component styling.

Search and arrow glyphs were not present in the inspected registry. Use a verified
alternative or propose adding glyphs within the approved scope; never claim unregistered
codes are available. Keep actual item previews as `ItemStack` rendering when item data
is needed. Adding or changing glyph registrations, artwork or gameplay screens requires
its own authorized change; using this design guidance does not imply such a migration.

## Game Components First

Use components already provided by Minecraft and NeoForge before creating equivalents. For an inventory GUI, use server-opened registered menus, `AbstractContainerMenu`, `AbstractContainerScreen`, and `Slot`. Keep native slot rendering, tooltips, cursor stacks, dragging, stack splitting, number/drop keys, and container state synchronization. Reuse vanilla slot textures first and apply NeoWorld frames, buttons and colors around the native controls.

Reference-only offers or item previews must not become obtainable inventory. Enforce read-only behavior on the server across pickup/place, shift-click, swap, throw, clone, quick craft and double-click collection. Never call refund/drop/clear-container logic on preview copies. Feature-specific selection actions may supplement native input when required, without replacing ordinary inventory mechanics.

With a popup over a container screen, inspect native `removed`, `onClose` and menu lifecycle before adding custom close handling. Preserve the open server menu while the modal is active; closing the actual menu should use vanilla carried-stack return behavior. Revalidate trades when inventory or cursor changes, and require empty cursors before committing. Do not permit a client snapshot to construct its own replacement server-owned container.

## Shared NeoButton

The user approved the button design in `docs/gui/common/neo-button-approved-mockup.png`.
Its shared implementation is `src/main/java/com/ambition/neoworld/client/gui/widget/NeoButton.java`,
with the blank transparent atlas at
`src/main/resources/assets/neoworld/textures/gui/common/buttons.png` and its `.mcmeta`.
Inspect those current project files when changing the button; do not duplicate them into the skill.

`NeoButton` extends Minecraft `Button`, retaining vanilla clicks, keyboard activation,
focus, click sounds, tooltips and narration. Its nine-slice renderer preserves corners
when resized and draws runtime text separately from the PNG.

Construct and register it in the screen's `init()` using vanilla `Button.Builder`:

```java
NeoButton confirm = addRenderableWidget(new NeoButton(
        Button.builder(Component.literal("ยืนยัน"), button -> beginConfirmation())
                .bounds(x, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.literal("ยืนยันรายการ"))))
        .style(NeoButton.Style.PRIMARY));
confirm.active = canConfirm;
```

The callback and layout variables above belong to the target screen. Use translated
components when the screen has localization keys. The available styles are `STANDARD`,
`PRIMARY` (teal), and `DESTRUCTIVE` (burgundy/red). `style(...)` and `selected(boolean)`
return the button for chaining. Use inherited `active`, `visible`, `setMessage`,
`setTooltip`, position and size APIs rather than adding parallel state or hitboxes.
Selection is controlled by the screen; clicking does not automatically toggle it.

Atlas rows follow the three styles; columns are normal, hover/focus, selected and
disabled. State precedence is disabled, selected, hover/focus, normal. Standard and
primary selection use gold accents; destructive selection uses red. Use the current approved focus appearance without the removed white outline. Read source region constants from
`NeoButton` rather than assuming equally sized atlas cells.

Register buttons for input and render each visible widget once after the frame/content.
Screens that manually render children should include registered buttons in that pass.
Do not call a parent render method that redraws the world background over the GUI.
Remove the migrated control's old manual drawing and `mouseClicked` hitbox, so actions
are not handled twice. Modal screens must disable or otherwise block underlying
widgets, including keyboard activation, and restore their proper availability on close.

Compact close/arrow controls can use dynamic labels and tooltips. The current class
has no dedicated image-icon API or close-only hover style; do not assume those exist
because the mockup includes icon examples. Treat additions as a separate scoped change.

## Shared NeoPopupScreen

Use `src/main/java/com/ambition/neoworld/client/gui/common/NeoPopupScreen.java`
for modal alerts instead of implementing a separate confirmation overlay in each screen.
It renders the parent screen behind a dark input-blocking overlay and uses `NeoButton`
for its action.

Use an acknowledgement popup when the player must explicitly read and close a message:

```java
NeoPopupScreen.showAlert(title, message, onAccept);
```

Use a non-timed two-button choice for confirmations such as a Market purchase:

```java
NeoPopupScreen.showChoice(
        title, message,
        primaryLabel, onPrimary,
        secondaryLabel, onSecondary);
```

Capture stable action data such as listing ID and quantity before opening the popup.
The parent screen is initialized again when restored, so callbacks must not depend on
the parent's current selection still being present.

Use a countdown popup for time-limited actions. The action remains clickable while the
timer runs; expiry closes the popup and invokes `onTimeout`:

```java
NeoPopupScreen.showCountdown(title, message, seconds, actionLabel, onAction, onTimeout);
```

For time-limited choices that also need an explicit decline or cancel action, use:

```java
NeoPopupScreen.showCountdownChoice(
        title, message, seconds,
        primaryLabel, onPrimary,
        secondaryLabel, onSecondary,
        onTimeout);
```

Escape invokes the secondary action for a choice popup. Controllers that must receive
payloads while a popup is open can resolve the underlying feature screen with
`NeoPopupScreen.findScreen(ScreenType.class)`. Market action results and Party invite
countdowns are the current reference integrations.

Pass runtime text as `Component` values. Keep callbacks client-side; send an explicit
validated payload when the action mutates server-owned state. The popup restores its
parent before invoking a callback, allowing that callback to open another screen safely.
Do not duplicate its modal frame, dim overlay, text wrapping, countdown bar, or timeout
lifecycle in a feature screen unless the approved design requires different behavior.

The shared class has passed `compileJava`, but its in-game appearance and input behavior
have not yet been verified. Existing screens have not been migrated as part of this
button work. Compare actual rendering with the approved mockup before claiming visual
completion, especially at compact sizes and supported GUI scales.

## Shared Progress Bar Design

The user approved Soft Slim, the softened version of option 1, for reusable progress
indicators. Reference: `docs/gui/common/neo-progress-soft-slim-approved-mockup.png`. This is an
approved design direction; a shared `NeoProgressBar` class has not yet been created
by this work. Before integration, inspect the current project for an implementation.
Do not present proposed method names as existing APIs or infer approval to create code.

- Use PNG assets matching the approved mockup for the thin silver beveled frame,
  dark charcoal track and textured fill. The user chose PNG-based rendering instead
  of a procedural code-drawn frame. Do not replace the approved art with plain rectangles.
- Keep frame, track and fill in separate assets or clearly defined atlas regions.
  Use transparent outer pixels and `.png.mcmeta` with `blur: false` and `clamp: true`.
  Store runtime assets under `src/main/resources/assets/neoworld/textures/gui/common/`.
  Asset names and atlas coordinates must be checked after generation; they are not
  established APIs. Runtime PNGs have not been created by this design update.
- Use nine-slice rendering for the frame, retaining its chamfered corners and edge
  thickness. Tile or stretch only suitable track/fill regions; never scale the whole
  frame independently along both axes. Clip fill to the track's actual inner shape,
  including its chamfered ends, and prevent opaque fill from covering the silver rim.
- Support multiple colors with tintable neutral fill art or approved fill variants.
  Preserve source texture detail and frame colors; tint only the fill, reset render
  color afterward, and verify that the result matches the mockup.
- Soften the silhouette with small 1–2 logical-pixel chamfers, a brighter silver top
  edge and darker lower edge. Avoid square outer corners, oversized rivets, blurred
  glow or a fully rounded capsule. Choose suitable slice borders or a compact asset
  variant when height cannot fit the original corner detail.
- Give the fill a restrained top highlight. The loading effect is a subtle diagonal
  light sweep moving inside the colored region; its motion must not change the
  determinate fill length or imply that the numeric progress has advanced.
- Keep frame, fill, animation and labels separate. Derive their bounds from one
  rectangle; constrain fill and effects to the track interior. Preserve thin borders
  rather than stretching corner art at different sizes.
- Support configurable width and height. Suggested examples are 80x8, 160x12 and
  240x20 logical GUI pixels, not mandatory presets. Degrade detail at tiny sizes
  without generating negative interior dimensions.
- Support arbitrary fill colors; use teal by default. Allow systems to choose colors
  appropriate to their meaning and keep the silver frame consistent. Use red for
  error/destructive meanings and gold for warnings or important values.
- Determinate progress represents 0–100%; clamp input to its valid range and render
  empty and full endpoints correctly. Add a moving light sweep while loading without
  implying a different progress value.
- Support an indeterminate loading mode with a moving highlight when progress is
  unknown. Do not display a fabricated percentage in that mode.
- Percentage text is optional. Hide it automatically when the bar is too small for
  the actual font metrics, and support explicitly hiding it at any size. A proposed
  control is `.showPercentage(false)`; verify the actual API once implemented.
- Keep labels dynamic and separate from any assets. Do not enlarge a small bar just
  to fit a percentage the user does not need. Include readable empty, full and
  disabled presentations and prevent animation leaking outside the frame.
- The shared component should own rendering and visual configuration; feature
  controllers provide progress/loading state. Keep network and gameplay logic out
  of the progress renderer.

When implementation is approved, compile Java changes and verify the animation,
colors and text visibility at small and large sizes in-game. A mockup or successful
compile alone does not establish visual correctness.

## Coordinate System

Choose one logical source size matching the frame texture. Derive panel dimensions while preserving its aspect ratio and usable screen margins.

Provide helpers equivalent to:

```java
int sx(int x) { return panelX + Math.round(x * panelWidth / (float) SOURCE_WIDTH); }
int sy(int y) { return panelY + Math.round(y * panelHeight / (float) SOURCE_HEIGHT); }
int sw(int w) { return Math.max(1, Math.round(w * panelWidth / (float) SOURCE_WIDTH)); }
int sh(int h) { return Math.max(1, Math.round(h * panelHeight / (float) SOURCE_HEIGHT)); }
```

Define each control once in source coordinates and reuse the same computed rectangle for drawing, hover, clicks, dragging, and tooltips. Do not maintain separate approximate hitboxes.

Use stable row, slot, and button dimensions. Text and dynamic content must not resize the surrounding layout. Trim or wrap content within explicit bounds.

## Rendering Order

1. Render the dimmed or blurred game background once.
2. Draw the base frame.
3. Draw previews and static panel content.
4. Draw dynamic lists, grids, details, and values.
5. Draw widget states and focus indicators.
6. Draw item icons and text.
7. Draw tooltips, modal overlays, and confirmation dialogs last.

Minecraft item and entity renderers use elevated Z values, so drawing a modal later at
Z=0 can still leave parent content visible above it. Before a modal overlay, flush the
parent `GuiGraphics` batch, push the pose, translate the complete modal layer above the
parent content, draw its dimmer, frame, text, progress and widgets at that same depth,
flush again, then pop the pose. Keep render depth and input blocking aligned: nothing
from the parent should be visible or interactive above an active modal.

For item-slot counts, durability, and other standard item overlays, call
`GuiGraphics.renderItemDecorations(...)` after `renderItem(...)`. Do not draw a custom
count with `drawString()` at the slot's base depth; the item renderer can remain above
that text even when the text call appears later in source order.

For preview images, preserve the intended aspect ratio. Choose crop-to-fill or fit-with-bars deliberately; do not stretch the source independently on each axis.

Use `NeoGuiStyle` colors and `NeoGuiRender` helpers when available. Reuse existing texture atlases and icons before adding a new visual language.

## Interaction and Networking

- Give every clickable control hover feedback and ensure disabled controls cannot trigger actions.
- Make scrollbars support the interaction expected by the design: wheel scrolling, track clicking, and thumb dragging where appropriate.
- Keep selection stable when data refreshes by stable ID when possible.
- Show explicit loading, empty, error, and unavailable states.
- Icon-only controls require tooltips.
- Confirmation dialogs block the content behind them.
- Debounce text search and use server-side filtering, sorting, or pagination for large data sets.
- Keep server-owned values authoritative. Use bounded payload fields and validate client requests on the server.

## Visual Comparison Checklist

Compare the in-game screenshot and approved mockup side by side. Check:

- Overall frame size, aspect ratio, margins, and title alignment
- Panel proportions and separator positions
- List or grid density, thumbnail size, selected row, and empty rows
- Font size, line height, trimming, and alignment
- Preview crop and image quality
- Detail hierarchy and primary-button dominance
- Scrollbar width, thumb proportion, and track alignment
- Hover, selected, disabled, empty, loading, and error states
- GUI scale and common window aspect ratios
- Render rectangles matching click and drag regions

Use `gradlew.bat compileJava` during iteration. Run broader project tests when networking, shared helpers, or gameplay behavior changes. A successful compile is not visual verification; obtain an in-game screenshot for substantial layout work.

## Lessons from Hub, Fashion, and Dungeon iterations

Read the current widget implementation before relying on an API description in this reference. Approved widget behavior can evolve without changing every earlier example.

- Design compact filters using the rendered font width and line height. Scaling a decorative frame does not automatically scale Minecraft text. Test long translated labels, numeric values, and narrow layouts.
- Where the approved design calls for full non-scrolling labels, use the current NeoButton text policy and allocate sufficient width; do not make every button overflow merely because one past control used that behavior.
- Do not reintroduce the removed white click/focus outline. Preserve keyboard activation and narration while following the current approved focus appearance.
- Inspect the bitmap font provider for coin glyph height/ascent as well as the source PNG dimensions. A 12x12 source image need not render at 12 pixels high. Resolve glyph mappings from current font JSON rather than memorizing old code points.
- Use only data that the current payload actually supplies. Do not invent difficulty, reward items, or actionable room controls to match a concept mockup. Show unavailable actions with an explanation until the approved backend path exists.
- Scrollbar source bounds must match visible sprite pixels, not an assumed full atlas cell with transparent padding. Keep wheel, arrows, track clicks, and thumb dragging consistent with the approved interaction.
- Distinguish a concept preview, installed runtime assets, and a screenshot from the game. A placeholder renderer is not a completed visual match. Report which stage was verified.

### Package locations

GUI classes are organized by feature under `client/gui/market`, `fashion`, `party`, `dungeon`, and `hub`. Shared code belongs to existing `widget`, `common`, `style`, `navigation`, `overlay`, or `integration` packages as appropriate. The current Market screen is under `client/gui/market/MarketScreen.java`; older project documentation may still show its previous flat path. Locate the current class before reading or linking it.

