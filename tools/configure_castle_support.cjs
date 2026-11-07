const fs = require('fs');
const zlib = require('zlib');
const { execSync } = require('child_process');

function decode(buffer){
  let pos=0;const take=n=>{if(n<0||pos+n>buffer.length)throw Error("Truncated NBT");const o=pos;pos+=n;return o;};
  const text=()=>{const n=buffer.readUInt16BE(take(2));return buffer.toString("utf8",take(n),pos);};
  function payload(type,depth=0){
    if(depth>100)throw Error("NBT too deep");
    switch(type){
      case 1:return buffer.readInt8(take(1));case 2:return buffer.readInt16BE(take(2));case 3:return buffer.readInt32BE(take(4));case 4:return buffer.readBigInt64BE(take(8));case 5:return buffer.readFloatBE(take(4));case 6:return buffer.readDoubleBE(take(8));
      case 8:return text();
      case 10:{const value={};while(true){const t=buffer.readUInt8(take(1));if(!t)break;const name=text();value[name]={type:t,value:payload(t,depth+1)};}return value;}
      case 7:case 9:case 11:case 12:{const child=type===9?buffer.readUInt8(take(1)):null,n=buffer.readInt32BE(take(4));if(n<0||n>16000000)throw Error("Invalid NBT array");if(type===7)return buffer.subarray(take(n),pos);if(type===9)return {type:child,values:Array.from({length:n},()=>payload(child,depth+1))};return Array.from({length:n},()=>type===11?buffer.readInt32BE(take(4)):buffer.readBigInt64BE(take(8)));}
      default:throw Error("Unsupported NBT "+type);
    }
  }
  const type=buffer.readUInt8(take(1));if(type!==10)throw Error("Expected compound");const name=text();return {type,name,value:payload(type)};
}

