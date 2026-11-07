# Dialogue skin verification — 2026-10-26

## Larger speech and three-row choices — 2026-10-27

- Changed the layout regression contract before implementation; reproduced an assertion failure because the original speech viewport did not fit three enlarged lines.
- Implemented 1.25 speech scale, matching unscaled wrap width, 14-pixel line pitch/42-pixel speech viewport at font height 9, and 55-pixel three-ordinary-row response viewport. Layout tests cover four window sizes, hidden preview, no/one/three choices without scroll, fourth choice after scroll, long lists and oversized rows.
- Independent review confirmed the expanded Thai ink clip could expose adjacent-line fragments. Added nominal-row visibility checks and regression coverage for previous/fourth-line exclusion, while retaining the scaled top ink allowance.
- Final compileJava, layout regression and text playback regression passed. The new text size and viewport appearance have not yet been visually verified in-game after client restart.

## Native preview label suppression — 2026-10-27

- Installed RenderNPCInterface bytecode confirms native name and title are both drawn by renderLivingLabel. Added a client-only cancellable hook guarded by the exact active preview entity.
- Scope regression checks cover other entities, normal world rendering, nested previews, thread isolation and restoration after exceptions. compileJava, preview scope and layout tests passed.
- Entity display flags are never mutated; UI plaques are outside the suppression scope. Live in-game label suppression still needs verification after a client restart.

## First-line Thai tone-mark clipping

- User screenshot `thai-first-line-clipping-before.png` shows upper marks clipped at one common horizontal edge on the first speech line, while the next line retains them.
- Confirmed renderHistory used text.y() both as the first draw origin and scissor top. Thai bitmap providers use ascent=9/height=12; their ink can extend above Minecraft's draw origin (7-ascent=-2).
- Added textClip() with three logical pixels of upper ink allowance, bounded by the window top. Drawing culling uses this same expanded clip. Nominal line placement, lower clipping edge, layout, scroll height and playback timing stay unchanged.
- Added first-line upper-mark coverage and unchanged lower-edge regression assertions before implementing textClip; initial compilation failed for the missing method, then the layout and playback suites passed. compileJava passed after integration.
- The provided screenshot is before the fix. A post-restart in-game screenshot has not yet been verified.

## NPC title above speech

- Replaced the upper name with DataDisplay.getTitle(); the preview's lower name still uses getDisplayName(). Null/blank title or missing display skips the upper plaque.
- Title formatting uses the existing NeoBitmapFonts parser. No layout, input or resource changes.
- compileJava passed after this change. Actual title/name placement has not yet been checked in the restarted game client.

## Fixed-size NPC and mouse tracking

- Root causes confirmed in NeoDialogueView: scale used portrait.height(), while mouse parameters were fixed portrait-relative coordinates.
- Replaced the scale expression with NPC_PREVIEW_SCALE=40 and passed actual render mouseX/mouseY. Inspected Minecraft 1.21.1 InventoryScreen source: the helper computes body/head angles from these GUI mouse coordinates and retains its clipping/restoration path.
- compileJava passed and DialogueLayoutRegressionTest passed after the change. No new test duplicating the two render arguments was added.
- In-game size across response counts and live mouse tracking are not yet visually verified after client restart. Model dimensions and user GUI Scale continue to apply.

## Quest-bound choice icon

- Confirmed in the installed CustomNPCs bytecode that Dialog.quest defaults to -1 and is serialized as DialogQuest. DialogOption.getDialog resolves the linked target via DialogController. Dialog.hasQuest additionally requires a QuestController cache, so it is not used for this client binding indicator.
- Added nullable target-binding classification tests before implementation; the initial compile failed for the missing overload/QUEST kind. After implementation, bound positive/zero IDs, unset/missing metadata and non-dialogue icon preservation all passed.
- compileJava and all three focused dialogue regression suites passed after integration (option kind, layout, text playback).
- No server protocol, asset registration or gameplay conditions changed. Native default-font exclamation renders in gold; the existing red E00C bitmap is not repurposed.
- Actual in-game gold icon appearance and multiplayer cache behavior remain unverified pending a restarted client.

## Response-type icons

