# Combat Logout Implementation Plan

> **For agentic workers:** Use executing-plans inline; finish with one read-only review.

**Goal:** ค้างตัวละครจริง 60 วินาทีเมื่อออกระหว่าง combat tag 60 วินาที
**Architecture:** pure state machine + server lifecycle service + narrowly targeted vanilla mixins; reuse Killer combat attribution and death queue
**Tech Stack:** Java 21 / Minecraft 1.21.1 / NeoForge 21.1.248
**Spec:** `docs/superpowers/specs/2026-10-27-combat-logout-design.md`

## Constraints
ไม่เปลี่ยนกติกาหัวแดง/ไอเทม ไม่ค้างในสนาม ไม่เพิ่ม dependency ไม่สร้างโลก ไม่ commit/push; รัน compileJava หลังชุดแก้โค้ด ไม่ทำ full build

## Tasks
- [x] เขียน regression ก่อนสำหรับ tag สองฝ่าย 1200 ticks, exempt arena, fixed retention 1200 ticks, duplicate disconnect, single release และข้อมูลตัวจริง
- [x] เพิ่ม `killer/CombatLogoutState.java`: tag(UUID,UUID,long,boolean), remaining(UUID,long), retain(UUID,T,long), body(UUID), take(UUID), bodies(), clearCombat(UUID), tagUntil(UUID,long), clear()
- [x] เพิ่ม `killer/CombatLogoutManager.java` และ `CombatLogoutEvents.java`: เก็บตัวจริง, tick/flush death, save/remove ตามปกติ, reconnect และ shutdown; เชื่อม KillerManager.recordDamage/tick และป้องกันล้างสิทธิ์ก่อน body หาย
- [x] เพิ่ม mixin แยกการขาด connection ออกจากการ remove, ตรวจ duplicate/full-server เฉพาะตัวค้างและนำออกก่อน load, ทิ้ง packet ของ connection ที่ขาดแล้ว; ลงทะเบียนใน neoworld.mixins.json
- [x] เพิ่มคำเตือนผ่าน ModLanguageProvider และภาษาไทย/อังกฤษ โดยไม่รัน datagen ทั้งโครงการ
- [x] ทดสอบ regression และ bytecode contract ของจุด hook, compileJava แล้วตรวจทานเรื่อง reconnect, death, logout และ server stop
- [x] บันทึกผลและข้อจำกัดใน docs/KILLER_SYSTEM.md

## Review Focus
ไม่ให้ attacker logout หลบ sin; ไม่บันทึก inventory เก่าทับสถานะตาย; duplicate login ระหว่าง config; dead/offline entity ไม่ลด sin; shutdown ไม่ทิ้ง entity และ packet queue

## Execution record
- RED: regression compilation showed missing CombatLogoutState before implementation. Initial reconnect timer test expected only body remainder but an additional hit had correctly refreshed combat longer; corrected the independently calculated expectation to 201 ticks. Body deadline itself stays fixed.
- GREEN: state machine and real PlayerData fixture passed. No entity/inventory copies are created.
- Fresh read-only review found the earlier duplicate check in ServerConfigurationPacketListenerImpl blocked reconnect before placeNewPlayer. Added configuration hook coverage (observed missing-hook assertion failure), then a narrowly targeted redirect that ignores only the retained old player. Real online duplicate rejection remains.
- Runtime source inspection also found ServerPlayer constructors grant 60 invulnerable ticks. Reconnect now transfers the old body's value via a field accessor; the bytecode contract test checks its exact field/type.
- Final command: `.\gradlew.bat compileJava killerRegressionTest playerProfileRegressionTest pvpRegressionTest tradeRegressionTest regressionTest --console=plain --max-workers=1` — BUILD SUCCESSFUL; 849 trade checks, 43 economy checks, 195 database checks and 35 market security checks passed alongside Killer/Profile/PvP suites. Only existing terminal-feature warnings.
- No two-client runtime test performed; see docs/KILLER_SYSTEM.md for the pending checklist. No commit, push, package, world generation or server launch performed.
