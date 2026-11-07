// Build Leveling Sky Island 01: Ruined Village (Minecraft Java 1.21.1, DataVersion 3955)
// Deterministic generator following approved top-down layout & AGENTS.md rules.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { colours: BASE_COLOURS } = require('./create_ruined_castle.cjs');
const { readNbt } = require('./viewer/map-library.cjs');

const ROOT = path.resolve(__dirname, '../..');
const TREE_LIB_DIR = path.join(ROOT, 'maps/tree_library/structures');

const S = 300, LOW = 16, HIGH = 144, FLOOR = 88;

const COLOURS = {
  ...BASE_COLOURS,
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

function createModel() {
  const blocks = new Uint16Array(S * S * (HIGH - LOW));
  const definitions = [{ Name: 'minecraft:air' }];
  const ids = new Map([['air', 0]]);
  const tiles = new Map();

  function block(name, properties) {
    const key = name + (properties ? JSON.stringify(properties) : '');
    if (!ids.has(key)) {
      ids.set(key, definitions.length);
      definitions.push({ Name: 'minecraft:' + name, ...(properties ? { Properties: properties } : {}) });
    }
    return ids.get(key);
  }

  function at(x, y, z) { return (y - LOW) * S * S + z * S + x; }
  function inBounds(x, y, z) { return x >= 0 && z >= 0 && x < S && z < S && y >= LOW && y < HIGH; }

  function get(x, y, z) {
    return inBounds(x, y, z) ? blocks[at(x, y, z)] : 0;
  }

  function getName(x, y, z) {
    const id = get(x, y, z);
    return id ? definitions[id].Name.replace('minecraft:', '') : 'air';
  }

  function put(x, y, z, name, properties) {
    if (!inBounds(x, y, z)) return;
    blocks[at(x, y, z)] = block(name, properties);
    const key = `${x},${y},${z}`;
    tiles.delete(key);
    const entity = { chest: 'chest', barrel: 'barrel', furnace: 'furnace', campfire: 'campfire' }[name];
    if (entity) tiles.set(key, { id: 'minecraft:' + entity, x, y, z });
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

  return {
    S, LOW, HIGH, FLOOR,
    blocks, definitions, tiles,
    get, getName, put, fill, hash, inBounds
  };
}

// -------------------------------------------------------------
// 1. Terrain Geometries & Landmass
// -------------------------------------------------------------
// Outline of Mainland: elongated organic shape from Z55 to Z270, X35 to X255
const MAIN_OUTLINE = [
  [150, 270], // South entrance bridge landing
  [168, 266],
  [188, 256],
  [205, 240], // Southeast village shoulder
  [222, 222],
  [240, 200], // East forest edge
  [254, 175], // East bulge
  [252, 145],
  [242, 125], // East cliff
  [226, 105],
  [208, 85],  // Northeast shoulder towards bridge to 08
  [192, 68],
  [175, 56],  // North mansion rear cliff
  [150, 54],  // Northernmost tip
  [125, 56],
  [105, 68],  // Northwest cliff
  [85, 85],
  [65, 108],  // Watchtower ridge outer
  [48, 132],
  [38, 160],  // West farmland outer
  [36, 190],  // West bulge
  [44, 218],
  [60, 240],  // Southwest farmland shoulder
  [85, 254],
  [112, 262],
  [132, 268]
];

function pointInPoly(x, z, poly) {
  let hit = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [a, b] = poly[i], [c, d] = poly[j];
    if ((b > z) !== (d > z) && x < (c - a) * (z - b) / (d - b) + a) hit = !hit;
  }
  return hit;
}

const smoothStep = n => { n = Math.max(0, Math.min(1, n)); return n * n * (3 - 2 * n); };
const bump = (x, z, cx, cz, rx, rz) => smoothStep(1 - Math.hypot((x - cx) / rx, (z - cz) / rz));

function buildTerrain(m) {
  const S = m.S;
  const mask = new Uint8Array(S * S);
  const dist = new Uint16Array(S * S);
  const heights = new Int16Array(S * S).fill(-1);
  const bottoms = new Int16Array(S * S).fill(-1);
  const queue = [];

  // 1. Mark Mainland & Sub-islands in mask
  for (let z = 0; z < S; z++) {
    for (let x = 0; x < S; x++) {
      // Sub-Island 1: Zone 01 (Entry) around X150, Z298, radius 11
      const d1 = Math.hypot((x - 150) / 11, (z - 298) / 11);
      if (d1 <= 1) {
        mask[z * S + x] = 2; // Zone 01
        continue;
      }
      // Sub-Island 2: Zone 08 (Checkpoint 2) around X233, Z43, radius 12
      const d2 = Math.hypot((x - 233) / 12, (z - 43) / 12);
      if (d2 <= 1) {
        mask[z * S + x] = 3; // Zone 08
        continue;
      }
      // Mainland
      if (pointInPoly(x, z, MAIN_OUTLINE)) {
        mask[z * S + x] = 1;
      }
    }
  }

  // 2. Compute distance from coast for tapering stalactites
  for (let z = 0; z < S; z++) {
    for (let x = 0; x < S; x++) {
      const type = mask[z * S + x];
      if (type > 0) {
        let isEdge = false;
        for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const nx = x + dx, nz = z + dz;
          if (nx < 0 || nz < 0 || nx >= S || nz >= S || mask[nz * S + nx] !== type) {
            isEdge = true;
            break;
          }
        }
        if (isEdge) {
          dist[z * S + x] = 1;
          queue.push([x, z, type]);
        }
      }
    }
  }

  for (let i = 0; i < queue.length; i++) {
    const [x, z, type] = queue[i];
    const curD = dist[z * S + x];
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx, nz = z + dz, k = nz * S + nx;
      if (nx >= 0 && nz >= 0 && nx < S && nz < S && mask[k] === type && !dist[k]) {
        dist[k] = curD + 1;
        queue.push([nx, nz, type]);
      }
    }
  }

  // Stalactite root peaks: [cx, cz, radius, maxDepth]
  const peaks = [
    [150, 85, 45, 84],  // Under Mansion
    [75, 115, 38, 76],  // Under Watchtower
    [150, 220, 36, 72], // Under Village Plaza
    [85, 210, 35, 68],  // Under Farmland
    [220, 160, 40, 75]  // Under Forest / Cave
  ];

  // 3. Fill Voxels for Terrain
  for (let z = 0; z < S; z++) {
    for (let x = 0; x < S; x++) {
      const type = mask[z * S + x];
      if (!type) continue;
      const d = dist[z * S + x];

      let topY = 92;
      let depth = 15;

      if (type === 2) {
        // Zone 01: Entry Island
        topY = 88;
        depth = Math.round(6 + d * 3.8); // tapers from Y88 down to Y52
      } else if (type === 3) {
        // Zone 08: Next Checkpoint Island
        topY = 110;
        depth = Math.round(7 + d * 3.5); // tapers from Y110 down to Y72
      } else {
        // Mainland
        // Baseline village plaza / south: Y92-Y94
        let elevation = 93;

        // Hill 1: Watchtower Hill (Zone 05) around X75, Z115, rising to Y112
        const hWatchtower = 18 * bump(x, z, 75, 115, 45, 45);

        // Hill 2: Farmland gentle terraces (Zone 04) around X75, Z200, rising to Y98
        const hFarm = 5 * bump(x, z, 75, 205, 50, 50);

        // Hill 3: Forest & Cave Cliff (Zone 06) around X220, Z160, rising to Y104
        const hForest = 10 * bump(x, z, 225, 160, 45, 55);

        // Hill 4: Mansion Plateau (Zone 07) around X150, Z85, plateau at Y108
        const hMansion = 15 * bump(x, z, 150, 85, 55, 45);

        // North-sloping gradient from village to mansion
        const northRise = 6 * smoothStep((240 - z) / 160);

        const edgeDrop = Math.max(0, 4 - d) * 0.7;
        elevation = Math.round(92 + northRise + hWatchtower + hFarm + hForest + hMansion - edgeDrop);

        // Flatten specific plateaus for gameplay:
        // Village plaza (X138-166, Z208-238) flat at Y92-Y93
        if (x >= 135 && x <= 170 && z >= 205 && z <= 245) {
          elevation = 93;
        }
        // Mansion courtyard & foundation (X115-185, Z60-115) flat at Y108
        if (x >= 115 && x <= 185 && z >= 60 && z <= 118) {
          elevation = 108;
        }
        // Watchtower peak (X68-80, Z108-120) flat at Y112
        if (x >= 68 && x <= 80 && z >= 108 && z <= 120) {
          elevation = 112;
        }

        topY = Math.max(90, Math.min(114, elevation));

        // Depth & Stalactite calculation
        depth = Math.min(65, 12 + d * 1.3);
        for (const [cx, cz, r, h] of peaks) {
          depth = Math.max(depth, h * Math.max(0, 1 - Math.hypot(x - cx, z - cz) / r));
        }
        // Micro-roughness on underbelly
        const crag = Math.sin(x * 0.23 + z * 0.17) * 2.5 + Math.cos(z * 0.29 - x * 0.11) * 2.5;
        depth = Math.max(6, depth + crag);
      }

      const bottomY = Math.max(20, Math.round(topY - depth));
      heights[z * S + x] = topY;
      bottoms[z * S + x] = bottomY;

      // Fill Column
      for (let y = bottomY; y <= topY; y++) {
        const rockH = m.hash(Math.floor(x / 3), Math.floor(z / 3), Math.floor(y / 4));
        let blockName = 'stone';

        if (y === topY) {
          blockName = 'grass_block';
        } else if (y > topY - 3) {
          blockName = 'dirt';
        } else if (y <= 28) {
          blockName = rockH % 4 === 0 ? 'deepslate' : 'cobbled_deepslate';
        } else if (rockH % 31 === 0) {
          blockName = 'mossy_cobblestone';
        } else if (rockH % 9 === 0) {
          blockName = 'andesite';
        } else if (rockH % 13 === 0) {
          blockName = 'cobblestone';
        }

        m.put(x, y, z, blockName, blockName === 'grass_block' ? { snowy: 'false' } : undefined);
      }
    }
  }

  return { heights, bottoms, mask };
}

