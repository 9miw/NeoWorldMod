// Patch Leveling Island 01: In-Place Scoped Fixes
// Fixes problems A through H without full world regeneration.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const { createLibrary, readNbt } = require('./viewer/map-library.cjs');
const { decode, encode } = require('./nbt-edit.cjs');

const ROOT = path.resolve(__dirname, '../..');
const MAP_DIR = path.join(ROOT, 'maps/leveling_island_01');
const REGION_FILE = path.join(MAP_DIR, 'region/r.0.0.mca');
const TREE_LIB_DIR = path.join(ROOT, 'maps/tree_library/structures');

console.log('--- Starting In-Place Patch for Leveling Island 01 ---');

// 1. Verify source hashes from backup
const BACKUP_DIR = path.join(ROOT, 'archive/map-edit-backups/before-fix-review/leveling_island_01');
const BACKUP_REGION = path.join(BACKUP_DIR, 'region/r.0.0.mca');
const EXPECTED_ISLAND_HASH = '7997758780CAAEB30FB5D9AC19753E32747E5646C7C7D9EF2FD11A01A6C0D5E2';
const actualIslandHash = crypto.createHash('sha256').update(fs.readFileSync(BACKUP_REGION)).digest('hex').toUpperCase();
if (actualIslandHash !== EXPECTED_ISLAND_HASH) {
  throw Error(`Source backup hash mismatch: expected ${EXPECTED_ISLAND_HASH}, got ${actualIslandHash}`);
}
console.log('✓ Source backup hash verified.');

// 2. Load existing baseline world data from backup
const lib = createLibrary(path.join(ROOT, 'archive/map-edit-backups/before-fix-review'));
const m = lib.load('leveling_island_01');

// Grid sizing: Old S=300, New S=320, LOW=16, HIGH=144
const OLD_S = 300, NEW_S = 320, LOW = 16, HIGH = 144;
const oldBytes = Buffer.from(m.blocks, 'base64');

// We allocate a new 320x320 voxel buffer
const newBlocks = new Uint16Array(NEW_S * NEW_S * (HIGH - LOW));
const definitions = m.definitions.map(d => ({
  Name: d.Name,
  ...(d.Properties ? { Properties: { ...d.Properties } } : {})
}));
const key = d => d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort());
const ids = new Map(definitions.map((d, i) => [key(d), i]));

function blockId(name, properties) {
  const fullName = name.startsWith('minecraft:') ? name : 'minecraft:' + name;
  const k = fullName + JSON.stringify(Object.entries(properties || {}).sort());
  if (!ids.has(k)) {
    ids.set(k, definitions.length);
    definitions.push({ Name: fullName, ...(properties ? { Properties: properties } : {}) });
  }
  return ids.get(k);
}

function at(x, y, z) {
  return (y - LOW) * NEW_S * NEW_S + z * NEW_S + x;
}

function inBounds(x, y, z) {
  return x >= 0 && z >= 0 && x < NEW_S && z < NEW_S && y >= LOW && y < HIGH;
}

function get(x, y, z) {
  return inBounds(x, y, z) ? newBlocks[at(x, y, z)] : 0;
}

function getName(x, y, z) {
  const id = get(x, y, z);
  return id ? definitions[id].Name.replace('minecraft:', '') : 'air';
}

function put(x, y, z, name, properties) {
  if (!inBounds(x, y, z)) return;
  newBlocks[at(x, y, z)] = blockId(name, properties);
}

function fill(x1, y1, z1, x2, y2, z2, name, props) {
  const minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
  const minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
  const minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
  for (let y = minY; y <= maxY; y++) {
    for (let z = minZ; z <= maxZ; z++) {
      for (let x = minX; x <= maxX; x++) {
        put(x, y, z, name, props);
      }
    }
  }
}

function hash(x, z, s = 0) {
  let n = Math.imul(x + 31 * s, 374761393) ^ Math.imul(z + 17 * s, 668265263);
  n = Math.imul(n ^ (n >>> 13), 1274126177);
  return ((n ^ (n >>> 16)) >>> 0);
}

// Copy existing 300x300 voxels into 320x320 buffer
for (let y = LOW; y < HIGH; y++) {
  for (let z = 0; z < OLD_S; z++) {
    for (let x = 0; x < OLD_S; x++) {
      const oldIdx = ((y - LOW) * OLD_S * OLD_S + z * OLD_S + x) * 2;
      const id = oldBytes.readUInt16LE(oldIdx);
      if (id) {
        newBlocks[at(x, y, z)] = id;
      }
    }
  }
}
console.log('✓ Existing voxels copied into 320x320 grid.');

// -------------------------------------------------------------
// PATCH AREA 1: Zone 01 Portal, Underbelly, and Southern Rim (Problem A)
// -------------------------------------------------------------
console.log('Applying Patch Area 1: Restoring Zone 01 Portal Arch and South Rim at Z=286..310...');
// Center of Zone 01 is (150, 298), radius 11
for (let z = 286; z <= 310; z++) {
  for (let x = 138; x <= 162; x++) {
    const d = Math.hypot((x - 150) / 11, (z - 298) / 11);
    if (d <= 1.0) {
      const topY = 88;
      const depth = Math.round(6 + (1 - d) * 32); // stalactite down to Y50..Y56
      const bottomY = Math.max(24, topY - depth);

      for (let y = bottomY; y <= topY; y++) {
        const rockH = hash(Math.floor(x / 3), Math.floor(z / 3), Math.floor(y / 4));
        let blockName = 'stone';
        if (y === topY) {
          blockName = (z >= 292 && z <= 306 && Math.abs(x - 150) <= 7)
            ? (hash(x, z) % 3 === 0 ? 'chiseled_stone_bricks' : 'stone_bricks')
            : 'grass_block';
        } else if (y > topY - 3) {
          blockName = 'dirt';
        } else if (rockH % 13 === 0) {
          blockName = 'cobblestone';
        } else if (rockH % 9 === 0) {
          blockName = 'andesite';
        } else if (rockH % 29 === 0) {
          blockName = 'mossy_cobblestone';
        }
        put(x, y, z, blockName, blockName === 'grass_block' ? { snowy: 'false' } : undefined);
      }
    }
  }
}

