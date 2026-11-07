// Diagnose terrain issues: holes, non-smooth surface, and edge gaps
// Reads the LIVE region file and reports problems.
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
const name = id => id ? m.definitions[id].Name.replace('minecraft:', '') : 'air';

const solidSet = new Set();
for (let i = 0; i < m.definitions.length; i++) {
  const n = m.definitions[i].Name.replace('minecraft:', '');
  if (n !== 'air' && !n.includes('leaves') && !n.includes('vine') && !n.includes('lantern')
      && !n.includes('torch') && n !== 'light_blue_stained_glass' && !n.includes('campfire')
      && !n.includes('fence') && !n.includes('wall') && !n.includes('iron_bars')
      && !n.includes('ladder') && !n.includes('flower') && !n.includes('grass')
      && n !== 'tall_grass' && n !== 'short_grass' && !n.includes('dandelion')
      && !n.includes('poppy') && !n.includes('dead_bush') && !n.includes('mushroom')
      && !n.includes('cobweb') && !n.includes('sign') && !n.includes('banner')
      && !n.includes('pointed_dripstone')) {
    solidSet.add(i);
  }
}

// Find surface Y for each column
const surfaceY = new Int16Array(S * S).fill(-1);
for (let z = 0; z < S; z++) {
  for (let x = 0; x < S; x++) {
    for (let y = HIGH - 1; y >= LOW; y--) {
      const id = get(x, y, z);
      if (solidSet.has(id)) {
        surfaceY[z * S + x] = y;
        break;
      }
    }
  }
}

// 1. Find holes: columns where surface is -1 but surrounded by non-void columns
const holes = [];
for (let z = 2; z < S - 2; z++) {
  for (let x = 2; x < S - 2; x++) {
    if (surfaceY[z * S + x] >= 0) continue;
    let solidNeighbors = 0;
    for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1],[1,1],[-1,1],[1,-1],[-1,-1]]) {
      if (surfaceY[(z+dz) * S + (x+dx)] >= 0) solidNeighbors++;
    }
    if (solidNeighbors >= 6) {
      holes.push({ x, z, neighbors: solidNeighbors });
    }
  }
}

// 2. Find steep jumps: adjacent columns with surface Y difference > 2 (excluding intended cliffs at buildings)
const steepJumps = [];
for (let z = 1; z < S - 1; z++) {
  for (let x = 1; x < S - 1; x++) {
    const sy = surfaceY[z * S + x];
    if (sy < 0) continue;
    for (const [dx, dz] of [[1,0],[0,1]]) {
      const nx = x + dx, nz = z + dz;
      const ny = surfaceY[nz * S + nx];
      if (ny < 0) continue;
      const diff = Math.abs(sy - ny);
      if (diff > 2) {
        steepJumps.push({ x1: x, z1: z, y1: sy, x2: nx, z2: nz, y2: ny, diff });
      }
    }
  }
}

// 3. Edge gaps: columns at the edge of landmass where neighbor height jumps > 3 (unnatural cliff in terrain)
const edgeGaps = [];
for (let z = 1; z < S - 1; z++) {
  for (let x = 1; x < S - 1; x++) {
    const sy = surfaceY[z * S + x];
    if (sy < 0) continue;
    for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
      const nx = x + dx, nz = z + dz;
      const ny = surfaceY[nz * S + nx];
      if (ny >= 0) continue; // neighbor is solid, not an edge gap
      // Check if the other side of the gap also has terrain
      const fx = nx + dx, fz = nz + dz;
      if (fx < 0 || fx >= S || fz < 0 || fz >= S) continue;
      const fy = surfaceY[fz * S + fx];
      if (fy >= 0) {
        edgeGaps.push({ x: nx, z: nz, fromY: sy, toY: fy, diff: Math.abs(sy - fy) });
      }
    }
  }
}

// 4. Summary of plateau transitions: look for jumps > 1 in flat areas (non-cliff terrain)
// Focus on areas that should be smooth: village (Z190-265), farmland (Z175-250, X45-120), road corridor
const smoothZones = [
  { name: 'Village area', x1: 120, z1: 190, x2: 195, z2: 265 },
  { name: 'Farmland area', x1: 45, z1: 175, x2: 120, z2: 250 },
  { name: 'Central road', x1: 135, z1: 135, x2: 165, z2: 270 },
  { name: 'Mansion approach', x1: 105, z1: 115, x2: 195, z2: 145 },
  { name: 'Forest area', x1: 185, z1: 120, x2: 255, z2: 205 },
];

console.log('=== LEVELING ISLAND 01 TERRAIN DIAGNOSIS ===\n');
console.log(`Map size: ${S}x${S}, Y range: ${LOW}..${HIGH}`);

