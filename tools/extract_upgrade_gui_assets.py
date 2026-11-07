"""Prepare approved Variant A frame plus existing NeoWorld widget art; no gameplay code."""
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
GENERATED = Path.home() / '.codex/generated_images/01a11ba5-dcf3-7012-8bfe-d66a81839a35'
DOC = ROOT / 'docs/gui/upgrade'
GUI = ROOT / 'src/main/resources/assets/neoworld/textures/gui'
OUT = GUI / 'upgrade'
DOC.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
shutil.copy2(GENERATED / 'exec-0ba99474-a3dd-45f5-98cc-a513bddb59fe.png', DOC / '01-approved-variant-a.png')
shutil.copy2(GENERATED / 'exec-c0be66a3-93b5-4a4b-a2f1-13b8c2254538.png', DOC / '02-frame-source.png')

metadata = {'texture': {'blur': False, 'clamp': True}}
assets = {}
def save(name, image):
    image = image.convert('RGBA')
    image.save(OUT / (name + '.png'))
    (OUT / (name + '.png.mcmeta')).write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    assets[name] = {'size': list(image.size), 'alphaBounds': list(image.getchannel('A').getbbox())}

frame = Image.open(DOC / '02-frame-source.png').convert('RGBA')
assert frame.getchannel('A').getextrema()[0] == 0, 'Frame exterior must be transparent'
bounds = frame.getchannel('A').getbbox()
save('upgrade_frame', frame.crop(bounds).resize((1024, 800), Image.Resampling.NEAREST))
for state in ['normal', 'hover', 'selected', 'equipped']:
    save('slot_' + state, Image.open(GUI / f'fashion/fashion_slot_{state}.png'))
# Export the shared primary-button states without their dynamic labels.
buttons = Image.open(GUI / 'common/buttons.png')
for state, x, width in [('normal', 28, 350), ('hover', 407, 347), ('selected', 784, 350), ('disabled', 1162, 348)]:
    save('button_' + state, buttons.crop((x, 441, x + width, 585)).resize((192, 80), Image.Resampling.NEAREST))

layout = {
    'logicalSize': [512, 400], 'frameTextureSize': [1024, 800],
    'title': [160, 8, 192, 32], 'closeButton': [480, 28, 16, 16],
    'equipmentSlot': [84, 80, 56, 56], 'stoneSlot': [42, 166, 40, 40], 'scrollSlot': [138, 166, 40, 40],
    'statsTable': [240, 76, 234, 88], 'statsColumns': [240, 340, 389, 435],
    'successBar': [240, 181, 234, 16], 'riskNote': [240, 201, 234, 10],
    'costRows': [240, 218, 150, 22], 'upgradeButton': [400, 218, 74, 22],
    'inventory': {'origin': [152, 279], 'columns': 9, 'rows': 3, 'pitch': 23, 'slotSize': 22, 'iconInset': 3},
    'hotbar': {'origin': [152, 353], 'columns': 9, 'rows': 1, 'pitch': 23, 'slotSize': 22, 'iconInset': 3},
    'reuse': {'buttons': 'NeoButton.Style.PRIMARY', 'progress': 'NeoProgressBar', 'slotMechanics': 'vanilla Slot'},
    'assets': assets,
}
(DOC / 'layout.json').write_text(json.dumps(layout, indent=2) + '\n', encoding='utf-8')

# Assemble exported textures for a visual layout check. Runtime assets stay text/icon-free.
review = Image.open(OUT / 'upgrade_frame.png').convert('RGBA')
def paste_asset(name, rect):
    x, y, w, h = rect
    image = Image.open(OUT / (name + '.png')).resize((w * 2, h * 2), Image.Resampling.NEAREST)
    review.alpha_composite(image, (x * 2, y * 2))
for key in ['equipmentSlot', 'stoneSlot', 'scrollSlot']:
    paste_asset('slot_normal', layout[key])
paste_asset('button_normal', layout['upgradeButton'])
for key in ['inventory', 'hotbar']:
    grid = layout[key]
    for row in range(grid['rows']):
        for col in range(grid['columns']):
            x = grid['origin'][0] + col * grid['pitch']
            y = grid['origin'][1] + row * grid['pitch']
            assert x + grid['slotSize'] <= 512 and y + grid['slotSize'] <= 400
            paste_asset('slot_normal', [x, y, grid['slotSize'], grid['slotSize']])
draw = ImageDraw.Draw(review)
for key in ['statsTable', 'successBar', 'riskNote', 'costRows']:
    x, y, w, h = layout[key]
    draw.rectangle((x * 2, y * 2, (x + w) * 2, (y + h) * 2), outline='#35B6AA', width=1)
    draw.text((x * 2 + 4, y * 2 + 3), key + ' (dynamic)', fill='#A6DCD6')
review.save(DOC / '03-exported-assets-layout-check.png')
assert layout['inventory']['columns'] * layout['inventory']['rows'] + layout['hotbar']['columns'] == 36
for filename in OUT.glob('*.png'):
    with Image.open(filename) as image:
        assert image.mode == 'RGBA'
    assert json.loads(Path(str(filename) + '.mcmeta').read_text()) == metadata
print(f'Exported {len(assets)} RGBA textures with metadata; 27 inventory + 9 hotbar slots verified')
print('Frame source alpha bounds:', bounds)
