// Local, deterministic construction of Zone 08 (Portal Island) in the saved city.
// Never generates a world; patches the existing r.0.0.mca region in place with hash guards.
'use strict';
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const { createLibrary } = require('./viewer/map-library.cjs'), { patch } = require('./smooth_active_land.cjs');

const ROOT = path.resolve(__dirname, '../..');
const MAP = path.join(ROOT, 'maps/starter_sky_land_spawn_v1');
const STAGE = path.join(ROOT, 'tmp/portal-island');
const REPORT = path.join(MAP, 'portal-island-report.json');

const key = d => d.Name + JSON.stringify(Object.entries(d.Properties || {}).sort());

function buildPortalIsland() {
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
    // Safety boundaries: Zone 08 is strictly in X: 276..362, Z: 196..240, Y: 40..135
    if (x < 276 || x > 362 || z < 196 || z > 240 || y < 40 || y > 135 || y < m.low || y >= m.high) {
      throw Error('Outside Zone 08 edit bounds: ' + [x, y, z]);
    }
    // Protect everything west of X=276 (Zone 06 Hall and existing city)
    if (x === 276 && (z < 216 || z > 220)) {
      throw Error('Protected Zone 06 boundary at ' + [x, z]);
    }
    // Protect Tree #22 trunk and canopy (X: 290..296, Z: 211..217, Y: 100..115)
    if (x >= 290 && x <= 296 && z >= 211 && z <= 217 && y >= 100 && y <= 115) {
      const orig = original(x, y, z).Name;
      if (/_wood$|_log$|_leaves$/.test(orig)) {
        throw Error('Protected Tree #22 block at ' + [x, y, z]);
      }
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

  // Helper to determine path center Z on cliffside promenade (X: 276..291)
  function promenadeCenterZ(x) {
    if (x <= 278) return 218;
    const t = (x - 278) / 13; // 0 to 1
    return Math.round(218 + 6 * t); // sweeps from 218 up to 224
  }

  // Helper to determine bridge center Z on curved sky suspension bridge (X: 292..318)
  function bridgeCenterZ(x) {
    const t = (x - 291) / 27; // 0 at 291, 1 at 318
    // Organic sweeping curve: curves south out over the sky void, then gracefully swings north-east toward island
    return Math.round(224 - 8 * t + 5 * Math.sin(Math.PI * t));
  }

  // -------------------------------------------------------------
  // 1. CLIFFSIDE PROMENADE (X: 276..291)
  // -------------------------------------------------------------
  // Departure from Zone 06 at X=276..277 (Y=107) and step up at X=278 (Y=108)
  for (let z = 217; z <= 219; z++) {
    put(276, 107, z, 'polished_andesite');
    put(277, 107, z, 'polished_andesite');
    put(278, 108, z, 'stone_brick_stairs', stairs('east'));
  }

  for (let x = 279; x <= 291; x++) {
    const cz = promenadeCenterZ(x);
    // Walkway width 3 (cz - 1 to cz + 1)
    for (let z = cz - 1; z <= cz + 1; z++) {
      put(x, 108, z, z === cz ? 'stone_bricks' : 'polished_andesite');
      // Sub-deck support down to natural surface
      for (let y = 107; y >= 92; y--) {
        const orig = original(x, y, z).Name;
        if (orig === 'minecraft:air') {
          put(x, y, z, y >= 105 ? 'stone_bricks' : 'stone');
        } else break;
      }
    }

    // Railings on cliffside promenade
    // North side railing
    const nRailingZ = cz - 2;
    const isPostN = (x === 280 || x === 285 || x === 291);
    if (isPostN) {
      put(x, 109, nRailingZ, 'chiseled_stone_bricks');
      put(x, 110, nRailingZ, 'lantern', { hanging: 'false', waterlogged: 'false' });
    } else {
      put(x, 109, nRailingZ, 'stone_brick_wall', wall());
    }

    // South side railing (except at the cliffside overlook at X=288..291)
    if (x < 288) {
      const sRailingZ = cz + 2;
      const isPostS = (x === 280 || x === 285);
      if (isPostS) {
        put(x, 109, sRailingZ, 'chiseled_stone_bricks');
        put(x, 110, sRailingZ, 'lantern', { hanging: 'false', waterlogged: 'false' });
      } else {
        put(x, 109, sRailingZ, 'stone_brick_wall', wall());
      }
    }
  }

  // Cliffside Overlook at X = 288..291 extending south (Z: 224..227)
  for (let x = 288; x <= 291; x++) {
    for (let z = 224; z <= 227; z++) {
      put(x, 108, z, (x === 289 || x === 290) && (z === 225 || z === 226) ? 'smooth_stone' : 'polished_andesite');
      // Overlook cliff supports
      for (let y = 107; y >= 92; y--) {
        const orig = original(x, y, z).Name;
        if (orig === 'minecraft:air') {
          put(x, y, z, y >= 105 ? 'stone_bricks' : 'stone');
        } else break;
      }
    }
    // South railing of overlook at Z=228
    put(x, 109, 228, 'stone_brick_wall', wall());
  }
  // East railing of overlook at X=291, Z: 226..227
  put(291, 109, 226, 'stone_brick_wall', wall());
  put(291, 109, 227, 'stone_brick_wall', wall());
  // Overlook corner lantern posts
  put(288, 109, 228, 'chiseled_stone_bricks');
  put(288, 110, 228, 'lantern', { hanging: 'false', waterlogged: 'false' });
  put(291, 109, 228, 'chiseled_stone_bricks');
  put(291, 110, 228, 'lantern', { hanging: 'false', waterlogged: 'false' });
  // Stone bench overlooking the cloudscape
  put(289, 109, 227, 'stone_brick_stairs', stairs('north'));
  put(290, 109, 227, 'stone_brick_stairs', stairs('north'));

  // -------------------------------------------------------------
  // 2. SWEEPING CURVED SKY SUSPENSION BRIDGE (X: 292..317)
  // -------------------------------------------------------------
  for (let x = 292; x <= 317; x++) {
    const bcz = bridgeCenterZ(x);

    // Arch elevation: rises 1 block in mid-span
    let deckY = 108;
    if (x >= 298 && x <= 311) {
      deckY = 109;
    }

    // Step transitions
    if (x === 297) {
      // Step up from 108 to 109
      for (let z = bcz - 1; z <= bcz + 1; z++) {
        put(x, 108, z, 'stone_bricks');
        put(x, 109, z, 'deepslate_tile_stairs', stairs('east'));
      }
    } else if (x === 312) {
      // Step down from 109 to 108
      for (let z = bcz - 1; z <= bcz + 1; z++) {
        put(x, 108, z, 'stone_bricks');
        put(x, 109, z, 'deepslate_tile_stairs', stairs('west'));
      }
    } else {
      // Regular deck
      for (let z = bcz - 1; z <= bcz + 1; z++) {
        put(x, deckY, z, z === bcz ? 'smooth_stone' : 'polished_andesite');
        put(x, deckY - 1, z, 'deepslate_tiles');
      }
    }

    // Structural underside rib following bridge curve
    const distCenter = Math.abs(x - 304);
    const ribY = distCenter <= 3 ? 105 : distCenter <= 7 ? 106 : 107;
    put(x, ribY, bcz, ribY === 105 ? 'crying_obsidian' : 'stone_bricks');
    if (ribY === 105) {
      put(x, 104, bcz, 'sea_lantern');
    }

    // North railing along curved edge (bcz - 2)
    const nRailZ = bcz - 2;
    const isPostN = (x === 293 || x === 299 || x === 305 || x === 311 || x === 317);
    if (isPostN) {
      put(x, deckY + 1, nRailZ, 'chiseled_deepslate');
      put(x, deckY + 2, nRailZ, 'sea_lantern');
      put(x, deckY + 3, nRailZ, 'deepslate_tile_wall', wall());
    } else {
      put(x, deckY + 1, nRailZ, 'stone_brick_wall', wall());
    }

    // South railing along curved edge (bcz + 2)
    // Note: X: 302..306 houses the Mid-span Sky Balcony extending south
    if (x < 302 || x > 306) {
      const sRailZ = bcz + 2;
      const isPostS = (x === 293 || x === 299 || x === 311 || x === 317);
      if (isPostS) {
        put(x, deckY + 1, sRailZ, 'chiseled_deepslate');
        put(x, deckY + 2, sRailZ, 'sea_lantern');
        put(x, deckY + 3, sRailZ, 'deepslate_tile_wall', wall());
      } else {
        put(x, deckY + 1, sRailZ, 'stone_brick_wall', wall());
      }
    }
  }

  // Mid-span Sky Balcony (X: 302..306, extending south over the void)
  for (let x = 302; x <= 306; x++) {
    const bcz = bridgeCenterZ(x); // ~225
    for (let z = bcz + 2; z <= bcz + 4; z++) {
      put(x, 109, z, (x === 303 || x === 304 || x === 305) && z === bcz + 3 ? 'chiseled_deepslate' : 'deepslate_tiles');
      put(x, 108, z, 'crying_obsidian');
    }
    // Balcony outer south railing at bcz + 5
    put(x, 110, bcz + 5, 'deepslate_tile_wall', wall());
  }
  // Balcony west & east side railings
  const bczW = bridgeCenterZ(302);
  const bczE = bridgeCenterZ(306);
  for (let z = bczW + 2; z <= bczW + 4; z++) put(301, 110, z, 'deepslate_tile_wall', wall());
  for (let z = bczE + 2; z <= bczE + 4; z++) put(307, 110, z, 'deepslate_tile_wall', wall());

  // Balcony corner lantern posts
  put(301, 110, bczW + 5, 'chiseled_deepslate');
  put(301, 111, bczW + 5, 'soul_lantern', { hanging: 'false', waterlogged: 'false' });
  put(307, 110, bczE + 5, 'chiseled_deepslate');
  put(307, 111, bczE + 5, 'soul_lantern', { hanging: 'false', waterlogged: 'false' });

  // Balcony stone bench facing north toward bridge
  put(303, 110, bczW + 4, 'deepslate_tile_stairs', stairs('north'));
  put(304, 110, bczW + 4, 'deepslate_tile_stairs', stairs('north'));
  put(305, 110, bczW + 4, 'deepslate_tile_stairs', stairs('north'));

  // -------------------------------------------------------------
  // 3. ARRIVAL GATEWAY (X: 318..319, Z: 213..219)
  // -------------------------------------------------------------
  // Centered at Z = 216 (where the bridge arrives at X=318)
  put(318, 109, 213, 'chiseled_deepslate');
  put(318, 110, 213, 'deepslate_tiles');
  put(318, 111, 213, 'deepslate_tiles');
  put(318, 112, 213, 'sea_lantern');
  put(318, 113, 213, 'deepslate_tile_wall', wall());

  put(318, 109, 219, 'chiseled_deepslate');
  put(318, 110, 219, 'deepslate_tiles');
  put(318, 111, 219, 'deepslate_tiles');
  put(318, 112, 219, 'sea_lantern');
  put(318, 113, 219, 'deepslate_tile_wall', wall());

  // Arch connection across arrival
  put(318, 113, 214, 'deepslate_tile_stairs', stairs('south', 'top'));
  put(318, 114, 215, 'deepslate_tiles');
  put(318, 114, 216, 'crying_obsidian');
  put(318, 114, 217, 'deepslate_tiles');
  put(318, 113, 218, 'deepslate_tile_stairs', stairs('north', 'top'));

  // Bridge arrival deck at X=318..319
  for (let z = 214; z <= 218; z++) {
    put(318, 108, z, z === 216 ? 'smooth_stone' : 'polished_andesite');
    put(319, 108, z, z === 216 ? 'smooth_stone' : 'polished_andesite');
  }

  // -------------------------------------------------------------
  // 4. PORTAL ISLAND TERRAIN & ROCK UNDERSIDE
  // -------------------------------------------------------------
  const islandCenter = [340, 216];
  const rx = 19, rz = 17;

  for (let x = 320; x <= 360; x++) {
    for (let z = 196; z <= 236; z++) {
      const dx = (x - islandCenter[0]) / rx;
      const dz = (z - islandCenter[1]) / rz;
      const angle = Math.atan2(dz, dx);
      // Organic natural edge with harmonics
      const r = Math.hypot(dx, dz) * (1 - 0.06 * Math.cos(3 * angle) + 0.04 * Math.sin(4 * angle));

      if (r > 1.0) continue;

      // Bottom cone elevation tapering down to ~56
      const bottomY = Math.round(58 + 48 * Math.pow(r, 1.25) + 2.5 * Math.sin(x * 0.45 + z * 0.35));

      // Fill rock cone from bottomY up to surface (107)
      for (let y = bottomY; y <= 107; y++) {
        let blockName = 'stone';
        // Material stratigraphy
        if (y >= 105 && r > 0.82) {
          blockName = 'dirt';
        } else {
          // Rock blend
          const h = (x * 37 + y * 13 + z * 19) & 0x7fff;
          if (h % 31 === 0 && y < 90) {
            blockName = 'amethyst_block';
          } else if (h % 41 === 0 && y < 85) {
            blockName = 'crying_obsidian';
          } else if (h % 7 === 0) {
            blockName = 'tuff';
          } else if (h % 5 === 0) {
            blockName = 'andesite';
          } else if (h % 3 === 0) {
            blockName = 'cobblestone';
          }
        }
        put(x, y, z, blockName);
      }

      // Island surface at Y=108
      if (r > 0.84) {
        // Outer garden/green rim
        put(x, 108, z, 'grass_block', { snowy: 'false' });
        // Perimeter safety wall (except entrance near X=320, Z=214..218)
        if (r >= 0.88 && !(x <= 322 && z >= 214 && z <= 218)) {
          put(x, 109, z, 'stone_brick_wall', wall());
        }
      } else {
        // Inner plaza floor pattern
        const dCenter = Math.hypot(x - islandCenter[0], z - islandCenter[1]);
        if (dCenter < 3.5) {
          put(x, 108, z, 'crying_obsidian');
        } else if (dCenter < 6.5) {
          put(x, 108, z, 'polished_andesite');
        } else if (dCenter < 9.5) {
          const radial = Math.abs(x - islandCenter[0]) <= 1 || Math.abs(z - islandCenter[1]) <= 1;
          put(x, 108, z, radial ? 'chiseled_stone_bricks' : 'smooth_stone');
        } else {
          put(x, 108, z, 'stone_bricks');
        }
      }
    }
  }

  // -------------------------------------------------------------
  // 5. FOUR AETHERIAL OBELISKS (SEALED CRYSTAL PILLARS)
  // -------------------------------------------------------------
  const obelisks = [
    [330, 206], // NW
    [330, 226], // SW
    [350, 206], // NE
    [350, 226]  // SE
  ];

  for (const [ox, oz] of obelisks) {
    // 3x3 base plinth
    for (let dx = -1; dx <= 1; dx++) {
      for (let dz = -1; dz <= 1; dz++) {
        put(ox + dx, 108, oz + dz, 'deepslate_tiles');
        if (Math.abs(dx) === 1 && Math.abs(dz) === 1) {
          put(ox + dx, 109, oz + dz, 'deepslate_tile_stairs', stairs(dx > 0 ? 'west' : 'east'));
        }
      }
    }
    // Pillar shaft
    put(ox, 109, oz, 'chiseled_deepslate');
    put(ox, 110, oz, 'crying_obsidian');
    put(ox, 111, oz, 'chiseled_deepslate');
    put(ox, 112, oz, 'sea_lantern');
    put(ox, 113, oz, 'crying_obsidian');
    put(ox, 114, oz, 'amethyst_block');
    put(ox, 115, oz, 'amethyst_cluster', { facing: 'up', waterlogged: 'false' });
    // Side brackets
    put(ox + 1, 111, oz, 'deepslate_tile_wall', wall());
    put(ox - 1, 111, oz, 'deepslate_tile_wall', wall());
    put(ox, 111, oz + 1, 'deepslate_tile_wall', wall());
    put(ox, 111, oz - 1, 'deepslate_tile_wall', wall());
  }

  // -------------------------------------------------------------
  // 6. GRAND DIMENSIONAL PORTAL (X: 343..351, Z: 211..221)
  // -------------------------------------------------------------
  // Raised Dais at Y=109 and Y=110
  for (let x = 343; x <= 351; x++) {
    for (let z = 212; z <= 220; z++) {
      put(x, 109, z, 'polished_blackstone_bricks');
      put(x, 110, z, 'deepslate_tiles');
    }
  }
  // Front stairs leading up from west (X=342, Z: 214..218)
  for (let z = 214; z <= 218; z++) {
    put(342, 109, z, 'deepslate_tile_stairs', stairs('east'));
    put(342, 110, z, 'deepslate_tile_stairs', stairs('east'));
  }

  // Dais corner markers
  for (const [cx, cz] of [[343, 212], [343, 220], [351, 212], [351, 220]]) {
    put(cx, 111, cz, 'crying_obsidian');
    put(cx, 112, cz, 'soul_lantern', { hanging: 'false', waterlogged: 'false' });
  }

  // Monumental Portal Frame
  // North Pillar: X: 346..347, Z: 212..213, Y: 111..123
  for (let x = 346; x <= 347; x++) {
    for (let z = 212; z <= 213; z++) {
      for (let y = 111; y <= 123; y++) {
        let blk = 'deepslate_tiles';
        if (y === 111 || y === 117 || y === 123) blk = 'crying_obsidian';
        else if (y === 114 || y === 120) blk = 'chiseled_deepslate';
        put(x, y, z, blk);
      }
    }
  }

  // South Pillar: X: 346..347, Z: 219..220, Y: 111..123
  for (let x = 346; x <= 347; x++) {
    for (let z = 219; z <= 220; z++) {
      for (let y = 111; y <= 123; y++) {
        let blk = 'deepslate_tiles';
        if (y === 111 || y === 117 || y === 123) blk = 'crying_obsidian';
        else if (y === 114 || y === 120) blk = 'chiseled_deepslate';
        put(x, y, z, blk);
      }
    }
  }

  // Arch overhead span: X: 346..347, Z: 214..218, Y: 121..124
  for (let x = 346; x <= 347; x++) {
    // Inner arch curved under-steps
    put(x, 121, 214, 'deepslate_tile_stairs', stairs('south', 'top'));
    put(x, 121, 218, 'deepslate_tile_stairs', stairs('north', 'top'));
    for (let z = 214; z <= 218; z++) {
      put(x, 122, z, 'deepslate_tiles');
      put(x, 123, z, z === 216 ? 'crying_obsidian' : 'deepslate_tiles');
      put(x, 124, z, 'deepslate_tiles');
    }
    // Crown crest
    put(x, 125, 216, 'amethyst_block');
    put(x, 126, 216, 'amethyst_cluster', { facing: 'up', waterlogged: 'false' });
    put(x, 125, 215, 'deepslate_tile_stairs', stairs('south'));
    put(x, 125, 217, 'deepslate_tile_stairs', stairs('north'));
  }

  // Portal Energy Core: X = 346..347, Z: 214..218, Y: 111..120
  for (let z = 214; z <= 218; z++) {
    // Portal threshold base
    put(346, 110, z, 'crying_obsidian');
    put(347, 110, z, 'crying_obsidian');

    for (let y = 111; y <= 120; y++) {
      // Swirling portal depth layers
      const isRim = (z === 214 || z === 218 || y === 111 || y === 120);
      const isCenter = (z === 216 && y >= 114 && y <= 117);

      // West face (front)
      put(346, y, z, isCenter ? 'cyan_stained_glass' : isRim ? 'crying_obsidian' : 'purple_stained_glass');

      // East face (core/back)
      put(347, y, z, isCenter ? 'sea_lantern' : isRim ? 'crying_obsidian' : 'cyan_stained_glass');
    }
  }

  // -------------------------------------------------------------
  // 7. AMENITIES, GARDENS & CELESTIAL DECORATION
  // -------------------------------------------------------------
  // Stone benches north and south of plaza
  for (let x = 336; x <= 340; x++) {
    put(x, 109, 204, 'stone_brick_stairs', stairs('south'));
    put(x, 109, 228, 'stone_brick_stairs', stairs('north'));
  }

  // Lectern destination altar facing portal
  put(337, 109, 216, 'chiseled_stone_bricks');
  put(337, 110, 216, 'lectern', { facing: 'east', has_book: 'true' });

  // Mystical vegetation on grass rim
  const flora = [
    [324, 210, 'allium'],
    [324, 222, 'cornflower'],
    [332, 201, 'flowering_azalea_leaves'],
    [333, 201, 'flowering_azalea_leaves'],
    [332, 231, 'flowering_azalea_leaves'],
    [333, 231, 'flowering_azalea_leaves'],
    [342, 200, 'blue_orchid'],
    [343, 200, 'cornflower'],
    [342, 232, 'blue_orchid'],
    [343, 232, 'allium'],
    [354, 208, 'flowering_azalea_leaves'],
    [354, 224, 'flowering_azalea_leaves'],
    [356, 216, 'amethyst_block'],
    [356, 217, 'amethyst_cluster', { facing: 'up', waterlogged: 'false' }]
  ];

  for (const [fx, fz, fName, fProp] of flora) {
    if (fName === 'flowering_azalea_leaves') {
      put(fx, 109, fz, fName, { persistent: 'true', waterlogged: 'false', distance: '1' });
    } else if (fName === 'amethyst_block') {
      put(fx, 108, fz, fName);
    } else if (fName === 'amethyst_cluster') {
      put(fx, 109, fz, fName, fProp);
    } else {
      put(fx, 109, fz, fName);
    }
  }

  // -------------------------------------------------------------
  // 8. VERIFICATION & STAGING / APPLY
  // -------------------------------------------------------------
  const regionFile = path.join(MAP, 'region/r.0.0.mca');
  const before = fs.readFileSync(regionFile);
  const sourceHash = crypto.createHash('sha256').update(before).digest('hex');

  let changedBlocks = 0;
  for (let i = 0; i < data.length; i++) {
    if (data[i] !== old[i]) changedBlocks++;
  }

  const zoneBounds = [276, 196, 360, 236];
  const islandBounds = [320, 196, 360, 236];
  const bridgeBounds = [276, 214, 319, 230];

  const planInfo = {
    zone: '08-dimensional-station',
    name: 'Portal Island',
    status: 'staged',
    sourceHash,
    changedBlocks,
    zoneBounds,
    islandBounds,
    bridgeBounds,
    islandCenter: [340, 108, 216],
    portalCenter: [346, 110, 216],
    counts
  };

  fs.mkdirSync(STAGE, { recursive: true });
  fs.writeFileSync(path.join(STAGE, 'plan.json'), JSON.stringify(planInfo, null, 2));

  if (process.argv.includes('--apply')) {
    const backupDir = path.join(ROOT, 'archive/map-edit-backups/before-zone08-portal/starter_sky_land_spawn_v1');
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
    const zone08Entry = {
      id: '08-dimensional-station',
      name: 'Portal Island',
      bounds: [276, 196, 360, 236],
      center: [340, 108, 216],
      islandBounds: [320, 196, 360, 236],
      bridgeBounds: [276, 214, 319, 230],
      portalCenter: [346, 110, 216],
      floorY: 108,
      portalFloorY: 110,
      status: 'active',
      details: {
        bridgeLength: 43,
        skyVoidSpan: 26,
        islandDiameter: 40,
        portalDimensions: [13, 15],
        aetherialObelisks: 4,
        geometry: 'curved_sky_suspension_bridge_with_overlook_and_balcony',
        gameplayConnected: false
      }
    };
    const filteredZones = zones.filter(z => z.id !== '08-dimensional-station');
    filteredZones.push(zone08Entry);
    fs.writeFileSync(zf, JSON.stringify(filteredZones, null, 2));

    // Update map-manifest.json
    const mf = path.join(MAP, 'map-manifest.json');
    const manifest = JSON.parse(fs.readFileSync(mf, 'utf8'));
    if (!manifest.rooms) manifest.rooms = [];
    manifest.rooms = manifest.rooms.filter(r => r.name !== 'Portal Island' && r.id !== '08-dimensional-station');
    manifest.rooms.push({
      id: '08-dimensional-station',
      name: 'Portal Island',
      bounds: [276, 196, 360, 236],
      center: [340, 108, 216]
    });
    fs.writeFileSync(mf, JSON.stringify(manifest, null, 2));

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
  buildPortalIsland();
}
