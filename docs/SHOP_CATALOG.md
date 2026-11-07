# ทะเบียนสินค้าร้านค้า NeoWorld

ตรวจจาก MariaDB ที่เซิร์ฟเวอร์ใช้งานจริง ณ **2026-11-01 21:42:01 (Asia/Bangkok)** อ่านภายใต้ read-only consistent snapshot ไม่เปลี่ยนสินค้า ราคา เงิน หรือข้อมูลผู้เล่น

รวม **14 ร้าน / 114 รายการสินค้า** (จำนวนแถวข้อเสนอ ไม่ใช่จำนวนชนิดไอเทมที่ไม่ซ้ำ) ใช้ Silver ทั้งหมด เปิดขายให้ผู้เล่น **31 รายการ** เปิดรับซื้อจากผู้เล่น **86 รายการ** โดย **3 รายการเปิดทั้งสองทาง**

## วิธีอ่านราคา

- **ร้านขายให้ผู้เล่น** = ผู้เล่นจ่ายเงินเพื่อซื้อ1ชิ้น
- **ร้านรับซื้อจากผู้เล่น** = ผู้เล่นขาย1ชิ้นแล้วได้เงิน
- เครื่องหมาย — หมายถึงราคา0และปิดการซื้อขายทิศนั้น ไม่ใช่แจกฟรี
- ราคาเป็นต่อ1ชิ้นตามข้อเสนอที่บันทึก ร้านเดียวกันอาจมีทั้งขายอย่างเดียว รับซื้ออย่างเดียว และทั้งสองทาง
- Skill Bookใช้itemIDเดียวกัน แต่ต่างcomponentของสกิล ต้องแยกเป็นคนละรายการ
- บันทึกเฉพาะร้านNPCแบบราคากำหนด ไม่รวมรายการประกาศซื้อขายระหว่างผู้เล่นของตลาดกลางออสการ์ ซึ่งเป็นคนละระบบและเปลี่ยนตามผู้ลงประกาศ

## สรุปร้าน

| ID | ร้าน | NPC ที่เปิดร้านนี้ | รายการ | ขายให้ผู้เล่น | รับซื้อจากผู้เล่น |
|---:|---|---|---:|---:|---:|
| 1 | ร้านอาหาร | เดรก | 6 | 6 | 0 |
| 2 | ร้านค้า-อีเทอร์ | อีเทอร์ | 0 | 0 | 0 |
| 3 | ร้านค้า-ฟินน์ | ฟินน์ | 11 | 0 | 11 |
| 4 | ร้านเกษตร | มาลี | 8 | 1 | 7 |
| 5 | ร้านประมง | ธารา | 4 | 0 | 4 |
| 6 | ร้านปศุสัตว์ | ภูผา | 26 | 0 | 26 |
| 7 | ร้านไม้ | โรแกน | 16 | 0 | 16 |
| 8 | ร้านรับซื้อของมอนสเตอร์ | เรเวน | 12 | 0 | 12 |
| 9 | ร้านสมุนไพรและวัตถุดิบปรุงยา | เอลินา | 9 | 4 | 8 |
| 10 | ร้านผึ้ง | มาลี | 2 | 0 | 2 |
| 11 | ร้านอุปกรณ์ผจญภัย | ฟินน์ | 2 | 2 | 0 |
| 12 | ร้านวัสดุและงานช่าง | โดรัน | 3 | 3 | 0 |
| 13 | ร้านอุปกรณ์ Create | เฟลิกซ์ | 7 | 7 | 0 |
| 14 | ร้านหนังสือทักษะ | ไอริส | 8 | 8 | 0 |


## ข้อค้นพบที่ควรรู้

