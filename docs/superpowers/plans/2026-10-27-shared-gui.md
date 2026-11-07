# Shared GUI Implementation Plan

Approved scope: reusable window shell, close control, text roles and a component showcase. Preserve existing screens and assets. Use the existing Teal Smoke PNG shell, NeoButton and heading/progress widgets; do not migrate feature screens.

- Test centered window and close bounds at reference and small logical viewport sizes.
- Add NeoWindowLayout, NeoWindowScreen, NeoCloseButton and NeoText.
- Add NeoComponentShowcaseScreen and client-only /neogui entry point.
- Document usage and remaining components, run layout regression and compileJava.
- In-game appearance, Thai readability, focus, Escape and GUI-scale checks remain separate visual verification.

## Result

Implemented the scoped components and preview command. Layout regression passed. Final compileJava passed. Gradle test completed with NO-SOURCE (not a regression-suite result). Review found and corrected Screen.render invoking the background hook a second time; the shell now explicitly draws the superclass background once and overrides the hook with a no-op. Close control uses existing PNG art. No in-game visual verification performed.
