# Title management redesign v3 — proposal

Status: preview proposal; not yet approved or implemented in game.
Preview: `title-management-redesign-v3.png` (generated concept at 16:9).
Reference game window: 1280x720. Actual Minecraft GUI scale must be measured at implementation.

## Layout and flow

- Header: title, unlocked count, close control.
- Left: search, ownership tabs, category/sort controls, five title rows and pagination.
- Right: large full-body player preview, display text above the player, selected title details and actions.
- Selecting a row only changes the preview. Equip is an explicit server-validated action.
- Keep `name` in the list/details and `display` above the character.
- Distinguish the equipped badge from the selected-row border.
- Show locked titles with progress. Their detail view explains the requirement and disables equip.
- Keep the visibility switch apart from equip/clear. Hide the unavailable particle control.

## Proposed interaction states

- Owned and not equipped: enable equip.
- Equipped: primary label becomes an equipped state; clear remains available.
- Locked: show remaining requirement; disable equip.
- Loading/saving: visible status and disabled dependent actions.
- Failure: readable error and retry; retain the last valid list.
- Empty search: explanatory message and clear-filter action.

## Implementation boundaries

This deliverable creates a concept image only. Ownership tabs, sorting and drag-to-rotate
are proposed interactions requiring implementation later; the existing backend currently
provides five-entry paging, text search and category filters. No schema, payload, screen
code or runtime textures are changed by this preview task.
Approved correction (2026-10-27): remove all list-row icons and their sockets. Align
title name and category at the left text inset; preserve status/progress on the right.

During implementation, split all decorative art into blank PNG assets. Render names,
display strings, state labels, progress and the actual player model dynamically.

## Art direction

Built-in image generation; prompt direction: single landscape NeoWorld title-management
screen at 1280x720, dark charcoal/gunmetal, thin layered chamfered silver borders without
rivets, restrained teal interaction states and gold rare-title text; five spacious rows;
large player preview against a blue-hour voxel village; clear Thai labels; explicit equip
and clear actions; no unavailable effect controls. The generated player and labels are
illustrations of the proposed runtime composition, not installed GUI assets.
