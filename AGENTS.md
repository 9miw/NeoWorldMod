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

## Token Optimization

- ตอบเป็นภาษาไทย สั้น กระชับ ตรงประเด็น
- จำกัดสรุปงานไม่เกิน 5 บรรทัดเมื่อทำได้ โดยไม่ละเว้นผลลัพธ์หรือคำเตือนสำคัญ
- หลีกเลี่ยงคำอธิบาย ตัวอย่าง และข้อความรายงานความคืบหน้าที่ไม่จำเป็น
- ค้นหาและอ่านเฉพาะไฟล์หรือช่วงโค้ดที่เกี่ยวข้อง หลีกเลี่ยงการอ่านซ้ำโดยไม่มีเหตุผล
- ไม่แสดงโค้ดเต็มไฟล์หรือ Log ยาว เว้นแต่ผู้ใช้ร้องขอหรือจำเป็นต่อการวิเคราะห์
- ไม่สร้างเอกสารหรือไฟล์เพิ่มเติมที่ไม่ได้ร้องขอ
- รักษาการตรวจสอบความถูกต้อง ความปลอดภัย และข้อกำหนด Compile/Test เดิมทั้งหมด
- หากคำสั่งนี้ขัดแย้งกับ Build Policy หรือคำสั่งเฉพาะของผู้ใช้ ให้ยึดข้อกำหนดเหล่านั้นก่อน