// Count total land columns
let landCols = 0;
for (let i = 0; i < S * S; i++) if (surfaceY[i] >= 0) landCols++;
console.log(`Land columns: ${landCols} / ${S * S} total\n`);

console.log(`--- HOLES (void columns surrounded by >= 6 solid neighbors) ---`);
console.log(`Found: ${holes.length} holes`);
if (holes.length > 0) {
  // Group holes into clusters
  const seen = new Set();
  const clusters = [];
  for (const h of holes) {
    const key = `${h.x},${h.z}`;
    if (seen.has(key)) continue;
    const cluster = [h];
    seen.add(key);
    const queue = [h];
    while (queue.length > 0) {
      const cur = queue.shift();
      for (const other of holes) {
        const okey = `${other.x},${other.z}`;
        if (seen.has(okey)) continue;
        if (Math.abs(cur.x - other.x) <= 1 && Math.abs(cur.z - other.z) <= 1) {
          seen.add(okey);
          cluster.push(other);
          queue.push(other);
        }
      }
    }
    clusters.push(cluster);
  }
  console.log(`  ${clusters.length} hole clusters:`);
  for (const c of clusters.slice(0, 30)) {
    const minX = Math.min(...c.map(h => h.x)), maxX = Math.max(...c.map(h => h.x));
    const minZ = Math.min(...c.map(h => h.z)), maxZ = Math.max(...c.map(h => h.z));
    // Get neighbor heights
    const neighborYs = new Set();
    for (const h of c) {
      for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
        const ny = surfaceY[(h.z+dz) * S + (h.x+dx)];
        if (ny >= 0) neighborYs.add(ny);
      }
    }
    console.log(`  Cluster at X${minX}..${maxX} Z${minZ}..${maxZ} (${c.length} blocks) neighbor Y: ${[...neighborYs].sort((a,b)=>a-b).join(',')}`);
  }
}

console.log(`\n--- STEEP JUMPS (adjacent surface height diff > 2) ---`);
console.log(`Found: ${steepJumps.length} steep pairs`);

// Aggregate by zone
for (const zone of smoothZones) {
  const zoneJumps = steepJumps.filter(j =>
    j.x1 >= zone.x1 && j.x1 <= zone.x2 && j.z1 >= zone.z1 && j.z1 <= zone.z2
  );
  if (zoneJumps.length > 0) {
    console.log(`  ${zone.name}: ${zoneJumps.length} steep jumps`);
    // Show worst 5
    const worst = zoneJumps.sort((a, b) => b.diff - a.diff).slice(0, 5);
    for (const j of worst) {
      console.log(`    X${j.x1},Z${j.z1} Y${j.y1} -> X${j.x2},Z${j.z2} Y${j.y2} (diff=${j.diff})`);
    }
  }
}

// Show the overall worst jumps outside smooth zones
const outsideJumps = steepJumps.filter(j => {
  return !smoothZones.some(z => j.x1 >= z.x1 && j.x1 <= z.x2 && j.z1 >= z.z1 && j.z1 <= z.z2);
});
if (outsideJumps.length > 0) {
  console.log(`  Outside defined zones: ${outsideJumps.length} steep jumps`);
  const worst = outsideJumps.sort((a, b) => b.diff - a.diff).slice(0, 10);
  for (const j of worst) {
    console.log(`    X${j.x1},Z${j.z1} Y${j.y1} -> X${j.x2},Z${j.z2} Y${j.y2} (diff=${j.diff})`);
  }
}

console.log(`\n--- EDGE GAPS (1-block void between two surfaces) ---`);
console.log(`Found: ${edgeGaps.length} edge gap columns`);
if (edgeGaps.length > 0) {
  const sample = edgeGaps.slice(0, 20);
  for (const g of sample) {
    console.log(`  Gap at X${g.x},Z${g.z} between surface Y${g.fromY} and Y${g.toY}`);
  }
}

// 5. Histogram of surface heights for global smoothness overview
const heightHist = {};
for (let i = 0; i < S * S; i++) {
  if (surfaceY[i] >= 0) {
    heightHist[surfaceY[i]] = (heightHist[surfaceY[i]] || 0) + 1;
  }
}
console.log('\n--- SURFACE HEIGHT HISTOGRAM ---');
const sortedHeights = Object.keys(heightHist).map(Number).sort((a,b) => a - b);
for (const h of sortedHeights) {
  const bar = '#'.repeat(Math.min(60, Math.round(heightHist[h] / 20)));
  console.log(`  Y${String(h).padStart(3)}: ${String(heightHist[h]).padStart(5)} ${bar}`);
}

console.log('\n=== DIAGNOSIS COMPLETE ===');
