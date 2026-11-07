// Export architectural blueprint SVGs and HTML viewer for The Winged Guardian Fountain in Zone 03.
// Reads real voxel data from starter_sky_land_spawn_v1.
'use strict';
const fs = require('node:fs'), path = require('node:path');
const { createLibrary } = require('./viewer/map-library.cjs');

const ROOT = path.resolve(__dirname, '../..');
const MAP_DIR = path.join(ROOT, 'maps/starter_sky_land_spawn_v1');
const OUT_DIR = path.join(ROOT, 'docs/maps/zone03-fountain-blueprint');
const ARTIFACT_DIR = 'C:/Users/miw/.gemini/antigravity/brain/a3cbb705-55fd-4d27-a4e4-5225be5a2d80';

const lib = createLibrary(path.dirname(MAP_DIR));
const m = lib.load(path.basename(MAP_DIR));
const S = m.size;
const b = Buffer.from(m.blocks, 'base64');
const at = (x, y, z) => (y - m.low) * S * S + z * S + x;
const get = (x, y, z) => m.definitions[b.readUInt16LE(at(x, y, z) * 2)];

// Extract voxels in fountain zone
const CX = 160, CZ = 215;
const X_MIN = 151, X_MAX = 169;
const Z_MIN = 206, Z_MAX = 224;
const Y_MIN = 103, Y_MAX = 114;

const voxels = [];
const counts = {};

for (let y = Y_MIN; y <= Y_MAX; y++) {
  for (let z = Z_MIN; z <= Z_MAX; z++) {
    for (let x = X_MIN; x <= X_MAX; x++) {
      const def = get(x, y, z);
      if (def && def.Name !== 'minecraft:air') {
        voxels.push({ x, y, z, name: def.Name, props: def.Properties || {} });
        counts[def.Name] = (counts[def.Name] || 0) + 1;
      }
    }
  }
}

// Material color scheme for architectural blueprint
function getColor(name) {
  if (name.includes('quartz') || name.includes('diorite')) return '#ffffff';
  if (name.includes('deepslate')) return '#334155';
  if (name.includes('water')) return '#38bdf8';
  if (name.includes('sea_lantern')) return '#22d3ee';
  if (name.includes('crying_obsidian')) return '#9333ea';
  if (name.includes('end_rod')) return '#fde047';
  if (name.includes('stone') || name.includes('andesite')) return '#64748b';
  if (name.includes('orchid') || name.includes('valley')) return '#f43f5e';
  return '#94a3b8';
}

function getLabel(name) {
  return name.replace('minecraft:', '').replace(/_/g, ' ');
}

// -------------------------------------------------------------
// 1. TOP PLAN SVG (Orthographic projection from above)
// -------------------------------------------------------------
const GRID = 28;
const PADDING = 70;
const W_TOP = (X_MAX - X_MIN + 1) * GRID + PADDING * 2;
const H_TOP = (Z_MAX - Z_MIN + 1) * GRID + PADDING * 2;

let svgTop = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W_TOP} ${H_TOP}" width="100%" height="100%" style="background:#0a192f;font-family:monospace">
  <defs>
    <pattern id="blueprint-grid" width="${GRID}" height="${GRID}" patternUnits="userSpaceOnUse">
      <path d="M ${GRID} 0 L 0 0 0 ${GRID}" fill="none" stroke="#1e3a5f" stroke-width="0.8"/>
    </pattern>
  </defs>
  <rect width="100%" height="100%" fill="#0a192f"/>
  <rect x="${PADDING}" y="${PADDING}" width="${(X_MAX - X_MIN + 1) * GRID}" height="${(Z_MAX - Z_MIN + 1) * GRID}" fill="url(#blueprint-grid)"/>

  <!-- Title & Specs -->
  <text x="${PADDING}" y="36" fill="#38bdf8" font-size="18" font-weight="bold" letter-spacing="1.5">ZONE 03: THE WINGED GUARDIAN FOUNTAIN — TOP PLAN</text>
  <text x="${PADDING}" y="54" fill="#94a3b8" font-size="12">SCALE: 1 BLOCK = ${GRID}px · CENTER (X:160, Z:215) · BASIN 13×13 · WINGSPAN 9 BLOCKS</text>
