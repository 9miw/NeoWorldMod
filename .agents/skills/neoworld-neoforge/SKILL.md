---
name: neoworld-neoforge
description: พัฒนา Minecraft NeoWorld ด้วย NeoForge 1.21.1 และ Java 21 พร้อมตรวจสอบ Multiplayer, GUI และ Gradle
---

# NeoWorld NeoForge Skill

- อ่าน AGENTS.md, gradle.properties และ build.gradle ก่อนแก้ไข
- ใช้ Minecraft 1.21.1, NeoForge 21.1.x และ Java 21 ตามโปรเจกต์จริง
- ห้ามใช้ Forge รุ่นเก่าหรือ Fabric API ปะปนกับ NeoForge
- สำรวจโค้ดเดิมและวางแผนก่อนแก้ไขระบบขนาดใหญ่
- ตรวจสอบ Event Bus, Registry, Client/Server separation และ Networking
- ตรวจสอบสิทธิ์ผู้เล่นและข้อมูลฝั่ง Server สำหรับ Party, Quest, NPC และ Economy
- ก่อนแก้ GUI ต้องอ่าน docs/gui/NEOWORLD_GUI_STYLE.md
- หลังแก้โค้ดรัน .\gradlew.bat compileJava และรายงานผล ห้ามสั่ง Full Build อัตโนมัติ
- ใช้ภาษาไทยเป็นหลักสำหรับเอกสาร Markdown
- ปฏิบัติตาม AGENTS.md เมื่อมีข้อกำหนดขัดแย้ง
- ห้ามกล่าวว่าทดสอบผ่านหากยังไม่ได้ทดสอบจริง
