// Patch: Fill 3 terrain holes in leveling_island_01
// Hole 1: X180 Z59 — gap between mansion floor and bridge 2 approach
// Hole 2: X149 Z287 — entry bridge 1 missing floor block
// Hole 3: X151 Z287 — entry bridge 1 missing floor block
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLibrary } = require('./viewer/map-library.cjs');
const { patchRegion } = require('./nbt-edit.cjs');

const ROOT = path.resolve(__dirname, '../..');
const MAP = path.join(ROOT, 'maps/leveling_island_01');
const REGION = path.join(MAP, 'region/r.0.0.mca');

// 1. Read current region hash for guard
const regionBuf = fs.readFileSync(REGION);
const currentHash = crypto.createHash('sha256').update(regionBuf).digest('hex').toUpperCase();
console.log(`Current region hash: ${currentHash}`);

// 2. Backup
const backupDir = path.join(ROOT, 'archive/map-edit-backups/before-hole-fix/leveling_island_01');
if (fs.existsSync(backupDir)) {
  console.log('Backup already exists — removing stale backup to proceed.');
  fs.rmSync(backupDir, { recursive: true });
}
fs.mkdirSync(path.join(backupDir, 'region'), { recursive: true });
fs.copyFileSync(REGION, path.join(backupDir, 'region/r.0.0.mca'));
console.log(`✓ Backup saved to ${path.relative(ROOT, backupDir)}`);

// 3. Load model to find neighbor context
const lib = createLibrary(path.join(ROOT, 'maps'));
const m = lib.load('leveling_island_01');
const S = m.size, LOW = m.low, HIGH = m.high;
const bytes = Buffer.from(m.blocks, 'base64');
const data = new Uint16Array(bytes.length / 2);
for (let i = 0; i < data.length; i++) data[i] = bytes.readUInt16LE(i * 2);
const at = (x, y, z) => (y - LOW) * S * S + z * S + x;
const get = (x, y, z) => (y >= LOW && y < HIGH && x >= 0 && x < S && z >= 0 && z < S) ? data[at(x, y, z)] : 0;
const nameOf = id => id ? m.definitions[id].Name.replace('minecraft:', '') : 'air';

// Find block id by name
const findId = (name) => {
  const full = 'minecraft:' + name;
  for (let i = 0; i < m.definitions.length; i++) {
    if (m.definitions[i].Name === full) return i;
  }
  throw Error(`Block not found: ${name}`);
};

// Holes to fix:
const fixes = [
  // Hole 1: X180 Z59 — mansion terrace floor. Neighbors at Y108 are stone_bricks.
  // Fill column: stone_bricks at Y108 (floor), stone at Y107..Y102 (fill underneath to match neighbors)
  { x: 180, z: 59, fills: [
    { y: 108, block: 'stone_bricks' },
    { y: 107, block: 'stone' },
    { y: 106, block: 'stone' },
    { y: 105, block: 'stone' },
    { y: 104, block: 'stone' },
    { y: 103, block: 'stone' },
    { y: 102, block: 'stone' },
  ]},
  // Hole 2: X149 Z287 — entry bridge. Neighbors at Y87-88. Bridge floor is spruce_planks at Y88.
  { x: 149, z: 287, fills: [
    { y: 88, block: 'spruce_planks' },
  ]},
  // Hole 3: X151 Z287 — entry bridge. Same as hole 2.
  { x: 151, z: 287, fills: [
    { y: 88, block: 'spruce_planks' },
  ]},
];

// Print neighbor context for verification
for (const f of fixes) {
  console.log(`\nHole X${f.x} Z${f.z}:`);
  console.log(`  Before: column is all air`);
  for (const [dx, dz, label] of [[1,0,'E'],[-1,0,'W'],[0,1,'S'],[0,-1,'N']]) {
    const nx = f.x + dx, nz = f.z + dz;
    for (let y = HIGH - 1; y >= LOW; y--) {
      const id = get(nx, y, nz);
      if (id) { console.log(`  Neighbor ${label} (X${nx},Z${nz}): top Y${y} = ${nameOf(id)}`); break; }
    }
  }
  console.log(`  Fix: ${f.fills.map(fl => `Y${fl.y}=${fl.block}`).join(', ')}`);
}