`;

// Draw Axis Labels for X and Z
for (let x = X_MIN; x <= X_MAX; x++) {
  const px = PADDING + (x - X_MIN) * GRID;
  svgTop += `<text x="${px + GRID / 2}" y="${PADDING - 12}" fill="${x === 160 ? '#fde047' : '#64748b'}" font-size="10" text-anchor="middle">${x}</text>`;
}
for (let z = Z_MIN; z <= Z_MAX; z++) {
  const pz = PADDING + (z - Z_MIN) * GRID;
  svgTop += `<text x="${PADDING - 12}" y="${pz + GRID / 2 + 4}" fill="${z === 215 ? '#fde047' : '#64748b'}" font-size="10" text-anchor="end">${z}</text>`;
}

// Render top-down highest non-air block per (x, z)
for (let z = Z_MIN; z <= Z_MAX; z++) {
  for (let x = X_MIN; x <= X_MAX; x++) {
    // Find highest block
    let topBlk = null;
    for (let y = Y_MAX; y >= Y_MIN; y--) {
      const def = get(x, y, z);
      if (def && def.Name !== 'minecraft:air') {
        topBlk = { def, y };
        break;
      }
    }
    if (!topBlk) continue;

    const px = PADDING + (x - X_MIN) * GRID;
    const pz = PADDING + (z - Z_MIN) * GRID;
    const color = getColor(topBlk.def.Name);
    const opacity = topBlk.def.Name === 'minecraft:water' ? 0.65 : 1.0;

    svgTop += `<rect x="${px + 1}" y="${pz + 1}" width="${GRID - 2}" height="${GRID - 2}" rx="2" fill="${color}" fill-opacity="${opacity}" stroke="#0f2b48" stroke-width="1">
      <title>X:${x}, Y:${topBlk.y}, Z:${z} - ${getLabel(topBlk.def.Name)}</title>
    </rect>`;

    // Special markers
    if (x === 160 && z === 215) {
      svgTop += `<circle cx="${px + GRID/2}" cy="${pz + GRID/2}" r="5" fill="#fde047" stroke="#000" stroke-width="1"/>`;
    }
  }
}

// Dimension Callouts on Top Plan
svgTop += `
  <!-- Basin Dimension Line -->
  <line x1="${PADDING + (154 - X_MIN) * GRID}" y1="${PADDING + (Z_MAX - Z_MIN + 1) * GRID + 18}" x2="${PADDING + (166 - X_MIN + 1) * GRID}" y2="${PADDING + (Z_MAX - Z_MIN + 1) * GRID + 18}" stroke="#38bdf8" stroke-width="1.5"/>
  <text x="${PADDING + (160 - X_MIN + 0.5) * GRID}" y="${PADDING + (Z_MAX - Z_MIN + 1) * GRID + 34}" fill="#38bdf8" font-size="12" text-anchor="middle">BASIN DIAMETER: 13 BLOCKS (X: 154..166)</text>

  <!-- Wingspan Dimension Line -->
  <line x1="${PADDING + (156 - X_MIN) * GRID}" y1="${PADDING + (214 - Z_MIN) * GRID - 8}" x2="${PADDING + (164 - X_MIN + 1) * GRID}" y2="${PADDING + (214 - Z_MIN) * GRID - 8}" stroke="#fde047" stroke-width="1.5" stroke-dasharray="4 2"/>
  <text x="${PADDING + (160 - X_MIN + 0.5) * GRID}" y="${PADDING + (214 - Z_MIN) * GRID - 14}" fill="#fde047" font-size="11" text-anchor="middle">WINGSPAN: 9 BLOCKS (X: 156..164)</text>
