// Local, deterministic construction of "The Winged Guardian" Fountain in Zone 03.
// Never generates a world; patches the existing r.0.0.mca region in place with hash guards.
'use strict';
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { createLibrary } = require('./viewer/map-library.cjs'), { patch } = require('./smooth_active_land.cjs');

const ROOT = path.resolve(__dirname, '../..');
const MAP = path.join(ROOT, 'maps/starter_sky_land_spawn_v1');
const STAGE = path.join(ROOT, 'tmp/winged-guardian-fountain');
const REPORT = path.join(MAP, 'winged-guardian-fountain-report.json');

const key = d => d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort());

function buildWingedGuardianFountain() {
  const library = createLibrary(path.dirname(MAP));
  const m = library.load(path.basename(MAP));
  if (m.size !== 384 || m.origin.some(v => v !== 0)) {
    throw Error('Expected original full city dimensions (384x384, origin [0,0])');
  }

  const S = m.size;
  const b = Buffer.from(m.blocks, 'base64');
  const old = new Uint16Array(b.length / 2);
  for (let i = 0; i < old.length; i++) old[i] = b.readUInt16LE(i * 2);
  const data = old.slice();

  const at = (x, y, z) => (y - m.low) * S * S + z * S + x;
  const get = (x, y, z) => m.definitions[data[at(x, y, z)]];
  const original = (x, y, z) => m.definitions[old[at(x, y, z)]];

  const ids = new Map(m.definitions.map((d, i) => [key(d), i]));
  const planned = new Set();
  const counts = {};

  function put(x, y, z, n, p) {
    if (!Number.isInteger(x + y + z)) throw Error('Non-integer coords ' + [x, y, z]);
    // Safety boundaries: strictly within Zone 03 plaza X: 151..169, Z: 206..224, Y: 102..115
    if (x < 151 || x > 169 || z < 206 || z > 224 || y < 102 || y > 115 || y < m.low || y >= m.high) {
      throw Error('Outside fountain edit bounds: ' + [x, y, z]);
    }

    const d = typeof n === 'object' ? n : { Name: 'minecraft:' + n, ...(p ? { Properties: p } : {}) };
    const k = key(d);
    if (!ids.has(k)) {
      ids.set(k, m.definitions.length);
      m.definitions.push(d);
    }
    const idx = at(x, y, z);
    data[idx] = ids.get(k);
    planned.add(idx);
    counts[d.Name] = (counts[d.Name] || 0) + 1;
  }

  const stairs = (f, half = 'bottom', shape = 'straight') => ({ facing: f, half, shape, waterlogged: 'false' });
  const wall = (up = 'true') => ({ up, waterlogged: 'false', east: 'none', north: 'none', south: 'none', west: 'none' });
  const slab = (type = 'bottom') => ({ type, waterlogged: 'false' });

  // Center of the fountain
  const CX = 160, CZ = 215;

  // -------------------------------------------------------------
  // 1. CLEAR OLD FOUNTAIN VOLUME
  // -------------------------------------------------------------
  // Reset any non-air blocks above Y=103 in the work zone to air
  for (let x = 153; x <= 167; x++) {
    for (let z = 208; z <= 222; z++) {
      for (let y = 104; y <= 113; y++) {
        put(x, y, z, 'air');
      }
    }
  }

  // -------------------------------------------------------------
  // 2. BASIN SHAPE & POOL FLOOR (Y = 103)
  // -------------------------------------------------------------
  // Define 13x13 octagonal / cruciform basin layout:
  // dx = x - CX, dz = z - CZ
  // Basin rim outer boundary condition:
  // |dx| <= 6 and |dz| <= 6, with corners clipped (|dx| + |dz| <= 8)
  function isBasin(x, z) {
    const dx = Math.abs(x - CX);
    const dz = Math.abs(z - CZ);
    return dx <= 6 && dz <= 6 && (dx + dz <= 8);
  }

  function isRim(x, z) {
    if (!isBasin(x, z)) return false;
    const dx = Math.abs(x - CX);
    const dz = Math.abs(z - CZ);
    return dx === 6 || dz === 6 || (dx + dz === 8);
  }

  function isPedestalBase(x, z) {
    return Math.abs(x - CX) <= 2 && Math.abs(z - CZ) <= 2;
  }

  // Lay the pool floor and under-structure at Y = 103
  for (let x = 154; x <= 166; x++) {
    for (let z = 209; z <= 221; z++) {
      if (!isBasin(x, z)) continue;

      const dx = Math.abs(x - CX);
      const dz = Math.abs(z - CZ);

      if (isRim(x, z)) {
        // Under the rim: solid deepslate foundation
        put(x, 103, z, 'deepslate_tiles');
      } else if (isPedestalBase(x, z)) {
        // Pedestal foundation
        put(x, 103, z, 'chiseled_deepslate');
      } else {
        // Basin floor pattern
        // Radial crying obsidian lines + corner glowing lanterns
        const isCornerLight = (dx === 3 && dz === 3) || (dx === 2 && dz === 4) || (dx === 4 && dz === 2);
        if (isCornerLight) {
          put(x, 103, z, 'sea_lantern');
        } else if (dx === 0 || dz === 0 || dx === dz) {
          put(x, 103, z, 'crying_obsidian');
        } else {
          put(x, 103, z, 'deepslate_tiles');
        }
      }
    }
  }

  // -------------------------------------------------------------
  // 3. BASIN RIM & WATER (Y = 104)
  // -------------------------------------------------------------
  for (let x = 154; x <= 166; x++) {
    for (let z = 209; z <= 221; z++) {
      if (!isBasin(x, z)) continue;

      const dx = x - CX;
      const dz = z - CZ;

      if (isRim(x, z)) {
        // Basin rim profile with outward-facing deepslate tile stairs
        let stairFacing = 'north';
        if (dz === 6) stairFacing = 'south';
        else if (dz === -6) stairFacing = 'north';
        else if (dx === 6) stairFacing = 'east';
        else if (dx === -6) stairFacing = 'west';
        else if (dx > 0 && dz > 0) stairFacing = 'south';
        else if (dx > 0 && dz < 0) stairFacing = 'east';
        else if (dx < 0 && dz > 0) stairFacing = 'west';
        else if (dx < 0 && dz < 0) stairFacing = 'north';

        // Corner plinths on the rim
        const isRimCorner = (Math.abs(dx) === 6 && Math.abs(dz) === 2) ||
                            (Math.abs(dz) === 6 && Math.abs(dx) === 2) ||
                            (Math.abs(dx) === 4 && Math.abs(dz) === 4);
        if (isRimCorner) {
          put(x, 104, z, 'chiseled_deepslate');
        } else {
          put(x, 104, z, 'deepslate_tile_stairs', stairs(stairFacing));
        }
      } else if (isPedestalBase(x, z)) {
        // Pedestal base steps
        const adx = Math.abs(dx);
        const adz = Math.abs(dz);
        if (adx === 2 || adz === 2) {
          let stepFacing = 'north';
          if (dz === 2) stepFacing = 'south';
          else if (dz === -2) stepFacing = 'north';
          else if (dx === 2) stepFacing = 'east';
          else if (dx === -2) stepFacing = 'west';
          put(x, 104, z, 'deepslate_tile_stairs', stairs(stepFacing));
        } else {
          put(x, 104, z, 'chiseled_deepslate');
        }
      } else {
        // Water pool
        put(x, 104, z, 'water', { level: '0' });
      }
    }
  }

  // -------------------------------------------------------------
  // 4. CENTRAL PEDESTAL (Y = 105..106)
  // -------------------------------------------------------------
  // Pedestal shaft at Y = 105 (3x3 core)
  for (let dx = -1; dx <= 1; dx++) {
    for (let dz = -1; dz <= 1; dz++) {
      const px = CX + dx, pz = CZ + dz;
      if (dx === 0 && dz === 0) {
        put(px, 105, pz, 'crying_obsidian');
      } else if (dx === 0 || dz === 0) {
        put(px, 105, pz, 'chiseled_deepslate');
      } else {
        put(px, 105, pz, 'deepslate_tile_wall', wall());
      }
    }
  }

  // Pedestal capital at Y = 106 (3x3 flared top)
  for (let dx = -1; dx <= 1; dx++) {
    for (let dz = -1; dz <= 1; dz++) {
      const px = CX + dx, pz = CZ + dz;
      if (dx === 0 && dz === 0) {
        put(px, 106, pz, 'smooth_quartz');
      } else if (dz === 1) {
        put(px, 106, pz, 'deepslate_tile_stairs', stairs('south', 'top'));
      } else if (dz === -1) {
        put(px, 106, pz, 'deepslate_tile_stairs', stairs('north', 'top'));
      } else if (dx === 1) {
        put(px, 106, pz, 'deepslate_tile_stairs', stairs('east', 'top'));
      } else if (dx === -1) {
        put(px, 106, pz, 'deepslate_tile_stairs', stairs('west', 'top'));
      }
    }
  }

  // -------------------------------------------------------------
  // 5. THE WINGED GUARDIAN STATUE (Y = 107..113)
  // -------------------------------------------------------------
  // Facing South (+Z direction, welcoming players entering from Spawn)

  // Robe Plinth & Lower Garments (Y = 107)
  put(CX, 107, CZ, 'smooth_quartz');
  put(CX, 107, CZ - 1, 'smooth_quartz_stairs', stairs('north'));
  put(CX, 107, CZ + 1, 'smooth_quartz_stairs', stairs('south'));
  put(CX - 1, 107, CZ, 'smooth_quartz_stairs', stairs('west'));
  put(CX + 1, 107, CZ, 'smooth_quartz_stairs', stairs('east'));

  // Lower Torso & Waist (Y = 108)
  put(CX, 108, CZ, 'smooth_quartz');
  put(CX, 108, CZ - 1, 'smooth_quartz'); // Back drapery
  put(CX, 108, CZ + 1, 'smooth_quartz_stairs', stairs('south')); // Front apron
  put(CX - 1, 108, CZ, 'smooth_quartz_stairs', stairs('west'));
  put(CX + 1, 108, CZ, 'smooth_quartz_stairs', stairs('east'));

  // Upper Torso & Chest Armor (Y = 109)
  put(CX, 109, CZ, 'polished_diorite'); // Breastplate core
  put(CX, 109, CZ - 1, 'smooth_quartz'); // Wing root anchor
  put(CX - 1, 109, CZ, 'smooth_quartz_stairs', stairs('west')); // Left shoulder
  put(CX + 1, 109, CZ, 'smooth_quartz_stairs', stairs('east')); // Right shoulder

  // Sacred Water Urn & Arms (Y = 109..110)
  // Forearms extending forward holding the urn
  put(CX - 1, 109, CZ + 1, 'smooth_quartz_stairs', stairs('east'));
  put(CX + 1, 109, CZ + 1, 'smooth_quartz_stairs', stairs('west'));
  // The Sacred Urn at (CX, Y=109, CZ+1)
  put(CX, 109, CZ + 1, 'chiseled_quartz_block');
  put(CX, 110, CZ + 1, 'quartz_stairs', stairs('south', 'top'));

  // Water spilling from the Urn
  put(CX, 110, CZ + 2, 'water', { level: '0' });
  put(CX, 109, CZ + 2, 'water', { level: '0' });
  put(CX, 108, CZ + 2, 'water', { level: '0' });
  put(CX, 107, CZ + 2, 'water', { level: '0' });
  put(CX, 106, CZ + 2, 'water', { level: '0' });
  put(CX, 105, CZ + 2, 'water', { level: '0' });

  // Head & Halo (Y = 110..111)
  put(CX, 110, CZ, 'smooth_quartz'); // Head
  put(CX, 111, CZ, 'smooth_quartz_slab', slab('bottom')); // Halo circlet
  put(CX, 111, CZ - 1, 'end_rod', { facing: 'up' }); // Glowing celestial aura

  // Celestial Wings (Y = 109..113)
  // Left Wing (West, -X):
  put(CX - 1, 110, CZ - 1, 'smooth_quartz');
  put(CX - 2, 110, CZ - 1, 'smooth_quartz_stairs', stairs('west'));
  put(CX - 2, 111, CZ - 1, 'smooth_quartz');
  put(CX - 3, 111, CZ - 1, 'smooth_quartz_stairs', stairs('west'));
  put(CX - 3, 112, CZ - 1, 'smooth_quartz');
  put(CX - 4, 112, CZ - 1, 'smooth_quartz_stairs', stairs('west'));
  put(CX - 4, 113, CZ - 1, 'smooth_quartz_slab', slab('bottom'));
  put(CX - 4, 114, CZ - 1, 'end_rod', { facing: 'up' }); // Wingtip starlight

  // Right Wing (East, +X):
  put(CX + 1, 110, CZ - 1, 'smooth_quartz');
  put(CX + 2, 110, CZ - 1, 'smooth_quartz_stairs', stairs('east'));
  put(CX + 2, 111, CZ - 1, 'smooth_quartz');
  put(CX + 3, 111, CZ - 1, 'smooth_quartz_stairs', stairs('east'));
  put(CX + 3, 112, CZ - 1, 'smooth_quartz');
  put(CX + 4, 112, CZ - 1, 'smooth_quartz_stairs', stairs('east'));
  put(CX + 4, 113, CZ - 1, 'smooth_quartz_slab', slab('bottom'));
  put(CX + 4, 114, CZ - 1, 'end_rod', { facing: 'up' }); // Wingtip starlight

  // -------------------------------------------------------------
  // 6. PLAZA PAVING BLEND & SURROUNDING BENCHES (Y = 103..105)
  // -------------------------------------------------------------
  // Ensure the road pavement smoothly surrounds the 13x13 basin
  for (let x = 153; x <= 167; x++) {
    for (let z = 208; z <= 222; z++) {
      if (isBasin(x, z)) continue; // Handled by fountain
      // Road paving pattern around the basin:
      const dx = Math.abs(x - CX);
      const dz = Math.abs(z - CZ);
      if (dx <= 1) {
        // Center road axis
        put(x, 103, z, 'smooth_stone');
      } else if (dx === 2 || dz === 2) {
        put(x, 103, z, 'polished_andesite');
      } else {
        put(x, 103, z, 'stone_bricks');
      }
    }
  }

  // Stone Benches around the plaza
  // North bench (facing South): X: 159..161, Z = 207
  for (let x = 159; x <= 161; x++) {
    put(x, 104, 207, 'deepslate_tile_stairs', stairs('south'));
  }
  // South bench (facing North): X: 159..161, Z = 223
  for (let x = 159; x <= 161; x++) {
    put(x, 104, 223, 'deepslate_tile_stairs', stairs('north'));
  }
  // West bench (facing East): X = 152, Z: 214..216
  for (let z = 214; z <= 216; z++) {
    put(152, 104, z, 'deepslate_tile_stairs', stairs('east'));
  }
  // East bench (facing West): X = 168, Z: 214..216
  for (let z = 214; z <= 216; z++) {
    put(168, 104, z, 'deepslate_tile_stairs', stairs('west'));
  }

  // Corner Planters with Flowers at Diagonal Coordinates
  const planters = [
    [153, 208, 'blue_orchid'],
    [167, 208, 'lily_of_the_valley'],
    [153, 222, 'lily_of_the_valley'],
    [167, 222, 'blue_orchid']
  ];
  for (const [px, pz, flower] of planters) {
    put(px, 104, pz, 'chiseled_deepslate');
    put(px, 105, pz, flower);
  }

  // -------------------------------------------------------------
  // 7. VERIFICATION & STAGING / APPLY
  // -------------------------------------------------------------
  const regionFile = path.join(MAP, 'region/r.0.0.mca');
  const before = fs.readFileSync(regionFile);
  const sourceHash = crypto.createHash('sha256').update(before).digest('hex');

  let changedBlocks = 0;
  for (let i = 0; i < data.length; i++) {
    if (data[i] !== old[i]) changedBlocks++;
  }

  const fountainBounds = [152, 207, 168, 223];

  const planInfo = {
    zone: '03-streets-square',
    subfeature: 'winged-guardian-fountain',
    name: 'The Winged Guardian Fountain',
    status: 'staged',
    sourceHash,
    changedBlocks,
    center: [CX, 104, CZ],
    fountainBounds,
    counts
  };

  fs.mkdirSync(STAGE, { recursive: true });
  fs.writeFileSync(path.join(STAGE, 'plan.json'), JSON.stringify(planInfo, null, 2));

  if (process.argv.includes('--apply')) {
    const backupDir = path.join(ROOT, 'archive/map-edit-backups/before-fountain-winged-guardian/starter_sky_land_spawn_v1');
    if (!fs.existsSync(backupDir)) {
      fs.mkdirSync(path.dirname(backupDir), { recursive: true });
      fs.cpSync(MAP, backupDir, { recursive: true });
    }

    // Patch region file
    const patchResult = patch(m, data, old, before);
    const currentHash = crypto.createHash('sha256').update(fs.readFileSync(regionFile)).digest('hex');
    if (currentHash !== sourceHash) {
      throw Error('Source region changed during staging; aborting to prevent overwrite');
    }

    fs.writeFileSync(regionFile + '.pending', patchResult.bytes);
    fs.renameSync(regionFile + '.pending', regionFile);

    // Verify saved data matches planned voxels
    const verifyLib = createLibrary(path.dirname(MAP));
    const saved = verifyLib.load(path.basename(MAP));
    const sb = Buffer.from(saved.blocks, 'base64');
    for (let i = 0; i < data.length; i++) {
      const savedDef = saved.definitions[sb.readUInt16LE(i * 2)];
      const plannedDef = m.definitions[data[i]];
      if (key(savedDef) !== key(plannedDef)) {
        throw Error('Saved block mismatch at voxel index ' + i);
      }
    }

    // Update zones.json
    const zf = path.join(MAP, 'zones.json');
    const zones = JSON.parse(fs.readFileSync(zf, 'utf8'));
    const z3 = zones.find(z => z.id === '03-streets-square');
    if (z3) {
      z3.fountain = {
        name: 'The Winged Guardian',
        style: 'celestial_angelic_monument',
        center: [CX, 104, CZ],
        bounds: [154, 209, 166, 221],
        wingspan: 9,
        statueHeight: 114,
        basinDiameter: 13,
        updated: new Date().toISOString()
      };
      fs.writeFileSync(zf, JSON.stringify(zones, null, 2));
    }

    planInfo.status = 'applied';
    planInfo.changedChunks = patchResult.changed;
    planInfo.backup = path.relative(ROOT, backupDir);
    planInfo.savedBlockComparison = 'All voxels match planned result using Name and sorted Properties';
    fs.writeFileSync(REPORT, JSON.stringify(planInfo, null, 2));
  }

  console.log(JSON.stringify({
    status: planInfo.status,
    changedBlocks,
    changedChunks: planInfo.changedChunks || 0,
    counts,
    report: process.argv.includes('--apply') ? REPORT : path.join(STAGE, 'plan.json')
  }, null, 2));
}

if (require.main === module) {
  buildWingedGuardianFountain();
}
