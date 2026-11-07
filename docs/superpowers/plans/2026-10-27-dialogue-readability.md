# Dialogue readability and three-row choices

Approved: enlarge NPC speech by 25%, show three speech lines, and reduce response viewport to three ordinary choice rows with scrolling. Keep Thai upper-mark allowance, input hitboxes, typewriter and native NPC preview behavior.

1. Change layout regression contract to three speech lines (42 GUI pixels for font height 9) and three complete ordinary 17-pixel responses, with a fourth response accessible only after scrolling. Observe failure before implementation.
2. Centralize speech scale=1.25 and scaled line height in DialogueLayout. Allocate exactly three speech lines and a choice viewport capped at three standard rows plus two gaps; longer wrapped responses consume their measured height and remain scrollable. Compute panel height from required content, with preview clearance and small-window bounds.
3. Wrap speech using unscaled width divided by 1.25; draw styled visible prefixes under an isolated scale transform. Use the scaled line pitch for typewriter-follow scrolling and manual speech scroll. Scale the upper-mark allowance too. Keep other labels and response fonts at existing size.
4. Run compileJava and focused dialogue regressions, review the transform/scroll coupling, and document live in-game verification as pending until the updated client is run.

Completed: updated layout contract (observed red assertion), scaled speech wrapping/rendering/scroll, three-row response viewport and independent review. Review's adjacent-line clipping finding fixed with nominal-row culling plus focused tests. Final compileJava and layout/playback regressions passed. Live in-game verification remains pending.
