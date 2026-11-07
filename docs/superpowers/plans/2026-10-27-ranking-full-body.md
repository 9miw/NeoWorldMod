# Ranking full-body and daily appearance plan

**Goal:** Implement the approved full-body podium and persistent first-login daily appearance.
**Spec:** docs/gui/ranking/DESIGN.md, full-body revision.
**Constraints:** Preserve unrelated edits and ESC menu, do not change GUI Scale, compileJava only (no full build).

- [x] Test Thai midnight, same-day reuse, NBT skin persistence and bounded three-preview wire format.
- [x] Add RankingAppearance attachment and login capture. Read saved offline appearances asynchronously; only top-three records are sent. Missing snapshots remain explicit.
- [x] Add isolated fashion render context and disposable preview players using recorded skin/equipment/fashion.
- [x] Install new blank shell and latest mockup reference. Update podium/table/personal-strip coordinates and tooltip hitboxes together.
- [x] Run compileJava/focused rankingRegressionTest and check dimensions/assets. Record that in-game appearance still requires actual screenshots (see DESIGN.md).
