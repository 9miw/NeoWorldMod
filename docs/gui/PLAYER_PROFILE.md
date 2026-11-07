# Player profile V4

## Approved scope

Current approved artwork: `docs/gui/player-profile-v5-approved.png`. V4 information layout: `docs/gui/player-profile-approved-v4.png`. Specification: `docs/gui/player-profile-v4-spec.md`. Implementation plan/ledger: `docs/superpowers/plans/2026-10-28-player-profile-v4.md`.

Four tabs: Overview, Combat, Equipment/Fashion, Skills. Persistent left snapshot portrait/name/level/public title. No weapon names, main-hand/offhand slots, refresh button, effects tab, fabricated skill levels or mockup example skills. Earlier references V1/V2 and V3 artwork remain archived in place; this specification supersedes their information layout.

## Implemented layout

- Logical frame 620 x 350 with outside close button and uniform responsive scaling. Primary game window reference 1280 x 720; actual GUI scale must be checked in game.
- Tabs at x166/264/382/532 with widths92/112/144/68, y48 h25. Equipment gets more width for its Thai caption.
- Left portrait panel (18,48,136,270), detached model viewport (24,54)-(148,245), name y258, level y278, visible title y300. Preview copies use snapshot armor/fashion and empty hands. Nonempty back fashion uses the smaller preview scale; empty back slots do not.
- Overview V5: red HP, green Stamina, gold owner-only EXP within panel (166,80,434,90); dedicated status heading, rows at y104/126/148; PvP heading y176 includes loading/unranked/unavailable state and five gold-corner cards at y204 h62; sin card and separated shortcuts y275 h26. No weapon section.
- Combat: existing 12 supported values grouped as attack/defense/movement. No offhand placeholder. Rows reserve18 logical pixels at each decorative end. Read-only explanation card distinguishes equipment values from conditional skill bonuses.
- Equipment: four armor slots and six fashion slots only. Selection highlights the slot and shows its full permitted tooltip in the right details panel. Empty slots say not equipped. Items are display copies, not container slots; no inventory actions exist.
- Skills: actual equipped skill list (six visible rows), slot/category label and installed mod texture; selected detail shows source mod, enabled state, supported bonus numbers and the mod's ability/condition description. Long lists and descriptions use independent shared scrollbars with wheel, drag, track click and keyboard support. No synthetic skill levels.
- Native NeoButton input, tooltip and narration retained. Tabs use feature PNG states; item/skill selections have teal focus feedback. Existing icon catalogue entries are reused; no icon sources or font registrations changed.

## Data and privacy

- Gameplay, title, sanitized gear and skill identities are captured together on the server thread. All tabs and the detached portrait consume that snapshot. Reopen for newer gameplay data.
- Owner EXP is removed on the server for other viewers, not merely hidden by the UI. Removed weapon/effect data is projected to empty values.
- Skill metadata includes actual ID, translation key, texture, slot and enabled state. WoM2.0.178 Critical Knowledge values are calculated from the subject's current armor enchantments, using the verified installed event formula. Vampirize reports the verified20% multiplier. Other skills use the mod's translated description; unresolved numeric format arguments are visibly marked as equipment/condition-dependent, never filled with invented values. This is not an exhaustive numeric adapter for every mod skill.
- Visual item copies whitelist model/dye/trim/durability/enchantment data and a plain bounded custom name. No container contents, books, lore payloads or arbitrary custom NBT are copied. Gear lists reject hand slots and duplicates and are bounded to10; skills to64 with bounded text/bonus lists.
- Profile opens immediately with PvP marked loading. Existing PvpRankingCache resolves database results off-thread; success/failure updates only the matching still-open request/target/capture timestamp, including under a popup. Closed/different screens ignore late results. Cached PvP may be up to the existing30s cache age; no per-frame queries or automatic profile polling.
- Unranked players have no invented position or win rate. Database failures display unavailable rather than zero. Client/server protocol changed35 ->36; both sides must run compatible code.
- Existing title/fashion and party-invite routes are preserved. Trading remains disabled by existing project policy; no trade action was restored. Navigation within the title/fashion system is otherwise unchanged.

## Assets — approved V5 art installed

- Current visual reference: `player-profile-v5-approved.png`. Older references and textures remain in place; runtime frame/control paths now target V5.
- New ImageGen shell and component-sheet sources: `player-profile-v5-shell-source.png`, `player-profile-v5-components-source.png`. Tool provenance and measured crop regions: `player-profile-v5-assets.json`; repeatable exporter: `export-profile-v5-assets.ps1`.
- Runtime under `assets/neoworld/textures/gui/player/v5/`: frame1672x940; four tab states320x80; four action-button states320x80; content panel320x192; normal/hover rows320x64; PvP card160x192; transparent-center portrait160x256; section320x56; progress track320x40; tintable fill300x16. Total17 PNGs, all wired through ProfileSkin and PlayerProfileScreen.
- Each component was isolated by measured alpha bounds, cleared below alpha160 and made opaque above that threshold, then normalized with nearest-neighbor sampling. Sibling control states have identical canvas dimensions; transparent portrait opening remains transparent. All PNGs have blur=false/clamp=true metadata. Sources are preserved separately.
- The new UI shell, portrait/identity panel, overview status rows, PvP cards, section panels, tabs, action buttons and progress textures use V5 artwork; no V2/V3 frame/control art remains referenced by the profile. Existing icon catalogue glyphs, vanilla HP heart, installed skill textures and the prior standalone combat/boot icons are reused independently; no duplicate icon-font registrations were created.
- `player-profile-v5-assets-checkerboard.png` is a checkerboard preview of the actual installed components. It is not an in-game screenshot.
- New art has thin silver bevels, antique-gold corners and restrained teal accents. PvP cards use centered label/icon/value layout; status values, names and all gameplay data remain dynamic. Portrait identity text has108px bounds inside its inner panel to clear decorative ends.

## Verification and limits

- `gradlew.bat compileJava`: passed after the final Java edit.
- `gradlew.bat playerProfileRegressionTest`: passed. Tests cover metadata round trips/immutability/bounds, owner/public EXP projection, removed data, PvP loading vs failure, win-rate/unranked behavior, rejection of hand/duplicate slots and rejection of old snapshot identities.
- The headless regression harness does not initialize the mod loader. Nonempty ItemStack network serialization and actual entity/skill rendering still require a running mod environment; no substitute bootstrap/stubs were added.
- Independent read-only code review identified portrait empty-back sizing and delayed-PvP navigation issues; both were fixed and rechecked, with no remaining important finding in that review.
- V5 visual-only revision: compileJava and existing profile regression passed;17 exported PNGs and their metadata were checked. Actual runtime visual comparison is still pending. No full build, packaging, commit or deployment performed. Existing running Minecraft was not restarted.
- Code compiled; matching the approved mockup has not yet been verified in-game. Still check1280x720 and alternate GUI scales, Thai text/tooltip depth, all ten gear slots, skill-list/detail scrolling, public title visibility, PvP errors/reconnect and dedicated-server startup.
