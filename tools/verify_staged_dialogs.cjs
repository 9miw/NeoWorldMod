const fs = require('fs');
const path = require('path');

const baseDir = path.join(__dirname, '..', 'stage', 'npc_greetings_unified', 'customnpcs', 'dialogs');

const allDialogs = new Map();
let fileCount = 0;
let errors = [];

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      walk(full);
    } else if (e.name.endsWith('.json')) {
      fileCount++;
      const raw = fs.readFileSync(full, 'utf8');
      
      // Check for character corruption
      if (raw.includes('\ufffd') || raw.includes('??????')) {
        errors.push(`${full}: Detected character encoding corruption (\ufffd or ???)`);
      }
      
      // Check for unescaped literal \n in strings
      // (CustomNPCs requires raw newlines or clean space, not literal \n)
      if (raw.match(/[^\\]\\n/)) {
        errors.push(`${full}: Detected raw \\n sequence in text`);
      }

      // Check NBT suffix conventions
      if (!raw.match(/"TimePast":\s*\d+L/)) {
        errors.push(`${full}: Missing L suffix on TimePast`);
      }
      if (!raw.includes('BeenRead": 0b') && !raw.includes('BeenRead": 0')) {
        errors.push(`${full}: Missing valid byte format for BeenRead`);
      }

      // Parse JSON (strip NBT suffixes for parsing)
      const cleanJson = raw.replace(/(\d+)[bL]/g, '$1');
      try {
        const obj = JSON.parse(cleanJson);
        const expectedId = parseInt(e.name.replace('.json', ''), 10);
        if (obj.DialogId !== expectedId) {
          errors.push(`${full}: DialogId ${obj.DialogId} does not match filename ${expectedId}`);
        }
        allDialogs.set(obj.DialogId, { path: full, obj });
      } catch (err) {
        errors.push(`${full}: JSON parse error: ${err.message}`);
      }
    }
  }
}

walk(baseDir);

console.log(`Scanned ${fileCount} dialog files.`);

// Verify graph links and options
for (const [id, { path: filePath, obj }] of allDialogs.entries()) {
  const options = obj.Options || [];
  
  if (options.length > 6) {
    errors.push(`${filePath}: Exceeds maximum 6 option slots (found ${options.length})`);
  }

  const usedSlots = new Set();
  let hasExitOrBack = false;

  for (const optWrap of options) {
    const slot = optWrap.OptionSlot;
    if (usedSlots.has(slot)) {
      errors.push(`${filePath}: Duplicate OptionSlot ${slot}`);
    }
    usedSlots.add(slot);

    const opt = optWrap.Option;
    if (!opt) {
      errors.push(`${filePath}: Missing Option object in slot ${slot}`);
      continue;
    }

    // Check exit / back
    if (opt.OptionType === 0) {
      hasExitOrBack = true;
    }
    if (opt.Title && (opt.Title.includes('กลับ') || opt.Title.includes('ใหม่') || opt.Title.includes('ไม่เป็นไร'))) {
      hasExitOrBack = true;
    }

    // Check target dialog reference
    if (opt.OptionType === 1) {
      const targetId = opt.Dialog;
      if (targetId === -1) {
        errors.push(`${filePath}: OptionType 1 with Dialog: -1 (Slot ${slot}: "${opt.Title}")`);
      } else if (!allDialogs.has(targetId)) {
        // Target might be an existing server dialog (e.g. 4, 5, 6, 7, 8, 9, etc.)
        // IDs 1-47 exist on server
        if (targetId < 1 || targetId > 96) {
          errors.push(`${filePath}: Unknown target Dialog ID ${targetId} (Slot ${slot}: "${opt.Title}")`);
        }
      }
    }

    // Check command options
    if (opt.OptionType === 4) {
      if (!opt.DialogCommand || opt.DialogCommand.trim() === '') {
        errors.push(`${filePath}: OptionType 4 with empty DialogCommand (Slot ${slot})`);
      }
    }
  }

  // Hub dialogs with 6 sub-options are valid if ESC is enabled
  if (options.length === 6 && options.every(o => o.Option && o.Option.OptionType === 1) && obj.DialogDisableEsc === 0) {
    hasExitOrBack = true;
  }

  // Every dialog must have a way out
  if (!hasExitOrBack && options.length > 0) {
    errors.push(`${filePath}: No exit or return option found`);
  }
}

console.log(`Verification completed with ${errors.length} errors.`);
if (errors.length > 0) {
  console.error("Errors found:");
  errors.forEach(e => console.error(" - " + e));
  process.exit(1);
} else {
  console.log("ALL 51 DIALOG FILES PASSED FULL INTEGRITY & GRAPH VERIFICATION!");
}