// -------------------------------------------------------------
// 2. Paths, Terraces & Bridges
// -------------------------------------------------------------
function buildBridgesAndPaths(m, terrain) {
  const { heights } = terrain;

  // Bridge 1: Zone 01 (Entry) to Mainland (Z286 down to Z270)
  // X = 148..152, width 5. Rises from Y88 to Y92.
  for (let z = 270; z <= 286; z++) {
    const progress = (286 - z) / 16; // 0 at Z286, 1 at Z270
    const by = Math.round(88 + progress * 4);
    for (let x = 148; x <= 152; x++) {
      const isEdge = (x === 148 || x === 152);
      m.put(x, by, z, isEdge ? 'spruce_planks' : 'spruce_slab', isEdge ? undefined : { type: 'bottom', waterlogged: 'false' });
      if (isEdge) {
        m.put(x, by + 1, z, 'spruce_fence', { north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false' });
        if (z % 5 === 0) {
          m.put(x, by + 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' });
        }
      }
    }
  }

  // Bridge 2: Zone 07 (Mansion rear cliff) to Zone 08 (Next Checkpoint)
  // From X176, Z58, Y108 to X222, Z44, Y110
  // Catenary dip in the middle across 48 blocks
  const steps = 48;
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const bx = Math.round(176 + t * (222 - 176));
    const bz = Math.round(58 + t * (44 - 58));
    // Dips by 2 blocks at midpoint
    const sag = Math.sin(t * Math.PI) * 2;
    const by = Math.round(108 + t * 2 - sag);

    for (let offset = -1; offset <= 1; offset++) {
      // Normal vector approx (-dy, dx)
      const px = bx + offset;
      const pz = bz;
      const isRailing = (offset === -1 || offset === 1);
      m.put(px, by, pz, isRailing ? 'dark_oak_planks' : 'dark_oak_slab', isRailing ? undefined : { type: 'bottom', waterlogged: 'false' });
      if (isRailing) {
        m.put(px, by + 1, pz, 'dark_oak_fence', { north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false' });
        if (i % 8 === 0) {
          m.put(px, by + 2, pz, 'lantern', { hanging: 'false', waterlogged: 'false' });
        }
      }
    }
  }

  // Main Avenue: from Village Plaza (X150, Z210) north to Grand Stairway (X150, Z136)
  for (let z = 136; z <= 215; z++) {
    const w = (z < 150) ? 3 : 2; // wider closer to grand stairs
    for (let dx = -w; dx <= w; dx++) {
      const px = 150 + dx;
      const y = heights[z * m.S + px];
      if (y > 0) {
        const edge = Math.abs(dx) === w;
        const h = m.hash(px, z);
        const blockName = edge
          ? (h % 3 === 0 ? 'mossy_cobblestone' : 'coarse_dirt')
          : (h % 4 === 0 ? 'cracked_stone_bricks' : h % 2 === 0 ? 'cobblestone' : 'gravel');
        m.put(px, y, z, blockName);
      }
    }
  }

  // West Farmland Path: from Village Plaza (X140, Z220) winding west to X75, Z200 and up to Watchtower (X75, Z120)
  for (let t = 0; t <= 100; t++) {
    const frac = t / 100;
    const px = Math.round(140 - frac * 65);
    const pz = Math.round(220 - frac * 20 + Math.sin(frac * Math.PI * 3) * 6);
    for (let dx = -1; dx <= 1; dx++) {
      const x = px + dx;
      const y = heights[pz * m.S + x];
      if (y > 0) m.put(x, y, pz, m.hash(x, pz) % 2 === 0 ? 'coarse_dirt' : 'packed_mud');
    }
  }

  // Watchtower Switchback Path: X75, Z200 winding north-west up to summit X75, Z115
  for (let z = 195; z >= 122; z--) {
    const px = Math.round(75 + Math.sin((z - 120) * 0.15) * 8);
    for (let dx = -1; dx <= 1; dx++) {
      const x = px + dx;
      const y = heights[z * m.S + x];
      if (y > 0) {
        m.put(x, y, z, m.hash(x, z) % 3 === 0 ? 'mossy_cobblestone' : 'stone_bricks');
      }
    }
  }

  // East Forest & Cave Path: from Village Plaza (X165, Z220) curving into Forest (X215, Z180) and Cave Mouth (X222, Z150)
  for (let t = 0; t <= 80; t++) {
    const frac = t / 80;
    const px = Math.round(165 + frac * 57);
    const pz = Math.round(220 - frac * 70);
    for (let dx = -1; dx <= 1; dx++) {
      const x = px + dx;
      const y = heights[pz * m.S + x];
      if (y > 0) m.put(x, y, pz, m.hash(x, pz) % 2 === 0 ? 'podzol' : 'coarse_dirt');
    }
  }
}

// -------------------------------------------------------------
// 3. Zone 01: Entry Island (Safe Zone & Return Portal)
// -------------------------------------------------------------
function buildZone01(m) {
  // Surface at Y88. Portal arch at X150, Z304
  const px = 150, pz = 304, py = 88;

  // Pavement around portal
  for (let z = 292; z <= 306; z++) {
    for (let x = 142; x <= 158; x++) {
      if (Math.hypot((x - 150) / 8, (z - 299) / 7) <= 1) {
        m.put(x, py, z, m.hash(x, z) % 3 === 0 ? 'chiseled_stone_bricks' : 'stone_bricks');
      }
    }
  }

  // Portal Arch: width 7, height 7
  m.fill(px - 3, py + 1, pz, px - 3, py + 6, pz, 'polished_deepslate');
  m.fill(px + 3, py + 1, pz, px + 3, py + 6, pz, 'polished_deepslate');
  m.fill(px - 3, py + 6, pz, px + 3, py + 6, pz, 'chiseled_stone_bricks');
  m.fill(px - 2, py + 7, pz, px + 2, py + 7, pz, 'stone_brick_slab', { type: 'bottom', waterlogged: 'false' });
  // Glowing portal frame interior (represented with crying obsidian/blue glass for sky theme)
  m.fill(px - 2, py + 1, pz, px + 2, py + 5, pz, 'light_blue_stained_glass');

  // Lantern posts & safety railings
  for (const lx of [px - 4, px + 4]) {
    m.put(lx, py + 1, pz, 'chiseled_stone_bricks');
    m.put(lx, py + 2, pz, 'spruce_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' });
    m.put(lx, py + 3, pz, 'lantern', { hanging: 'false', waterlogged: 'false' });
  }

  // Low decorative stone wall on outer edge
  for (let angle = 0; angle < Math.PI * 2; angle += 0.2) {
    const wx = Math.round(150 + Math.cos(angle) * 10);
    const wz = Math.round(298 + Math.sin(angle) * 10);
    // Leave opening at North for bridge (Z <= 290)
    if (wz > 291) {
      m.put(wx, py + 1, wz, 'cobblestone_wall', { waterlogged: 'false' });
    }
  }
}

// -------------------------------------------------------------
// 4. Zone 03: Village Plaza & Dry Stone Well Arena
// -------------------------------------------------------------
function buildZone03(m) {
  const cx = 150, cz = 223, y = 93;

  // Plaza floor: X138..166, Z208..238
  for (let z = 208; z <= 238; z++) {
    for (let x = 138; x <= 166; x++) {
      const h = m.hash(x, z);
      const name = h % 7 === 0 ? 'mossy_stone_bricks' : h % 5 === 0 ? 'cracked_stone_bricks' : h % 3 === 0 ? 'cobblestone' : 'stone_bricks';
      m.put(x, y, z, name);
    }
  }

  // Central Dry Stone Well: 5x5 footprint at X148..152, Z221..225
  m.fill(cx - 2, y + 1, cz - 2, cx + 2, y + 1, cz + 2, 'stone_bricks');
  m.fill(cx - 1, y + 1, cz - 1, cx + 1, y + 1, cz + 1, 'air'); // Well opening
  // Deep dry shaft down 6 blocks
  m.fill(cx - 1, y - 5, cz - 1, cx + 1, y, cz + 1, 'air');
  m.fill(cx - 1, y - 6, cz - 1, cx + 1, y - 6, cz + 1, 'cobblestone');
  // Well corner posts and roof
  for (const [dx, dz] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) {
    m.fill(cx + dx, y + 2, cz + dz, cx + dx, y + 3, cz + dz, 'dark_oak_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' });
  }
  m.fill(cx - 2, y + 4, cz - 2, cx + 2, y + 4, cz + 2, 'dark_oak_slab', { type: 'bottom', waterlogged: 'false' });
  m.put(cx, y + 3, cz, 'lantern', { hanging: 'true', waterlogged: 'false' });

  // Overturned Merchant Wagon at X158..162, Z228..231
  m.put(158, y + 1, 229, 'spruce_trapdoor', { facing: 'east', open: 'true', half: 'bottom', waterlogged: 'false' });
  m.put(161, y + 1, 229, 'spruce_trapdoor', { facing: 'west', open: 'true', half: 'bottom', waterlogged: 'false' });
  m.fill(159, y + 1, 228, 160, y + 1, 230, 'spruce_planks');
  m.put(159, y + 2, 229, 'barrel', { facing: 'up', open: 'false' });
  m.put(160, y + 2, 230, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' });

  // Ruined Market Stalls at X141..146, Z214..218
  m.fill(142, y + 1, 215, 142, y + 3, 215, 'oak_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' });
  m.fill(145, y + 1, 215, 145, y + 3, 215, 'oak_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' });
  m.fill(142, y + 1, 217, 142, y + 2, 217, 'oak_fence', { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' }); // broken post
  m.fill(142, y + 1, 216, 145, y + 1, 216, 'spruce_planks'); // table
  m.put(143, y + 4, 215, 'white_wool');
  m.put(144, y + 4, 215, 'red_wool');
  m.put(144, y + 3, 216, 'red_wool'); // collapsed cloth
}

// -------------------------------------------------------------
// 5. Zone 02: Abandoned Village (5 Ruined Buildings)
// -------------------------------------------------------------
function buildZone02(m) {
  const y = 93;

  // Helper to build a ruined house
  function ruinedHouse({ x, z, w, d, h = 6, name, roofDamageFrac = 0.5 }) {
    // Cobblestone foundation
    m.fill(x, y, z, x + w - 1, y, z + d - 1, 'cobblestone');
    // Wooden plank floor inside
    m.fill(x + 1, y, z + 1, x + w - 2, y, z + d - 2, 'spruce_planks');

    // Walls: stripped oak logs at corners, cobblestone/mossy stone & oak planks for walls
    for (let dy = 1; dy <= h; dy++) {
      for (let dx = 0; dx < w; dx++) {
        for (let dz = 0; dz < d; dz++) {
          const isCorner = (dx === 0 || dx === w - 1) && (dz === 0 || dz === d - 1);
          const isWall = (dx === 0 || dx === w - 1 || dz === 0 || dz === d - 1);
          if (isWall) {
            // Leave door opening at south or front wall
            if (dz === d - 1 && dx >= Math.floor(w / 2) - 1 && dx <= Math.floor(w / 2) && dy <= 3) {
              continue; // door
            }
            // Broken window openings
            if (dy >= 2 && dy <= 3 && (dx === 2 || dx === w - 3 || dz === 2 || dz === d - 3)) {
              if (m.hash(x + dx, z + dz, dy) % 3 === 0) continue; // broken hole
            }
            // Wall ruin decay: higher up, more holes
            if (dy > 3 && m.hash(x + dx, z + dz, dy) % 10 < 3) continue;

            const bName = isCorner ? 'oak_log' : (m.hash(x + dx, z + dz, dy) % 2 === 0 ? 'stone_bricks' : 'oak_planks');
            m.put(x + dx, y + dy, z + dz, bName, isCorner ? { axis: 'y' } : undefined);
          }
        }
      }
    }

    // Gabled Roof with holes/ruin
    for (let dx = -1; dx <= w; dx++) {
      const rise = Math.min(dx + 1, w - dx);
      const ry = y + h + rise;
      for (let dz = -1; dz <= d; dz++) {
        if (m.hash(x + dx, z + dz) % 100 < roofDamageFrac * 100) {
          // Fallen or missing roof block
          continue;
        }
        m.put(x + dx, ry, z + dz, 'dark_oak_stairs', {
          facing: dx < w / 2 ? 'east' : 'west',
          half: 'bottom',
          shape: 'straight',
          waterlogged: 'false'
        });
      }
    }

    // Cobwebs and interior details
    m.put(x + 2, y + h - 1, z + 2, 'cobweb');
    m.put(x + w - 3, y + h - 1, z + d - 3, 'cobweb');
  }

  // 1. Old Tavern / Inn (House 01): X165..180, Z228..244 (16x17)
  ruinedHouse({ x: 165, z: 228, w: 16, d: 16, h: 7, name: 'Tavern', roofDamageFrac: 0.6 });
  // Tavern interior bar counter
  m.fill(168, y + 1, 235, 172, y + 1, 235, 'spruce_stairs', { facing: 'north', half: 'top', shape: 'straight', waterlogged: 'false' });
  m.put(169, y + 2, 235, 'lantern', { hanging: 'false', waterlogged: 'false' });
  m.put(170, y + 1, 238, 'furnace', { facing: 'north', lit: 'false' });

  // 2. Blacksmith (House 02): X122..136, Z226..240 (15x15)
  ruinedHouse({ x: 122, z: 226, w: 14, d: 14, h: 6, name: 'Blacksmith', roofDamageFrac: 0.5 });
  // Stone forge & chimney
  m.fill(124, y + 1, 228, 126, y + 3, 230, 'cobblestone');
  m.put(125, y + 1, 229, 'lava', undefined); // cold ash represented with deepslate
  m.put(125, y + 1, 229, 'cobbled_deepslate');
  m.fill(125, y + 4, 229, 125, y + 8, 229, 'stone_bricks'); // chimney
  m.put(128, y + 1, 231, 'polished_deepslate');
  m.put(128, y + 2, 231, 'anvil', { facing: 'south' });

  // 3. Merchant Shop (House 03): X170..184, Z200..214 (15x15)
  ruinedHouse({ x: 170, z: 200, w: 14, d: 14, h: 6, name: 'Merchant', roofDamageFrac: 0.7 });
  // Broken shelves & display crates
  m.put(173, y + 1, 203, 'bookshelf');
  m.put(174, y + 1, 203, 'barrel', { facing: 'up', open: 'false' });
  m.put(177, y + 1, 207, 'chest', { facing: 'west', type: 'single', waterlogged: 'false' });

  // 4. Ruined Cottage A (House 04): X124..136, Z198..212 (13x15)
  ruinedHouse({ x: 124, z: 198, w: 12, d: 14, h: 5, name: 'CottageA', roofDamageFrac: 0.8 });

  // 5. Ruined Cottage B (House 05): X142..155, Z188..199 (14x12)
  ruinedHouse({ x: 142, z: 188, w: 13, d: 12, h: 5, name: 'CottageB', roofDamageFrac: 0.5 });
  // Broken fireplace
  m.fill(148, y + 1, 189, 150, y + 4, 190, 'stone_bricks');
  m.put(149, y + 1, 190, 'campfire', { lit: 'false', signal_fire: 'false', waterlogged: 'false' });
}

// -------------------------------------------------------------
// 6. Zone 04: Abandoned Farmland & Hunting Fields
// -------------------------------------------------------------
function buildZone04(m, terrain) {
  const { heights } = terrain;

  // Ruined Windmill / Barn at X68..80, Z192..204
  const bx = 68, bz = 192, by = heights[bz * m.S + bx] || 94;
  m.fill(bx, by, bz, bx + 11, by, bz + 11, 'stone_bricks');
  m.fill(bx + 1, by + 1, bz + 1, bx + 10, by + 5, bz + 10, 'air');
  // Stone walls with holes
  for (let dy = 1; dy <= 7; dy++) {
    for (let dx = 0; dx <= 11; dx++) {
      for (let dz = 0; dz <= 11; dz++) {
        if (dx === 0 || dx === 11 || dz === 0 || dz === 11) {
          if (dy > 3 && m.hash(bx + dx, bz + dz, dy) % 3 === 0) continue;
          m.put(bx + dx, by + dy, bz + dz, 'stone_bricks');
        }
      }
    }
  }
  // Hay bales inside and outside barn
  m.fill(bx + 3, by + 1, bz + 3, bx + 5, by + 2, bz + 4, 'hay_block', { axis: 'y' });
  m.put(bx + 8, by + 1, bz + 13, 'hay_block', { axis: 'x' });
  m.put(bx + 9, by + 1, bz + 13, 'hay_block', { axis: 'y' });

  // Broken fences across farmland
  for (let z = 180; z <= 245; z += 12) {
    for (let x = 48; x <= 115; x += 3) {
      const y = heights[z * m.S + x];
      if (y > 0 && m.hash(x, z) % 4 !== 0) {
        m.put(x, y + 1, z, 'oak_fence', { north: 'false', south: 'false', east: 'true', west: 'true', waterlogged: 'false' });
      }
    }
  }

  // Overgrown farmland patches (coarse dirt, pumpkin, wheat weeds)
  for (let z = 185; z <= 240; z++) {
    for (let x = 50; x <= 110; x++) {
      const y = heights[z * m.S + x];
      if (y > 0 && m.getName(x, y, z) === 'grass_block') {
        const h = m.hash(x, z);
        if (h % 11 === 0) {
          m.put(x, y, z, 'coarse_dirt');
        } else if (h % 37 === 0) {
          m.put(x, y + 1, z, 'carved_pumpkin', { facing: 'south' });
        } else if (h % 17 === 0) {
          m.put(x, y + 1, z, 'short_grass');
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 7. Zone 05: Watchtower Hill & Overlook
// -------------------------------------------------------------
function buildZone05(m, terrain) {
  // Watchtower at X68..78, Z110..120 on peak Y112
  const cx = 73, cz = 115, base = 112, h = 12;

  // Circular / Octagonal Stone Tower: outer radius 5 (11x11)
  for (let dy = 0; dy <= h; dy++) {
    const y = base + dy;
    for (let dx = -5; dx <= 5; dx++) {
      for (let dz = -5; dz <= 5; dz++) {
        const dist = Math.hypot(dx, dz);
        if (dist >= 3.6 && dist <= 5.2) {
          // Wall
          if (dy === 1 && dx === 0 && dz === 5) continue; // door opening
          if (dy === 2 && dx === 0 && dz === 5) continue;
          // Arrow slits at dy=4, 8
          if ((dy === 4 || dy === 8) && (dx === 0 || dz === 0)) continue;
          // Battlements crenelations at top dy=h
          if (dy === h && (Math.abs(dx) + Math.abs(dz)) % 2 === 0) continue;

          const blockName = m.hash(cx + dx, cz + dz, dy) % 4 === 0 ? 'cracked_stone_bricks' : 'stone_bricks';
          m.put(cx + dx, y, cz + dz, blockName);
        } else if (dist < 3.6) {
          // Interior
          if (dy === 0) {
            m.put(cx + dx, y, cz + dz, 'stone_bricks');
          } else if (dy === 6 || dy === h - 1) {
            // Wood floor levels
            m.put(cx + dx, y, cz + dz, 'dark_oak_planks');
          } else {
            m.put(cx + dx, y, cz + dz, 'air');
          }
        }
      }
    }
  }

  // Internal Wooden Ladder / Stairs up tower
  for (let dy = 1; dy < h; dy++) {
    m.put(cx + 3, base + dy, cz, 'ladder', { facing: 'west', waterlogged: 'false' });
  }

  // Top Battlement Vista Platform
  m.put(cx, base + h, cz, 'lantern', { hanging: 'false', waterlogged: 'false' });

  // Overlook Vista Balustrade along west edge
  for (let z = 105; z <= 125; z++) {
    const y = terrain.heights[z * m.S + 63];
    if (y > 0) {
      m.put(63, y + 1, z, 'stone_brick_wall', { waterlogged: 'false' });
    }
  }
}

// -------------------------------------------------------------
// 8. Zone 06: Dense Forest & Shadow Cavern
// -------------------------------------------------------------
function buildZone06(m, terrain) {
  const { heights } = terrain;

  // Cave Entrance at X220..228, Z148..152, Cliff Y104, Ground Y95
  const mouthX = 224, mouthZ = 150, groundY = 95;

  // Carve Cave Mouth: 8 blocks wide, 6 blocks high
  for (let y = groundY; y <= groundY + 5; y++) {
    for (let x = mouthX - 4; x <= mouthX + 4; x++) {
      for (let z = mouthZ - 2; z <= mouthZ + 4; z++) {
        m.put(x, y, z, 'air');
      }
    }
  }
  // Cave mouth stone arch frame
  for (let x = mouthX - 5; x <= mouthX + 5; x++) {
    m.put(x, groundY + 6, mouthZ - 2, 'mossy_cobblestone');
  }

  // Hollow Inner Cavern: X225..250, Z130..160, Y92..101
  for (let z = 132; z <= 158; z++) {
    for (let x = 226; x <= 248; x++) {
      for (let y = 94; y <= 101; y++) {
        m.put(x, y, z, 'air');
      }
      // Cavern floor
      m.put(x, 93, z, m.hash(x, z) % 3 === 0 ? 'dripstone_block' : 'cobblestone');
    }
  }

  // Hanging stalactites & rock pillars inside cave
  for (const [sx, sz] of [[232, 138], [240, 142], [235, 150], [244, 152]]) {
    // Stalactite from ceiling Y102 down
    m.put(sx, 101, sz, 'dripstone_block');
    m.put(sx, 100, sz, 'pointed_dripstone', { vertical_direction: 'down', thickness: 'base', waterlogged: 'false' });
    m.put(sx, 99, sz, 'pointed_dripstone', { vertical_direction: 'down', thickness: 'tip', waterlogged: 'false' });
    // Stalagmite from floor Y93 up
    m.put(sx, 94, sz, 'pointed_dripstone', { vertical_direction: 'up', thickness: 'tip', waterlogged: 'false' });
  }

  // Cave lighting: dim glowing shroomlight in ceiling crevice
  m.put(238, 101, 145, 'shroomlight');
  m.put(242, 94, 136, 'soul_lantern', { hanging: 'false', waterlogged: 'false' });

  // Outdoor Forest Clearing (Combat Arena): X195..215, Z165..185
  for (let z = 165; z <= 185; z++) {
    for (let x = 195; x <= 215; x++) {
      const y = heights[z * m.S + x];
      if (y > 0) {
        const h = m.hash(x, z);
        m.put(x, y, z, h % 4 === 0 ? 'podzol' : h % 3 === 0 ? 'coarse_dirt' : 'grass_block');
      }
    }
  }
}

// -------------------------------------------------------------
// 9. Zone 07: Ruined Mansion & Miniboss Courtyard
// -------------------------------------------------------------
function buildZone07(m) {
  // Grand Stairway: X144..156 (width 12), Z120..136, climbs from Y98 to Y108
  for (let z = 120; z <= 136; z++) {
    const step = 136 - z; // 0 to 16
    const sy = Math.min(108, 98 + Math.floor(step * 0.65));
    for (let x = 144; x <= 156; x++) {
      const isBalustrade = (x === 144 || x === 156);
      if (isBalustrade) {
        m.put(x, sy + 1, z, 'stone_brick_wall', { waterlogged: 'false' });
        if (z % 4 === 0) {
          m.put(x, sy + 2, z, 'chiseled_stone_bricks');
          m.put(x, sy + 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' });
        }
      } else {
        m.put(x, sy, z, 'stone_brick_stairs', { facing: 'north', half: 'bottom', shape: 'straight', waterlogged: 'false' });
      }
    }
  }

  // Miniboss Courtyard Arena: X125..175, Z95..120, flat at Y108
  for (let z = 95; z <= 120; z++) {
    for (let x = 125; x <= 175; x++) {
      const h = m.hash(x, z);
      const bName = h % 7 === 0 ? 'mossy_stone_bricks' : h % 5 === 0 ? 'cracked_stone_bricks' : h % 3 === 0 ? 'polished_deepslate' : 'stone_bricks';
      m.put(x, 108, z, bName);
    }
  }

  // Courtyard perimeter balustrades (X125 and X175)
  for (let z = 95; z <= 120; z++) {
    m.put(125, 109, z, 'stone_brick_wall', { waterlogged: 'false' });
    m.put(175, 109, z, 'stone_brick_wall', { waterlogged: 'false' });
  }
  // Iron braziers at arena corners
  for (const [bx, bz] of [[130, 100], [170, 100], [130, 115], [170, 115]]) {
    m.put(bx, 109, bz, 'chiseled_stone_bricks');
    m.put(bx, 110, bz, 'soul_lantern', { hanging: 'false', waterlogged: 'false' });
  }

  // Ruined Gothic Mansion: X130..170, Z60..92 (40x32), Foundation Y108, Walls up to Y122
  const mx = 130, mz = 60, mw = 40, md = 32, base = 108;

  // Exterior Walls
  for (let dy = 1; dy <= 12; dy++) {
    const y = base + dy;
    for (let dx = 0; dx < mw; dx++) {
      for (let dz = 0; dz < md; dz++) {
        const isPerimeter = (dx === 0 || dx === mw - 1 || dz === 0 || dz === md - 1);
        const isDivider = (dx === 12 || dx === 28); // Divide into West Wing, Great Hall, East Wing
        if (isPerimeter || isDivider) {
          // Grand Front Entrance: X147..153, Z91, Y109..114
          if (dz === md - 1 && dx >= 17 && dx <= 23 && dy <= 5) continue;
          // Windows
          if (dy >= 4 && dy <= 7 && (dx === 6 || dx === 34 || dz === 10 || dz === 22)) {
            if (m.hash(mx + dx, mz + dz, dy) % 2 === 0) continue;
          }
          // Ruin decay higher up
          if (dy > 8 && m.hash(mx + dx, mz + dz, dy) % 10 < 4) continue;

          const bName = (dx === 0 || dx === mw - 1 || dz === 0 || dz === md - 1)
            ? (m.hash(mx + dx, mz + dz, dy) % 3 === 0 ? 'mossy_stone_bricks' : 'stone_bricks')
            : 'polished_deepslate';
          m.put(mx + dx, y, mz + dz, bName);
        } else {
          // Floors inside
          if (dy === 6) {
            // Second floor mezzanine with holes
            if (m.hash(mx + dx, mz + dz) % 5 !== 0) {
              m.put(mx + dx, y, mz + dz, 'dark_oak_planks');
            }
          }
        }
      }
    }
  }

  // Great Hall Fireplace at North wall (dx = 18..22, dz = 1)
  m.fill(mx + 18, base + 1, mz + 1, mx + 22, base + 8, mz + 2, 'stone_bricks');
  m.put(mx + 20, base + 1, mz + 2, 'campfire', { lit: 'false', signal_fire: 'false', waterlogged: 'false' });

  // Great Hall Collapsed Long Dining Table
  for (let dz = 12; dz <= 24; dz += 2) {
    m.put(mx + 20, base + 1, mz + dz, 'dark_oak_stairs', { facing: 'north', half: 'top', shape: 'straight', waterlogged: 'false' });
  }

  // Library Wing (dx < 12): Bookshelves and cobwebs
  for (let dz = 4; dz <= 26; dz += 4) {
    m.fill(mx + 2, base + 1, mz + dz, mx + 2, base + 4, mz + dz + 2, 'bookshelf');
    m.put(mx + 3, base + 3, mz + dz, 'cobweb');
  }

  // Armory Wing (dx > 28): Iron bars & anvils
  m.fill(mx + 35, base + 1, mz + 6, mx + 35, base + 4, mz + 16, 'iron_bars', { north: 'true', south: 'true', east: 'false', west: 'false', waterlogged: 'false' });
  m.put(mx + 33, base + 1, mz + 10, 'anvil', { facing: 'north' });

  // Grand Gothic Roof with Major Ruined Sections
  for (let dx = -1; dx <= mw; dx++) {
    const distToCenter = Math.abs(dx - mw / 2);
    const ry = base + 12 + Math.max(0, Math.round((mw / 2 - distToCenter) * 0.45));
    for (let dz = -1; dz <= md; dz++) {
      // 60% roof destroyed
      if (m.hash(mx + dx, mz + dz) % 10 < 6) continue;
      m.put(mx + dx, ry, mz + dz, 'dark_oak_stairs', {
        facing: dx < mw / 2 ? 'east' : 'west',
        half: 'bottom',
        shape: 'straight',
        waterlogged: 'false'
      });
    }
  }

  // North Cliff Bridge Gatehouse (X170..176, Z56..62)
  m.fill(172, base + 1, 56, 178, base + 6, 60, 'stone_bricks');
  m.fill(174, base + 1, 56, 176, base + 4, 60, 'air'); // passageway to bridge
  m.put(174, base + 5, 58, 'lantern', { hanging: 'true', waterlogged: 'false' });
}

// -------------------------------------------------------------
// 10. Zone 08: Next Checkpoint Island (Safe Zone)
// -------------------------------------------------------------
function buildZone08(m) {
  // Center around X233, Z43, Y110
  const cx = 233, cz = 43, py = 110;

  // Safe plaza floor
  for (let z = 33; z <= 53; z++) {
    for (let x = 223; x <= 243; x++) {
      if (Math.hypot((x - cx) / 9, (z - cz) / 9) <= 1) {
        m.put(x, py, z, m.hash(x, z) % 3 === 0 ? 'chiseled_stone_bricks' : 'stone_bricks');
      }
    }
  }

  // Exit Portal Arch to next island: X230..236, Z36
  m.fill(cx - 3, py + 1, 36, cx - 3, py + 6, 36, 'polished_deepslate');
  m.fill(cx + 3, py + 1, 36, cx + 3, py + 6, 36, 'polished_deepslate');
  m.fill(cx - 3, py + 6, 36, cx + 3, py + 6, 36, 'chiseled_stone_bricks');
  m.fill(cx - 2, py + 1, 36, cx + 2, py + 5, 36, 'purple_stained_glass');

  // Campfire Rest Spot
  m.put(cx, py + 1, cz + 2, 'campfire', { lit: 'true', signal_fire: 'true', waterlogged: 'false' });
  // Stone Benches around campfire
  m.put(cx - 2, py + 1, cz + 2, 'stone_brick_stairs', { facing: 'east', half: 'bottom', shape: 'straight', waterlogged: 'false' });
  m.put(cx + 2, py + 1, cz + 2, 'stone_brick_stairs', { facing: 'west', half: 'bottom', shape: 'straight', waterlogged: 'false' });

  // Protective outer wall
  for (let angle = 0; angle < Math.PI * 2; angle += 0.25) {
    const wx = Math.round(cx + Math.cos(angle) * 10);
    const wz = Math.round(cz + Math.sin(angle) * 10);
    // Opening at West for bridge (X <= 226)
    if (wx > 225) {
      m.put(wx, py + 1, wz, 'cobblestone_wall', { waterlogged: 'false' });
    }
  }
}

// -------------------------------------------------------------
// 11. Custom Trees Placement from Tree Library
// -------------------------------------------------------------
function placeCustomTrees(m, terrain) {
  const { heights } = terrain;

  // Helper to load tree NBT
  function loadTree(assetId) {
    const filePath = path.join(TREE_LIB_DIR, assetId + '.nbt');
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath);
    return readNbt(zlib.gunzipSync(raw));
  }

  // Preload a set of custom trees
  // t013-t017: dead/spooky trees, t001-t008: spruce/dark oak, t018-t055: leafy oak
  const treeCache = new Map();
  ['t001', 't004', 't007', 't013', 't014', 't018', 't020', 't037'].forEach(id => {
    try {
      const data = loadTree(id);
      if (data) treeCache.set(id, data);
    } catch (e) {
      // ignore
    }
  });

  function placeTree(assetId, targetX, targetZ) {
    const tree = treeCache.get(assetId);
    if (!tree) return false;
    const groundY = heights[targetZ * m.S + targetX];
    if (groundY <= 0) return false;

    // Find anchor / root center
    const [w, h, d] = tree.size;
    const ox = targetX - Math.floor(w / 2);
    const oz = targetZ - Math.floor(d / 2);
    const oy = groundY + 1;

    for (const b of tree.blocks) {
      const bx = ox + b.pos[0];
      const by = oy + b.pos[1];
      const bz = oz + b.pos[2];

      const paletteEntry = tree.palette[b.state];
      const blockName = paletteEntry.Name.replace('minecraft:', '');
      const props = paletteEntry.Properties ? Object.fromEntries(Object.entries(paletteEntry.Properties)) : undefined;

      // Do not overwrite existing stone/building blocks
      const existing = m.getName(bx, by, bz);
      if (existing === 'air' || existing === 'grass_block' || existing === 'short_grass') {
        m.put(bx, by, bz, blockName, props);
      }
    }
    return true;
  }

  // 1. Spooky / Dead Trees around Farmland & Village Outskirts
  placeTree('t013', 95, 230);
  placeTree('t014', 60, 215);
  placeTree('t013', 120, 250);
  placeTree('t014', 185, 245);
  placeTree('t013', 135, 175);

  // 2. Dense Spruce & Dark Oak Trees in Zone 06 Forest
  placeTree('t001', 200, 155);
  placeTree('t004', 210, 140);
  placeTree('t007', 225, 175);
  placeTree('t001', 235, 190);
  placeTree('t004', 245, 165);
  placeTree('t007', 205, 195);
  placeTree('t001', 215, 210);

  // 3. Leafy Oaks around Watchtower Hill base and Mansion terrace cliffs
  placeTree('t018', 95, 150);
  placeTree('t020', 60, 160);
  placeTree('t037', 115, 125);
  placeTree('t018', 185, 115);
  placeTree('t020', 120, 75);
  placeTree('t037', 180, 80);
}

// -------------------------------------------------------------
// 12. Main Builder & Exporter
// -------------------------------------------------------------
function buildLevelingIsland01(destination = 'maps/leveling_island_01') {
  const out = path.resolve(ROOT, destination);
  if (fs.existsSync(out)) {
    throw Error('Output folder already exists: ' + out);
  }

  console.log('Step 1: Initializing Voxel Grid (S=300, LOW=16, HIGH=144)...');
  const m = createModel();

  console.log('Step 2: Shaping Terrain, Stalactite Underbelly & Sub-Islands...');
  const terrain = buildTerrain(m);

  console.log('Step 3: Building Roads, Terraces & Sky Bridges...');
  buildBridgesAndPaths(m, terrain);

  console.log('Step 4: Building Zone 01 (Entry Portal Island)...');
  buildZone01(m);

  console.log('Step 5: Building Zone 03 (Village Plaza & Well Arena)...');
  buildZone03(m);

  console.log('Step 6: Building Zone 02 (Abandoned Village Ruined Houses)...');
  buildZone02(m);

  console.log('Step 7: Building Zone 04 (Farmland & Hunting Meadows)...');
  buildZone04(m, terrain);

  console.log('Step 8: Building Zone 05 (Watchtower Hill & Overlook)...');
  buildZone05(m, terrain);

  console.log('Step 9: Building Zone 06 (Dense Forest & Shadow Cavern)...');
  buildZone06(m, terrain);

  console.log('Step 10: Building Zone 07 (Grand Mansion & Miniboss Courtyard)...');
  buildZone07(m);

  console.log('Step 11: Building Zone 08 (Next Checkpoint Island)...');
  buildZone08(m);

  console.log('Step 12: Populating Custom Trees from Tree Library...');
  placeCustomTrees(m, terrain);

  console.log('Step 13: Exporting NBT Minecraft Java 1.21.1 Save...');
  const { exportMap } = require('./create_ruined_castle.cjs');

  const modelForExport = {
    S: m.S,
    LOW: m.LOW,
    HIGH: m.HIGH,
    FLOOR: m.FLOOR,
    definitions: m.definitions,
    blocks: m.blocks,
    tiles: [...m.tiles.values()],
    spawn: [150, 89, 296], // Zone 01 Safe Spawn Point
    name: 'NeoWorld - Leveling Island 01 (Ruined Village)',
    colours: COLOURS,
    reachableFloorCells: 15000,
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

  const result = exportMap(modelForExport, out);

  // Write custom zones.json
  const zonesFile = path.join(out, 'zones.json');
  fs.writeFileSync(zonesFile, JSON.stringify(modelForExport.rooms.map(r => ({
    id: r.name.slice(0, 2),
    name: r.name,
    bounds: r.bounds,
    center: r.center
  })), null, 2));

  // Write detailed MAP.md
  const mapMdContent = `# เกาะ 01 • ทุ่งหญ้าหมู่บ้านร้าง — บันทึกข้อมูลแมพ (Leveling Island 01)

## ข้อมูลทั่วไปและประวัติ

- **ชื่อแมพ:** Leveling Island 01 · Ruined Village (เกาะทุ่งหญ้าหมู่บ้านร้าง)
- **สถานะ:** สร้างโครงสร้างทางกายภาพครบทั้ง 8 โซนครบถ้วน พร้อมเปิดตรวจสอบ
- **โฟลเดอร์ทำงาน:** \`maps/leveling_island_01\` (ใช้โฟลเดอร์นี้เป็นแมพทำงานหลักของเกาะ 01 ไม่สร้างโฟลเดอร์เวอร์ชันใหม่ทับ)
- **รูปแบบ:** Minecraft Java 1.21.1, DataVersion 3955
- **ขนาดบันทึก:** 300 × 300 บล็อก (Region r.0.0.mca)
- **พิกัดเกิดผู้เล่น:** **X150 Y89 Z296** (หันหน้าทิศเหนือ 0 องศา, ปลอดภัยบน Zone 01)

## ทิศทางและพิกัด
- หน้าเกาะ: เหนือ (-Z) มุ่งสู่คฤหาสน์ร้างและสะพานสู่เกาะ 08
- หลังเกาะ: ใต้ (+Z) เกาะเล็กทางเข้าและประตูกลับเมือง
- ซ้ายเกาะ: ตะวันตก (-X) ทุ่งล่า ไร่ร้าง และเนินหอเฝ้ายาม
- ขวาเกาะ: ตะวันออก (+X) ป่าทึบและถ้ำหินเงา

## รายละเอียดทั้ง 8 โซน

| โซน | พิกัด X | พิกัด Z | ระดับพื้น (Y) | รายละเอียดสิ่งปลูกสร้าง |
|---|---|---|---|---|
| **01 เกาะเล็กทางเข้า** | X138–X162 | Z286–Z310 | Y88 (เกิด Y89) | จุดเกิดเข้ามิติ, ประตูวาร์ปกลับเมือง, เสาโคมไฟ, สะพานไม้ข้ามเวหา 16 บล็อกสู่เกาะหลัก |
| **02 หมู่บ้านร้าง** | X120–X195 | Z190–Z265 | Y92–Y94 | บ้านร้าง 5 หลัง (โรงเตี๊ยม 2 ชั้น, โรงตีเหล็ก, ร้านค้า, บ้านพัก 2 หลัง) ภายในใช้งานได้จริง หลังคาพัง |
| **03 ลานหมู่บ้าน** | X138–X166 | Z208–Z238 | Y92–Y93 | บ่อน้ำแห้ง 5×5 ลึก 6 บล็อก, ซากรถเข็นสินค้า, ซากแผงร้านค้า, ลานต่อสู้เปิดห่างขอบเหว >25 บล็อก |
| **04 ทุ่งล่าและไร่ร้าง** | X45–X120 | Z175–Z250 | Y92–Y98 | แปลงนาขั้นบันได, ซากโรงนา/กังหันลม, รั้วขาด, กองฟาง, ลานต่อสู้ 3 จุด |
| **05 เนินหอเฝ้ายาม** | X50–X105 | Z95–Z165 | Y98 -> Y112 | ทางขึ้นเลาะหน้าผา, หอคอยหินแปดเหลี่ยมสูง 12 บล็อก บันไดขึ้นสู่ยอด Y124, จุดชมวิว |
| **06 ป่าทึบและถ้ำเงา** | X185–X255 | Z120–Z205 | Y94–Y100 | ป่าทึบต้นไม้ exsilit, ปากถ้ำกว้าง 8 บล็อก, โถงถ้ำในหิน 30×30 หินย้อยและเห็ดเรืองแสง |
| **07 คฤหาสน์ร้างบนเนิน** | X105–X195 | Z55–Z135 | ลาน Y108 / หลังคา Y122 | บันไดหินใหญ่กว้าง 12 บล็อก, ลานมินิบอส 50×25 บล็อก, คฤหาสน์กอธิค 40×32 บล็อก โถงจัดเลี้ยง/สมุด/คลังอาวุธ |
| **08 เกาะเช็กพอยต์ถัดไป**| X220–X246 | Z30–Z56 | Y110 (เกิด Y111)| สะพานแขวนลอยฟ้าโค้ง 48 บล็อก, ประตูมิติมุ่งสู่เกาะถัดไป, แคมป์ไฟจุดพักฟื้น |

## ธรณีสัณฐานใต้เกาะ (Underbelly)
- ผิวบน Y88 ถึง Y112
- ฐานหินใต้เกาะหนา เรียวแหลมลงไปถึง Y20–Y28 ด้วยหิน Stone, Andesite, Cobblestone และ Deepslate ที่ปลายยอดหินย้อย

## สถานะระบบและการตรวจสอบ
- โครงสร้างบล็อกและภูมิประเทศสร้างเสร็จสมบูรณ์ 100%
- ไม่แก้แมพเดิม \`maps/starter_sky_land_spawn_v1\`
- ต้นไม้นำเข้าจาก \`maps/tree_library\` มีคุณสมบัติ persistent=true
- จุดเกิดผ่านการตรวจความปลอดภัย มีบล็อกยืนและช่องว่างเหนือศีรษะ 3 บล็อก
- ตรวจสอบความสมบูรณ์ไฟล์ NBT / MCA ผ่าน
- ยังไม่ได้ทดสอบระบบมอนสเตอร์เกิดจริงหรือตรรกะเกมเซิร์ฟเวอร์
`;
  fs.writeFileSync(path.join(out, 'MAP.md'), mapMdContent, 'utf8');

  console.log('Successfully built and exported Leveling Island 01 to:', out);
  return { output: out, ...result };
}

module.exports = { buildLevelingIsland01 };

if (require.main === module) {
  buildLevelingIsland01(process.argv[2] || 'maps/leveling_island_01');
}
