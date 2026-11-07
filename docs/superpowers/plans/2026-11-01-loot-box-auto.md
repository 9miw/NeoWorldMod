# Loot box effects preference and automatic opening

Approved: persistent effects on/off toggle, number of boxes, automatic sequential opening, stop control. Off skips all motion/sounds/light/fireworks and shows results immediately. Use the existing frameless view and shared buttons, no new art.

Implementation decision: the original right-click opening is already committed, so the input explicitly says "open additional boxes", 1–64. Server schedules those further exchanges, validates the same held stack/components and hotbar slot, and stops on failed validation, insufficient inventory, empty stack, death, logout or cancellation. Every exchange still uses LootBoxService. No client-selected rewards. A server-generated opening token binds starting automation to the result screen. Reuse the screen during automatic results; closing it cancels further work.

2026-11-02 approved speed change: effects-off auto mode uses a server-defined 4-tick interval (5 boxes/second at 20 TPS), including replacing the first box's remaining cooldown with 4 ticks on a valid start. Animated mode stays at 80 ticks. The request transmits a boolean fast flag, not an arbitrary interval; repeated starts cannot extend or accelerate an active run. Effects toggle is disabled while a run/request is active so mode and presentation agree. Stop before switching. Protocol 41 carries the flag.

Speed-change validation: compileJava passed. All three regression tasks passed with `-x processResources`: 234 inventory/rules + 37 configuration/codec + 18 auto/preference checks (289 total). The initial combined run's processResources failed setting file mode on unrelated `build/resources/main/assets/neoworld/models/armor/dragon_chestplate.json`; no resource sources changed in this speed adjustment. Live rate and presentation remain unverified in-game.

- [x] Add tests for bounded auto progress/cooldown, cancellation and effects preference roundtrip.
- [x] Implement client-local atomic preference file and server-owned sessions with bounded request/state packets. Clean up at logout/server stop.
- [x] Add saved toggle, count field, start/stop controls and result updates. Stop this screen's playing sounds when disabling effects or closing.
- [x] Run compileJava, existing loot regressions, new behavior regressions and processResources. Record runtime visual/audio checks as unverified unless actually run.

Verification: compileJava, processResources and all three loot box regression tasks passed (234 rules/inventory, 37 configuration/codec, 11 auto/preference checks). Tests cover max batch, invalid counts, success-only progress, cooldown, cancellation, saved preference reload and packet metadata/bounds. In-game layout, sound behavior, live server scheduling/cancellation and dedicated-server classloading remain unverified. Protocol 40 adds automatic result metadata and request/state packets. No full build or packaging.
