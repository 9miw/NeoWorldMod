// Check what blocks cause the tall spikes in village/farmland areas
const path = require('node:path');
const { createLibrary } = require('./viewer/map-library.cjs');

const ROOT = path.resolve(__dirname, '../..');
const lib = createLibrary(path.join(ROOT, 'maps'));
const m = lib.load('leveling_island_01');

const S = m.size, LOW = m.low, HIGH = m.high;
const bytes = Buffer.from(m.blocks, 'base64');
const data = new Uint16Array(bytes.length / 2);
for (let i = 0; i < data.length; i++) data[i] = bytes.readUInt16LE(i * 2);

const at = (x, y, z) => (y - LOW) * S * S + z * S + x;
const get = (x, y, z) => (y >= LOW && y < HIGH && x >= 0 && x < S && z >= 0 && z < S) ? data[at(x, y, z)] : 0;
const nameOf = id => id ? m.definitions[id].Name.replace('minecraft:', '') : 'air';

// Check specific problem spots from the diagnosis
const spots = [
  // Village area spikes
  { x: 185, z: 234, label: 'Village spike' },
  { x: 186, z: 234, label: 'Village spike' },
  // Farmland spikes 
  { x: 50, z: 225, label: 'Farmland spike' },
  { x: 58, z: 224, label: 'Farmland spike' },
  { x: 60, z: 204, label: 'Farmland spike' },
  // Mansion front cliff
  { x: 150, z: 59, label: 'Mansion front' },
  { x: 185, z: 120, label: 'Mansion edge' },
  // Forest spike
  { x: 241, z: 153, label: 'Forest spike' },
  // Central road
  { x: 148, z: 192, label: 'Road edge' },
  { x: 140, z: 179, label: 'Road edge' },
  // Bridge area holes
  { x: 180, z: 59, label: 'Bridge hole' },
  { x: 149, z: 287, label: 'Entry bridge hole' },
  { x: 151, z: 287, label: 'Entry bridge hole' },
];

console.log('=== BLOCK COLUMN INSPECTION ===\n');
for (const s of spots) {
  console.log(`--- ${s.label} at X${s.x} Z${s.z} ---`);
  for (let y = HIGH - 1; y >= LOW; y--) {
    const id = get(s.x, y, s.z);
    if (id) {
      console.log(`  Y${y}: ${nameOf(id)}`);
    }
  }
  console.log('');
}

// Also look at the terrain around the plateau edges to see the cliff/smooth transition
console.log('=== CROSS-SECTION: Village-to-Mansion road (X=150, Z=130..240) ===');
for (let z = 130; z <= 240; z += 2) {
  let topY = -1;
  for (let y = HIGH - 1; y >= LOW; y--) {
    const n = nameOf(get(150, y, z));
    if (n !== 'air' && !n.includes('leaves') && !n.includes('vine')) { topY = y; break; }
  }
  const topBlock = topY >= 0 ? nameOf(get(150, topY, z)) : 'void';
  console.log(`  Z${String(z).padStart(3)}: surface Y${topY >= 0 ? topY : '--'} (${topBlock})`);
}

// Cross-section: Farmland edge (Z=225, X=40..60)
console.log('\n=== CROSS-SECTION: Farmland edge (Z=225, X=40..60) ===');
for (let x = 40; x <= 60; x++) {
  let topY = -1;
  for (let y = HIGH - 1; y >= LOW; y--) {
    const n = nameOf(get(x, y, z = 225));
    if (n !== 'air' && !n.includes('leaves') && !n.includes('vine')) { topY = y; break; }
  }
  const topBlock = topY >= 0 ? nameOf(get(x, topY, 225)) : 'void';
  console.log(`  X${String(x).padStart(3)}: surface Y${topY >= 0 ? topY : '--'} (${topBlock})`);
}