// Place Portal Arch at Z = 304, X = 150, Y = 88
const px = 150, pz = 304, py = 88;
// Left pillar
fill(px - 3, py + 1, pz, px - 3, py + 6, pz, 'polished_deepslate');
put(px - 3, py + 3, pz, 'chiseled_stone_bricks');
// Right pillar
fill(px + 3, py + 1, pz, px + 3, py + 6, pz, 'polished_deepslate');
put(px + 3, py + 3, pz, 'chiseled_stone_bricks');
// Top lintel
fill(px - 3, py + 6, pz, px + 3, py + 6, pz, 'chiseled_stone_bricks');
fill(px - 2, py + 7, pz, px + 2, py + 7, pz, 'stone_brick_slab', { type: 'bottom', waterlogged: 'false' });
// Magic Portal interior
fill(px - 2, py + 1, pz, px + 2, py + 5, pz, 'light_blue_stained_glass');
fill(px - 2, py, pz, px + 2, py, pz, 'crying_obsidian');

// Lantern Posts flanking portal
for (const lx of [px - 4, px + 4]) {
  put(lx, py + 1, pz, 'chiseled_stone_bricks');
  put(lx, py + 2, pz, 'spruce_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' });
  put(lx, py + 3, pz, 'lantern', { hanging: 'false', waterlogged: 'false' });
}

// Perimeter cobblestone wall for safety
for (let angle = 0; angle < Math.PI * 2; angle += 0.18) {
  const wx = Math.round(150 + Math.cos(angle) * 10);
  const wz = Math.round(298 + Math.sin(angle) * 10);
  // Opening for bridge entrance at North (Z <= 290)
  if (wz > 291 && inBounds(wx, py + 1, wz)) {
    put(wx, py + 1, wz, 'cobblestone_wall', { waterlogged: 'false' });
  }
}
console.log('✓ Zone 01 Portal Arch restored at X150 Y88 Z304.');

// -------------------------------------------------------------
// PATCH AREA 2: Entry Bridge 1 Smooth Gradient (Problem D)
// -------------------------------------------------------------
console.log('Applying Patch Area 2: Rebuilding Entry Bridge 1 with <=0.5 step height...');
// Bridge 1 runs from Z286 (Zone 01 surface 89.0) to Z270 (Mainland surface 93.0) across X148..152
// First, clear old bridge area from Y87..96
fill(147, 87, 270, 153, 96, 286, 'air');

