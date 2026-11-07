# Island01 Abandoned Mine Implementation Plan

> **For agentic workers:** Use executing-plans inline, with one independent final review.

**Goal:** Build the user's approved ruined mine interior in maps/leveling_island_01 without changing the village, chapel, trees, island footprint or existing walk routes.

**Architecture:** Natural rounded chambers and curving five-wide corridors carved into existing rock. A guarded scoped Anvil patch reuses typed NBT helpers and an independent verifier; changes are saved in the same map. No gameplay/spawner/rewards/deployment.

**Tech Stack:** Existing NodeCommonJS map-library/NBT patch, WebGL viewer, nativeMinecraft reader andcompileJava.

**Spec:** User approved mouth7×6, descent4–5wide/~20long, main21×17/high8, side passages3–4wide and final17×15/high7. Rooms are irregular/rounded, timber ruined supports, brokenrails, mine props and restrained warm lights.

## Survey and concrete layout

- Source regionSHA256 e2a3e117325bc94995f6c39cbd725761364bbe72ac76429f5ad0ffbc62088824. 384square/144high preview. Arrivalroad endpoint295/175 floor113 feet114. Keep original812walkNodes/allroutes.
- Main ellipse center282/147 extents272–292/139–155, floor100, air101–108, ceiling109. Final center255/145 extents247–263/138–152, floor98, air99–105, ceiling106. Store alcove center277/127 radius4/3 floor100 ceiling107. East ore branch to303/141 floor100.
- Preserve room dimensions, do not shrink to fit tree roots. Check minimum3solid roof layers/3solid base layers for underground cavities, avoiding surface plant/tree changes. Add small natural lobes outside primary room ellipses only where margins pass.
- Approach endpoint295/175 descends north alongX295 through stairsZ174–162 from113to101; bottomZ161 floor100, curved lower approach via295/160→290/160→287/156→287/153. Underground mouthatZ166 floor105,7wide/high6; preceding stairs are an open rock cut. Ruling: the current slope is too shallow for a6-high roof at originalsurfaceheight; place actual mouth partway down approach rather than raise a false mountain over trees or shrink opening.
- Main→final passage west atZ147 descends viaX270floor100stairs and269floor99stairs towardfloor98. Twochambers connect continuously; side paths/store/oredeadend accessible.
- Scope X244–306/Z122–176/Y96–120. Existing walk cell floor/headroom voxels protected and natural-only excavation guard. Exterior trench onlyX291–299/Z167–174; underground area retains roof/base cover.

## Tasks

- [ ] Survey solid cover, guard source hash, write meaningful independent tests and see old map fail on mine interior.
- [ ] Implement tools/maps/build_island01_mine.cjs and verify_island01_mine.cjs. Defaultdry-run withbackup/once/hashguards, nointeractiveentities. Stage all room/corridor/shape/support/reachability checks beforewrite.
- [ ] Apply currentworld patch withbackup outside maps; compare semantic entireworld and originaltree/walk/structure data; verify stairsurface0.5increments and roomreachability. Run syntax, existing relevant village/chapel/route verification, native reader, compileJava.
- [ ] Inspect actual entrance/interior/cut views; fix visible geometry and unsupported details, independentreview, record MAP.md/manifest/report and useful actual screenshots. No serverdeploy or in-game verification claim.

## Review focus

No accidental daylight holes/void underside; roomfootprints usable after pillars/props; descent slope/facing/headroom; roof joins at room/corridor intersections; existing paths/trees wholly unchanged; no exposed unsupported rails/plants/lanterns; checkpoints/boss/loot remain reservedgeometry, not active gameplay.

Ruling after actual solid-column survey: initialfloor100 would break through one thin undersidecolumn and northstorelocation277/127has naturaloverhangair. Keep dimensions and raise mainfloor104/ceiling113 (minimumroof5/base4), finalfloor102/ceiling110 (roof3/base22); move storeto271/132floor104/ceiling111(roof8/base8). Main21×17 andfinal17×15 unchanged. Descentnine stairsZ174–166from113to105, bottomZ165floor104 thencurvetohall; mouthZ166floor105/high6. Orebranchends303/153wherecoveristhicker. No artificialrockmassorroomshrinking.

## Completion record — 2026-11-02

Saved geometry, scope comparison, independent review, syntax, native Minecraft read and compileJava passed. Final region d87f07852055c12cccdec1d7a57f95a23fc7697e88039806eac09b1898e28723: 4393 net changes / 11 chunks. Actual geometry supersedes initial survey coordinates: main floor104, final102, store271/132. Four old border slabs were relocated to supported outer approach flanks; original walk nodes remain unchanged. Final floors use patchy noise rather than diagonal stripes, and the main chamber has grounded complete/broken timber frames. Final native count2965945 blocks/576 chunks/72 states. See MAP.md step8 for full record. Final screenshot capture remains pending; no in-game test or deployment.
