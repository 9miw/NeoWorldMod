# NeoWorld ranking — approved design A

Approved 2026-10-27: vertical navigation at left (PvP, Lv., silver only), podium 2/1/3, rows 4–10, personal strip. Thai labels. Reference window 1280x720; GUI Scale is independent.

Reference: `approved-comparison.png`, upper alternative A. Lower alternative B was rejected. Runtime shell: `textures/gui/ranking/shell.png`, native 1672x941; use one uniform transform for art, text and hitboxes. Art contains no player data, text or interactive states. Shared NeoButton and NeoTitlePlaque provide dynamic controls/title; existing bitmap fonts provide icons.

PvP preserves existing rating/wins/UUID sorting and played-match eligibility. Lv. sorts descending level, then current EXP (approved). Silver sorts descending silver only. Remaining ties use UUID for stable ordering (implementation choice). No seasons, rewards or additional categories.

Data: read saved player attachments for offline players, override with current online attachments; do not mutate player data. PvP uses existing repository. Cache offline scans for 30 seconds and run disk/database reads off server thread. Requests have correlation IDs, bounded codecs and per-player rate limits. Errors must not become an empty successful table. Unresolvable saved names use a short UUID label; skin fallback uses vanilla default skin.

Entry point: `/neow ranking` plus public `RankingScreen.open()`. Do not edit ESC menu/navigation registry; menu integration remains separate.

Verification: comparator/top-10/personal-position and malformed inputs, payload round trips, saved attachment extraction, compileJava. Runtime appearance, Thai readability and input at actual GUI scales require in-game screenshots. Generated art is not visual completion.

## Review fixes — 2026-10-27
- Cache TTL now starts after successful scan completion; in-flight scans remain shared and failures permit retry. Deterministic regression simulates a 60-second scan and verifies the full subsequent 30-second lifetime.
- Ranking buttons opt into `NeoButton.uniformTextScale`, preserving the layout's scale below 0.5 and above 3. Existing `textScale` callers retain their previous limits. Regression covers 0.35 and 5.98 scales and the existing bounded behavior.
- `compileJava` and `rankingRegressionTest` passed after these fixes. Actual in-game appearance and interaction at supported GUI scales remain unverified.

## Full-body revision — approved 2026-10-27
- Latest approved reference: `approved-full-body.png`. Keep design A and all three categories. Top three show full-body preview copies, armor and equipped fashion.
- Capture appearance at the first login of each Asia/Bangkok calendar date. Same-day reconnect does not update it. Store the signed skin texture property, six equipment slots, equipped fashion and capture date in a copy-on-death player attachment; offline lookup reads that attachment. Never replace missing historical snapshots with guessed outfits.
- Rankings retain existing live/saved statistics; the daily policy applies only to appearance. Only three preview records travel with a response. Missing snapshots show a clear placeholder until the next login.
- Render fashion through a scoped preview context, without replacing live entity state. Preview models are not added to the world.
- Reference remains 1280x720, saved GUI Scale 3. New text-free shell and revised content coordinates reserve approximately 100 logical pixels vertically for models, then names/stats. Seven compact rows and the personal strip remain visible.

### Installed implementation and verification
- Runtime asset: `src/main/resources/assets/neoworld/textures/gui/ranking/shell_full_body.png`, measured **1673x940 RGBA**, transparent outside, nearest sampling metadata installed. Original shell retained. Source rendering bounds 640x360; podium model regions come from `RankingLayout`, text at y172/183/194, table header y222, seven rows y234..294, personal strip y320. Navigation and close hitboxes adjusted to the new shell.
- At 1280x720/GUI Scale 3, uniform factor is approximately 1.933 physical pixels per source unit: model regions are 191–201 px high, base font 17.4 px, compact table rows 19.3 px. Wide back fashion uses a conservative wider fit and a clipped preview viewport; real mesh fit still needs runtime inspection.
- `RankingAppearance` persists via `neoworld:ranking_appearance` attachment, including copy-on-death. Equipment snapshots reconstruct display-only items with model data, dye, trim, damage, glint and bounded NeoWorld upgrade level; inventories, books and arbitrary NBT are excluded. Skin property and signature are separately bounded. Missing prior snapshots show `ยังไม่มีภาพประจำวัน`; first subsequent login populates them. No live/outfit fallback bypasses the daily policy.
- Wire protocol is now 35 because ranking replies include up to three appearance records. Client and server must both use this version. No package/full build was run.
- Passed: `compileJava`, focused `rankingRegressionTest` (ordering/cache, Thai midnight/same-day capture rule, signed skin round trip, actual compressed offline attachment file, payload limits, layout geometry and button scale). Standalone fixture uses empty equipment stacks; real modded gear and fashion rendering are not covered by that fixture.
- Normal regression source compilation was blocked by unrelated in-progress `KillerLifecycleRegressionTest` APIs. A local init script `.codex-artifacts/ranking-focused.gradle` selected only ranking/button tests without changing that work.
- Read-only reviewer rechecked the final appearance payload and cache changes; no remaining definite important findings. **In-game screenshot comparison, actual armor/fashion fit, login/death/reconnect and dedicated-server rendering remain unverified.** The currently open game is not proof that newly compiled code is loaded.