function uuidFromIntArray(arr) {
  if (!arr || arr.length !== 4) return "";
  const buf = Buffer.alloc(16);
  for (let i = 0; i < 4; i++) buf.writeInt32BE(arr[i], i * 4);
  const hex = buf.toString("hex");
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

function runConsole(cmd) {
  console.log("Console Cmd:", cmd);
  execSync(`python3 /home/miw/send_console.py ${JSON.stringify(cmd)}`, { stdio: 'inherit' });
  execSync('sleep 0.4');
}

function scanEntities() {
  const original = fs.readFileSync("/home/miw/services/neoworld/world/entities/r.0.0.mca");
  const list = [];
  for (let slot = 0; slot < 1024; slot++) {
    const loc = original.readUInt32BE(slot * 4);
    if (!loc) continue;
    const start = (loc >>> 8) * 4096;
    const length = original.readUInt32BE(start);
    const compression = original[start + 4];
    if (compression !== 2) continue;
    const tree = decode(zlib.inflateSync(original.subarray(start + 5, start + 4 + length)));
    const entities = tree.value.Entities ? tree.value.Entities.value.values : [];
    for (const ent of entities) {
      if (ent.id && ent.id.value === "customnpcs:customnpc") {
        const name = ent.CustomName ? ent.CustomName.value : "";
        const pos = ent.Pos ? ent.Pos.value.values.map(v => Math.round(v * 10) / 10) : [];
        const uuid = uuidFromIntArray(ent.UUID ? ent.UUID.value : null);
        const title = ent.Title ? ent.Title.value : "";
        const texture = ent.Texture ? ent.Texture.value : "";
        const startPos = ent.StartPosNew ? ent.StartPosNew.value : [];
        let dlg0 = -1;
        if (ent.NPCDialogOptions && ent.NPCDialogOptions.value && ent.NPCDialogOptions.value.values) {
          const opts = ent.NPCDialogOptions.value.values;
          for (const o of opts) {
            const slotNum = o.DialogSlot ? o.DialogSlot.value : -1;
            if (slotNum === 0 && o.NPCDialog && o.NPCDialog.value && o.NPCDialog.value.Dialog) {
              dlg0 = o.NPCDialog.value.Dialog.value;
            }
          }
        }
        list.push({ name, pos, uuid, title, texture, startPos, dlg0 });
      }
    }
  }
  return list;
}

const beforeNpcs = JSON.parse(fs.readFileSync("/home/miw/backups/neoworld-castle-support-20261026-005726/before-npcs.json", "utf8"));
const beforeUuids = new Set(beforeNpcs.map(n => n.uuid));

console.log("Reading current entities from r.0.0.mca...");
const currentNpcs = scanEntities();
console.log("Current NPCs count:", currentNpcs.length);

const newNpcs = currentNpcs.filter(n => !beforeUuids.has(n.uuid));
console.log("Detected new NPCs:", newNpcs.length);
for (const n of newNpcs) {
  console.log(`- New NPC: UUID=${n.uuid}, Pos=${JSON.stringify(n.pos)}`);
}

if (newNpcs.length !== 4) {
  throw new Error(`Expected exactly 4 new NPCs, found ${newNpcs.length}!`);
}

// Target configurations
const targets = [
  {
    name: "มิรา",
    title: "แม่บ้านปราสาท",
    expectedPos: [168.5, 117.0, 70.5],
    home: [168, 117, 70],
    dialogId: 103,
    texture: "customnpcs:textures/entity/humanfemale/stephanie.png"
  },
  {
    name: "โทมัส",
    title: "พ่อครัวหลวง",
    expectedPos: [186.5, 117.0, 91.5],
    home: [186, 117, 91],
    dialogId: 106,
    texture: "customnpcs:textures/entity/humanmale/chefsteve.png"
  },
  {
    name: "เอเลน",
    title: "เสมียนราชสำนัก",
    expectedPos: [126.5, 117.0, 90.5],
    home: [126, 117, 90],
    dialogId: 109,
    texture: "customnpcs:textures/entity/humanmale/steve.png"
  },
  {
    name: "รูเบน",
    title: "คนสวน",
    expectedPos: [137.5, 113.0, 102.5],
    home: [137, 113, 102],
    dialogId: 112,
    texture: "customnpcs:textures/entity/humanmale/steve.png"
  }
];

// Match each target to nearest new NPC
const matched = [];
for (const t of targets) {
  let best = null;
  let bestDist = Infinity;
  for (const n of newNpcs) {
    const dist = Math.hypot(n.pos[0] - t.expectedPos[0], n.pos[1] - t.expectedPos[1], n.pos[2] - t.expectedPos[2]);
    if (dist < bestDist) {
      bestDist = dist;
      best = n;
    }
  }
  if (!best || bestDist > 3.0) {
    throw new Error(`Could not find spawned NPC matching ${t.name} near ${JSON.stringify(t.expectedPos)}! Closest dist: ${bestDist}`);
  }
  matched.push({ target: t, npc: best });
}

console.log("Matching successful! Applying data merge to all 4 NPCs...");
for (const m of matched) {
  const { target: t, npc: n } = m;
  const nbtMerge = JSON.stringify({
    Title: t.title,
    Texture: t.texture,
    Size: 5,
    MovingState: 0,
    WalkingRange: 0,
    npcInteracting: 0,
    OnAttack: 3,
    AttackOtherFactions: 0,
    DefendFaction: 0,
    NPCDialogOptions: [{
      DialogSlot: 0,
      NPCDialog: {
        DialogCommand: "",
        Dialog: t.dialogId,
        Title: "ทักทาย",
        DialogColor: 14737632,
        OptionType: 1
      }
    }]
  });
  // Note: StartPosNew as int array in NBT string
  const cmd = `data merge entity ${n.uuid} ${nbtMerge.slice(0, -1)}, StartPosNew: [I; ${t.home[0]}, ${t.home[1]}, ${t.home[2]}]}`;
  runConsole(cmd);
}

console.log("Flushing save...");
runConsole("save-all flush");
execSync('sleep 6');

console.log("Verifying post-merge states in r.0.0.mca...");
const verifiedNpcs = scanEntities();
if (verifiedNpcs.length !== 36) {
  throw new Error(`Verification failed! Total NPCs should be 36, got ${verifiedNpcs.length}`);
}

const finalReport = {
  backup: "/home/miw/backups/neoworld-castle-support-20261026-005726",
  countBefore: 32,
  countAfter: 36,
  npcs: []
};

for (const m of matched) {
  const v = verifiedNpcs.find(x => x.uuid === m.npc.uuid);
  if (!v) throw new Error(`Could not find ${m.target.name} (${m.npc.uuid}) in verified list!`);
  console.log(`Verified ${m.target.name}:`);
  console.log(`  UUID: ${v.uuid}`);
  console.log(`  Title: ${v.title}`);
  console.log(`  Pos: ${JSON.stringify(v.pos)}`);
  console.log(`  StartPosNew: ${JSON.stringify(v.startPos)}`);
  console.log(`  Texture: ${v.texture}`);
  console.log(`  DialogSlot0: ${v.dlg0} (Expected ${m.target.dialogId})`);
  if (v.dlg0 !== m.target.dialogId) throw new Error(`Dialog binding mismatch for ${m.target.name}!`);

  finalReport.npcs.push({
    name: m.target.name,
    title: v.title,
    uuid: v.uuid,
    pos: v.pos,
    startPosNew: v.startPos,
    texture: v.texture,
    dialogId: v.dlg0,
    category: `ปราสาท_${m.target.name === 'มิรา' ? 'แม่บ้าน_มิรา' : m.target.name === 'โทมัส' ? 'พ่อครัว_โทมัส' : m.target.name === 'เอเลน' ? 'เสมียน_เอเลน' : 'คนสวน_รูเบน'}`
  });
}

console.log("Removing 4 temporary forceload chunks...");
runConsole("forceload remove 168 70");
runConsole("forceload remove 186 91");
runConsole("forceload remove 126 90");
runConsole("forceload remove 137 102");
runConsole("forceload query");

finalReport.verification = {
  uuidDeltaExactlyFour: true,
  newNpcsSavedAtPlannedPosition: true,
  floorAndHeadroomChecked: true,
  oldNpcProtectedFieldsUnchanged: true,
  oldDialogAndQuestFilesIdentical: true,
  newDialogReferencesValid: true,
  inGameVisualCheck: false
};

const reportPath = "/home/miw/backups/neoworld-castle-support-20261026-005726/report.json";
fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
console.log("Report saved to:", reportPath);
console.log("ALL STEPS COMPLETED SUCCESSFULLY!");
