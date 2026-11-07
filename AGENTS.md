# NeoWorld Project Instructions

## Map Editing Workflow

- Maintain one working map: `maps/starter_sky_land_spawn_v1`. Keep its folder and map identity stable; do not create a new map/version folder for each requested change.
- The user separately authorized the reusable tree asset library at `maps/tree_library`. It is an asset collection, not another city draft. Keep this folder stable and read its `MAP.md` before tree work.
- Before map work, read `maps/starter_sky_land_spawn_v1/MAP.md`. Record approved dimensions, coordinates, completed areas, pending work and each change in that Markdown file.
- Modify only the requested area inside the current map. Preserve existing blocks and manual changes elsewhere. The initial generators are not an instruction to regenerate the whole world for routine edits.
- Stage temporary validation data outside `maps` if needed. The viewer may list the working city and the authorized tree library only. Do not add map-composition controls.
- Prior maps and references are archived under `archive/maps-before-single-world`, outside the active library. They are not new working copies. Historical tests may use this archive.
- Inspect the actual 3D result and run relevant validation plus `compileJava` after code changes. Do not describe a draft as matching the mockup before visual comparison.

## Build Policy

- After every code change in this project, run `.\gradlew.bat compileJava` and inspect the result before reporting completion. This applies in every chat working in this project.
- Do not automatically run a full build or package the project unless the user explicitly requests it.
- This project preference overrides conflicting compile/build steps in skills.
- Continue relevant file, resource-reference, and other verification. Report the compilation result and state when in-game verification has not been performed. Documentation-only changes do not require compilation.

## Automatic Skill Selection

- Automatically select and use available skills relevant to the current task; do not require the user to type `/` or explicitly invoke a skill.
- Apply this rule in every chat working in this project, and avoid invoking skills that do not materially help the task.

## GUI Design

- Before creating bitmap icons or icon-font registrations, consult `docs/gui/common/bitmap-icons/README.md` and its `catalog.json`/visual index. Reuse existing glyphs by Font ID + Unicode and matching metrics. After changing icon sources, regenerate with `tools/fonts/icon_catalog.py` and validate with `--check`. The catalogue excludes Thai/English text fonts.

- Before creating or changing any NeoWorld GUI, read `docs/gui/NEOWORLD_GUI_STYLE.md`.
- Use the documented NeoWorld market visual language as the default for future systems unless the user requests a different design.
- Reuse the shared PNG frame, widget states, colors, spacing, and interaction patterns where appropriate.
- Keep text, player data, item icons, prices, and other dynamic content out of baked PNG backgrounds.


## Token Optimization

- ตอบเป็นภาษาไทย สั้น กระชับ ตรงประเด็น
- จำกัดสรุปงานไม่เกิน 5 บรรทัด
- หลีกเลี่ยงคำอธิบายและตัวอย่างที่ไม่จำเป็น
- ค้นหาและอ่านเฉพาะไฟล์ที่เกี่ยวข้อง
- ไม่แสดงโค้ดเต็มไฟล์หรือ Log ยาว
- ไม่สร้างเอกสารที่ไม่ได้ร้องขอ
- รายงานความคืบหน้าเฉพาะเมื่อจำเป็น
- รักษาการตรวจสอบความถูกต้องและความปลอดภัย
- ทำตามกฎ Compile/Test เดิมของโปรเจกต์
