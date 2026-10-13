"""Generate the in-mod Roboto SemiBold bitmap, metrics and previews. Requires Pillow."""
import io
import json
import math
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).parent / 'roboto'
ASSETS = ROOT / 'src/main/resources/assets/neoworld'
UPSTREAM = 'https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/'
FONT_WEIGHT = 600
SOURCE.mkdir(parents=True, exist_ok=True)
for name in ('Roboto[wdth,wght].ttf', 'OFL.txt'):
    path = SOURCE / name
    if not path.exists():
        with urllib.request.urlopen(UPSTREAM + urllib.parse.quote(name), timeout=60) as response:
            path.write_bytes(response.read())

def regular(size):
    font = ImageFont.truetype(str(SOURCE / 'Roboto[wdth,wght].ttf'), size)
    axes = font.get_variation_axes()
    font.set_variation_by_axes([FONT_WEIGHT if a['name'] == b'Weight' else a['default'] for a in axes])
    return font

font = regular(64)
codes = list(range(33, 127)) + [0xa3, 0xb0, 0xb7, 0xd7, 0xf7, 0x2013, 0x2014,
                                  0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2026, 0x20ac]
cols, cell_h = 16, 96
cell_w = max(8 * math.ceil((font.getbbox(chr(c), anchor='ls')[2]
                           - min(0, font.getbbox(chr(c), anchor='ls')[0])) / 8) for c in codes)
rows = math.ceil(len(codes) / cols)
atlas = Image.new('RGBA', (cols * cell_w, rows * cell_h))
draw = ImageDraw.Draw(atlas)
metrics = {}
for index, cp in enumerate(codes):
    left, top, right, bottom = font.getbbox(chr(cp), anchor='ls')
    assert top >= -72 and bottom <= 24, (cp, top, bottom)
    bearing = min(0, left)
    x, y = index % cols * cell_w, index // cols * cell_h
    draw.text((x-bearing, y+72), chr(cp), anchor='ls', font=font, fill='white')
    metrics[str(cp)] = {'advance': font.getlength(chr(cp))/8, 'bearingLeft': bearing/8}
texture = ASSETS / 'textures/font/roboto/regular.png'
texture.parent.mkdir(parents=True, exist_ok=True)
atlas.save(texture)
spaces = {' ': font.getlength(' ')/8, '\u00a0': font.getlength('\u00a0')/8}
chars = []
for row in range(rows):
    entries = codes[row*cols:(row+1)*cols]
    chars.append(''.join(map(chr, entries)) + '\0' * (cols-len(entries)))
definition = {'providers': [
    {'type':'space','advances':spaces},
    {'type':'bitmap','file':'neoworld:font/roboto/regular.png','height':12,'ascent':9,'chars':chars}
]}
(ASSETS / 'font/roboto.json').write_text(json.dumps(definition, indent=2, ensure_ascii=True)+'\n', encoding='utf-8')
(ASSETS / 'roboto_metrics.json').write_text(json.dumps(metrics, indent=2)+'\n', encoding='utf-8')
license_path = ROOT / 'src/main/resources/META-INF/licenses/Roboto-OFL.txt'
license_path.parent.mkdir(parents=True, exist_ok=True)
license_path.write_bytes((SOURCE / 'OFL.txt').read_bytes())

# Preview the generated PNGs and stored bearings, including the existing Thai bitmap.
thai_metrics = json.loads((ASSETS / 'thaifix_metrics.json').read_text(encoding='utf-8'))
def line(text):
    canvas = Image.new('RGBA', (600*8, cell_h))
    pen = 0.0
    for ch in text:
        key = str(ord(ch))
        if ch in spaces:
            pen += spaces[ch]
            continue
        if key in metrics:
            index = codes.index(ord(ch))
            x, y = index % cols * cell_w, index // cols * cell_h
            tile = atlas.crop((x,y,x+cell_w,y+cell_h))
            metric = metrics[key]
        elif key in thai_metrics:
            tile = Image.open(ASSETS / f'textures/font/thai/{ord(ch):04x}.png').convert('RGBA')
            metric = thai_metrics[key]
        else:
            raise ValueError(f'Unsupported preview character: {ord(ch):04x}')
        canvas.alpha_composite(tile,(round((pen+metric['bearingLeft'])*8),0))
        pen += metric['advance']
    return canvas.crop((0,0,math.ceil(pen*8)+cell_w,cell_h)).resize(
        (math.ceil(pen)+cell_w//8,12), Image.Resampling.LANCZOS)

preview = Image.new('RGB',(1080,430),'#161c21')
pd = ImageDraw.Draw(preview)
label = regular(20)
pd.text((24,18),f'Roboto SemiBold {FONT_WEIGHT} / bitmap / 8 px em',font=label,fill='#66ddd5')
pd.text((24,52),'Native size                           4x enlarged',font=label,fill='#9ea7ae')
samples = ['Equipment: Iron Sword', 'Required Level 25  /  Attack +12.5%',
           'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz 0123456789',
           'Price: 1,250 Gold - Buy / Sell', '\u0e01\u0e34 \u0e01\u0e35\u0e48 \u0e01\u0e38 \u0e01\u0e39\u0e49 \u0e19\u0e49\u0e33  Level 25']
for index,text in enumerate(samples):
    y = 92+index*52
    strip = line(text)
    preview.paste(strip,(24,y+16),strip)
    large = strip.resize((strip.width*4,strip.height*4),Image.Resampling.NEAREST)
    preview.paste(large,(320,y),large)
output = ROOT / 'docs/gui/fonts/roboto-bitmap-preview.png'
output.parent.mkdir(parents=True,exist_ok=True)
preview.save(output)
previous = output.with_name('roboto-bitmap-preview-400.png')
if previous.exists():
    comparison = Image.new('RGB',(1080,390),'#161c21')
    comparison.paste(Image.open(previous).crop((0,0,1080,195)),(0,0))
    comparison.paste(preview.crop((0,0,1080,195)),(0,195))
    comparison.save(output.with_name('roboto-weight-comparison.png'))
assert len(metrics) == len(codes) and len(chars) == rows
print(f'Generated {len(codes)} Roboto glyphs, two spaces; atlas {atlas.size}; weight {FONT_WEIGHT}')
print(f'Preview: {output}')