- ร้านค้า-อีเทอร์ ID2 **ไม่มีรายการสินค้า** แม้มีNPCและปุ่มเปิดร้าน
- ร้านไม้ ID7 และสินค้าเร่ของฟินน์ ID3 ปัจจุบัน **รับซื้ออย่างเดียว** ไม่ได้ลงขายวัสดุไม้หรือสินค้าเร่ให้ผู้เล่น
- ร้านปศุสัตว์มี26รายการ แต่จำนวนมากเป็นขนแกะต่างสี ไม่ใช่26ระบบบริการ
- ร้านสมุนไพรมีบางรายการเปิดสองทาง ต้องดูราคาแต่ละแถว
- ร้านหนังสือทักษะ ID14 มี8เล่มครบ กลุ่มเริ่มต้น100Silver กลุ่มพัฒนาตัวละคร300Silver ไม่รับซื้อคืน
- ไฟล์H2เก่าบนเซิร์ฟเวอร์ไม่มีร้าน14 จึงไม่ใช้เป็นแหล่งข้อมูลรายงานนี้
- ไม่ได้เสนอเพิ่ม/ลบหรือปรับราคาในงานตรวจนี้

## รายการสินค้าแยกร้าน

### ร้าน 
1
 — 
ร้านอาหาร

NPC: **เดรก** · XYZ 102.5 / 104 / 187.5 · UUID `5a3cb80d-e1fd-47fe-a714-cff33d4e3dcb`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 13 | ขนมปัง | `minecraft:bread` | อาหาร | 3 | — | SILVER |
| 14 | มันฝรั่งอบ | `minecraft:baked_potato` | อาหาร | 2 | — | SILVER |
| 15 | ไก่ย่าง | `minecraft:cooked_chicken` | อาหาร | 4 | — | SILVER |
| 16 | เนื้อหมูย่าง | `minecraft:cooked_porkchop` | อาหาร | 6 | — | SILVER |
| 17 | เนื้อวัวย่าง | `minecraft:cooked_beef` | อาหาร | 6 | — | SILVER |
| 18 | ปลาแซลมอนย่าง | `minecraft:cooked_salmon` | อาหาร | 5 | — | SILVER |

### ร้าน 
2
 — 
ร้านค้า-อีเทอร์

NPC: **อีเทอร์** · XYZ 86.5 / 108 / 139.5 · UUID `595afd9c-2c63-40a8-9256-b80fae50609d`

ไม่มีรายการสินค้าในฐานข้อมูล ณเวลาตรวจ

### ร้าน 
3
 — 
ร้านค้า-ฟินน์

NPC: **ฟินน์** · XYZ 70.5 / 104 / 225.5 · UUID `4a31de44-31d0-4d68-bc26-0289a2ae93fc`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 2 | ถ่านหิน | `minecraft:coal` | แร่ | — | 1 | SILVER |
| 3 | ทองแดงดิบ | `minecraft:raw_copper` | แร่ | — | 1 | SILVER |
| 4 | เหล็กดิบ | `minecraft:raw_iron` | แร่ | — | 3 | SILVER |
| 5 | ทองดิบ | `minecraft:raw_gold` | แร่ | — | 5 | SILVER |
| 6 | ผงเรดสโตน | `minecraft:redstone` | แร่ | — | 1 | SILVER |
| 7 | ลาพิสลาซูลี | `minecraft:lapis_lazuli` | แร่ | — | 2 | SILVER |
| 8 | เนเธอร์ควอตซ์ | `minecraft:quartz` | แร่ | — | 2 | SILVER |
| 9 | เพชร | `minecraft:diamond` | แร่ | — | 40 | SILVER |
| 10 | บล็อกทองแดงดิบ | `minecraft:raw_copper_block` | แร่ | — | 9 | SILVER |
| 11 | บล็อกเหล็กดิบ | `minecraft:raw_iron_block` | แร่ | — | 27 | SILVER |
| 12 | บล็อกทองดิบ | `minecraft:raw_gold_block` | แร่ | — | 45 | SILVER |

### ร้าน 
4
 — 
ร้านเกษตร

