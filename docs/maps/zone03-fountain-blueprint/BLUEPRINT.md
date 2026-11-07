# แบบแปลนพิมพ์เขียวน้ำพุเทวทูตปีกเวหา โซน 03 (Zone 03 Winged Guardian Fountain)

> **สถานะ:** ผู้ใช้ตรวจสอบและยืนยันอนุมัติแบบแปลนอย่างเป็นทางการแล้ว (2026-10-25) บันทึกและแพตช์ลงแมพจริง `maps/starter_sky_land_spawn_v1` เรียบร้อยแล้ว (Report: `winged-guardian-fountain-report.json`)

---

## 1. ขนาดและพิกัดทางกายภาพ (Physical Dimensions & Coordinates)

| ส่วนประกอบ | มิติและพิกัด | รายละเอียด |
|---|---|---|
| **พิกัดศูนย์กลาง** | `X = 160, Y = 104, Z = 215` | จุดตัดแนวถนนหลักแกนกลางและทางแยกซ้าย–ขวา |
| **กรอบรวมไซต์งาน** | `X: 151..169, Z: 206..224` | 19 × 19 บล็อก รวมม้านั่งหิน 4 ทิศ และกระถางดอกไม้มุมทแยง |
| **ขนาดอ่างน้ำกากบาทแปดเหลี่ยม** | `13 × 13 บล็อก` (`X: 154..166, Z: 209..221`) | ขอบอ่าง Y104, ก้นอ่าง Y103, ผิวน้ำ Y104 |
| **ปีกคู่เทวทูต (Wingspan)** | กว้างรวม **9 บล็อก** (`X: 156..164`) | ปีกคู่สยายขึ้นสู่ฟ้า ปลายปีกแตะระดับ `Y = 113` ประดับ `end_rod` |
| **คนโทน้ำศักดิ์สิทธิ์ (Sacred Urn)** | `X = 160, Y = 109..110, Z = 216` | รินสายน้ำพวยพุ่งและตกลงสู่อ่างด้านหน้า (ทิศใต้) |
| **ความสูงยอดประติมากรรม** | `Y = 114` (สูง 11 บล็อกนับจากก้นอ่าง) | อยู่ในระดับสายตาที่สง่างาม ไม่บดบังทัศนียภาพปราสาทเจ้าเมืองด้านเหนือ |

---

## 2. ไฟล์พิมพ์เขียวและเครื่องมือ (Blueprint Assets)

- **ภาพพิมพ์เขียวแผ่นเต็ม:** [`fountain-blueprint-preview.jpg`](fountain-blueprint-preview.jpg)
- **หน้าเว็บแบบแปลนอินเทอร์แอคทีฟ:** [`fountain-blueprint.html`](fountain-blueprint.html) (เปิดดูได้ที่ `http://localhost:8766/fountain`)
- **ภาพพิมพ์เขียวรูปด้านหน้า (SVG):** [`fountain-front.svg`](fountain-front.svg)
- **ภาพพิมพ์เขียวผังด้านบน (SVG):** [`fountain-top.svg`](fountain-top.svg)
- **ข้อมูล Voxel ทุกบล็อก (JSON):** [`fountain-voxels.json`](fountain-voxels.json)
- **สคริปต์ตัวสร้างและถอดแบบ:** [`tools/maps/build_winged_guardian_fountain.cjs`](file:///C:/Users/miw/Documents/NeoWorldMod/tools/maps/build_winged_guardian_fountain.cjs) และ [`tools/maps/export_fountain_blueprint.cjs`](file:///C:/Users/miw/Documents/NeoWorldMod/tools/maps/export_fountain_blueprint.cjs)

---

## 3. สถิติบล็อกที่ใช้สร้างจริง (Bill of Materials)

จำนวนบล็อกทั้งหมดในขอบเขตไซต์งาน 19×19 บล็อก: **575 บล็อก** (ไม่รวมบล็อกอากาศ)

1. `minecraft:deepslate_tiles`: 80 บล็อก (ก้นอ่างและฐานราก)
2. `minecraft:water`: 78 บล็อก (ผิวน้ำและสายน้ำมนตราจากคนโท)
3. `minecraft:deepslate_tile_stairs`: 56 บล็อก (ขอบอ่างและม้านั่ง 4 ทิศ)
4. `minecraft:chiseled_deepslate`: 54 บล็อก (เสาแปดเหลี่ยมฐานรูปปั้นและกระถาง)
5. `minecraft:stone_bricks`: 82 บล็อก (พื้นถนนรอบอ่าง)
6. `minecraft:smooth_quartz_stairs`: 17 บล็อก (ปีกเทวทูต ไหล่ และชายผ้าคลุม)
7. `minecraft:crying_obsidian`: 13 บล็อก (แกนพลังงานเวทมนตร์และลวดลายก้นอ่าง)
8. `minecraft:sea_lantern`: 12 บล็อก (โคมไฟส่องสว่างใต้ผิวน้ำ)
9. `minecraft:smooth_quartz`: 12 บล็อก (ลำตัว ศีรษะ และขนนกชั้นใน)
10. `minecraft:polished_andesite`: 8 บล็อก (ขอบพื้นถนน)
11. `minecraft:smooth_stone`: 6 บล็อก (แนวแกนถนนหลัก)
12. `minecraft:deepslate_tile_wall`: 4 บล็อก (ค้ำยันเสาแปดเหลี่ยม)
13. `minecraft:end_rod`: 3 บล็อก (ปลายปีกและประกายแสงดาว Halo)
14. `minecraft:smooth_quartz_slab`: 3 บล็อก (ปลายปีกชั้นบนและวงแหวนเทวทูต)
15. `minecraft:blue_orchid`: 2 บล็อก (ดอกกล้วยไม้สีคราม)
16. `minecraft:lily_of_the_valley`: 2 บล็อก (ดอกไม้ขาว)
17. `minecraft:chiseled_quartz_block`: 1 บล็อก (คนโทน้ำ)
18. `minecraft:quartz_stairs`: 1 บล็อก (ปากคนโทน้ำ)
19. `minecraft:polished_diorite`: 1 บล็อก (เกราะอกหินอ่อน)
