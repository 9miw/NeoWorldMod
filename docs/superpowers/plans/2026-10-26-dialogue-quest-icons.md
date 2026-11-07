# Quest-bound dialogue response icon

Approved: linked-dialogue choices whose target Dialog has an assigned quest show a gold exclamation icon. All other option kinds keep their existing icons; absent target metadata falls back to dialogue. This denotes a quest binding, not player quest status.

Implementation: add a nullable target-quest-ID overload to the pure option-kind mapping. Installed `Dialog.quest` defaults to -1, so zero and positive IDs are bindings. Resolve only real type-1 `DialogOption` links through the existing client DialogController, guarded for missing controller/target. Do not call hasQuest(), which depends on a separate QuestController cache and may be unavailable client-side. Use the existing default-font exclamation glyph, gold even while hovered, muted when disabled. Inspection showed E00C artwork is red, so tinting that bitmap cannot produce the approved gold; the monochrome default glyph needs no new asset. No additional network payload or resource registration.

Verification: first add regression cases for bound, unbound, missing, zero ID and non-dialogue kinds; observe failure before implementation, then run compileJava and all focused dialogue regressions. In-game appearance remains a separate client-restart check.

Completed: target metadata resolver, quest-kind classification, gold icon override, compileJava and all three focused regression suites. Live in-game verification remains pending.
