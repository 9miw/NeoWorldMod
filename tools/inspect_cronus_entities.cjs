const fs = require('fs');
const zlib = require('zlib');

const buffer = fs.readFileSync('/home/miw/services/neoworld/world/entities/r.0.0.mca');

function parseTag(buf, offset) {
  const type = buf[offset++];
  if (type === 0) return { tag: null, nextOffset: offset };
  const nameLen = buf.readUInt16BE(offset);
  offset += 2;
  const name = buf.toString('utf8', offset, offset + nameLen);
  offset += nameLen;
  const val = parsePayload(type, buf, offset);
  return { name, value: val.value, nextOffset: val.nextOffset };
}

function parsePayload(type, buf, offset) {
  if (type === 1) return { value: buf.readInt8(offset), nextOffset: offset + 1 };
  if (type === 2) return { value: buf.readInt16BE(offset), nextOffset: offset + 2 };
  if (type === 3) return { value: buf.readInt32BE(offset), nextOffset: offset + 4 };
  if (type === 4) return { value: buf.readBigInt64BE(offset), nextOffset: offset + 8 };
  if (type === 5) return { value: buf.readFloatBE(offset), nextOffset: offset + 4 };
  if (type === 6) return { value: buf.readDoubleBE(offset), nextOffset: offset + 8 };
  if (type === 7) {
    const len = buf.readInt32BE(offset);
    return { value: buf.slice(offset + 4, offset + 4 + len), nextOffset: offset + 4 + len };
  }
  if (type === 8) {
    const len = buf.readUInt16BE(offset);
    return { value: buf.toString('utf8', offset + 2, offset + 2 + len), nextOffset: offset + 2 + len };
  }
  if (type === 9) {
    const itemType = buf[offset++];
    const len = buf.readInt32BE(offset);
    offset += 4;
    const list = [];
    for (let i = 0; i < len; i++) {
      const p = parsePayload(itemType, buf, offset);
      list.push(p.value);
      offset = p.nextOffset;
    }
    return { value: list, nextOffset: offset };
  }
  if (type === 10) {
    const map = {};
    while (true) {
      const t = parseTag(buf, offset);
      if (t.tag === null && buf[offset] === 0) {
        offset++;
        break;
      }
      map[t.name] = t.value;
      offset = t.nextOffset;
    }
    return { value: map, nextOffset: offset };
  }
  if (type === 11) {
    const len = buf.readInt32BE(offset);
    const arr = [];
    offset += 4;
    for (let i = 0; i < len; i++) {
      arr.push(buf.readInt32BE(offset));
      offset += 4;
    }
    return { value: arr, nextOffset: offset };
  }
  if (type === 12) {
    const len = buf.readInt32BE(offset);
    const arr = [];
    offset += 4;
    for (let i = 0; i < len; i++) {
      arr.push(buf.readBigInt64BE(offset));
      offset += 8;
    }
    return { value: arr, nextOffset: offset };
  }
  throw new Error(`Unknown type ${type} at ${offset}`);
}

function uuidIntArrayToString(arr) {
  if (!arr || arr.length !== 4) return 'unknown';
  const hex = arr.map(n => (n >>> 0).toString(16).padStart(8, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

const npcs = [];
for (let chunkZ = 0; chunkZ < 32; chunkZ++) {
  for (let chunkX = 0; chunkX < 32; chunkX++) {
    const idx = 4 * (chunkX + chunkZ * 32);
    const loc = buffer.readUInt32BE(idx);
    if (loc === 0) continue;
    const sectorOffset = (loc >> 8) * 4096;
    const length = buffer.readUInt32BE(sectorOffset);
    if (length <= 1) continue;
    const compType = buffer[sectorOffset + 4];
    const compData = buffer.slice(sectorOffset + 5, sectorOffset + 4 + length);
    let decomp = (compType === 2) ? zlib.inflateSync(compData) : zlib.gunzipSync(compData);
    const root = parseTag(decomp, 0);
    const entities = root.value.Entities || [];
    for (const ent of entities) {
      if (ent.id === 'customnpcs:customnpc') {
        const uuid = uuidIntArrayToString(ent.UUID);
        const name = ent.Name || ent.CustomName || 'unnamed';
        const pos = (ent.Pos || []).map(p => Math.round(p));
        const dialogs = ent.NPCDialogOptions || [];
        npcs.push({ uuid, name, pos, dialogs });
      }
    }
  }
}

console.log(`Found ${npcs.length} CustomNPCs in r.0.0.mca:`);
npcs.forEach((n, idx) => {
  const dStr = n.dialogs.map(d => `slot${d.DialogSlot}:dlg${typeof d.NPCDialog === 'object' ? JSON.stringify(d.NPCDialog) : d.NPCDialog}`).join(',') || 'NONE';
  console.log(`${(idx+1).toString().padStart(2, '0')}. [${n.uuid}] "${n.name}" @ [${n.pos.join(', ')}] -> ${dStr}`);
});
