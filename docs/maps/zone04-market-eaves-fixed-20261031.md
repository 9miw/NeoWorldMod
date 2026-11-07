# ตลาด — แก้ชายคาและอัปเดต skill

แก้เฉพาะหลังคาของอาคารปัจจุบัน ไม่ใช่งานขยายฐาน31×29/17×21และมุขภายนอกที่ยังเป็นแบบเสนอ ไม่deploy

- ร้านพ่อค้า: เพิ่มปลายชายคารอบผนังที่เปิดของหลังคาหลักและปีกซ้าย ใช้กรอบแกนหลังคาแยกจากmaskพื้นหมุน30°
- ร้านอาหาร: เติมชายคาตะวันออกX111 อีกสามด้านมีชายคาเดิมอยู่แล้ว เก็บขอบไม้หลังคาเก่าY112/Z180–196ที่ค้างเหนือทางเดิน ไม่แตะพื้นและฐาน
- ร้านสมุนไพร: เติมขอบด้านข้างหลังคาหลัก แยกหลังคามุขกระจกต่ำและชายคาของมุข ไม่เอาหลังคาหลักคลุมกระจก รอยต่อเก็บคานไม้เดิม

เปลี่ยน276บล็อก13ชังก์ แยก5ส่วนหลังคา ขนาดอาคารและพื้นเดิมคงเดิม จุดยืน/home/patrol24จุดและช่องทางโรงตีเหล็กตรวจผ่าน ไม่มีdeltaนอกroof mask และไม่เปลี่ยนblock entitiesหรือlevel.dat

Backup: archive/map-edit-backups/before-market-eaves-20261031/starter_sky_land_spawn_v1

Source a9429fbed131a8b8c3ba0695398a3c79eb37bbf45482c03b892d19a3ef16e62d → result c3bc2abeb8970fc50bf735a413e4fc36af1febd91fba295ac13293c11d283071

Editor: tools/maps/fix_market_eaves.cjs มีdry-run/default, hashguard, once-only/reportguard และตรวจcollisionก่อนเขียน แพตช์โลกล่าสุด ไม่ย้อนgeneratorเดิม ตัวตรวจ tools/maps/verify_market_eaves.cjs และคำสั่ง verify_market_redesign.cjs ใช้การตรวจต่อจากbaselineล่าสุดโดยยังตรวจการเข้าถึงเดิม

ตรวจREDในแมพเดิมพบ Missing overhang 62,115,199 จากนั้นGREENหลังแก้ ตรวจnode syntax, sampleชายคา, semanticทุกvoxelนอกmask, พื้น/บันได/จุดบริการ และindependent reviewผ่าน ไม่ใช่การรับรองรูปลักษณ์มอคอัพทุกส่วน

Native Minecraft1.21.1อ่าน576chunks/3553936blocks/176states/30block entitiesผ่าน compileJava BUILD SUCCESSFUL หลังแก้โค้ดล่าสุด ตรวจภาพtextured WebGLและเก็บ zone04-market-eaves-fixed-20261031.png ยังไม่ทดสอบเกมจริงหรือสีSapixcraft และยังไม่deploy

Skillเดิม C:/Users/miw/.codex/skills/neoworld-maps อัปเดตSKILL.mdและreferences/architecture.mdหัวข้อRoof envelopes and eaves: แยกwall/roof/awning envelopes, สร้างroofmaskอิสระ, ตรวจทุกด้าน/อาคารหมุน/รอยต่อหลังคา/การชน3D และไม่ใช้floor countแทนการเทียบมวลอาคาร

ตรวจfrontmatterกับlocal links8จุดผ่าน ตัวquick_validate.pyมาตรฐานยังรันไม่ได้เพราะruntimeไม่มีPyYAML ไม่ได้ติดตั้งเพิ่มและไม่อ้างว่าvalidatorเต็มผ่าน

ข้อเสนอขยายตลาดต้องสำรวจ/แพตช์จากbaselineใหม่c3bc2a...และใช้backupใหม่ ห้ามrerunหรือrestageeditorตลาดเก่าเพื่อขยายเพราะจะลบงานชายคาชุดนี้
