# แอสเซ็ตหมวดแฟชั่น

ระยะ 1 ของการออกแบบตู้เสื้อผ้าสามส่วนที่อนุมัติ ครอบคลุมเฉพาะแผงหมวดซ้าย พรีวิวผู้เล่น แผงตู้ และช่องตู้ยังใช้แอสเซ็ตเดิม

อ้างอิง: `fashion-wardrobe-approved-mockup.png`

## เท็กซ์เจอร์รันไทม์

ทั้งหมดอยู่ใต้ `assets/neoworld/textures/gui/fashion/`:

| ไฟล์ | ขนาด | หน้าที่ |
| --- | --- | --- |
| `fashion_category_panel.png` | 384 x 728 | แผงซ้ายว่างและเส้นแบ่งหัว |
| `fashion_category_row_normal.png` | 384 x 104 | แถวหมวดปกติ |
| `fashion_category_row_hover.png` | 384 x 104 | ชี้เมาส์หรือโฟกัสคีย์บอร์ด |
| `fashion_category_row_selected.png` | 384 x 104 | หมวดที่เลือก ขอบทอง |
| `fashion_category_row_disabled.png` | 384 x 104 | แถวหมวดปิดใช้ |

สี่แถวที่วัดขอบอัลฟาในชีตต้นฉบับส่งออกแยกด้วย nearest-neighbor และขนาดปลายทางเท่ากัน `export-category-assets.ps1` แสดงขอบต้นทาง ผลส่งออกตัด padding โปร่งใสของชีตออก

`FashionCategoryButton` ใช้ขอบต้นทาง 8 พิกเซลและ nine-slice สืบทอดอินพุต ทูลทิป เสียง และการบรรยายจาก Minecraft Button ไอคอน ป้าย ชื่อที่สวม และเชฟรอนวาดแบบไดนามิกโดย `FashionScreen`

`fashion-category-assets-preview.png` ประกอบ PNG รันไทม์จริงพร้อมป้ายตัวอย่างและไอคอนอ้างอิงเพื่อรีวิว ไม่ใช่สกรีนช็อตในเกม และไอคอนตัวอย่างไม่ได้ติดตั้งเป็นไอคอนหมวดรันไทม์
