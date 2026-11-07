const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const backupDialogsDir = '/home/miw/backups/neoworld-dialog-migration-20261025-2334/customnpcs/dialogs';
const liveDialogsDir = '/home/miw/services/neoworld/world/customnpcs/dialogs';
const tempDir = '/home/miw/dialogs_unified_temp';

console.log('=== STEP 1: Verify Backup Directory ===');
if (!fs.existsSync(backupDialogsDir)) {
  console.error(`Backup directory does not exist: ${backupDialogsDir}`);
  process.exit(1);
}
console.log('Backup directory verified.');

console.log('=== STEP 2: Prepare Staging in Temp Directory ===');
if (fs.existsSync(tempDir)) {
  fs.rmSync(tempDir, { recursive: true, force: true });
}
fs.mkdirSync(tempDir, { recursive: true });

function createDialog(id, title, text, options, extra = {}) {
  const opts = options.map((opt, idx) => ({
    OptionSlot: opt.slot !== undefined ? opt.slot : idx,
    Option: {
      DialogCommand: opt.cmd || "",
      Dialog: opt.dialog !== undefined ? opt.dialog : -1,
      Title: opt.title,
      DialogColor: opt.color || 14737632,
      OptionType: opt.type !== undefined ? opt.type : 1
    }
  }));

  return {
    DialogShowWheel: extra.DialogShowWheel || "0b",
    AvailabilityQuestId: -1,
    Options: opts,
    AvailabilityScoreboardType: 1,
    DialogHideNPC: "0b",
    AvailabilityFactionStance: 0,
    AvailabilityScoreboard2Value: 1,
    DialogId: id,
    AvailabilityQuest: 0,
    AvailabilityDialog4: 0,
    AvailabilityScoreboardObjective: "",
    AvailabilityDialog3: 0,
    AvailabilityQuest2: 0,
    AvailabilityQuest3: 0,
    AvailabilityScoreboard2Objective: "",
    AvailabilityQuest4: 0,
    ModRev: 18,
    DecreaseFaction1Points: "0b",
    DialogQuest: -1,
    AvailabilityDialog2: 0,
    OptionFactions1: -1,
    AvailabilityDayTime: 0,
    OptionFactions2: -1,
    AvailabilityFaction2Id: -1,
    OptionFaction1Points: 100,
    AvailabilityScoreboardValue: 1,
    DialogDisableEsc: "0b",
    AvailabilityFaction: 0,
    DialogTitle: title,
    AvailabilityDialog: 0,
    AvailabilityScoreboard2Type: 1,
    AvailabilityFaction2: 0,
    AvailabilityFactionId: -1,
    AvailabilityFaction2Stance: 0,
    DialogCommand: extra.DialogCommand || "",
    AvailabilityDialogId: -1,
    OptionFaction2Points: 100,
    DialogText: text,
    AvailabilityQuest4Id: -1,
    AvailabilityQuest3Id: -1,
    AvailabilityQuest2Id: -1,
    AvailabilityDialog2Id: -1,
    AvailabilityDialog3Id: -1,
    AvailabilityDialog4Id: -1,
    AvailabilityMinPlayerLevel: 0,
    DecreaseFaction2Points: "0b",
    DialogMail: {
      Sender: "",
      BeenRead: "0b",
      Message: {},
      MailItems: [],
      MailQuest: -1,
      TimePast: "1792500000000L",
      Time: "0L",
      Subject: ""
    }
  };
}