NPC: **มาลี** · XYZ 120.5 / 104 / 227.5 · UUID `6526b8bc-e2e2-4289-b1cb-a66eff7f42dc`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 19 | ข้าวสาลี | `minecraft:wheat` | เกษตร | — | 2 | SILVER |
| 20 | แครอต | `minecraft:carrot` | เกษตร | — | 1 | SILVER |
| 21 | มันฝรั่ง | `minecraft:potato` | เกษตร | — | 1 | SILVER |
| 22 | บีทรูต | `minecraft:beetroot` | เกษตร | — | 2 | SILVER |
| 23 | ฟักทอง | `minecraft:pumpkin` | เกษตร | — | 4 | SILVER |
| 24 | แตงโม | `minecraft:melon` | เกษตร | — | 3 | SILVER |
| 25 | อ้อย | `minecraft:sugar_cane` | เกษตร | — | 1 | SILVER |
| 96 | ผงกระดูก | `minecraft:bone_meal` | เกษตร | 2 | — | SILVER |

### ร้าน 
5
 — 
ร้านประมง

NPC: **ธารา** · XYZ 103.5 / 103 / 237.5 · UUID `fbd944fd-71d7-47fe-969b-9caa00d00a96`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 26 | ปลาค็อด | `minecraft:cod` | ประมง | — | 2 | SILVER |
| 27 | ปลาแซลมอน | `minecraft:salmon` | ประมง | — | 3 | SILVER |
| 28 | ปลาปักเป้า | `minecraft:pufferfish` | ประมง | — | 5 | SILVER |
| 29 | ปลาเขตร้อน | `minecraft:tropical_fish` | ประมง | — | 4 | SILVER |

### ร้าน 
6
 — 
ร้านปศุสัตว์

NPC: **ภูผา** · XYZ 120.5 / 104 / 239.5 · UUID `4c6fc590-55f9-4f76-8e88-9bf5d6726279`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 30 | เนื้อวัวดิบ | `minecraft:beef` | ปศุสัตว์ | — | 3 | SILVER |
| 31 | เนื้อหมูดิบ | `minecraft:porkchop` | ปศุสัตว์ | — | 3 | SILVER |
| 32 | ไก่ดิบ | `minecraft:chicken` | ปศุสัตว์ | — | 2 | SILVER |
| 33 | เนื้อแกะดิบ | `minecraft:mutton` | ปศุสัตว์ | — | 3 | SILVER |
| 34 | เนื้อกระต่ายดิบ | `minecraft:rabbit` | ปศุสัตว์ | — | 3 | SILVER |
| 35 | หนัง | `minecraft:leather` | ปศุสัตว์ | — | 2 | SILVER |
| 36 | ขนแกะสีขาว | `minecraft:white_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 37 | ไข่ | `minecraft:egg` | ปศุสัตว์ | — | 1 | SILVER |
| 38 | ขนนก | `minecraft:feather` | ปศุสัตว์ | — | 1 | SILVER |
| 72 | หนังกระต่าย | `minecraft:rabbit_hide` | ปศุสัตว์ | — | 1 | SILVER |
| 73 | เท้ากระต่าย | `minecraft:rabbit_foot` | ปศุสัตว์ | — | 5 | SILVER |
| 74 | ขนแกะสีส้ม | `minecraft:orange_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 75 | ขนแกะสีบานเย็น | `minecraft:magenta_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 76 | ขนแกะสีฟ้าอ่อน | `minecraft:light_blue_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 77 | ขนแกะสีเหลือง | `minecraft:yellow_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 78 | ขนแกะสีเขียวอ่อน | `minecraft:lime_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 79 | ขนแกะสีชมพู | `minecraft:pink_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 80 | ขนแกะสีเทา | `minecraft:gray_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 81 | ขนแกะสีเทาอ่อน | `minecraft:light_gray_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 82 | ขนแกะสีฟ้าอมเขียว | `minecraft:cyan_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 83 | ขนแกะสีม่วง | `minecraft:purple_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 84 | ขนแกะสีน้ำเงิน | `minecraft:blue_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 85 | ขนแกะสีน้ำตาล | `minecraft:brown_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 86 | ขนแกะสีเขียว | `minecraft:green_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 87 | ขนแกะสีแดง | `minecraft:red_wool` | ปศุสัตว์ | — | 2 | SILVER |
| 88 | ขนแกะสีดำ | `minecraft:black_wool` | ปศุสัตว์ | — | 2 | SILVER |