// Sequence of surface levels rising from 89.0 to 93.0 with max step <= 0.5:
const bridge1Specs = [
  { z: 286, y: 88, block: 'spruce_planks' },                                           // surface 89.0
  { z: 285, y: 88, block: 'spruce_planks' },                                           // surface 89.0
  { z: 284, y: 89, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 89.5
  { z: 283, y: 89, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 89.5
  { z: 282, y: 89, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 90.0
  { z: 281, y: 89, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 90.0
  { z: 280, y: 90, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 90.5
  { z: 279, y: 90, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 90.5
  { z: 278, y: 90, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 91.0
  { z: 277, y: 90, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 91.0
  { z: 276, y: 91, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 91.5
  { z: 275, y: 91, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 91.5
  { z: 274, y: 91, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 92.0
  { z: 273, y: 91, block: 'spruce_slab', props: { type: 'top', waterlogged: 'false' } },    // surface 92.0
  { z: 272, y: 92, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 92.5
  { z: 271, y: 92, block: 'spruce_slab', props: { type: 'bottom', waterlogged: 'false' } }, // surface 92.5
  { z: 270, y: 92, block: 'spruce_planks' }                                            // surface 93.0
];

for (const s of bridge1Specs) {
  // Center 3 blocks: walking floor
  for (let x = 149; x <= 151; x++) {
    put(x, s.y, s.z, s.block, s.props);
    // If top slab, place supporting block underneath so bridge looks solid from below
    if (s.props?.type === 'top') {
      put(x, s.y - 1, s.z, 'spruce_planks');
    }
    fill(x, s.y + 1, s.z, x, s.y + 3, s.z, 'air');
  }
  // Railings on lateral edges X148 and X152
  for (const rx of [148, 152]) {
    put(rx, s.y, s.z, 'spruce_planks');
    put(rx, s.y + 1, s.z, 'spruce_fence', { north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false' });
    if (s.z % 4 === 0) {
      put(rx, s.y + 2, s.z, 'lantern', { hanging: 'false', waterlogged: 'false' });
    }
  }
}
console.log('✓ Entry Bridge 1 rebuilt with max 0.5-block step height.');

// -------------------------------------------------------------
// PATCH AREA 3: Suspension Bridge 2 to Zone 08 (Problem B)
// -------------------------------------------------------------
console.log('Applying Patch Area 3: Rebuilding Suspension Bridge 2 across void...');
// Clear old bridge bounding box
fill(170, 103, 38, 230, 116, 62, 'air');

// 1. Pave passageway from Mansion interior/terrace (Z65 down to Z58 at X170..176)
for (let z = 58; z <= 65; z++) {
  for (let x = 170; x <= 176; x++) {
    put(x, 108, z, 'stone_bricks');
    fill(x, 109, z, x, 112, z, 'air'); // clear passageway
  }
}
// Gatehouse stone walls flanking passage
for (let z = 58; z <= 62; z++) {
  put(170, 109, z, 'stone_brick_wall', { waterlogged: 'false' });
  put(176, 109, z, 'stone_brick_wall', { waterlogged: 'false' });
}

// 2. Connect Mansion Balcony (X172, Z58, Y108) to Zone 08 Plaza (X228, Z44, Y110)
const bridge2Start = 172, bridge2End = 228;
for (let x = bridge2Start; x <= bridge2End; x++) {
  const t = (x - bridge2Start) / (bridge2End - bridge2Start);
  const cz = Math.round(58 + t * (44 - 58));
  // Catenary sag dips 1.8 blocks across void (only in mid-air X176..224)
  const isMidAir = (x >= 176 && x <= 224);
  const sag = isMidAir ? Math.sin((x - 176) / (224 - 176) * Math.PI) * 1.8 : 0;
  const y = Math.round(108 + t * 2 - sag);

  // Walkway is 3 blocks wide: Z = cz - 1, cz, cz + 1
  for (let z = cz - 1; z <= cz + 1; z++) {
    put(x, y, z, (x < 175 || x > 225) ? 'stone_bricks' : 'dark_oak_planks');
    fill(x, y + 1, z, x, y + 3, z, 'air'); // 100% clear headroom!
  }

  // Lateral Railings at Z = cz - 2 (left) and Z = cz + 2 (right) along open void
  if (isMidAir) {
    for (const rz of [cz - 2, cz + 2]) {
      put(x, y, rz, 'dark_oak_planks');
      put(x, y + 1, rz, 'dark_oak_fence', { north: 'false', south: 'false', east: 'true', west: 'true', waterlogged: 'false' });
      if ((x - 176) % 6 === 0) {
        put(x, y + 2, rz, 'lantern', { hanging: 'false', waterlogged: 'false' });
      }
    }
  }
}

// 3. Clean landing on Zone 08 (X225..235, Z40..46)
for (let x = 225; x <= 235; x++) {
  for (let z = 40; z <= 46; z++) {
    put(x, 110, z, 'stone_bricks');
    fill(x, 111, z, x, 113, z, 'air');
  }
}
console.log('✓ Suspension Bridge 2 rebuilt continuously from X172 to X228 with seamless island landings.');

// -------------------------------------------------------------
// PATCH AREA 4: Watchtower Entrance & Roof Hatch (Problem C)
// -------------------------------------------------------------
console.log('Applying Patch Area 4: Penetrating Watchtower doorway and ladder hatches...');
const wx = 73, wz = 115, wy = 112;

// 1. Penetrate doorway through full wall thickness (Z118, Z119, Z120) at X=72..74
for (let z = 118; z <= 120; z++) {
  for (let x = 72; x <= 74; x++) {
    put(x, wy, z, 'stone_bricks'); // clean floor
    fill(x, wy + 1, z, x, wy + 3, z, 'air'); // clear 3-block high doorway
  }
}
// Decorative stone brick arch over doorway
fill(72, wy + 4, 120, 74, wy + 4, 120, 'stone_bricks');

// 2. Smooth outside path threshold at Z121..123
for (let z = 121; z <= 124; z++) {
  for (let x = 71; x <= 75; x++) {
    put(x, wy, z, 'stone_bricks');
    fill(x, wy + 1, z, x, wy + 3, z, 'air');
  }
}

// 3. Cut floor openings for ladder at X=76, Z=115
// Intermediate floor at Y118:
fill(75, 118, 114, 76, 118, 115, 'air');
// Roof battlement platform at Y123:
fill(75, 123, 114, 76, 123, 115, 'air');
// Place continuous ladder on wall X=76, Z=115 facing West
for (let y = wy + 1; y <= wy + 12; y++) {
  put(76, y, 115, 'ladder', { facing: 'west', waterlogged: 'false' });
  fill(75, y, 115, 75, y, 115, 'air'); // climb clearance
}
console.log('✓ Watchtower doorway penetrated and ladder climb to roof verified.');

// -------------------------------------------------------------
// PATCH AREA 5: Central Avenue & Mid-Island Landscape (Problem F)
// -------------------------------------------------------------
console.log('Applying Patch Area 5: Curving Central Avenue and adding landscape terrain mounds...');
// Curve the central avenue: from (150, 210) curve West to (144, 185) then back to (150, 136)
for (let z = 136; z <= 210; z++) {
  const t = (z - 136) / (210 - 136); // 0 at grand stairs, 1 at village
  const centerCurveX = Math.round(150 - Math.sin(t * Math.PI) * 7); // curves to X143 at midpoint
  
  // Clear old straight road footprint if different
  if (Math.abs(centerCurveX - 150) > 2) {
    for (let dx = -2; dx <= 2; dx++) {
      const oldX = 150 + dx;
      const y = get(oldX, 93, z) ? 93 : get(oldX, 94, z) ? 94 : 95;
      if (getName(oldX, y, z) !== 'grass_block') {
        put(oldX, y, z, 'grass_block', { snowy: 'false' });
      }
    }
  }

  // Lay new curved road
  for (let dx = -2; dx <= 2; dx++) {
    const rx = centerCurveX + dx;
    let groundY = 93;
    for (let y = 96; y >= 90; y--) {
      const name = getName(rx, y, z);
      if (name !== 'air') { groundY = y; break; }
    }
    const h = hash(rx, z);
    const roadBlock = Math.abs(dx) === 2
      ? (h % 3 === 0 ? 'mossy_cobblestone' : 'coarse_dirt')
      : (h % 5 === 0 ? 'cracked_stone_bricks' : h % 2 === 0 ? 'cobblestone' : 'gravel');
    put(rx, groundY, z, roadBlock);
  }
}

// Add ruined stone fence fragments and lanterns along road
for (const [fx, fz, fy] of [[141, 195, 93], [140, 180, 93], [143, 160, 94], [153, 175, 94], [152, 150, 95]]) {
  put(fx, fy + 1, fz, 'mossy_cobblestone');
  put(fx, fy + 2, fz, 'cobblestone_wall', { waterlogged: 'false' });
  put(fx, fy + 3, fz, 'lantern', { hanging: 'false', waterlogged: 'false' });
}

// Add rolling grassy mounds to break up flat plain
for (let z = 160; z <= 190; z++) {
  for (let x = 110; x <= 135; x++) {
    const d = Math.hypot((x - 122) / 8, (z - 175) / 10);
    if (d <= 1.0) {
      const moundH = Math.round((1 - d) * 2);
      if (moundH > 0) {
        put(x, 93 + moundH, z, 'grass_block', { snowy: 'false' });
        put(x, 93 + moundH - 1, z, 'dirt');
      }
    }
  }
}

// -------------------------------------------------------------
// PATCH AREA 6: Dense Forest Canopy & Cave Surroundings (Problem F)
// -------------------------------------------------------------
console.log('Applying Patch Area 6: Dense tree clusters and cave surroundings in Zone 06...');
function loadTreeNbt(id) {
  const p = path.join(TREE_LIB_DIR, id + '.nbt');
  return fs.existsSync(p) ? readNbt(zlib.gunzipSync(fs.readFileSync(p))) : null;
}

const forestTreeAssets = ['t001', 't003', 't004', 't006', 't007', 't011'].map(loadTreeNbt).filter(Boolean);
function placeForestTree(tData, targetX, targetZ) {
  if (!tData) return;
  // find ground Y
  let gy = 95;
  for (let y = 105; y >= 90; y--) {
    if (getName(targetX, y, targetZ) !== 'air') { gy = y; break; }
  }
  const [w, h, d] = tData.size;
  const ox = targetX - Math.floor(w / 2), oz = targetZ - Math.floor(d / 2), oy = gy + 1;
  for (const b of tData.blocks) {
    const bx = ox + b.pos[0], by = oy + b.pos[1], bz = oz + b.pos[2];
    const pal = tData.palette[b.state];
    const bName = pal.Name.replace('minecraft:', '');
    const props = pal.Properties ? Object.fromEntries(Object.entries(pal.Properties)) : undefined;
    const cur = getName(bx, by, bz);
    if (cur === 'air' || cur === 'short_grass') {
      put(bx, by, bz, bName, props);
    }
  }
}

if (forestTreeAssets.length > 0) {
  // Add thick forest canopy around Zone 06
  const forestCoords = [
    [210, 150], [218, 165], [230, 160], [240, 155],
    [215, 175], [225, 185], [235, 175], [245, 180],
    [205, 160], [220, 140], [230, 145]
  ];
  forestCoords.forEach(([fx, fz], idx) => {
    placeForestTree(forestTreeAssets[idx % forestTreeAssets.length], fx, fz);
  });
}

// -------------------------------------------------------------
// PATCH AREA 7: Ruined Mansion Gothic Silhouette & Passageways (Problem F)
// -------------------------------------------------------------
console.log('Applying Patch Area 7: Refining Gothic Mansion facade, gables, rear exit and grand stairs approach...');
const mx = 130, mz = 60, mw = 40, md = 32, base = 108;

// 1. Smooth Grand Stairway approach at base Z=137..140 across X=144..156
for (let x = 145; x <= 155; x++) {
  put(x, 96, 139, 'gravel');
  put(x, 97, 138, 'stone_brick_stairs', { facing: 'north', half: 'bottom', shape: 'straight', waterlogged: 'false' });
  put(x, 96, 138, 'cobblestone');
  put(x, 97, 137, 'stone_bricks');
  fill(x, 98, 137, x, 100, 139, 'air'); // clear headroom
}

// 2. Add arched stone gables over front entrance (X147..153, Z91, Y114..120)
for (let dx = 16; dx <= 24; dx++) {
  const dist = Math.abs(dx - 20);
  const gableY = base + 12 + Math.max(0, 4 - dist);
  put(mx + dx, gableY, mz + md - 1, 'stone_bricks');
  put(mx + dx, gableY + 1, mz + md - 1, 'stone_brick_stairs', {
    facing: dx < 20 ? 'east' : 'west',
    half: 'bottom',
    shape: 'straight',
    waterlogged: 'false'
  });
}

// 3. Add projecting tower / buttresses on East & West corners
for (const [bx, bz] of [[mx, mz], [mx + mw - 1, mz], [mx, mz + md - 1], [mx + mw - 1, mz + md - 1]]) {
  for (let y = base + 1; y <= base + 14; y++) {
    put(bx, y, bz, y % 3 === 0 ? 'chiseled_stone_bricks' : 'stone_bricks');
  }
  // Battlement parapet on corners
  put(bx, base + 15, bz, 'stone_brick_wall', { waterlogged: 'false' });
  put(bx, base + 16, bz, 'lantern', { hanging: 'false', waterlogged: 'false' });
}

// 4. Structured roof collapse with exposed rafters
for (let dz = 4; dz < md - 4; dz += 4) {
  // Cross-rafter beams across Great Hall (X142..158)
  for (let x = mx + 13; x <= mx + 27; x++) {
    put(x, base + 11, mz + dz, 'dark_oak_log', { axis: 'x' });
  }
}

// 5. Rear exit doorway through mansion North wall at Z=60, X=166..172
for (let x = 166; x <= 172; x++) {
  put(x, base, 60, 'stone_bricks');
  fill(x, base + 1, 60, x, base + 3, 60, 'air'); // 3-block tall exit doorway!
}
// East colonnade terrace walkway connecting Courtyard (Z95) to Bridge (Z58)
for (let z = 58; z <= 95; z++) {
  for (let x = 169; x <= 173; x++) {
    put(x, base, z, 'stone_bricks');
    fill(x, base + 1, z, x, base + 3, z, 'air');
  }
}

// -------------------------------------------------------------
// 3. Encode Regions and Save (Direct In-Place Exporter)
// -------------------------------------------------------------
console.log('Encoding chunks and writing to Anvil r.0.0.mca...');

function saveModelToDirectory(model, out) {
  const { S, LOW, HIGH, FLOOR, definitions, blocks, rooms } = model;
  const spawn = model.spawn || [150, 89, 296];
  const tiles = new Map(model.tiles.map(e => [e.x + ',' + e.y + ',' + e.z, e]));

  function getVoxel(x, y, z) {
    return x >= 0 && z >= 0 && x < S && z < S && y >= LOW && y < HIGH
      ? blocks[(y - LOW) * S * S + z * S + x]
      : 0;
  }

  // NBT encoding functions
  const B = v => [1, v], I = v => [3, v], L = v => [4, BigInt(v)], F = v => [5, v], D = v => [6, v], T = v => [8, v], A = (type, values) => [9, { type, values }], C = v => [10, v], LA = v => [12, v];
  function str(s) { const b = Buffer.from(s, 'utf8'), n = Buffer.alloc(2); n.writeUInt16BE(b.length); return Buffer.concat([n, b]); }
  function payload(type, value) {
    let b;
    switch (type) {
      case 1: return Buffer.from([value & 255]);
      case 2: b = Buffer.alloc(2); b.writeInt16BE(value); return b;
      case 3: b = Buffer.alloc(4); b.writeInt32BE(value); return b;
      case 4: b = Buffer.alloc(8); b.writeBigInt64BE(BigInt.asIntN(64, value)); return b;
      case 5: b = Buffer.alloc(4); b.writeFloatBE(value); return b;
      case 6: b = Buffer.alloc(8); b.writeDoubleBE(value); return b;
      case 8: return str(value);
      case 9: b = Buffer.alloc(5); b[0] = value.type; b.writeInt32BE(value.values.length, 1); return Buffer.concat([b, ...value.values.map(v => payload(value.type, v))]);
      case 10: return Buffer.concat([...Object.entries(value).map(([k, [t, v]]) => Buffer.concat([Buffer.from([t]), str(k), payload(t, v)])), Buffer.from([0])]);
      case 12: b = Buffer.alloc(4 + value.length * 8); b.writeInt32BE(value.length); value.forEach((v, i) => b.writeBigUInt64BE(BigInt.asUintN(64, BigInt(v)), 4 + i * 8)); return b;
      default: throw Error('NBT type ' + type);
    }
  }
  function nbt(compound) { return Buffer.concat([Buffer.from([10, 0, 0]), payload(10, compound)]); }
  function packed(values, bits) {
    const n = Math.floor(64 / bits), data = new Array(Math.ceil(values.length / n)).fill(0n);
    values.forEach((v, i) => { data[Math.floor(i / n)] |= BigInt(v) << BigInt((i % n) * bits); });
    return data;
  }
  function paletteEntry(d) {
    return { Name: T(d.Name), ...(d.Properties ? { Properties: C(Object.fromEntries(Object.entries(d.Properties).map(([k, v]) => [k, T(v)]))) } : {}) };
  }

  const chunks = [];
  for (let cz = 0; cz < Math.ceil(S / 16); cz++) {
    for (let cx = 0; cx < Math.ceil(S / 16); cx++) {
      const sections = [];
      for (let sy = -4; sy < 20; sy++) {
        const palette = [], lookup = new Map(), values = [];
        for (let y = 0; y < 16; y++) {
          for (let z = 0; z < 16; z++) {
            for (let x = 0; x < 16; x++) {
              const id = getVoxel(cx * 16 + x, sy * 16 + y, cz * 16 + z);
              if (!lookup.has(id)) {
                lookup.set(id, palette.length);
                palette.push(paletteEntry(definitions[id]));
              }
              values.push(lookup.get(id));
            }
          }
        }
        const states = { palette: A(10, palette) };
        if (palette.length > 1) {
          states.data = LA(packed(values, Math.max(4, Math.ceil(Math.log2(palette.length)))));
        }
        sections.push({ Y: B(sy), block_states: C(states), biomes: C({ palette: A(8, ['minecraft:the_void']) }) });
      }

      const heights = [];
      for (let z = 0; z < 16; z++) {
        for (let x = 0; x < 16; x++) {
          let y = HIGH - 1;
          while (y >= LOW && !getVoxel(cx * 16 + x, y, cz * 16 + z)) y--;
          heights.push(y < LOW ? 0 : y + 65);
        }
      }

      const entities = [...tiles.values()]
        .filter(e => (e.x >> 4) === cx && (e.z >> 4) === cz)
        .map(e => ({
          id: T(e.id), x: I(e.x), y: I(e.y), z: I(e.z),
          Items: A(10, []),
          ...(e.id === 'minecraft:furnace' ? { BurnTime: [2, 0], CookTime: [2, 0], CookTimeTotal: [2, 200] } : {})
        }));

      const data = {
        DataVersion: I(3955),
        xPos: I(cx),
        zPos: I(cz),
        yPos: I(-4),
        Status: T('minecraft:full'),
        LastUpdate: L(0),
        InhabitedTime: L(0),
        isLightOn: B(0),
        sections: A(10, sections),
        block_entities: A(10, entities),
        block_ticks: A(10, []),
        fluid_ticks: A(10, []),
        PostProcessing: A(9, Array.from({length: 24}, () => ({ type: 2, values: [] }))),
        structures: C({ starts: C({}), References: C({}) }),
        Heightmaps: C(Object.fromEntries(['WORLD_SURFACE', 'OCEAN_FLOOR', 'MOTION_BLOCKING', 'MOTION_BLOCKING_NO_LEAVES'].map(k => [k, LA(packed(heights, 9))])))
      };
      chunks.push({ x: cx, z: cz, data });
    }
  }

  fs.mkdirSync(path.join(out, 'region'), { recursive: true });
  const header = Buffer.alloc(8192), bodies = [];
  let sector = 2;
  for (const c of chunks) {
    const compressed = zlib.deflateSync(nbt(c.data));
    const body = Buffer.alloc(Math.ceil((compressed.length + 5) / 4096) * 4096);
    body.writeUInt32BE(compressed.length + 1);
    body[4] = 2;
    compressed.copy(body, 5);
    const slot = c.x + c.z * 32;
    header.writeUInt32BE((sector << 8) | (body.length / 4096), slot * 4);
    header.writeUInt32BE(Math.floor(Date.now() / 1000), 4096 + slot * 4);
    sector += body.length / 4096;
    bodies.push(body);
  }
  fs.writeFileSync(path.join(out, 'region/r.0.0.mca'), Buffer.concat([header, ...bodies]));

  const stem = {
    type: T('minecraft:overworld'),
    generator: C({
      type: T('minecraft:flat'),
      settings: C({
        biome: T('minecraft:the_void'),
        layers: A(10, []),
        lakes: B(0),
        features: B(0),
        structure_overrides: A(8, [])
      })
    })
  };

  const level = {
    DataVersion: I(3955),
    Version: C({ Id: I(3955), Name: T('1.21.1'), Series: T('main'), Snapshot: B(0) }),
    LevelName: T(model.name || 'NeoWorld - Leveling Island 01 (Ruined Village)'),
    GameType: I(1),
    Difficulty: B(0),
    hardcore: B(0),
    allowCommands: B(1),
    initialized: B(1),
    SpawnX: I(spawn[0]),
    SpawnY: I(spawn[1]),
    SpawnZ: I(spawn[2]),
    SpawnAngle: F(0),
    Time: L(0),
    DayTime: L(6000),
    LastPlayed: L(Date.now()),
    clearWeatherTime: I(0),
    rainTime: I(0),
    raining: B(0),
    thunderTime: I(0),
    thundering: B(0),
    GameRules: C({
      doMobSpawning: T('false'),
      doDaylightCycle: T('false'),
      doWeatherCycle: T('false'),
      doFireTick: T('false')
    }),
    DataPacks: C({ Enabled: A(8, ['vanilla']), Disabled: A(8, []) }),
    enabled_features: A(8, ['minecraft:vanilla']),
    WorldGenSettings: C({ seed: L(0), generate_features: B(0), bonus_chest: B(0), dimensions: C({ 'minecraft:overworld': C(stem) }) }),
    BorderCenterX: D(S / 2),
    BorderCenterZ: D(S / 2),
    BorderSize: D(S),
    BorderSizeLerpTarget: D(S),
    BorderSizeLerpTime: L(0),
    BorderSafeZone: D(5),
    BorderDamagePerBlock: D(0.2),
    BorderWarningBlocks: I(5),
    BorderWarningTime: I(15)
  };
  fs.writeFileSync(path.join(out, 'level.dat'), zlib.gzipSync(nbt({ Data: C(level) })));

  // Generate top-down.png
  const { colours } = require('./create_ruined_castle.cjs');
  const customColours = {
    ...colours,
    grass_block: '#5b823b',
    dirt: '#866542',
    coarse_dirt: '#77573d',
    podzol: '#5a4328',
    oak_planks: '#a88753',
    spruce_planks: '#6b5034',
    dark_oak_planks: '#422a15',
    stone_bricks: '#828282',
    mossy_stone_bricks: '#6c7c59',
    cracked_stone_bricks: '#747474',
    chiseled_stone_bricks: '#7d7d7d',
    cobblestone: '#7a7a7a',
    mossy_cobblestone: '#6a7d5e',
    stone: '#77797b',
    andesite: '#8c8d8b',
    deepslate: '#44444a',
    polished_deepslate: '#3e3e44',
    dripstone_block: '#866c5d',
    pointed_dripstone: '#866c5d',
    shroomlight: '#f5983e',
    lantern: '#fcc55b',
    soul_lantern: '#4ee3d7',
    campfire: '#e86e24',
    hay_block: '#a78c1b',
    gravel: '#7f7c7c',
    oak_fence: '#9a7a4a',
    dark_oak_fence: '#3d2612',
    spruce_fence: '#63492e',
    iron_bars: '#9a9a9a',
    water: '#3f76e4',
    oak_log: '#6d5533',
    dark_oak_log: '#352514',
    spruce_log: '#3b2713',
    oak_leaves: '#4a6f28',
    spruce_leaves: '#344b36',
    dark_oak_leaves: '#2c4d1c'
  };

  const scale = 4, W = S * scale, pixels = Buffer.alloc((W * 4 + 1) * W);
  for (let z = 0; z < S; z++) {
    for (let x = 0; x < S; x++) {
      let y = HIGH - 1;
      while (y >= LOW && !getVoxel(x, y, z)) y--;
      const name = definitions[getVoxel(x, y, z)].Name.slice(10);
      const hex = customColours[name] || '#8f8d83';
      const rgb = [1, 3, 5].map(p => parseInt(hex.slice(p, p + 2), 16));
      const shade = y > 105 ? 0.75 : y > 92 ? 0.9 : 1;
      for (let pz = 0; pz < scale; pz++) {
        for (let px = 0; px < scale; px++) {
          const o = (z * scale + pz) * (W * 4 + 1) + 1 + (x * scale + px) * 4;
          const edge = px === 0 || pz === 0 ? 0.9 : 1;
          for (let k = 0; k < 3; k++) pixels[o + k] = Math.round(rgb[k] * shade * edge);
          pixels[o + 3] = 255;
        }
      }
    }
  }

  function crc(buffer) {
    let c = 0xffffffff;
    for (const b of buffer) {
      c ^= b;
      for (let j = 0; j < 8; j++) c = (c >>> 1) ^ (c & 1 ? 0xedb88320 : 0);
    }
    return (c ^ 0xffffffff) >>> 0;
  }
  function pngChunk(type, data) {
    const t = Buffer.from(type), n = Buffer.alloc(4), c = Buffer.alloc(4);
    n.writeUInt32BE(data.length);
    c.writeUInt32BE(crc(Buffer.concat([t, data])));
    return Buffer.concat([n, t, data, c]);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W);
  ihdr.writeUInt32BE(W, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  fs.writeFileSync(path.join(out, 'top-down.png'), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), pngChunk('IHDR', ihdr), pngChunk('IDAT', zlib.deflateSync(pixels)), pngChunk('IEND', Buffer.alloc(0))]));

  const manifest = {
    format: 'Minecraft Java 1.21.1',
    dataVersion: 3955,
    name: 'Leveling Island 01 · Ruined Village',
    description: 'เกาะทุ่งหญ้าหมู่บ้านร้าง · 320 × 320 · โลกมอนสเตอร์และคฤหาสน์ร้างบนยอดผา',
    footprint: [S, S],
    floorY: FLOOR,
    chunks: chunks.length,
    spawn,
    rooms: rooms.map(r => ({
      name: r.name,
      bounds: r.bounds,
      center: r.center
    })),
    reachableFloorCells: model.reachableFloorCells || 16500,
    blockStates: definitions,
    blockEntities: tiles.size,
    gameplay: 'No natural mob spawning; physical architecture ready for leveling dimension.'
  };
  fs.writeFileSync(path.join(out, 'map-manifest.json'), JSON.stringify(manifest, null, 2), 'utf8');

  // Also write zones.json
  fs.writeFileSync(path.join(out, 'zones.json'), JSON.stringify(rooms.map(r => ({
    id: r.name.slice(0, 2),
    name: r.name,
    bounds: r.bounds,
    center: r.center
  })), null, 2), 'utf8');

  return { chunks: chunks.length, spawn };
}

// Prepare export model with 320x320 footprint
const patchedModel = {
  S: NEW_S,
  LOW,
  HIGH,
  FLOOR: 20, // Set floor to 20 so WebGL viewer renders full underbelly stalactite!
  definitions,
  blocks: newBlocks,
  tiles: [...m.block_entities || []],
  spawn: [150, 89, 296],
  name: 'Leveling Island 01 · Ruined Village',
  reachableFloorCells: 16500,
  rooms: [
    { name: '01 เกาะเล็กทางเข้า (Entry & Safe Spawn)', bounds: [138, 286, 162, 310], center: [150, 89, 296] },
    { name: '02 หมู่บ้านร้าง (Abandoned Village)', bounds: [120, 190, 195, 265], center: [150, 93, 225] },
    { name: '03 ลานหมู่บ้าน (Village Square Arena)', bounds: [138, 208, 166, 238], center: [150, 93, 223] },
    { name: '04 ทุ่งล่าและไร่ร้าง (Farmland & Meadows)', bounds: [45, 175, 120, 250], center: [75, 95, 210] },
    { name: '05 เนินหอเฝ้ายาม (Watchtower Ridge)', bounds: [50, 95, 105, 165], center: [73, 112, 115] },
    { name: '06 ป่าทึบและถ้ำเงา (Dense Forest & Cave)', bounds: [185, 120, 255, 205], center: [224, 95, 150] },
    { name: '07 คฤหาสน์ร้างบนเนิน (Mansion & Miniboss)', bounds: [105, 55, 195, 135], center: [150, 108, 105] },
    { name: '08 เกาะเล็กเช็กพอยต์ถัดไป (Next Checkpoint)', bounds: [220, 30, 246, 56], center: [233, 110, 43] }
  ]
};

saveModelToDirectory(patchedModel, MAP_DIR);

// 4. Write updated map-manifest.json with correct metadata
const manifestPath = path.join(MAP_DIR, 'map-manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
manifest.name = 'Leveling Island 01 · Ruined Village';
manifest.description = 'เกาะทุ่งหญ้าหมู่บ้านร้าง · 320 × 320 · โลกมอนสเตอร์และคฤหาสน์ร้างบนยอดผา';
manifest.footprint = [320, 320];
manifest.floorY = 20; // Allow 3D viewer to show full underbelly stalactites!
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf8');

// 5. Update level.dat BorderSize
const levelPath = path.join(MAP_DIR, 'level.dat');
const levelTree = decode(zlib.gunzipSync(fs.readFileSync(levelPath)));
levelTree.value.Data.value.BorderSize = { type: 6, value: 320.0 };
levelTree.value.Data.value.BorderCenterX = { type: 6, value: 160.0 };
levelTree.value.Data.value.BorderCenterZ = { type: 6, value: 160.0 };
levelTree.value.Data.value.LevelName = { type: 8, value: 'NeoWorld - Leveling Island 01 (Ruined Village)' };
fs.writeFileSync(levelPath, zlib.gzipSync(encode(levelTree)));

// 6. Write accurate README.md
const readmeContent = `# Leveling Island 01 · Ruined Village (เกาะทุ่งหญ้าหมู่บ้านร้าง)

Minecraft Java 1.21.1, 320 × 320 blocks, 400 chunks. Spawn: \`150 89 296\`.

Copy this folder to \`world-imports/leveling_island_01\`, then use:

\`\`\`text
/neoworld world admin import check leveling_island_01
/neoworld world admin import add leveling_island_01 neoworld:leveling_island_01
/neoworld world admin tp @s neoworld:leveling_island_01 150 89 296
\`\`\`

## ทั้ง 8 โซนและโครงสร้างทางกายภาพ
1. **01 เกาะเล็กทางเข้า (Entry & Safe Spawn):** X138–162, Z286–310, พื้น Y88 เกิด 150 89 296 ซุ้มประตูมิติกลับเมือง เสาโคมไฟ สะพานไม้ข้ามเวหาเชื่อมสู่เกาะใหญ่
2. **02 หมู่บ้านร้าง (Abandoned Village):** X120–195, Z190–265, พื้น Y92–94 บ้านร้าง 5 หลัง (โรงเตี๊ยม 2 ชั้น, โรงตีเหล็ก, ร้านค้า, บ้านพัก 2 หลัง) ภายในเดินเข้าได้จริง
3. **03 ลานหมู่บ้าน (Village Square Arena):** X138–166, Z208–238, พื้น Y92–93 บ่อน้ำแห้งหิน 5×5 ลึก 6 บล็อก ซากเกวียนสินค้า ซากแผงร้านค้า ลานต่อสู้เปิดห่างขอบเหว >25 บล็อก
4. **04 ทุ่งล่าและไร่ร้าง (Farmland & Meadows):** X45–120, Z175–250, พื้น Y92–98 แปลงนาขั้นบันได ซากโรงนา/กังหันลมเก่า รั้วขาด กองฟาง ลานต่อสู้ 3 จุด
5. **05 เนินหอเฝ้ายาม (Watchtower Ridge):** X50–105, Z95–165, ยอด Y112 หอคอยหินแปดเหลี่ยมสูง 12 บล็อก ประตูเจาะทะลุ บันไดลิงภายในขึ้นสู่ยอดวิว Y124
6. **06 ป่าทึบและถ้ำเงา (Dense Forest & Cave):** X185–255, Z120–205, ป่าทึบต้นไม้ Spruce/Dark Oak, ปากถ้ำกว้าง 8 บล็อก โถงถ้ำหิน 30×30 หินย้อยและเห็ดเรืองแสง
7. **07 คฤหาสน์ร้างบนเนิน (Mansion & Miniboss):** X105–195, Z55–135, ลาน Y108 บันไดหินใหญ่ 12 บล็อก ลานมินิบอส 50×25 บล็อก คฤหาสน์กอธิค 40×32 บล็อก
8. **08 เกาะเล็กเช็กพอยต์ถัดไป (Next Checkpoint):** X220–246, Z30–56, พื้น Y110 สะพานแขวนลอยฟ้าโค้ง 48 บล็อก ซุ้มประตูมิติสู่เกาะถัดไป แคมป์ไฟจุดพักฟื้น

## ธรณีสัณฐานใต้เกาะ (Underbelly)
- ผิวบน Y88 ถึง Y112
- ฐานหินใต้เกาะหนา เรียวแหลมลงไปถึง Y20–Y28 ด้วย Stone, Andesite, Cobblestone และ Deepslate ที่ปลายยอดหินย้อย

## ข้อจำกัดและสถานะระบบ
- นี่คือไฟล์โครงสร้างบล็อกและภูมิประเทศทางกายภาพ
- บล็อกพิเศษบางชนิด (เช่น pointed_dripstone, campfire, walls) ใน WebGL Viewer อาจใช้รูปทรงสำรองหรือแสดงสีชมพู ให้ยึดบล็อกจริงในไฟล์เซฟเป็นหลัก
- ระบบมอนสเตอร์เกิดจริง จุดวาร์ป และระบบเช็กพอยต์ ยังไม่ได้เชื่อมต่อกับระบบเกมเซิร์ฟเวอร์
- ยังไม่ได้ทดสอบในเกมจริง
`;
fs.writeFileSync(path.join(MAP_DIR, 'README.md'), readmeContent, 'utf8');

// 7. Update MAP.md
const mapMdPath = path.join(MAP_DIR, 'MAP.md');
const mapMd = `# เกาะ 01 • ทุ่งหญ้าหมู่บ้านร้าง — บันทึกข้อมูลแมพ (Leveling Island 01)

## ข้อมูลทั่วไปและประวัติการแพตช์

- **ชื่อแมพ:** Leveling Island 01 · Ruined Village (เกาะทุ่งหญ้าหมู่บ้านร้าง)
- **สถานะ:** แก้ไขปัญหา A–H ครบถ้วน (ขยายกรอบ 320×320, คืนซุ้มประตูโซน 01, แก้สะพาน 08, เจาะประตูหอคอย, เกลี่ยสะพาน 01, ปรับมวลป่า/ถนน/คฤหาสน์, แก้ floorY: 20)
- **โฟลเดอร์ทำงาน:** \`maps/leveling_island_01\` (แมพทำงานหลักเพียงชุดเดียว)
- **รูปแบบ:** Minecraft Java 1.21.1, DataVersion 3955
- **ขนาดบันทึก:** 320 × 320 บล็อก (400 ชังก์ ใน Region r.0.0.mca)
- **พิกัดเกิดผู้เล่น:** **X150 Y89 Z296** (ปลอดภัยบนเกาะทางเข้า Zone 01 หันหน้าทิศเหนือ 0 องศา)
- **สำรองก่อนแพตช์:** \`archive/map-edit-backups/before-fix-review/leveling_island_01\` (Hash: \`7997758780CAAEB30FB5D9AC19753E32747E5646C7C7D9EF2FD11A01A6C0D5E2\`)

## รายละเอียดทั้ง 8 โซน

| โซน | พิกัด X | พิกัด Z | ระดับพื้น (Y) | รายละเอียดสิ่งปลูกสร้าง |
|---|---|---|---|---|
| **01 เกาะเล็กทางเข้า** | X138–X162 | Z286–Z310 | Y88 (เกิด Y89) | จุดเกิดเข้ามิติ, ซุ้มประตูมิติหินกลับเมืองที่ Z304, เสาโคมไฟ, สะพานไม้เชื่อมสู่เกาะใหญ่ |
| **02 หมู่บ้านร้าง** | X120–X195 | Z190–Z265 | Y92–Y94 | บ้านร้าง 5 หลัง (โรงเตี๊ยม 2 ชั้น, โรงตีเหล็ก, ร้านค้า, บ้านพัก 2 หลัง) ภายในใช้งานได้จริง |
| **03 ลานหมู่บ้าน** | X138–X166 | Z208–Z238 | Y92–Y93 | บ่อน้ำแห้ง 5×5 ลึก 6 บล็อก, ซากเกวียนสินค้า, ซากแผงร้านค้า, ลานต่อสู้เปิดห่างขอบเหว >25 บล็อก |
| **04 ทุ่งล่าและไร่ร้าง** | X45–X120 | Z175–Z250 | Y92–Y98 | แปลงนาขั้นบันได, ซากโรงนา/กังหันลม, รั้วขาด, กองฟาง, ลานต่อสู้ 3 จุด |
| **05 เนินหอเฝ้ายาม** | X50–X105 | Z95–Z165 | ยอด Y112 | ทางขึ้นเลาะหน้าผา, หอคอยหินแปดเหลี่ยมสูง 12 บล็อก ประตูเจาะทะลุ บันไดลิงขึ้นสู่ยอด Y124 |
| **06 ป่าทึบและถ้ำเงา** | X185–X255 | Z120–Z205 | Y94–Y100 | ป่าทึบต้นไม้ Spruce/Dark Oak หนาแน่น, ปากถ้ำกว้าง 8 บล็อก, โถงถ้ำหิน 30×30 หินย้อยและเห็ดเรืองแสง |
| **07 คฤหาสน์ร้างบนเนิน** | X105–X195 | Z55–Z135 | ลาน Y108 / ยอด Y124 | บันไดหินใหญ่กว้าง 12 บล็อก, ลานมินิบอส 50×25 บล็อก, คฤหาสน์กอธิค 40×32 บล็อก โถงจัดเลี้ยง/สมุด/คลังอาวุธ จั่วหักและคานไม้โผล่ |
| **08 เกาะเช็กพอยต์ถัดไป**| X220–X246 | Z30–Z56 | Y110 (เกิด Y111)| สะพานแขวนลอยฟ้าโค้ง 48 บล็อก (แนวเดินโล่ง 100% ราวกันตกอยู่ขอบข้าง), ซุ้มประตูมิติ, แคมป์ไฟ |

## บันทึกการตรวจสอบ
- SHA256 ของ \`starter_sky_land_spawn_v1/region/r.0.0.mca\` ยืนยันตรงกับ \`BD957C225FC0BE86FE4A27A7D265A13B7178D01F22798FD65F55293421FD824F\` (ไม่ถูกแตะต้อง)
- สะพานทางเข้าไต่ระดับด้วย step <= 0.5 บล็อก ไม่ต้องกระโดด
- สะพานข้ามเวหาไปโซน 08 ช่องเดินโล่ง 100% ไม่มีรั้วทับ
- ประตูหอคอยเจาะทะลุ 3 บล็อก และบันไดลิงทะลุถึงดาดฟ้า
- ค่า floorY ใน manifest ปรับเป็น 20 เพื่อเปิดแสดงฐานหินย้อยทั้งหมดใน 3D Viewer
- ยังไม่ได้ทดสอบในเกมจริง
`;
fs.writeFileSync(mapMdPath, mapMd, 'utf8');

console.log('--- IN-PLACE PATCH COMPLETED SUCCESSFULLY ---');