</svg>`;

// -------------------------------------------------------------
// 2. FRONT ELEVATION SVG (South Elevation looking North into the Urn & Statue)
// -------------------------------------------------------------
const W_FRONT = (X_MAX - X_MIN + 1) * GRID + PADDING * 2;
const H_FRONT = (Y_MAX - Y_MIN + 1) * GRID + PADDING * 2 + 30;

let svgFront = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W_FRONT} ${H_FRONT}" width="100%" height="100%" style="background:#0a192f;font-family:monospace">
  <defs>
    <pattern id="front-grid" width="${GRID}" height="${GRID}" patternUnits="userSpaceOnUse">
      <path d="M ${GRID} 0 L 0 0 0 ${GRID}" fill="none" stroke="#1e3a5f" stroke-width="0.8"/>
    </pattern>
  </defs>
  <rect width="100%" height="100%" fill="#0a192f"/>
  <rect x="${PADDING}" y="${PADDING}" width="${(X_MAX - X_MIN + 1) * GRID}" height="${(Y_MAX - Y_MIN + 1) * GRID}" fill="url(#front-grid)"/>

  <!-- Title & Specs -->
  <text x="${PADDING}" y="36" fill="#38bdf8" font-size="18" font-weight="bold" letter-spacing="1.5">ZONE 03: THE WINGED GUARDIAN FOUNTAIN — SOUTH ELEVATION</text>
  <text x="${PADDING}" y="54" fill="#94a3b8" font-size="12">FRONT VIEW FACING NORTH · STATUE WELCOMING TRAVELERS ARRIVING FROM SPAWN</text>
`;

// Draw X Labels at bottom and Y Labels at left
for (let x = X_MIN; x <= X_MAX; x++) {
  const px = PADDING + (x - X_MIN) * GRID;
  svgFront += `<text x="${px + GRID / 2}" y="${PADDING + (Y_MAX - Y_MIN + 1) * GRID + 20}" fill="${x === 160 ? '#fde047' : '#64748b'}" font-size="10" text-anchor="middle">X${x}</text>`;
}
for (let y = Y_MIN; y <= Y_MAX; y++) {
  const py = PADDING + (Y_MAX - y) * GRID;
  svgFront += `<text x="${PADDING - 12}" y="${py + GRID / 2 + 4}" fill="${y === 104 ? '#38bdf8' : y === 113 ? '#fde047' : '#64748b'}" font-size="10" text-anchor="end">Y${y}</text>`;
  // Level guideline
  svgFront += `<line x1="${PADDING}" y1="${py + GRID}" x2="${PADDING + (X_MAX - X_MIN + 1) * GRID}" y2="${py + GRID}" stroke="#1e3a5f" stroke-width="1" stroke-dasharray="2 4"/>`;
}

// Render South elevation projection: for each (x, y), find the southernmost (largest z) non-air block
for (let y = Y_MIN; y <= Y_MAX; y++) {
  for (let x = X_MIN; x <= X_MAX; x++) {
    let frontBlk = null;
    for (let z = Z_MAX; z >= Z_MIN; z--) {
      const def = get(x, y, z);
      if (def && def.Name !== 'minecraft:air') {
        frontBlk = { def, z };
        break;
      }
    }
    if (!frontBlk) continue;

    const px = PADDING + (x - X_MIN) * GRID;
    const py = PADDING + (Y_MAX - y) * GRID;
    const color = getColor(frontBlk.def.Name);
    const opacity = frontBlk.def.Name === 'minecraft:water' ? 0.7 : 1.0;

    svgFront += `<rect x="${px + 1}" y="${py + 1}" width="${GRID - 2}" height="${GRID - 2}" rx="2" fill="${color}" fill-opacity="${opacity}" stroke="#0f2b48" stroke-width="1">
      <title>X:${x}, Y:${y}, Z:${frontBlk.z} - ${getLabel(frontBlk.def.Name)}</title>
    </rect>`;
  }
}