### ร้าน 
7
 — 
ร้านไม้

NPC: **โรแกน** · XYZ 85.5 / 104 / 239.5 · UUID `8bb489f7-da3a-4407-b3ff-734430b77773`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 39 | ท่อนไม้โอ๊ก | `minecraft:oak_log` | ไม้ | — | 1 | SILVER |
| 40 | ท่อนไม้สนสปรูซ | `minecraft:spruce_log` | ไม้ | — | 1 | SILVER |
| 41 | ท่อนไม้เบิร์ช | `minecraft:birch_log` | ไม้ | — | 1 | SILVER |
| 42 | ท่อนไม้ป่าดงดิบ | `minecraft:jungle_log` | ไม้ | — | 1 | SILVER |
| 43 | ท่อนไม้อะเคเซีย | `minecraft:acacia_log` | ไม้ | — | 1 | SILVER |
| 44 | ท่อนไม้ดาร์กโอ๊ก | `minecraft:dark_oak_log` | ไม้ | — | 1 | SILVER |
| 45 | ท่อนไม้โกงกาง | `minecraft:mangrove_log` | ไม้ | — | 1 | SILVER |
| 46 | ท่อนไม้เชอร์รี | `minecraft:cherry_log` | ไม้ | — | 1 | SILVER |
| 47 | ต้นอ่อนโอ๊ก | `minecraft:oak_sapling` | ไม้ | — | 1 | SILVER |
| 48 | ต้นอ่อนสนสปรูซ | `minecraft:spruce_sapling` | ไม้ | — | 1 | SILVER |
| 49 | ต้นอ่อนเบิร์ช | `minecraft:birch_sapling` | ไม้ | — | 1 | SILVER |
| 50 | ต้นอ่อนป่าดงดิบ | `minecraft:jungle_sapling` | ไม้ | — | 1 | SILVER |
| 51 | ต้นอ่อนอะเคเซีย | `minecraft:acacia_sapling` | ไม้ | — | 1 | SILVER |
| 52 | ต้นอ่อนดาร์กโอ๊ก | `minecraft:dark_oak_sapling` | ไม้ | — | 1 | SILVER |
| 53 | ต้นอ่อนเชอร์รี | `minecraft:cherry_sapling` | ไม้ | — | 1 | SILVER |
| 54 | หน่อโกงกาง | `minecraft:mangrove_propagule` | ไม้ | — | 1 | SILVER |

### ร้าน 
8
 — 
ร้านรับซื้อของมอนสเตอร์

NPC: **เรเวน** · XYZ 278.5 / 114 / 135.5 · UUID `c636db50-60bf-4dfc-8f36-2ebe38b53300`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 55 | กระดูก | `minecraft:bone` | มอนสเตอร์ | — | 1 | SILVER |
| 56 | เนื้อเน่า | `minecraft:rotten_flesh` | มอนสเตอร์ | — | 1 | SILVER |
| 57 | ดินปืน | `minecraft:gunpowder` | มอนสเตอร์ | — | 2 | SILVER |
| 58 | ใยแมงมุม | `minecraft:string` | มอนสเตอร์ | — | 1 | SILVER |
| 59 | ตาแมงมุม | `minecraft:spider_eye` | มอนสเตอร์ | — | 2 | SILVER |
| 60 | ไข่มุกเอนเดอร์ | `minecraft:ender_pearl` | มอนสเตอร์ | — | 3 | SILVER |
| 61 | ลูกเมือก | `minecraft:slime_ball` | มอนสเตอร์ | — | 1 | SILVER |
| 62 | ถุงหมึก | `minecraft:ink_sac` | มอนสเตอร์ | — | 1 | SILVER |
| 63 | ถุงหมึกเรืองแสง | `minecraft:glow_ink_sac` | มอนสเตอร์ | — | 2 | SILVER |
| 89 | เศษพริสมารีน | `minecraft:prismarine_shard` | มอนสเตอร์ | — | 2 | SILVER |
| 90 | ผลึกพริสมารีน | `minecraft:prismarine_crystals` | มอนสเตอร์ | — | 3 | SILVER |
| 91 | ลูกธนู | `minecraft:arrow` | มอนสเตอร์ | — | 1 | SILVER |

