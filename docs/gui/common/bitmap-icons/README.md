# ทะเบียน Bitmap Icon กลาง

ค้นของเดิมก่อนสร้างไอคอนหรือเพิ่ม Font ID ใหม่ โดยค้นชื่อ หมวด Unicode พาธภาพ หรือชื่อ helper ใน [หน้ารวมภาพ](index.html) ข้อมูลที่โปรแกรมอ่านได้อยู่ใน [catalog.json](catalog.json) และ [ภาพรวม PNG](contact-sheet.png)

## ขอบเขต

รวมเฉพาะ 10 ชุด: icons, currency, ranks, gui_icons, gui_controls, dungeon_hud, fashion_tooltip, activity_icons, pvp_titles, pvp_rating ไม่รวมชุดตัวอักษร thai และ roboto ตรายศและภาพข้อความตกแต่ง PvP ยังคงรวมตามขอบเขตที่อนุมัติ

ทะเบียนนี้เป็นข้อมูลอ้างอิงสำหรับพัฒนา ไม่ใช่ฟอนต์ใหม่ ไม่ได้เปลี่ยน PNG, Unicode, เมตริก หรือการลงทะเบียนฟอนต์ในเกม ภาพพรีวิวมาจาก PNG จริงและขยายแบบ nearest-neighbor เพื่อดูรายละเอียด ไม่ใช่ตัวอย่างขนาดจริงในเกม

## การเลือกใช้

1. ค้นความหมายของไอคอนใน index.html และตรวจภาพก่อนใช้
2. เลือกรายการด้วยคู่ `font_id` + `codepoint` หรือ `id` อย่าใช้ Unicode อย่างเดียว เพราะรหัสเดียวกันมีหลายขนาดได้
3. ใช้ `usage_java` ตามรายการ โดยนำเข้า class ที่เกี่ยวข้องจาก `client.gui.common` หรือ `pvp.room.PvpTitles`; ตัวอย่างตรงใช้ `Component` และ `ResourceLocation` ของ Minecraft
4. สร้างป้ายด้วย Component แยก glyph ที่ระบุฟอนต์ออกจากข้อความทั่วไป อย่ากำหนดฟอนต์ไอคอนให้ข้อความทั้งบรรทัด
5. ถ้าต้องปรับขนาด ให้คงสัดส่วนและพิจารณา height/ascent หรือใช้ตัวช่วย render ที่มีอยู่ ไม่ต้องสร้าง Font ID หรือ PNG ซ้ำเพื่อปรับขนาด
6. เพิ่มไอคอนใหม่เฉพาะเมื่อทะเบียนไม่มีรูปที่เหมาะสม แล้วสร้างทะเบียนใหม่และตรวจสอบก่อนส่งงาน

## ข้อมูลต่อรายการ

| ฟิลด์ | ความหมาย |
| --- | --- |
| id | คีย์ประกอบ font_id และ Unicode |
| name / category | ชื่อจากไฟล์ PNG และชุดฟอนต์ ไม่ใช่การเดาความหมายจากภาพ |
| font_id / codepoint / glyph / java_escape | ฟอนต์ รหัส ตัวอักษรจริง และ escape สำหรับ Java |
| image_resource | ResourceLocation ของ bitmap provider; อยู่ใต้ textures/ ไม่ใช่พาธ PNG ที่เดาเอง |
| image_path / image_size | พาธจากรากโปรเจกต์ และขนาดภาพต้นทางเป็นพิกเซล |
| source_rect | x,y,width,height ของช่อง glyph ในภาพต้นทาง รองรับภาพตาราง |
| alpha_bounds | left,top,right,bottom ของพิกเซลที่มี alpha ภายในช่อง glyph; right/bottom ไม่รวมพิกเซลปลาย |
| height / ascent | เมตริกจาก provider เป็น logical GUI pixels; ไม่ใช่ขนาด PNG |
| source_font / provider_index | ที่มาสำหรับตรวจกลับไปยัง JSON จริง |
| default_font | null หาก default ไม่รวมรหัสนี้ มิฉะนั้นระบุฟอนต์ที่ถูกเลือกและเมตริกจริง |
| usage_java | ตัวอย่างใช้ helper เดิม หรือกำหนดฟอนต์ตรงเมื่อตัวช่วยยังไม่ครอบคลุม |

`sources` เก็บ SHA-256 ของฟอนต์ ภาพ และตัวช่วยที่เกี่ยวข้อง เพื่อระบุแหล่งข้อมูล ตรวจ `--check` หลังแก้แหล่งข้อมูลหรือเปลี่ยนตัวสร้างทะเบียน

ชื่อเรียกครบทุก variant อยู่ใน `java_constant` เช่น `NeoBitmapIcon.DUNGEON_HUD_MEMBER.component()` ตัวสร้างทะเบียนสร้าง `NeoBitmapIcon.java` ไปพร้อมกันและ `--check` ตรวจว่าไฟล์นี้ตรงกับข้อมูลด้วย ดู [SHARED_REUSE.md](../SHARED_REUSE.md) สำหรับจุดที่ย้ายมาใช้ส่วนกลางแล้ว

## ข้อควรระวัง

- E100–E107 ใน gui_icons มี height 16/ascent 13 แต่ gui_controls มี 10/9; default เลือก gui_controls
- E101 ใน dungeon_hud มี 12/11 แม้เป็นรูป member เดียวกัน default จึงไม่ได้ให้ขนาด HUD นี้ ให้ระบุ neoworld:dungeon_hud
- pvp_titles และ pvp_rating ต้องกำหนดฟอนต์เฉพาะ เพราะไม่ได้รวมใน default
- กราฟฟอนต์ห้ามอ้าง default กลับจนเกิดวงจร รหัสซ้ำที่ชี้คนละรูปใน default ถือเป็นข้อผิดพลาด ส่วนการใช้รูปเดียวกันต่างเมตริกถือเป็น variant และเก็บแยกไว้
- ตัวตรวจและภาพ offline ไม่ยืนยันการแสดงผลในเกม หลังนำไปใช้ต้องตรวจ baseline, GUI Scale, สี และความอ่านง่ายจริง

## สร้างใหม่และตรวจสอบ

รันจากรากโปรเจกต์ด้วย Python ที่มี Pillow:

```powershell
python tools/fonts/icon_catalog.py
python tools/fonts/icon_catalog.py --check
python -m unittest discover -s tools/fonts -p test_icon_catalog.py
.\gradlew.bat compileJava
```

ตัวตรวจตรวจภาพหาย ช่องตารางผิด เมตริกผิด glyph ซ้ำในฟอนต์ วงจรอ้างอิง การชนของภาพใน default และไฟล์ผลลัพธ์ล้าสมัย โดยไม่แก้ข้อมูลเกม `--check` ไม่เขียนไฟล์ผลลัพธ์