const dialogs = [
  // --- กิลด์นักผจญภัย (48-56) ---
  {
    cat: 'กิลด์นักผจญภัย', id: 48,
    title: 'เซริน — เจ้าหน้าที่กิลด์นักผจญภัย',
    text: 'ยินดีต้อนรับสู่โถงนักผจญภัย @p! ข้าชื่อเซริน เป็นเจ้าหน้าที่กิลด์ หากเพิ่งรับเสบียงจากอัลเลย์มา ข้าจะช่วยแนะนำขั้นตอนต่อไปให้เจ้า',
    options: [
      { slot: 0, title: 'ข้าต้องการสมัครนักผจญภัย', dialog: 49, type: 1 },
      { slot: 1, title: 'มีคำแนะนำสำหรับมือใหม่ไหม?', dialog: 50, type: 1 },
      { slot: 2, title: 'ข้าควรเริ่มงานจากใคร?', dialog: 51, type: 1 },
      { slot: 3, title: 'ใครดูแลงานแต่ละประเภท?', dialog: 52, type: 1 },
      { slot: 4, title: 'ข้าแค่อยากพูดคุย', dialog: 53, type: 1 },
      { slot: 5, title: 'ไว้ข้าจะมาใหม่', dialog: 56, type: 1, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 49,
    title: 'การสมัครนักผจญภัย',
    text: 'ยินดีเลย @p! ข้าดูแลเรื่องต้อนรับและการสมัครของกิลด์ แต่ตอนนี้ยังไม่เปิดลงทะเบียนสมาชิก ระหว่างนี้เจ้ามาถามทางและเตรียมตัวกับข้าได้ หากยังไม่ได้รับเสบียง ให้กลับไปพบเอลริคที่ลานเกิดก่อน แล้วคุยกับอัลเลย์เพื่อรับชุดเริ่มต้น',
    options: [
      { slot: 0, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 50,
    title: 'คำแนะนำสำหรับมือใหม่',
    text: 'ก่อนออกเดินทาง ลองตรวจอาหาร อาวุธ และช่องว่างในกระเป๋าให้พร้อม อ่านจำนวนและชนิดของที่งานต้องการให้ครบ แล้วจำชื่อคนที่เจ้าต้องกลับมาส่งงานไว้ด้วย ถ้าศัตรูเกินกำลัง การถอยกลับมาเตรียมตัวใหม่ก็เป็นเรื่องปกติ นักผจญภัยที่กลับถึงเมืองย่อมมีโอกาสออกเดินทางอีกครั้ง',
    options: [
      { slot: 0, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 51,
    title: 'เริ่มต้นงานแรก',
    text: 'ถ้าเพิ่งเข้าเมือง เริ่มจากเอลริค แล้วไปรับเสบียงกับอัลเลย์ก่อน เมื่อพร้อมแล้วเจ้ามาถึงโถงถูกที่แล้ว! นารินมีงานประจำวันให้ทำ ส่วนงานเนื้อเรื่องของ Kocimira ยังอยู่ระหว่างเตรียมการ ข้ายังไม่มีภารกิจหลักให้รับแทนเขา',
    options: [
      { slot: 0, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 52,
    title: 'ผู้ดูแลงานแต่ละประเภท',
    text: 'นารินดูแลงานประจำวัน ทำครบชุดจะได้เหรียญ Silver หนึ่งชิ้นมูลค่า100 และรับชุดใหม่ได้หลังส่งงานสุดท้ายครบ24ชั่วโมง Kocimiraรับผิดชอบงานหลักของเมือง ส่วนลีโอดูแลเรื่องดันเจี้ยน แต่สองบริการหลังยังไม่เปิด หากต้องการซื้อของให้ไปย่านตลาด หรือไปหาอีเทอร์ที่ย่านช่าง',
    options: [
      { slot: 0, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 53,
    title: 'พูดคุยกับเซริน',
    text: 'ได้สิ! วันหนึ่งข้าได้ยินทั้งเรื่องคนลืมเสบียง คนหลงทาง และคนที่กลับมาเล่าเรื่องการเดินทางครั้งแรก บางคนออกไปด้วยท่าทางมั่นใจ แต่กลับมาถามหาขนมปังก่อนอย่างอื่นเสียอีก แล้วเจ้าล่ะ @p วันนี้อยากเดินชมเมือง หรือเตรียมตัวออกผจญภัย?',
    options: [
      { slot: 0, title: 'ข้าอยากชมเมือง', dialog: 54, type: 1 },
      { slot: 1, title: 'ข้าอยากออกผจญภัย', dialog: 55, type: 1 },
      { slot: 2, title: 'กลับไปถามเรื่องอื่น', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้ข้าจะมาใหม่', dialog: 56, type: 1, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 54,
    title: 'เดินชมเมือง',
    text: 'ลองแวะตลาดก่อนก็ได้ เจ้าจะได้จำทางซื้อของไว้ แล้วค่อยเดินขึ้นไปชมปราสาท ถ้าชอบมุมสงบก็มีหอสมุดเวท แต่บริการบางแห่งยังเตรียมไม่เสร็จ หากจำทางกลับไม่ได้ ให้ดูพิกัดลานเกิดประมาณ X160 Y98 Z310 ไว้เป็นหลัก',
    options: [
      { slot: 0, title: 'กลับไปคุยเรื่องเดิม', dialog: 53, type: 1 },
      { slot: 1, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 55,
    title: 'เตรียมตัวผจญภัย',
    text: 'เช่นนั้นตรวจอาหารกับอาวุธอีกครั้ง แล้วลองถามงานจากนารินดู งานส่งของและงานล่าต้องทำตามลำดับ อ่านเป้าหมายของขั้นที่รับอยู่ก่อนออกไปด้วยนะ ข้าจะรอฟังเรื่องเล่าตอนเจ้ากลับมา',
    options: [
      { slot: 0, title: 'กลับไปคุยเรื่องเดิม', dialog: 53, type: 1 },
      { slot: 1, title: 'กลับเมนูหลัก', dialog: 48, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'กิลด์นักผจญภัย', id: 56,
    title: 'แล้วพบกันใหม่',
    text: 'เดินทางปลอดภัยนะ @p! ถ้าต้องการคำแนะนำ แวะมาหาข้าที่จุดต้อนรับได้เสมอ',
    options: [
      { slot: 5, title: 'ไว้พบกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- เควสหลัก_โคซิไมรา (57-59) ---
  {
    cat: 'เควสหลัก_โคซิไมรา', id: 57,
    title: 'Kocimira — ผู้ให้เควสต์หลัก',
    text: 'เจ้ามาถึงโถงแล้วสินะ @p ข้าคือ Kocimira ดูแลงานหลักของเมือง ระหว่างที่ข้าเตรียมภารกิจ เจ้าถามเรื่องการทำงานที่นี่ก่อนได้',
    options: [
      { slot: 0, title: 'มีงานหลักให้ข้าหรือยัง?', dialog: 58, type: 1 },
      { slot: 1, title: 'ทำไมเจ้าถึงดูแลงานหลัก?', dialog: 59, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'เควสหลัก_โคซิไมรา', id: 58,
    title: 'สถานะภารกิจหลัก',
    text: 'ตอนนี้ภารกิจหลักยังเตรียมไม่เสร็จ ข้ายังไม่มีงานให้เจ้ารับผ่านเมนูนี้ หากต้องการทำงานระหว่างรอ ลองถามนารินเรื่องงานประจำวัน',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 57, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'เควสหลัก_โคซิไมรา', id: 59,
    title: 'หน้าที่ดูแลงานหลัก',
    text: 'งานบางอย่างเกี่ยวข้องกับคนทั้งเมือง ข้าจึงต้องรวบรวมเรื่องราวและตรวจรายละเอียดก่อนส่งนักผจญภัยออกไป ข้าอยากให้ทุกคนรู้ว่าตนกำลังช่วยอะไรอยู่',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 57, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ผู้ดูแลดันเจี้ยน_ลีโอ (60-62) ---
  {
    cat: 'ผู้ดูแลดันเจี้ยน_ลีโอ', id: 60,
    title: 'ลีโอ — ผู้ดูแลดันเจี้ยน',
    text: 'ยินดีที่ได้พบ @p ข้าคือลีโอ ผู้ดูแลการลงดันเจี้ยนของเมือง มีเรื่องไหนอยากถามเกี่ยวกับพื้นที่ท้าทายไหม?',
    options: [
      { slot: 0, title: 'ข้าลงดันเจี้ยนได้หรือยัง?', dialog: 61, type: 1 },
      { slot: 1, title: 'มีคำแนะนำสำหรับการลงดันเจี้ยนไหม?', dialog: 62, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ผู้ดูแลดันเจี้ยน_ลีโอ', id: 61,
    title: 'สถานะดันเจี้ยน',
    text: 'ระบบดันเจี้ยนยังอยู่ระหว่างการเตรียมความพร้อม ข้ายังไม่เปิดให้เข้าพื้นที่และยังไม่เปิดเมนูวาร์ปในตอนนี้',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 60, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ผู้ดูแลดันเจี้ยน_ลีโอ', id: 62,
    title: 'คำแนะนำการลงดันเจี้ยน',
    text: 'เตรียมอุปกรณ์และยาฟื้นฟูให้พร้อม การไปเป็นกลุ่มมักปลอดภัยกว่าคนเดียว เมื่อระบบเปิดใช้งาน ข้าจะแจ้งเงื่อนไขให้ทราบอีกครั้ง',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 60, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ทหารรักษาโถง (63-65) ---
  {
    cat: 'ทหารรักษาโถง', id: 63,
    title: 'ทหารรักษาโถงผจญภัย',
    text: 'สวัสดี @p ข้าดูแลความเรียบร้อยรอบโถงนักผจญภัย เจ้าต้องการถามทางในบริเวณนี้ไหม?',
    options: [
      { slot: 0, title: 'ด้านในนี้มีใครบ้าง?', dialog: 64, type: 1 },
      { slot: 1, title: 'เจ้าเดินตรวจแถวไหนบ้าง?', dialog: 65, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาโถง', id: 64,
    title: 'บุคคลในโถงผจญภัย',
    text: 'มีเซรินที่จุดต้อนรับ นารินดูแลงานประจำวัน Kocimiraเตรียมงานหลัก และลีโอดูแลเรื่องดันเจี้ยน',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 63, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาโถง', id: 65,
    title: 'เส้นทางตรวจโถงผจญภัย',
    text: 'ข้าเดินตรวจตามแนวทางเดินและทางเข้าโถง เพื่อให้มั่นใจว่าผู้มาเยือนทุกคนปลอดภัย',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 63, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ตลาดกลาง_ออสการ์ (5, 66, 67) ---
  {
    cat: 'ตลาดกลาง_ออสการ์', id: 5,
    title: 'ออสการ์ — ผู้ดูแลตลาดกลาง',
    text: 'ยินดีต้อนรับสู่ตลาดกลาง @p! ข้าคือออสการ์ ผู้ดูแลพื้นที่ค้าขายของเมือง เจ้าต้องการเปิดตลาด จัดการแผง หรือสอบถามระเบียบการค้า?',
    options: [
      { slot: 0, title: 'เปิดดูสินค้าในตลาดกลาง', cmd: '/neow market admin open market @dp', dialog: -1, type: 4, color: 15501366 },
      { slot: 1, title: 'จัดการแผงค้าของข้า', cmd: '/neow market admin open manage @dp', dialog: -1, type: 4, color: 16544773 },
      { slot: 2, title: 'ข้าจะเปิดแผงค้าได้อย่างไร?', dialog: 66, type: 1 },
      { slot: 3, title: 'มีระเบียบการซื้อขายอย่างไร?', dialog: 67, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 16714241 }
    ]
  },
  {
    cat: 'ตลาดกลาง_ออสการ์', id: 66,
    title: 'วิธีเปิดแผงค้าในตลาด',
    text: 'เจ้าสามารถเปิดแผงขายของตนเองได้ผ่านระบบตลาด วางสินค้าและกำหนดราคาให้เหมาะสม แล้วตรวจดูสินค้าสม่ำเสมอ',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 5, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ตลาดกลาง_ออสการ์', id: 67,
    title: 'ระเบียบการซื้อขาย',
    text: 'ตรวจสอบราคาก่อนยืนยันทุกครั้ง ตลาดกลางเป็นพื้นที่สำหรับทุกคน ขอให้รักษาระเบียบและซื้อขายอย่างเป็นธรรม',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 5, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- วาร์ป_ตลาด (68-70) ---
  {
    cat: 'วาร์ป_ตลาด', id: 68,
    title: 'วาร์ปเมือง — ตลาด',
    text: 'ถึงย่านตลาดแล้ว @p! ต้องการถามตำแหน่งร้านค้า หรือดูเมนูวาร์ปที่มี?',
    options: [
      { slot: 0, title: 'ร้านค้าแถวนี้มีอะไรบ้าง?', dialog: 69, type: 1 },
      { slot: 1, title: 'วาร์ปนี้ส่งไปไหนได้บ้าง?', dialog: 70, type: 1 },
      { slot: 2, title: 'เปิดเมนูวาร์ป', dialog: -1, type: 3, color: 11140865 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ตลาด', id: 69,
    title: 'ร้านค้าในตลาด',
    text: 'มีร้านอาหารของเดรก ร้านเร่ของฟินน์ ร้านสมุนไพรของเอลินา และแผงเกษตร ประมง ปศุสัตว์ของมาลี ธารา ภูผา',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 68, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ตลาด', id: 70,
    title: 'ปลายทางวาร์ปตลาด',
    text: 'จุดนี้เชื่อมกับเครือข่ายวาร์ปเมือง ตอนนี้ปลายทางหลักคือจุดเกิด ตรวจเมนูวาร์ปก่อนเดินทางเสมอ',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 68, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ทหารรักษาตลาด (71-73) ---
  {
    cat: 'ทหารรักษาตลาด', id: 71,
    title: 'ทหารรักษาตลาด',
    text: 'สวัสดี @p ข้ากำลังตรวจความสงบเรียบร้อยในตลาด มีเรื่องอยากถามไหม?',
    options: [
      { slot: 0, title: 'ตลาดนี้เปิดถึงเมื่อไร?', dialog: 72, type: 1 },
      { slot: 1, title: 'ถ้ามีปัญหาในตลาดต้องบอกใคร?', dialog: 73, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาตลาด', id: 72,
    title: 'เวลาทำการตลาด',
    text: 'ตลาดกลางเปิดตลอดเวลา พ่อค้าแม่ค้าพร้อมต้อนรับผู้มาเยือนเสมอ',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 71, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาตลาด', id: 73,
    title: 'การแจ้งเหตุในตลาด',
    text: 'หากพบความไม่เรียบร้อย บอกทหารยามที่เดินตรวจรอบตลาด หรือแจ้งออสการ์ที่คุมตลาดกลางได้',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 71, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ช่างตีเกราะ_บรันท์ (74-76) ---
  {
    cat: 'ช่างตีเกราะ_บรันท์', id: 74,
    title: 'บรันท์ — ช่างตีเกราะ',
    text: 'ยินดีต้อนรับสู่งานเกราะ @p ข้าคือบรันท์ ช่างตีเกราะประจำย่านนี้ เจ้าอยากถามเรื่องเกราะป้องกันไหม?',
    options: [
      { slot: 0, title: 'ข้าตีเกราะใหม่ได้หรือยัง?', dialog: 75, type: 1 },
      { slot: 1, title: 'เกราะที่ดีควรเลือกอย่างไร?', dialog: 76, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ช่างตีเกราะ_บรันท์', id: 75,
    title: 'สถานะงานตีเกราะ',
    text: 'บริการตีเกราะยังอยู่ระหว่างการปรับปรุงเตาหลอมและเครื่องมือ ข้ายังไม่เปิดรับงานตีเกราะในตอนนี้',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 74, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ช่างตีเกราะ_บรันท์', id: 76,
    title: 'การเลือกเกราะป้องกัน',
    text: 'น้ำหนักและความทนทานต้องสมดุลกับการเคลื่อนไหว เกราะหนักป้องกันดีแต่อาจช้าลง เลือกให้เหมาะกับการต่อสู้ของเจ้า',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 74, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- เจ้าเมือง_ลิออน (77-79) ---
  {
    cat: 'เจ้าเมือง_ลิออน', id: 77,
    title: 'ลิออน — เจ้าเมือง NeoWorld',
    text: 'ยินดีต้อนรับสู่ปราสาท @p ข้าคือลิออน ผู้ดูแลความสงบและการพัฒนาเมือง เจ้ามีเรื่องใดอยากปรึกษาหรือถามไถ่?',
    options: [
      { slot: 0, title: 'เมืองนี้มีแผนพัฒนาอย่างไร?', dialog: 78, type: 1 },
      { slot: 1, title: 'ในฐานะผู้มาใหม่ ข้าช่วยอะไรได้บ้าง?', dialog: 79, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'เจ้าเมือง_ลิออน', id: 78,
    title: 'แผนพัฒนาเมือง',
    text: 'เรากำลังปรับปรุงพื้นที่รอบเมืองและระบบบริการต่าง ๆ เพื่อให้นักผจญภัยมีสิ่งอำนวยความสะดวกครบครัน',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 77, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'เจ้าเมือง_ลิออน', id: 79,
    title: 'การมีส่วนร่วมของนักผจญภัย',
    text: 'เริ่มต้นด้วยการทำภารกิจของเมือง ค้าขายอย่างสุจริต และช่วยเหลือผู้มาใหม่ เพียงเท่านี้ก็ช่วยเมืองได้มากแล้ว',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 77, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- หอสมุดเวท_โอไรออน (80-82) ---
  {
    cat: 'หอสมุดเวท_โอไรออน', id: 80,
    title: 'โอไรออน — ผู้ดูแลหอสมุดเวท',
    text: 'ขอความสงบในหอสมุดด้วย @p ข้าคือโอไรออน ผู้ดูแลคลังความรู้และเวทมนตร์ เจ้ากำลังค้นหาเรื่องใดอยู่?',
    options: [
      { slot: 0, title: 'ข้าเรียนเวทมนตร์ที่นี่ได้ไหม?', dialog: 81, type: 1 },
      { slot: 1, title: 'มีหนังสืออะไรน่าสนใจบ้าง?', dialog: 82, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'หอสมุดเวท_โอไรออน', id: 81,
    title: 'การเรียนเวทมนตร์',
    text: 'ตอนนี้ยังไม่มีระบบเรียนเวทให้ใช้งานผ่านข้า ข้าจึงยังไม่รับเงินหรือไอเทมเพื่อสอนวิชา',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 80, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'หอสมุดเวท_โอไรออน', id: 82,
    title: 'หนังสือที่ชื่นชอบ',
    text: 'เรื่องบันทึกการเดินทางน่าสนใจเสมอ คนสองคนผ่านทางเดียวกันอาจสังเกตเห็นคนละอย่าง ข้าจึงชอบฟังหลายมุมก่อนสรุป',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 80, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- วาร์ป_ข้ามมิติ (4, 83, 84) ---
  {
    cat: 'วาร์ป_ข้ามมิติ', id: 4,
    title: 'ฮันนาห์ — ผู้นำทางมิติ',
    text: 'พร้อมเดินทางหรือ @p? ข้าคือฮันนาห์ ช่วยพาไปโลกสร้างบ้านและโลกทรัพยากร ก่อนเลือกปลายทางเตรียมของให้พร้อมนะ',
    options: [
      { slot: 0, title: 'โลกสร้างบ้าน', cmd: '/neow world admin tp @dp neoworld:build_world', dialog: -1, type: 4, color: 4914186 },
      { slot: 1, title: 'โลกหาทรัพยากร (สุ่มพื้นที่)', cmd: '/neow world admin tp @dp neoworld:resource_world random', dialog: -1, type: 4, color: 9106198 },
      { slot: 2, title: 'แต่ละโลกต่างกันอย่างไร?', dialog: 83, type: 1 },
      { slot: 3, title: 'มีอะไรต้องตรวจก่อนไป?', dialog: 84, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 16737792 }
    ],
    extra: { DialogShowWheel: "1b" }
  },
  {
    cat: 'วาร์ป_ข้ามมิติ', id: 83,
    title: 'ความแตกต่างของแต่ละโลก',
    text: 'เมนูของข้ามีปลายทางโลกสร้างบ้านและการสุ่มพื้นที่โลกทรัพยากร เลือกให้ตรงจุดประสงค์ของเจ้า และตรวจข้อมูลระบบโลกที่เปิดใช้ล่าสุดด้วย',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 4, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ข้ามมิติ', id: 84,
    title: 'การเตรียมตัวก่อนเดินทางข้ามมิติ',
    text: 'อาหาร อุปกรณ์ และพื้นที่กระเป๋า หากไปกับเพื่อนควรตกลงเรื่องปลายทางก่อน การสุ่มพื้นที่อาจไม่ใช่จุดนัดพบที่เจ้าคาดไว้',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 4, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- วาร์ป_ปราสาท (85-87) ---
  {
    cat: 'วาร์ป_ปราสาท', id: 85,
    title: 'วาร์ปเมือง — ปราสาท',
    text: 'เจ้ามาถึงย่านปราสาทแล้ว @p อยากถามทางหรือดูเมนูวาร์ปที่มี?',
    options: [
      { slot: 0, title: 'เจ้าเมืองอยู่แถวไหน?', dialog: 86, type: 1 },
      { slot: 1, title: 'วาร์ปนี้ส่งไปที่ใด?', dialog: 87, type: 1 },
      { slot: 2, title: 'เปิดเมนูวาร์ป', dialog: -1, type: 3, color: 11140865 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ปราสาท', id: 86,
    title: 'ตำแหน่งเจ้าเมืองและหอสมุด',
    text: 'ลิออนอยู่บริเวณ X160 Y117 Z59 ส่วนโอไรออนอยู่แถวหอสมุด X130 Y117 Z62',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 85, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ปราสาท', id: 87,
    title: 'ปลายทางวาร์ปปราสาท',
    text: 'ตามข้อมูลที่มี ปลายทางวาร์ปเมืองยังเป็นจุดเกิด ตรวจเมนูปัจจุบันก่อนใช้ ข้ายังไม่ยืนยันว่าตั้งปลายทางโซนอื่นครบแล้ว',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 85, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- ทหารรักษาปราสาท (88-90) ---
  {
    cat: 'ทหารรักษาปราสาท', id: 88,
    title: 'ทหารรักษาปราสาท',
    text: 'ยินดีต้อนรับสู่บริเวณปราสาท @p ข้ากำลังตรวจเส้นทางรอบลาน หากไม่คุ้นทางก็ถามได้',
    options: [
      { slot: 0, title: 'ข้าอยากพบเจ้าเมือง', dialog: 89, type: 1 },
      { slot: 1, title: 'แถวนี้มีที่ไหนน่าแวะอีก?', dialog: 90, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาปราสาท', id: 89,
    title: 'ทางไปพบเจ้าเมือง',
    text: 'เดินขึ้นไปบริเวณปราสาท ลิออนประจำอยู่ประมาณ X160 Y117 Z59',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 88, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'ทหารรักษาปราสาท', id: 90,
    title: 'สถานที่น่าแวะย่านปราสาท',
    text: 'หอสมุดเวทอยู่ไม่ไกล ลองไปคุยกับโอไรออนได้ แต่บริการหอสมุดยังเตรียมไม่ครบ',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 88, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- วาร์ป_ท่าเรือ (91-93) ---
  {
    cat: 'วาร์ป_ท่าเรือ', id: 91,
    title: 'วาร์ปเมือง — ท่าเรือ',
    text: 'สวัสดี @p ข้าเตรียมไว้สำหรับจุดท่าเรือ แต่ตอนนี้พื้นที่ปลายทางยังไม่พร้อม จึงยังพักอยู่ที่ลานเกิด',
    options: [
      { slot: 0, title: 'ข้าไปท่าเรือได้หรือยัง?', dialog: 92, type: 1 },
      { slot: 1, title: 'หาของเกี่ยวกับประมงที่ไหน?', dialog: 93, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ท่าเรือ', id: 92,
    title: 'สถานะเส้นทางท่าเรือ',
    text: 'ตอนนี้ยังไม่มีเส้นทางท่าเรือที่ยืนยันพร้อมใช้งานจากข้า เมื่อพื้นที่และปลายทางจัดเสร็จจึงจะเปิดให้เดินทาง',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 91, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_ท่าเรือ', id: 93,
    title: 'สินค้าประมง',
    text: 'แผนร้านประมงอยู่กับธาราที่ตลาด เมื่อแผงติดตั้งเสร็จให้ไปดูสินค้าที่นั่นก่อน ไม่ต้องรอท่าเรือ',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 91, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },

  // --- วาร์ป_เหมือง (94-96) ---
  {
    cat: 'วาร์ป_เหมือง', id: 94,
    title: 'วาร์ปเมือง — เหมืองแร่',
    text: 'สวัสดี @p ข้าเป็นจุดวาร์ปที่เตรียมไว้สำหรับเหมือง ตอนนี้ยังตั้งอยู่ลานเกิดเพื่อรอพื้นที่ปลายทาง',
    options: [
      { slot: 0, title: 'เข้าเหมืองจากตรงนี้ได้ไหม?', dialog: 95, type: 1 },
      { slot: 1, title: 'ข้าควรเตรียมอะไรก่อนขุดแร่?', dialog: 96, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_เหมือง', id: 95,
    title: 'สถานะเส้นทางเหมือง',
    text: 'ยังไม่ได้ เส้นทางเหมืองยังไม่พร้อม ข้ายังไม่มีปลายทางใหม่ให้เดินทางผ่านบทสนทนานี้',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 94, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  },
  {
    cat: 'วาร์ป_เหมือง', id: 96,
    title: 'การเตรียมตัวก่อนขุดแร่',
    text: 'เครื่องมือ อาหาร และช่องกระเป๋าให้พอ ตรวจเป้าหมายเควสต์ด้วยว่าต้องใช้แร่ดิบหรือของชนิดใด ก่อนออกเดินทางจริง',
    options: [
      { slot: 0, title: 'กลับไปถามเรื่องอื่น', dialog: 94, type: 1 },
      { slot: 5, title: 'ไว้คุยกันใหม่', dialog: -1, type: 0, color: 11140865 }
    ]
  }
];

let created = 0;
for (const d of dialogs) {
  const dir = path.join(tempDir, d.cat);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const filePath = path.join(dir, `${d.id}.json`);
  const data = createDialog(d.id, d.title, d.text, d.options, d.extra || {});
  
  let jsonStr = JSON.stringify(data, null, 4);
  jsonStr = jsonStr.replace(/"(\d+b)"/g, '$1');
  jsonStr = jsonStr.replace(/"(\d+L)"/g, '$1');
  
  fs.writeFileSync(filePath, jsonStr, 'utf8');
  created++;
}
console.log(`Generated ${created} new dialog files in staging.`);

// Server migration mapping
const serverFileMap = {
  // Elric
  'npc_elric/9.json': 'ครูฝึก_เอลริค',
  'npc_elric/10.json': 'ครูฝึก_เอลริค',
  'npc_elric/11.json': 'ครูฝึก_เอลริค',
  'npc_elric/12.json': 'ครูฝึก_เอลริค',
  'npc_elric/13.json': 'ครูฝึก_เอลริค',
  'npc_elric/14.json': 'ครูฝึก_เอลริค',
  'npc_spawn/22.json': 'ครูฝึก_เอลริค',
  // Alley
  'npc_spawn/23.json': 'เสบียงมือใหม่_อัลเลย์',
  'npc_spawn/24.json': 'เสบียงมือใหม่_อัลเลย์',
  'npc_spawn/30.json': 'เสบียงมือใหม่_อัลเลย์',
  // Spawn Warp
  'npc_spawn/25.json': 'วาร์ป_ลานเกิด',
  'npc_spawn/26.json': 'วาร์ป_ลานเกิด',
  'npc_spawn/27.json': 'วาร์ป_ลานเกิด',
  // Spawn Guards
  'npc_spawn/28.json': 'ทหารรักษาลานเกิด',
  'npc_spawn/29.json': 'ทหารรักษาลานเกิด',
  // Narin
  'npc_narin/15.json': 'เควสประจำวัน_นาริน',
  'npc_narin/16.json': 'เควสประจำวัน_นาริน',
  'npc_narin/17.json': 'เควสประจำวัน_นาริน',
  'npc_narin/18.json': 'เควสประจำวัน_นาริน',
  'npc_narin/19.json': 'เควสประจำวัน_นาริน',
  'npc_narin/20.json': 'เควสประจำวัน_นาริน',
  'npc_narin/21.json': 'เควสประจำวัน_นาริน',
  // Drake
  'shop_Drake/6.json': 'ร้านอาหาร_เดรก',
  'shop_Drake/45.json': 'ร้านอาหาร_เดรก',
  // Finn
  'ฟินน์/8.json': 'ร้านสินค้าเร่_ฟินน์',
  'ฟินน์/47.json': 'ร้านสินค้าเร่_ฟินน์',
  // Aether
  'npc_อีเทอร์/7.json': 'ร้านตีเหล็ก_อีเทอร์',
  'npc_อีเทอร์/46.json': 'ร้านตีเหล็ก_อีเทอร์',
  // Mali
  'shop_market/31.json': 'ร้านเกษตรและผึ้ง_มาลี',
  'shop_market/32.json': 'ร้านเกษตรและผึ้ง_มาลี',
  // Thara
  'shop_market/33.json': 'ร้านประมง_ธารา',
  'shop_market/34.json': 'ร้านประมง_ธารา',
  // Phupha
  'shop_market/35.json': 'ร้านปศุสัตว์_ภูผา',
  'shop_market/36.json': 'ร้านปศุสัตว์_ภูผา',
  // Elina
  'shop_market/41.json': 'ร้านสมุนไพร_เอลินา',
  'shop_market/42.json': 'ร้านสมุนไพร_เอลินา',
  // Rogan
  'npc_blacksmith/37.json': 'ร้านไม้_โรแกน',
  'npc_blacksmith/38.json': 'ร้านไม้_โรแกน',
  // Doran
  'npc_blacksmith/43.json': 'ร้านวัสดุช่าง_โดรัน',
  'npc_blacksmith/44.json': 'ร้านวัสดุช่าง_โดรัน',
  // Raven
  'shop_guild/39.json': 'รับซื้อของมอนสเตอร์_เรเวน',
  'shop_guild/40.json': 'รับซื้อของมอนสเตอร์_เรเวน'
};

let migrated = 0;
for (const [relPath, targetCat] of Object.entries(serverFileMap)) {
  const src = path.join(backupDialogsDir, relPath);
  if (!fs.existsSync(src)) {
    console.error(`Missing backup server file: ${src}`);
    process.exit(1);
  }
  const destDir = path.join(tempDir, targetCat);
  if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true });
  const filename = path.basename(relPath);
  const dest = path.join(destDir, filename);
  let content = fs.readFileSync(src, 'utf8');
  content = content.replace(/"TimePast":\s*(\d+)(?!L),/g, '"TimePast": $1L,');
  fs.writeFileSync(dest, content, 'utf8');
  migrated++;
}
console.log(`Migrated ${migrated} server dialog files.`);
console.log(`Total staged files in temp: ${created + migrated}`);

console.log('=== STEP 3: Verify All Staged Files ===');
const allDialogs = new Map();
let errors = [];

function cleanParse(raw) {
  const sanitized = raw
    .replace(/:\s*(-?[0-9]+)[bBsSlL]\b/g, ': $1')
    .replace(/:\s*(-?[0-9]*\.[0-9]+)[fFdD]\b/g, ': $1');
  return JSON.parse(sanitized);
}

function walk(dir) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      walk(p);
    } else if (ent.name.endsWith('.json')) {
      const raw = fs.readFileSync(p, 'utf8');
      try {
        const obj = cleanParse(raw);
        allDialogs.set(obj.DialogId, { path: p, obj });
      } catch (e) {
        errors.push(`Parse error in ${p}: ${e.message}`);
      }
    }
  }
}
walk(tempDir);

if (allDialogs.size !== 93) {
  errors.push(`Expected 93 dialog files, found ${allDialogs.size}`);
}

for (const [id, { path: filePath, obj }] of allDialogs.entries()) {
  const options = obj.Options || [];
  if (options.length > 6) {
    errors.push(`${filePath}: Exceeds 6 options`);
  }
  for (const optWrap of options) {
    const opt = optWrap.Option;
    if (!opt) continue;
    if (opt.OptionType === 1) {
      if (opt.Dialog === -1) {
        errors.push(`${filePath}: OptionType 1 with Dialog: -1 ("${opt.Title}")`);
      } else if (!allDialogs.has(opt.Dialog)) {
        errors.push(`${filePath}: Target Dialog ${opt.Dialog} not found ("${opt.Title}")`);
      }
    } else if (opt.OptionType === 4) {
      if (!opt.DialogCommand || opt.DialogCommand.trim() === '') {
        errors.push(`${filePath}: OptionType 4 with empty command`);
      }
    }
  }
}

console.log(`Verification completed: ${allDialogs.size} dialogs, ${errors.length} errors.`);
if (errors.length > 0) {
  errors.forEach(e => console.error('  ERROR: ' + e));
  process.exit(1);
}

console.log('=== STEP 4: Flush Old Dialogs and Deploy Unified Structure ===');
// Remove existing folders from liveDialogsDir
const liveEntries = fs.readdirSync(liveDialogsDir);
for (const entry of liveEntries) {
  const full = path.join(liveDialogsDir, entry);
  fs.rmSync(full, { recursive: true, force: true });
}
console.log('Cleared old dialog folders from live directory.');

// Copy 30 new Thai categories to liveDialogsDir
const stagedCats = fs.readdirSync(tempDir);
for (const cat of stagedCats) {
  const srcCatDir = path.join(tempDir, cat);
  const destCatDir = path.join(liveDialogsDir, cat);
  fs.cpSync(srcCatDir, destCatDir, { recursive: true });
}
console.log(`Installed ${stagedCats.length} Thai categories into live directory.`);

// Clean temp directory
fs.rmSync(tempDir, { recursive: true, force: true });

// Ensure proper permissions
execSync(`docker exec -u 0 neoworld-server chown -R 1000:1000 /data/world/customnpcs/dialogs`);
console.log('Permissions set to 1000:1000.');

console.log('=== STEP 5: Reload CustomNPCs Dialogs ===');
execSync(`python3 /home/miw/send_console.py "noppes dialog reload"`);
console.log('Sent "noppes dialog reload" to console.');

console.log('=== STEP 6: Bind Dialogs to 12 Unbound NPCs ===');
const npcBindings = [
  { name: 'Kocimira', uuid: 'd781a6af-921e-48b4-be21-346ee190f0ea', dialog: 57 },
  { name: 'ลีโอ', uuid: '506815a0-f23b-47f0-bf33-b8febea6fe9f', dialog: 60 },
  { name: 'ทหารรักษาโถง', uuid: '231dd439-dfe2-4f94-af8c-6cc740b2ec94', dialog: 63 },
  { name: 'วาร์ปตลาด', uuid: 'a4a4ef9b-19a7-4fef-9ea3-fc290ce945ab', dialog: 68 },
  { name: 'ทหารรักษาตลาด', uuid: '32ad453a-6ffd-489d-bc52-f043a5929e76', dialog: 71 },
  { name: 'บรันท์', uuid: 'f29bbbcd-ee84-4b0f-b6a7-7f640003272d', dialog: 74 },
  { name: 'ลิออน', uuid: '7225e0dc-f9a2-40c1-88c7-6ce54da85700', dialog: 77 },
  { name: 'โอไรออน', uuid: '503a5733-3742-4427-835b-6157661e4600', dialog: 80 },
  { name: 'วาร์ปปราสาท', uuid: 'ed0aeb17-6481-4e0e-a63b-67f451f208d9', dialog: 85 },
  { name: 'ทหารรักษาปราสาท', uuid: '7f0866af-67a4-49aa-8f05-879eb094dd4a', dialog: 88 },
  { name: 'วาร์ปท่าเรือ', uuid: '60241c70-e3f1-4d84-90f1-c5602f6549d6', dialog: 91 },
  { name: 'วาร์ปเหมือง', uuid: '099391ba-7704-4b41-b1ce-114f309d3e6d', dialog: 94 }
];

for (const b of npcBindings) {
  const cmd = `data merge entity ${b.uuid} {NPCDialogOptions:[{DialogSlot:0,NPCDialog:${b.dialog}}]}`;
  execSync(`python3 /home/miw/send_console.py "${cmd}"`);
  console.log(`Bound ${b.name} (${b.uuid}) -> Dialog ${b.dialog}`);
}

console.log('=== STEP 7: Save Game Flush ===');
execSync(`python3 /home/miw/send_console.py "save-all flush"`);
console.log('Sent "save-all flush" to console.');

console.log('=== DEPLOYMENT COMPLETED SUCCESSFULLY ===');