### ร้าน 
9
 — 
ร้านสมุนไพรและวัตถุดิบปรุงยา

NPC: **เอลินา** · XYZ 123.5 / 104 / 187.5 · UUID `48c45355-ca94-4193-afbb-8119a7ad3ec9`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 64 | เห็ดน้ำตาล | `minecraft:brown_mushroom` | ปรุงยา | — | 1 | SILVER |
| 65 | เห็ดแดง | `minecraft:red_mushroom` | ปรุงยา | — | 1 | SILVER |
| 66 | เนเธอร์วอร์ต | `minecraft:nether_wart` | ปรุงยา | — | 1 | SILVER |
| 67 | แท่งเบลซ | `minecraft:blaze_rod` | ปรุงยา | — | 4 | SILVER |
| 68 | น้ำตาแกสต์ | `minecraft:ghast_tear` | ปรุงยา | — | 8 | SILVER |
| 92 | ผงหินเรืองแสง | `minecraft:glowstone_dust` | ปรุงยา | 6 | 2 | SILVER |
| 93 | เยื่อแฟนทอม | `minecraft:phantom_membrane` | ปรุงยา | 12 | 4 | SILVER |
| 94 | ตาแมงมุม | `minecraft:spider_eye` | ปรุงยา | 6 | — | SILVER |
| 95 | ครีมแมกมา | `minecraft:magma_cream` | ปรุงยา | 8 | 2 | SILVER |

### ร้าน 
10
 — 
ร้านผึ้ง

NPC: **มาลี** · XYZ 120.5 / 104 / 227.5 · UUID `6526b8bc-e2e2-4289-b1cb-a66eff7f42dc`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 70 | ขวดน้ำผึ้ง | `minecraft:honey_bottle` | ผึ้ง | — | 3 | SILVER |
| 71 | รวงผึ้ง | `minecraft:honeycomb` | ผึ้ง | — | 2 | SILVER |

### ร้าน 
11
 — 
ร้านอุปกรณ์ผจญภัย

NPC: **ฟินน์** · XYZ 70.5 / 104 / 225.5 · UUID `4a31de44-31d0-4d68-bc26-0289a2ae93fc`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 97 | ลูกธนู | `minecraft:arrow` | ผจญภัย | 3 | — | SILVER |
| 98 | อาน | `minecraft:saddle` | ผจญภัย | 60 | — | SILVER |

### ร้าน 
12
 — 
ร้านวัสดุและงานช่าง

NPC: **โดรัน** · XYZ 72.5 / 108 / 117.5 · UUID `010fe91a-d2ff-47e7-8be3-db5e4e231b8e`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 99 | ใยแมงมุม | `minecraft:string` | งานช่าง | 3 | — | SILVER |
| 100 | ลูกเมือก | `minecraft:slime_ball` | งานช่าง | 4 | — | SILVER |
| 101 | ถุงหมึก | `minecraft:ink_sac` | งานช่าง | 4 | — | SILVER |

### ร้าน 
13
 — 
ร้านอุปกรณ์ Create

NPC: **เฟลิกซ์** · XYZ 70.5 / 104 / 236.5 · UUID `e3bd39e1-1ae7-42a7-a600-76464856aef0`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 102 | ประแจ Create | `create:wrench` | Create | 80 | — | SILVER |
| 103 | แว่นวิศวกร | `create:goggles` | Create | 100 | — | SILVER |
| 104 | เพลาหมุน | `create:shaft` | Create | 3 | — | SILVER |
| 105 | เฟืองเล็ก | `create:cogwheel` | Create | 6 | — | SILVER |
| 106 | เฟืองใหญ่ | `create:large_cogwheel` | Create | 9 | — | SILVER |
| 107 | สายพานกล | `create:belt_connector` | Create | 12 | — | SILVER |
| 108 | กังหันน้ำ | `create:water_wheel` | Create | 30 | — | SILVER |