- Added type-based mapping and feature renderer using five existing icon assets; no resource registry or server-side changes.
- Measured source PNGs: dialogue 12×12, lock 12×12, PROFILE 64×64, LEAVE 32×32, scroll 64×64. Verified corresponding existing bitmap font metrics before fitting into a 10×10 slot.
- compileJava passed. Option-kind contract checks (five installed types and unknown values), layout regressions and playback regressions all passed.
- Source-asset preview inspected; not an in-game screenshot. Live appearance and actual GUI-scale placement remain unverified pending a restarted client.

## Latest approved update: current speech and typewriter

This update supersedes the initial accumulated-history display below. Player choices are no longer displayed; each submitted selection immediately clears the current speech until the next NPC block arrives.

- `DialogueTextPlayback` regression test was added before implementation; initial compilation failed because the new model did not yet exist, then the implemented model passed.
- Playback checks cover Thai leading vowel/tone/SARA AM grouping, emoji ZWJ, elapsed-time reveal, empty text, skip versus select, held-key repeat across server responses, clear/wait and resize timing.
- `compileJava` passed after integration. Both `DialogueTextPlaybackRegressionTest` and the existing `DialogueLayoutRegressionTest` passed against the compiled classes.
- Follow-up independent review found no significant actionable issue in the playback model, view or Mixin. The view receives only the NPC block captured at append, not the mixed NPC/player list.
- No PNG or server protocol changes. No game restart, package or upload was performed. Actual in-game Thai reveal, keyboard release and server round-trip remain to be checked in the updated client.

## Implemented

- `DialogueLayout`: scaled panel/portrait/text/response/close bounds, variable-height rows, clipping-aware hit-testing, scroll clamp and keyboard reveal.
- `NeoDialogueView`: separate PNG frame/backing, actual NPC preview, shared title plaques/close button/scrollbars, formatted history preserving each block color, wrapped responses and synthetic mouse-continue row.
- `GuiDialogInteractMixin`: list-only rendering/input integration, original selection and Escape lifecycle, wheel/sub-GUI fallback, resize/append refresh.
- `neoworld.mixins.json`: client registration. No server-side source, dependency version or protocol changes.

## Verification evidence

- Initial regression compile failed because `DialogueLayout` did not exist; implementation then passed the layout assertions.
- Added oversized-answer regression reproduced `AssertionError: oversized answer must reveal its beginning`; explicit top alignment then passed, including repeat-reveal stability.
- `gradlew.bat compileJava` passed after integration and again after review fixes.
- `gradlew.bat compileJava processResources` passed; resource generation/copy succeeded.
- `java --class-path build/classes/java/main src/regressionTest/java/com/ambition/neoworld/client/gui/dialogue/DialogueLayoutRegressionTest.java` passed.
- Independent code review checked the installed target bytecode and found two issues (history color, oversized reveal). Both were corrected; second review found no new important issues.
- Exported actual PNGs checked over checkerboard. Dimensions and alpha bounds are in `asset-metrics.json`. Every runtime component has blur=false/clamp=true metadata. Side ornaments are separate; straight edge samples exclude the source diamonds so resizing does not stretch them.
- Actual installed references: `frame.png`, `portrait.png`, `response.png`, `diamond.png` in `NeoDialogueView`; title plaques and scrollbars use the shared library. The unused generated plaque remains only in the source sheet, not as a runtime asset.

## Outstanding in-game verification

The open Minecraft window is an already-running multiplayer client in the NPC editor. It has not been restarted or disconnected. New Mixin classes cannot be installed into that running JVM by a resource reload. No visual match to the approved reference, live server interaction, or runtime Mixin application is claimed.

Next verification: restart the development client from the updated project, open list-mode dialogue and compare at 1280×720 using the actual scaled dimensions; test normal/hover/keyboard/close, dialogue history and long lists, hideNPC, disableEsc, zero options, wheel fallback and server close. F3+T alone is insufficient for Java/Mixin changes. No full build/package was run.

Status: Java compiled; assets wired and exported; matching the approved mockup has not yet been verified in-game.

## Asset provenance

Built-in ImageGen generated `component-source.png` from `approved-concept-01.png`. Final instruction: produce a transparent sprite sheet with separate blank wide outer frame, tall portrait frame, slim blank name plaque and response row; preserve charcoal/gunmetal/silver/teal materials and chamfered corners; no text, characters, selection, or X baked in. `export-assets.ps1` crops/cleans the used components, isolates the side diamond and exports measured PNGs plus a checkerboard preview. The main frame uses nine-slice sampling; dynamic text and entity rendering remain in Java.
