# เก็บงานจากการตรวจทั้งเกาะ — จุดเกิดและกิลด์

ผู้ใช้ยืนยันให้เก็บ3รายการจากรายงานwhole-island-audit-20261031.md แก้ในโลกเดิมเฉพาะจุด ไม่ขยายตลาด ไม่ปรับภูมิประเทศหรือNPC และไม่deploy

## งานที่ลงแมพ

1. โคมจุดเกิด153/99/325และ167/99/325: เติมฐานchiseled_stone_bricksที่Y97และเสาstone_brick_wallที่Y98 รวม4บล็อก โคมเดิมคงตำแหน่งและมีฐานจากพื้นหินY96รองรับ
2. โรงเตี๊ยมกิลด์: คงผนังแกน23×29หมุน20° เพิ่มroofmaskแกน25×31ให้ชายคายื่นหนึ่งบล็อก รอบนอก108คอลัมน์ รวม216บล็อก ใช้purple_concreteต่อความลาดเดิมพร้อมขอบstone_brick_slab ไม่ตัดห้องหรือครอบทางเดินด้วยหลังคาต่ำ
3. ราวชั้นลอยโถงกิลด์: ปรับconnection propertiesเฉพาะfenceที่X240/248,Y119,Z179–198 จำนวน33บล็อก ทำให้คู่ราว31ช่วงเชื่อมทั้งสองด้าน ไม่เชื่อมขาโต๊ะอื่นแบบเหมา

เปลี่ยนรวม253บล็อกใน12ชังก์: เพิ่มบล็อกจริง220 ส่วน33รายการเป็นการเปลี่ยนstateของรั้วเดิม

## สำรองและการป้องกัน

Backup: archive/map-edit-backups/before-island-audit-repairs-20261031/starter_sky_land_spawn_v1

Source c3bc2abeb8970fc50bf735a413e4fc36af1febd91fba295ac13293c11d283071 → result 3de64fce5d84eaea219f737a267a8863ccec7bea2c4449828b755fb82ad78f02

tools/maps/repair_island_audit.cjs ค่าเริ่มต้นdry-run ต้องระบุ--apply มีsource-hashguardและห้ามreplayเมื่อมีreport/backupแล้ว ตรวจcollisionในวงชายคาก่อนเขียน งานต่อไปต้องแพตช์จากbaselineใหม่นี้ ไม่restagegeneratorกิลด์หรือตลาดเก่าทับงานซ่อม

รายงานบล็อกทุกตำแหน่ง: maps/starter_sky_land_spawn_v1/island-audit-repairs-report.json ข้อมูลstageอยู่archive/map-edit-staging/island-audit-repairs-20261031 ไม่เพิ่มแมพในviewer

## Verification

- REDก่อนสร้าง: ตัวตรวจพบMissing spawn lantern plinthในแมพเดิม หลังแก้ตรวจครบทั้งฐานโคม ชายคา108คอลัมน์ และราว31คู่ผ่าน
- ตรวจsemanticทั้งกริดเทียบbackup: deltaตรงrepair mask253ตำแหน่ง นอกmask0 ไม่มีการเปลี่ยนผนัง/พื้นอาคารหรือโซนอื่น
- จุดยืนNPCกิลด์9จุดและheadroomคงเดิม ทางเข้าห้องพัก4ห้องและพื้นที่ชั้นลอยยังเชื่อมถึงกัน ตรวจจุดรองรับ/ช่องศีรษะเส้นทางที่บันทึกไว้ผ่าน
- level.dat และblock entitiesเดิมทุกชังก์ไม่เปลี่ยน ไม่เขียนentity filesหรือserver
- Node syntaxผ่าน saved-map verifier tools/maps/verify_island_audit_repairs.cjsผ่าน independent reviewไม่มีข้อแก้สำคัญ
- Native Minecraft1.21.1อ่าน576ชังก์3554156บล็อก180statesและ30block entitiesผ่าน compileJava BUILD SUCCESSFUL
- ตรวจWebGLจากsaved worldและเก็บภาพexterior/ชั้นลอย ใช้ภาพจริงไม่ใช่ภาพAI ยังไม่ทดสอบfull-hitbox/neighbor updates/แสงกลางคืนหรือNPCในMinecraftจริง

สถานะ: local only งานขยายตลาดและตกแต่งภูมิประเทศยังแยกเป็นงานค้าง ไม่ถือว่าทั้งแมพตรงมอคอัพทุกส่วนจากการเก็บ3จุดนี้
