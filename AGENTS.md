# NeoWorld Project Instructions

## Build Policy

- After every code change in this project, run `.\gradlew.bat compileJava` and inspect the result before reporting completion. This applies in every chat working in this project.
- Do not automatically run a full build or package the project unless the user explicitly requests it.
- This project preference overrides conflicting compile/build steps in skills.
- Continue relevant file, resource-reference, and other verification. Report the compilation result and state when in-game verification has not been performed. Documentation-only changes do not require compilation.

## Automatic Skill Selection

- Automatically select and use available skills relevant to the current task; do not require the user to type `/` or explicitly invoke a skill.
- Apply this rule in every chat working in this project, and avoid invoking skills that do not materially help the task.

## GUI Design

- Before creating or changing any NeoWorld GUI, read `docs/gui/NEOWORLD_GUI_STYLE.md`.
- Use the documented NeoWorld market visual language as the default for future systems unless the user requests a different design.
- Reuse the shared PNG frame, widget states, colors, spacing, and interaction patterns where appropriate.
- Keep text, player data, item icons, prices, and other dynamic content out of baked PNG backgrounds.

