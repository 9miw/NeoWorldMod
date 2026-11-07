# Ruined Chapel Implementation Plan

**Goal:** ต่อเติมโบสถ์ร้างในโลก leveling_island_01 ตามขอบเขตที่ผู้ใช้ยืนยัน

**Architecture:** แพตช์ region เดิมด้วย typed NBT helper; สำรองและตรวจ source hash ก่อนเขียน ไม่สร้างโลกใหม่

**Tech Stack:** Node.js, Vanilla Minecraft blocks, existing WebGL viewer

- ขอบเขต X105–148 Z42–92; พื้น Y132, ประตูทิศใต้แกน X126
- โถง X117–135 Z53–81, มุขแท่นบูชาทรงโค้งด้านเหนือ, หอระฆังหักตะวันตก และสุสานเล็กตะวันออก
- คงต้นไม้เดิม ถนน และหมู่บ้าน; ไม่มี NPC/มอนสเตอร์หรือการ deploy

## Tasks

- [x] เพิ่ม tools/maps/build_ruined_chapel.cjs สำหรับ staged patch, backup, semantic verification และ report
- [x] ตรวจโถงเดินได้จากประตูถึงแท่นบูชา ช่องเหนือหัว ต้นไม้/เส้นทางเดิม และขอบเขตการเปลี่ยน
- [x] รันตัวตรวจหมู่บ้าน ทางเดิน และ compileJava/native Minecraft reader
- [ ] ตรวจ saved-world ผ่าน WebGL และเก็บภาพ — ถูกบล็อกเพราะ Local browser use is disabled in Work settings
- [x] บันทึกขนาด ระดับ backup ผลตรวจ และข้อจำกัดใน MAP.md