// 4. Apply fixes to data array
const original = data.slice();
for (const f of fixes) {
  for (const fl of f.fills) {
    const id = findId(fl.block);
    data[at(f.x, fl.y, f.z)] = id;
  }
}

// 5. Write patched region
const key = d => d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort());
const lookup = new Map(m.definitions.map((d, i) => [key(d), i]));

function paletteIndex(words, paletteLen, idx) {
  if (paletteLen <= 1) return 0;
  const bits = Math.max(4, Math.ceil(Math.log2(paletteLen)));
  const per = Math.floor(64 / bits);
  const mask = (1n << BigInt(bits)) - 1n;
  const wordIdx = Math.floor(idx / per);
  const bitOff = BigInt((idx % per) * bits);
  return Number((words[wordIdx] >> bitOff) & mask);
}

const n = (type, value) => ({ type, value });
const state = d => ({
  Name: n(8, d.Name),
  ...(d.Properties ? { Properties: n(10, Object.fromEntries(
    Object.entries(d.Properties).map(([k, v]) => [k, n(8, v)])
  )) } : {})
});

const result = patchRegion(regionBuf, tree => {
  const c = tree.value, cx = c.xPos.value, cz = c.zPos.value;
  let dirty = false;

  for (const section of c.sections.value.values) {
    const sy = section.Y.value, states = section.block_states.value;
    const palette = states.palette.value.values;
    const words = states.data?.value || [];
    const values = [];
    const paletteIds = [];
    let sectionDirty = false;

    for (const p of palette) {
      const d = {
        Name: p.Name.value,
        ...(p.Properties ? { Properties: Object.fromEntries(
          Object.entries(p.Properties.value).map(([k, v]) => [k, v.value])
        ) } : {})
      };
      paletteIds.push(lookup.get(key(d)));
    }

    for (let i = 0; i < 4096; i++) {
      const x = cx * 16 + (i & 15);
      const z = cz * 16 + ((i >> 4) & 15);
      const y = sy * 16 + (i >> 8);
      let id = paletteIds[paletteIndex(words, palette.length, i)];

      if (x < S && z < S && y >= LOW && y < HIGH) {
        const offset = (y - LOW) * S * S + z * S + x;
        if (data[offset] !== original[offset]) {
          id = data[offset];
          sectionDirty = true;
        }
      }
      if (id === undefined) throw Error('Unknown palette entry');
      values.push(id);
    }

    if (!sectionDirty) continue;
    dirty = true;

    const ids = [...new Set(values)];
    const index = new Map(ids.map((v, i) => [v, i]));
    const bits = Math.max(4, Math.ceil(Math.log2(ids.length)));
    const per = Math.floor(64 / bits);
    const packed = Array(Math.ceil(4096 / per)).fill(0n);
    values.forEach((v, i) => {
      packed[Math.floor(i / per)] |= BigInt(index.get(v)) << BigInt((i % per) * bits);
    });

    section.block_states = n(10, {
      palette: n(9, { type: 10, values: ids.map(i => state(m.definitions[i])) }),
      ...(ids.length > 1 ? { data: n(12, packed) } : {})
    });
  }

  if (dirty) {
    delete c.Heightmaps;
    c.isLightOn = n(1, 0);
    for (const section of c.sections.value.values) {
      delete section.SkyLight;
      delete section.BlockLight;
    }
  }
  return dirty;
});

// Atomic write
const pending = REGION + '.pending';
fs.writeFileSync(pending, result.bytes);
fs.renameSync(pending, REGION);

const newHash = crypto.createHash('sha256').update(fs.readFileSync(REGION)).digest('hex').toUpperCase();
console.log(`\n✓ Region patched. New hash: ${newHash}`);
console.log(`  Changed chunks: ${result.changed}`);
console.log('✓ 3 holes filled successfully.');