### Preview clipping fix — 2026-10-28
- Actual game observation: Dev and TitleReview podiums had appearance data (TitleReview also displayed the offline badge), but their models were invisible. Miw separately displayed the missing-daily-snapshot placeholder.
- Root cause confirmed in the installed Minecraft source: `GuiGraphics.enableScissor` uses GUI screen coordinates directly and does not transform them through the current pose. The ranking renderer passed untranslated source coordinates while the model used the screen's translated/scaled pose.
- Fix: `RankingLayout.Region.screenBounds` transforms the preview rectangle with the current pose, rounds outward, then supplies those bounds to the scissor. Model rendering continues to use source coordinates; no data/capture policies changed.
- Regression verifies translated/scaled clipping rectangles for all three podiums at 1280x720 and GUI Scales 1–3. `compileJava` and focused `rankingRegressionTest` passed. The running client must load the new classes before a post-fix in-game comparison; that visual check has not been performed.

## Level table revision — approved 2026-10-30
- Only `LEVEL` uses a full table without podium cards, player entities or skin portraits (including the personal strip). PvP and silver retain their existing presentation.
- Columns: position, player name, level, `EXP สะสม` from `Entry.totalExp`. Existing online/offline readers and payload already carry `PlayerData.getTotalExp()`; no replacement of current-level EXP, persistence changes or wire changes were needed here.
- Ordering remains level descending, then current-level EXP descending and stable UUID. Cumulative EXP is display-only. Footer explicitly states tracking starts with this system and excludes earlier history.
- Top-three font scales are 1.65, 1.4, 1.2 with gold/silver/bronze text; remaining rows use scale 1. Source-space row heights 33/29/25/19 keep all ten rows between y70 and y290. Column fitting prevents long names/balances overflowing; matching row hitboxes show full values in tooltips.
- `RankingLevelTable` composes the existing shell's outer/navigation strips and nine-sliced table/personal frames, preserving corner geometry and omitting all baked podium art. No new PNG or icon fonts. `RankingService` returns the level table without loading or sending appearance previews.
- Passed `compileJava`, focused `rankingRegressionTest` and whitespace checks. Regression covers ten-row bounds, descending top-three typography, hover regions, unchanged ordering and cumulative EXP offline/wire round trips. Actual Thai readability/frame alignment at 1280x720 requires post-restart in-game inspection; not visually verified yet.

## Planned category buttons — approved 2026-10-30
- Left navigation now contains nine tabs: existing PvP/Lv./silver plus dungeon, quests, collection, trading, boss kills and guilds. Five buttons are visible at once; wheel scrolling over the navigation and the shared NeoScrollbar reach all nine. Footer/refresh controls stay outside the scroll viewport.
- Planned tabs are client-only navigation entries, not new `RankingTable.Category` or wire enum values. Selecting one clears pending replies and previews, shows `ยังไม่พร้อมใช้งาน`, and disables refresh without requesting server data. Existing three categories retain their requests and screen layouts.
- Reuses NeoButton, NeoScrollbar and existing glyph labels for the original tabs. Planned labels are plain Thai; no bitmap/icon registration changes.
- `compileJava` and focused `rankingRegressionTest` passed, including navigation count, five-row viewport limits and reachability of the last tab. In-game wheel/drag/focus and visual comparison remain unverified until the new code is loaded in the game.
