# NeoWorld dialogue — approved concept 1

Approved by the user on 2026-10-26. Reference: `approved-concept-01.png`.

Use a lower charcoal panel with a layered silver/teal frame, NPC preview on the left, dynamic speaker plaque, current NPC speech and six example response rows on the right. Preserve existing option colors, formatting, availability, IDs, packets, sounds and close lifecycle. No entrance animation in this scope.

Implementation: a client-only Mixin delegates list-mode drawing and input to a feature renderer. Wheel mode and sub-GUIs retain CustomNPCs behavior. Escape and X respect `disableEsc`; `hideNPC` removes the preview. Long current speech and response lists scroll independently. Layout is computed in actual scaled GUI coordinates, without changing the user's scale.

## Approved speech update — 2026-10-26

Show only the latest NPC block captured from `appendDialog`; never show player responses. Clear displayed speech immediately when a choice is submitted and wait for the next server response. Keep the original mixed CustomNPCs history internal for compatibility.

Reveal one extended grapheme group every 30 ms (about 33 groups/second). Thai leading vowels and combining marks belong with their base; retain inline styles and icon glyphs. Enter, keypad Enter and Space finish revealing the current speech without submitting a choice. A key release is required before that physical key can act again. Space never submits a choice. While revealing or waiting, response rows are disabled; after completion, existing mouse/keyboard selection resumes. Resize reflows the current speech without restarting its clock. Empty NPC text is ready immediately.

Alternatives considered: resource-pack-only changes cannot change list layout/hitboxes; a new dialogue protocol is unnecessary. The approved choice is the existing dialogue with a new client skin.

Assets: generate blank frame components from the exact approved image, export separate nine-slice outer/portrait frames and a row background. Use shared `NeoTitlePlaque` and `NeoButton` behavior where appropriate. Text, selection and the live entity are never baked into PNGs. Keep generated source and extraction metadata here. The new assets supplement the shared theme rather than changing other screens.

Reference game window: 1280×720, GUI scale Auto in the existing options. Runtime scaled dimensions and in-game appearance still require verification. Compilation alone is not visual approval.

## Approved response icons — 2026-10-26

Replace the leading chevron with a 10×10 logical icon chosen by the installed `DialogOption.optionType`: 0 close (shared LEAVE door), 1 dialogue (E009), 2 disabled (E015 lock), 3 NPC service (shared PROFILE), 4 command (existing protection_scroll artwork). Unknown types and the synthetic Continue use a neutral speech icon. Native Role is generic: do not imply that it necessarily opens a shop. No label/command-text heuristics or new glyph/PNG registration.

Icon at row x+5, label at x+20, right text inset 10; wrapping uses the same insets. Original row hitboxes and option IDs remain unchanged. Icons dim while playback blocks input; the disabled-kind lock remains muted. Shared colored artwork retains its original colors, with silver/teal feedback on monochrome glyphs and the selected row.

## Approved quest-bound choice override — 2026-10-26

For a real type-1 dialogue link, resolve the target Dialog from the existing client DialogController. If its serialized quest binding is nonnegative (unset is -1), replace the speech icon with a gold exclamation glyph. Gold remains gold on selection and dims while input is blocked. Missing controller/target metadata retains the ordinary speech icon. Other option kinds retain their icons. This identifies a quest binding, not whether that player can accept/complete it; no title heuristics, quest-cache requirement or additional network messages are introduced.

## Approved NPC preview correction — 2026-10-26

Use a fixed render scale of 40 logical GUI units, independent of the portrait height or response count. Pass the actual scaled GUI mouseX/mouseY into Minecraft's inventory entity renderer so its existing body/head tracking works. Preserve the portrait clipping region, name plaque and hideNPC behavior. The user's Minecraft GUI Scale still governs physical pixel size; custom NPC model dimensions remain native.

## Approved title placement — 2026-10-26

The plaque above speech displays `npc.display.getTitle()`, parsed through NeoBitmapFonts to retain formatting and icon fonts. Null/blank titles omit that plaque. The plaque under the entity preview continues to show `npc.getDisplayName()`; the NPC name is no longer repeated above speech. A missing entity/display also omits the title safely. Layout and close behavior are unchanged.

## Approved native preview labels — 2026-10-27

Suppress the floating native name/title only while drawing the exact NPC in this dialogue preview. A thread-local entity render scope surrounds the inventory preview and flush; a client RenderNPCInterface Mixin cancels the shared native renderLivingLabel method inside that scope. Scope restoration is exception-safe and supports nesting. UI plaques and normal world labels retain their existing behavior; no NPC display settings are changed.

## Approved readability update — 2026-10-27

NPC speech renders at 125% of its previous size. Its viewport is three scaled lines: for the normal 9-pixel font, the scaled pitch is 14 and viewport height is 42 logical pixels. Wrap width is divided by the same scale; manual and typewriter-follow scrolling use the scaled pitch. Thai ink allowance scales to four pixels above the nominal viewport. Cull rows by nominal bounds so adjacent lines do not leak glyph fragments into that allowance.

The response viewport is capped at three ordinary 17-pixel rows with two 2-pixel gaps (55 logical pixels). Longer labels retain wrapping and scrolling rather than being truncated. Panel height derives from required content and retains clearance for the fixed-size NPC preview. Other text and response font sizes are unchanged.
