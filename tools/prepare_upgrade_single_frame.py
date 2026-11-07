"""Export the approved single-shell frame and check native inventory bounds."""
from pathlib import Path
import json
import shutil
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
generated = Path.home() / '.codex/generated_images/01a11ba5-dcf3-7012-8bfe-d66a81839a35'
docs = root / 'docs/gui/upgrade'
out = root / 'src/main/resources/assets/neoworld/textures/gui/upgrade'
docs.mkdir(parents=True, exist_ok=True)
shutil.copy2(generated / 'exec-a493d512-767d-4584-a7ae-afa956c7b33d.png', docs / '04-approved-single-frame.png')
shutil.copy2(generated / 'exec-f3f9500b-05f9-4ea1-8fb0-4f826e07282e.png', docs / '05-single-frame-source.png')
with Image.open(docs / '05-single-frame-source.png') as source:
    source = source.convert('RGBA')
    assert source.getchannel('A').getextrema()[0] == 0
    bounds = source.getchannel('A').point(lambda value: 255 if value > 32 else 0).getbbox()
    frame = source.crop(bounds).resize((640, 464), Image.Resampling.NEAREST)
frame.save(out / 'upgrade_single_frame.png')
(out / 'upgrade_single_frame.png.mcmeta').write_text(json.dumps({'texture': {'blur': False, 'clamp': True}}, indent=2) + '\n')
layout = {'size': [320, 232], 'inventory': [147, 151, 9, 3, 18], 'hotbarY': 207,
          'dividerX': 138, 'dividerY': 138, 'action': [18, 206, 110, 16],
          'frameSourceAlphaBounds': list(bounds), 'frameTextureSize': [640, 464]}
(docs / 'single-frame-layout.json').write_text(json.dumps(layout, indent=2) + '\n')
preview = frame.copy()
draw = ImageDraw.Draw(preview)
draw.line((276, 56, 276, 450), fill='#777F82', width=2)
draw.line((16, 276, 624, 276), fill='#777F82', width=2)
socket = Image.open(out / 'slot_normal.png').convert('RGBA').resize((36, 36), Image.Resampling.NEAREST)
for row in range(4):
    for col in range(9):
        x = 147 + col * 18 - 1
        y = (151 + row * 18 if row < 3 else 207) - 1
        assert x > 138 and x + 18 <= 312 and y >= 150 and y + 18 <= 224
        preview.alpha_composite(socket, (x * 2, y * 2))
for x, y in [(62, 56), (36, 94), (94, 94)]:
    preview.alpha_composite(socket, ((x - 1) * 2, (y - 1) * 2))
button = Image.open(out / 'button_normal.png').resize((220, 32), Image.Resampling.NEAREST)
preview.alpha_composite(button, (36, 412))
preview.save(docs / '06-single-frame-layout-check.png')
print('Single frame exported; all 36 slots inside frame, right of divider, with 8px bottom clearance')
