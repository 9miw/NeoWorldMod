import subprocess
import time

bindings = [
  ("d781a6af-921e-48b4-be21-346ee190f0ea", 57, "Kocimira — ผู้ให้เควสต์หลัก"),
  ("506815a0-f23b-47f0-bf33-b8febea6fe9f", 60, "ลีโอ — ผู้ดูแลดันเจี้ยน"),
  ("231dd439-dfe2-4f94-af8c-6cc740b2ec94", 63, "ทหารรักษาโถงผจญภัย"),
  ("a4a4ef9b-19a7-4fef-9ea3-fc290ce945ab", 68, "วาร์ปเมือง — ตลาด"),
  ("32ad453a-6ffd-489d-bc52-f043a5929e76", 71, "ทหารรักษาตลาด"),
  ("f29bbbcd-ee84-4b0f-b6a7-7f640003272d", 74, "บรันท์ — ช่างตีเกราะ"),
  ("7225e0dc-f9a2-40c1-88c7-6ce54da85700", 77, "ลิออน — เจ้าเมือง NeoWorld"),
  ("503a5733-3742-4427-835b-6157661e4600", 80, "โอไรออน — ผู้ดูแลหอสมุดเวท"),
  ("ed0aeb17-6481-4e0e-a63b-67f451f208d9", 85, "วาร์ปเมือง — ปราสาท"),
  ("7f0866af-67a4-49aa-8f05-879eb094dd4a", 88, "ทหารรักษาปราสาท"),
  ("60241c70-e3f1-4d84-90f1-c5602f6549d6", 91, "วาร์ปเมือง — ท่าเรือ"),
  ("099391ba-7704-4b41-b1ce-114f309d3e6d", 94, "วาร์ปเมือง — เหมืองแร่"),
]

for uuid, dlg_id, title in bindings:
    cmd = f'data merge entity {uuid} {{NPCDialogOptions:[{{DialogSlot:0,NPCDialog:{{DialogCommand:"",Dialog:{dlg_id},Title:"{title}",DialogColor:14737632,OptionType:1}}}}]}}'
    subprocess.run(["python3", "/home/miw/send_console.py", cmd])
    print(f"Bound {uuid} -> {dlg_id} ({title})")
    time.sleep(0.1)

subprocess.run(["python3", "/home/miw/send_console.py", "save-all flush"])
print("Saved game")