// Height Callouts
svgFront += `
  <!-- Ground Line -->
  <line x1="${PADDING - 20}" y1="${PADDING + (Y_MAX - 103 + 1) * GRID}" x2="${W_FRONT - PADDING + 20}" y2="${PADDING + (Y_MAX - 103 + 1) * GRID}" stroke="#64748b" stroke-width="2"/>
  <text x="${W_FRONT - PADDING + 25}" y="${PADDING + (Y_MAX - 103 + 1) * GRID - 4}" fill="#64748b" font-size="11">PLAZA GROUND (Y=103)</text>
  <text x="${W_FRONT - PADDING + 25}" y="${PADDING + (Y_MAX - 104) * GRID + 16}" fill="#38bdf8" font-size="11">BASIN RIM (Y=104)</text>
  <text x="${W_FRONT - PADDING + 25}" y="${PADDING + (Y_MAX - 109) * GRID + 16}" fill="#a855f7" font-size="11">SACRED URN (Y=109)</text>
  <text x="${W_FRONT - PADDING + 25}" y="${PADDING + (Y_MAX - 113) * GRID + 16}" fill="#fde047" font-size="11">WINGTIPS (Y=113)</text>
</svg>`;

// -------------------------------------------------------------
// 3. GENERATE COMPLETE INTERACTIVE HTML BLUEPRINT WIDGET
// -------------------------------------------------------------
const htmlContent = `<!DOCTYPE html>
<html lang="th">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>โซน 03 · แบบแปลนพิมพ์เขียวน้ำพุเทวทูตปีกเวหา</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    body { background-color: #060d17; color: #e2e8f0; font-family: ui-monospace, monospace; }
    .blueprint-card { background: #0a192f; border: 1px solid #1e3a5f; }
    .blueprint-tab[aria-selected="true"] { background: #1e3a5f; color: #38bdf8; border-color: #38bdf8; }
    .blueprint-grid { background-image: radial-gradient(#1e3a5f 1px, transparent 1px); background-size: 16px 16px; }
  </style>
</head>
<body class="p-4 md:p-6 max-w-7xl mx-auto">
  <!-- Header -->
  <header class="mb-6 border-b border-[#1e3a5f] pb-4 flex flex-wrap justify-between items-end gap-4">
    <div>
      <div class="flex items-center gap-2 text-xs text-[#38bdf8] font-bold tracking-widest uppercase mb-1">
        <span>ARCHITECTURAL BLUEPRINT</span>
        <span>•</span>
        <span>ZONE 03 CITY SQUARE</span>
      </div>
      <h1 class="text-2xl md:text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
        <span>THE WINGED GUARDIAN FOUNTAIN</span>
        <span class="text-xs px-2.5 py-1 bg-[#1e3a5f] text-[#38bdf8] rounded-full border border-[#38bdf8]/40">REV 2.0 APPLIED</span>
      </h1>
      <p class="text-sm text-slate-400 mt-1">
        พิกัดศูนย์กลาง: <code class="text-[#fde047]">X: 160, Y: 104, Z: 215</code> · สถาปัตยกรรมหินอ่อนควอตซ์ขาวบนอ่าง Deepslate แปดเหลี่ยม
      </p>
    </div>
    <div class="text-right text-xs text-slate-400">
      <div>MAP: <strong class="text-slate-200">starter_sky_land_spawn_v1</strong></div>
      <div>MC VERSION: <strong class="text-slate-200">Java 1.21.1 · DataVersion 3955</strong></div>
    </div>
  </header>

  <!-- Navigation Tabs -->
  <nav class="flex flex-wrap gap-2 mb-6">
    <button class="blueprint-tab px-4 py-2 text-sm font-semibold rounded-lg border border-[#1e3a5f] transition-colors" data-tab="front" aria-selected="true">
      📐 ภาพรูปด้านหน้า (South Elevation)
    </button>
    <button class="blueprint-tab px-4 py-2 text-sm font-semibold rounded-lg border border-[#1e3a5f] text-slate-400 transition-colors" data-tab="top" aria-selected="false">
      🗺️ ผังมุมมองด้านบน (Top-down Plan)
    </button>
    <button class="blueprint-tab px-4 py-2 text-sm font-semibold rounded-lg border border-[#1e3a5f] text-slate-400 transition-colors" data-tab="concept" aria-selected="false">
      🎨 ทัศนียภาพ 3D Concept Art
    </button>
    <button class="blueprint-tab px-4 py-2 text-sm font-semibold rounded-lg border border-[#1e3a5f] text-slate-400 transition-colors" data-tab="specs" aria-selected="false">
      📋 สถิติวัสดุและบล็อก (Bill of Materials)
    </button>
  </nav>

  <!-- Tab Contents -->
  <main>
    <!-- Tab 1: Front Elevation -->
    <section id="tab-front" class="blueprint-card rounded-xl p-4 md:p-6 mb-6">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-[#38bdf8] flex items-center gap-2">
          <span>SOUTH ELEVATION / รูปด้านใต้ (มองตรงเข้าหารูปสลักเทวทูต)</span>
        </h2>
        <span class="text-xs text-slate-400 bg-[#0f2b48] px-3 py-1 rounded-md border border-[#1e3a5f]">
          สเกล: 1 บล็อก = 28px
        </span>
      </div>
      <div class="overflow-auto border border-[#1e3a5f] rounded-lg p-2 bg-[#0a192f]">
        ${svgFront}
      </div>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mt-4 text-xs">
        <div class="p-3 bg-[#0a192f] border border-[#1e3a5f] rounded-lg">
          <span class="text-slate-400 block mb-1">ความสูงรวม</span>
          <span class="text-white font-bold text-sm">11 บล็อก (Y103–Y114)</span>
        </div>
        <div class="p-3 bg-[#0a192f] border border-[#1e3a5f] rounded-lg">
          <span class="text-slate-400 block mb-1">ปีกกว้างรวม</span>
          <span class="text-[#fde047] font-bold text-sm">9 บล็อก (X156–X164)</span>
        </div>
        <div class="p-3 bg-[#0a192f] border border-[#1e3a5f] rounded-lg">
          <span class="text-slate-400 block mb-1">ตำแหน่งคนโทน้ำ</span>
          <span class="text-[#38bdf8] font-bold text-sm">X160, Y109–110, Z216</span>
        </div>
        <div class="p-3 bg-[#0a192f] border border-[#1e3a5f] rounded-lg">
          <span class="text-slate-400 block mb-1">ทิศทางการหัน</span>
          <span class="text-emerald-400 font-bold text-sm">ทิศใต้ (+Z) สู่จุดเกิด</span>
        </div>
      </div>
    </section>

    <!-- Tab 2: Top-down Plan -->
    <section id="tab-top" class="blueprint-card rounded-xl p-4 md:p-6 mb-6 hidden">
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-lg font-bold text-[#38bdf8] flex items-center gap-2">
          <span>TOP-DOWN SITE PLAN / ผังระดับพื้นและแนวอ่างน้ำ</span>
        </h2>
        <span class="text-xs text-slate-400 bg-[#0f2b48] px-3 py-1 rounded-md border border-[#1e3a5f]">
          กรอบไซต์: 19×19 บล็อก (X:151..169, Z:206..224)
        </span>
      </div>
      <div class="overflow-auto border border-[#1e3a5f] rounded-lg p-2 bg-[#0a192f]">
        ${svgTop}
      </div>
      <div class="mt-4 p-4 bg-[#0a192f] border border-[#1e3a5f] rounded-lg text-xs space-y-1 text-slate-300">
        <div>• <strong>อ่างกากบาทแปดเหลี่ยม (Basin):</strong> ขนาด 13×13 บล็อก (X154–166, Z209–221) ขอบอ่าง Y104 ก้นอ่าง Y103 ผิวน้ำ Y104</div>
        <div>• <strong>ม้านั่งหิน 4 ทิศ (Stone Benches):</strong> เหนือ (Z207), ใต้ (Z223), ตะวันตก (X152), ตะวันออก (X168)</div>
        <div>• <strong>กระถางดอกไม้ประดับ (Corner Planters):</strong> Chiseled Deepslate + ดอกไม้ 4 มุมทแยง (153,208), (167,208), (153,222), (167,222)</div>
      </div>
    </section>

    <!-- Tab 3: 3D Concept Art -->
    <section id="tab-concept" class="blueprint-card rounded-xl p-4 md:p-6 mb-6 hidden">
      <h2 class="text-lg font-bold text-[#38bdf8] mb-4">
        ISOMETRIC 3D CONCEPT ART / ทัศนียภาพจำลองการออกแบบ
      </h2>
      <div class="border border-[#1e3a5f] rounded-lg overflow-hidden bg-black/40">
        <img src="file:///C:/Users/miw/.gemini/antigravity/brain/a3cbb705-55fd-4d27-a4e4-5225be5a2d80/neoworld_fountain_concepts_1792859427039.jpg" alt="Fountain Concept Art" class="w-full h-auto max-h-[600px] object-contain mx-auto">
      </div>
      <p class="text-xs text-slate-400 mt-3 text-center">
        แบบที่ 2 (ตรงกลางภาพ): The Winged Guardian — รูปสลักเทวทูตกางปีกสยายสีขาวบนอ่างหินสีเข้ม
      </p>
    </section>

    <!-- Tab 4: Materials & Specs -->
    <section id="tab-specs" class="blueprint-card rounded-xl p-4 md:p-6 mb-6 hidden">
      <h2 class="text-lg font-bold text-[#38bdf8] mb-4">
        BILL OF MATERIALS / รายการบล็อกที่ใช้สร้างจริง (รวม 575 บล็อกในไซต์)
      </h2>
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr class="border-b border-[#1e3a5f] text-slate-400 bg-[#060d17]">
              <th class="p-3">ชนิดบล็อก (Block State)</th>
              <th class="p-3">บทบาทในโครงสร้าง</th>
              <th class="p-3 text-right">จำนวน (บล็อก)</th>
              <th class="p-3 text-right">สัดส่วน</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-[#1e3a5f]/50">
            ${Object.entries(counts).sort((a,b) => b[1] - a[1]).map(([name, count]) => `
              <tr class="hover:bg-[#1e3a5f]/20">
                <td class="p-3 font-semibold text-white flex items-center gap-2">
                  <span class="w-3 h-3 rounded-full inline-block" style="background:${getColor(name)}"></span>
                  <code>${name}</code>
                </td>
                <td class="p-3 text-slate-400">${getBlockRole(name)}</td>
                <td class="p-3 text-right font-mono text-[#38bdf8] font-bold">${count}</td>
                <td class="p-3 text-right font-mono text-slate-400">${((count / voxels.length) * 100).toFixed(1)}%</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </section>
  </main>

  <footer class="text-center text-xs text-slate-500 mt-8 pt-4 border-t border-[#1e3a5f]">
    NEOWORLD ARCHITECTURAL REGISTRY · STARTER SKY TOWN · MAP: starter_sky_land_spawn_v1
  </footer>

  <script>
    const tabs = document.querySelectorAll('.blueprint-tab');
    const sections = {
      front: document.getElementById('tab-front'),
      top: document.getElementById('tab-top'),
      concept: document.getElementById('tab-concept'),
      specs: document.getElementById('tab-specs')
    };

    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        tabs.forEach(t => {
          t.setAttribute('aria-selected', 'false');
          t.classList.remove('text-[#38bdf8]');
          t.classList.add('text-slate-400');
        });
        tab.setAttribute('aria-selected', 'true');
        tab.classList.remove('text-slate-400');
        tab.classList.add('text-[#38bdf8]');

        const target = tab.dataset.tab;
        Object.entries(sections).forEach(([key, sec]) => {
          sec.classList.toggle('hidden', key !== target);
        });
      });
    });
  </script>
