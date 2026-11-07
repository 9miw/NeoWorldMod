# Guild Campus Implementation Plan

**Goal:** สร้างศูนย์กิลด์หลายปีกในแมพเดิมตามมอคอัพ/ขนาดและระดับที่ผู้ใช้ยืนยัน

**Architecture:** Scoped in-place Anvil patch guarded by hash, complete backup, frozen baseline semantic audit. Preserve city outside scope and existing live NPC access coordinates. No deployment.

**Tech Stack:** Node.js, typed NBT helper, Vanilla blocks, existing WebGL viewer.

- [x] ตรวจจุดNPCและwaypointล่าสุดแบบread-only และสร้างตัวตรวจบล็อกก่อนสร้าง (เห็นตัวตรวจลานใหม่ fail ก่อนสร้าง)
- [x] สร้างฐานเกาะโค้ง/terraces ระดับ107/110/113 โดยคงถนนและสะพานเดิม
- [x] โถง25×29พร้อมชั้นลอย118 ปีกภารกิจ19×25 ห้องแผนที่17×17 ปาร์ตี้15×13
- [x] โรงเตี๊ยม23×29หมุน20องศา ห้องพัก4ห้องชั้น117 ลานฝึกclear25×21 ปีกบริการ23×19
- [x] รักษาจุดNPCเดิม/แนวpatrolและพื้นที่Waypoint; ย้ายt106ครบ2291บล็อกไปสวนที่ขยายด้านเหนือ/ตะวันออก
- [x] ตรวจเส้นทาง3371samples/บันได/headroom/ภายใน/นอกscope, node syntax, native Minecraft reader และcompileJava
- [x] ตรวจ3Dภายนอกและภายใน บันทึกภาพ/รายงาน/MAP.md พร้อมข้อจำกัดการทดสอบจริง

Ruling: พิกัดตัวอาคารในsite-auditเป็นกรอบขั้นต่ำ คงทุกขนาดหลัก; การจัดผนัง/บันได/ห้องชั้นบนให้ใช้พื้นที่เต็มและขยายส่วนต่อเมื่อจำเป็น ไม่ลดสเกลบล็อก
Ruling: สำเนาเซิร์ฟเวอร์เป็นข้อมูลป้องกันพิกัดเท่านั้น ไม่ใช้แทนที่localทั้งโลก ไม่ย้ายNPCหรือแก้เควสต์

Ruling: สวนรับต้นไม้เพิ่มเหนือกรอบหลักจากZ120ถึงZ102ตามสิทธิขยายเกาะเท่าที่จำเป็น ไม่ลดลานฝึก ไม่แตะกรอบปราสาทX≤220/Z≤124
Ruling: ทรงinn23×29ที่หมุน20องศามี667voxelพื้นเท่าเดิม กรอบcenter-sampledจริง31×35 ไม่ใช้กรอบครอบนี้เป็นเหตุย่อแกนท้องถิ่น
Ruling: native checkerเดิมเทียบidของblock entityกับblock nameตรงๆ ซึ่งไม่รองรับป้ายVanilla ปรับให้ยอมรับminecraft:signกับ*_sign/*_wall_signที่ถูกต้องโดยยังตรวจItemsว่างและตำแหน่งchunk
Review: fresh reviewerตรวจscope, stairs/doors, guest rooms, tree, signs และblock entities ไม่พบmaterial finding; ยังไม่ใช่การทดสอบในเกม