### ร้าน 
14
 — 
ร้านหนังสือทักษะ

NPC: **ไอริส** · XYZ 76.5 / 111 / 188.5 · UUID `45218be8-cd0f-4b26-a596-eac27d3a188f`

| Offer ID | สินค้า | Item ID | หมวด | ร้านขายให้ผู้เล่น | ร้านรับซื้อจากผู้เล่น | สกุลเงิน |
|---:|---|---|---|---:|---:|---|
| 109 | หนังสือทักษะ roll | `epicfight:skillbook` | เริ่มต้น | 100 | — | SILVER |
| 110 | หนังสือทักษะ step | `epicfight:skillbook` | เริ่มต้น | 100 | — | SILVER |
| 111 | หนังสือทักษะ guard | `epicfight:skillbook` | เริ่มต้น | 100 | — | SILVER |
| 112 | หนังสือทักษะ stamina_pillager | `epicfight:skillbook` | เริ่มต้น | 100 | — | SILVER |
| 113 | หนังสือทักษะ technician | `epicfight:skillbook` | พัฒนาตัวละคร | 300 | — | SILVER |
| 114 | หนังสือทักษะ swordmaster | `epicfight:skillbook` | พัฒนาตัวละคร | 300 | — | SILVER |
| 115 | หนังสือทักษะ berserker | `epicfight:skillbook` | พัฒนาตัวละคร | 300 | — | SILVER |
| 116 | หนังสือทักษะ parrying | `epicfight:skillbook` | พัฒนาตัวละคร | 300 | — | SILVER |

ข้อมูลพิเศษของไอเทมที่บันทึกจริง:

| Offer ID | Components |
|---:|---|
| 109 | `{"epicfight:skill":"epicfight:roll"}` |
| 110 | `{"epicfight:skill":"epicfight:step"}` |
| 111 | `{"epicfight:skill":"epicfight:guard"}` |
| 112 | `{"epicfight:skill":"epicfight:stamina_pillager"}` |
| 113 | `{"epicfight:skill":"epicfight:technician"}` |
| 114 | `{"epicfight:skill":"epicfight:swordmaster"}` |
| 115 | `{"epicfight:skill":"epicfight:berserker"}` |
| 116 | `{"epicfight:skill":"epicfight:parrying"}` |

## แหล่งข้อมูลและขอบเขตการตรวจ

- แหล่งหลัก: ตารางร้านค้าและข้อเสนอของMariaDBที่เกมใช้งานจริง อ่านในtransactionแบบread-only consistent snapshot
- ถอดitem_nbtทุกแถวตามรูปแบบMarketItemCodec: gzip NBT → item รวมcomponentsครบ
- ตรวจ14ร้าน114แถว จำนวนไอเทมต่อข้อเสนอเป็น1ทั้งหมด รายการSkill Book8แถวตรงกับcomponentและราคาอนุมัติ
- ข้อมูลJSONอ้างอิง: [catalog-20261101.json](shop-data/catalog-20261101.json)
- พิกัดNPCเป็นค่าที่บันทึกในโลกเวลาสำรวจ ไม่ใช่ตำแหน่งติดตามสด ชื่อภาษาไทยของสินค้าบางรายการเป็นคำเรียกเพื่ออ่านง่าย Item IDและcomponentsเป็นตัวอ้างอิงหลัก
- ไม่ดึงประวัติธุรกรรม ยอดเงิน รหัสผ่าน หรือข้อมูลบัญชีมาใส่รายงาน
- เป็นการตรวจฐานข้อมูล ไม่ได้ทดสอบซื้อ/ขายจริงทุกแถว และไม่แก้โค้ด จึงไม่ต้องcompileJava