</body>
</html>`;

function getBlockRole(name) {
  if (name.includes('water')) return 'ผิวน้ำและสายน้ำมนตราจากคนโท';
  if (name.includes('stone_bricks')) return 'พื้นถนนรอบอ่างน้ำพุ';
  if (name.includes('deepslate_tiles')) return 'ก้นอ่างน้ำและฐานรากโครงสร้าง';
  if (name.includes('deepslate_tile_stairs')) return 'ขอบอ่างน้ำ ม้านั่งพักผ่อน และบันไดฐาน';
  if (name.includes('chiseled_deepslate')) return 'เสาแปดเหลี่ยมฐานประติมากรรมและกระถางดอกไม้';
  if (name.includes('smooth_quartz_stairs')) return 'ปีกเทวทูต ไหล่ และชายผ้าคลุมพริ้วไหว';
  if (name.includes('crying_obsidian')) return 'แกนพลังงานเวทมนตร์และลวดลายก้นอ่าง';
  if (name.includes('sea_lantern')) return 'โคมไฟส่องสว่างใต้ผิวน้ำ';
  if (name.includes('smooth_quartz')) return 'ลำตัวเทวทูต ศีรษะ และขนนกชั้นใน';
  if (name.includes('polished_andesite')) return 'ขอบพื้นถนนและแนวเชื่อมลาน';
  if (name.includes('smooth_stone')) return 'แนวแกนถนนหลักทิศเหนือ-ใต้';
  if (name.includes('deepslate_tile_wall')) return 'ค้ำยันเสาแปดเหลี่ยมแท่นรูปปั้น';
  if (name.includes('end_rod')) return 'ปลายปีกเทวทูตและประกายแสงดาว Halo';
  if (name.includes('smooth_quartz_slab')) return 'ปลายปีกชั้นบนและวงแหวนเทวทูต';
  if (name.includes('lily_of_the_valley')) return 'ดอกไม้ขาวประดับมุมทแยง';
  if (name.includes('blue_orchid')) return 'ดอกกล้วยไม้สีครามประดับมุมทแยง';
  if (name.includes('chiseled_quartz_block')) return 'คนโทน้ำศักดิ์สิทธิ์ (Sacred Urn)';
  if (name.includes('quartz_stairs')) return 'ปากคนโทน้ำศักดิ์สิทธิ์';
  if (name.includes('polished_diorite')) return 'เกราะอกหินอ่อนเทวทูต';
  return 'โครงสร้างตกแต่ง';
}

// Write files to docs/maps/zone03-fountain-blueprint
fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, 'fountain-top.svg'), svgTop);
fs.writeFileSync(path.join(OUT_DIR, 'fountain-front.svg'), svgFront);
fs.writeFileSync(path.join(OUT_DIR, 'fountain-blueprint.html'), htmlContent);
fs.writeFileSync(path.join(OUT_DIR, 'fountain-voxels.json'), JSON.stringify({
  center: [CX, 104, CZ],
  bounds: [X_MIN, Y_MIN, Z_MIN, X_MAX, Y_MAX, Z_MAX],
  totalVoxels: voxels.length,
  counts,
  voxels
}, null, 2));

// Also write to artifact directory for inline embed
fs.writeFileSync(path.join(ARTIFACT_DIR, 'winged_guardian_blueprint.html'), htmlContent);

console.log(JSON.stringify({
  status: 'exported',
  voxelsCount: voxels.length,
  files: [
    path.join(OUT_DIR, 'fountain-blueprint.html'),
    path.join(OUT_DIR, 'fountain-front.svg'),
    path.join(OUT_DIR, 'fountain-top.svg'),
    path.join(ARTIFACT_DIR, 'winged_guardian_blueprint.html')
  ]
}, null, 2));
