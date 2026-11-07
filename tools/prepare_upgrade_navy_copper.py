"""Split approved navy/copper raster art; retain hard alpha and widget registration."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/gui/upgrade/navy_copper'
OUT = ROOT / 'src/main/resources/assets/neoworld/textures/gui/upgrade/navy_copper'
DOC.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)
source = Image.open(DOC / 'components-source.png').convert('RGBA')
reference = Image.open(DOC / 'approved-mockup.png').convert('RGBA')
regions = {
    'outer_frame': (36, 72, 391, 270),
    'panel_frame': (410, 72, 730, 271),
    'title_plaque': (736, 111, 1137, 242),
    'section_header': (1150, 132, 1507, 222),
    'equipment_slot': (103, 302, 324, 509),
    'inventory_slot': (459, 300, 679, 509),
    'progress_track': (756, 377, 1118, 448),
    'progress_fill': (1164, 384, 1492, 439),
    'button_normal': (36, 560, 388, 697),
    'button_hover': (405, 560, 759, 697),
    'button_pressed': (774, 560, 1130, 697),
    'button_disabled': (1147, 560, 1503, 697),
    'close_backing': (122, 751, 303, 937),
    'icon_hammer': (469, 735, 686, 941),
    'icon_anvil': (839, 767, 1070, 927),
    'icon_shield': (1240, 740, 1411, 946),
}
assets = {}
images = {}
def save(name, art, crop=None):
    art.save(OUT / (name + '.png'))
    (OUT / (name + '.png.mcmeta')).write_text(
        json.dumps({'texture': {'blur': False, 'clamp': True}}, indent=2) + '\n')
    assets[name] = {'size': list(art.size), 'alpha_bounds': list(art.getbbox()),
                    'source_crop': crop, 'padding': 2}
    images[name] = art

for name, box in regions.items():
    art = source.crop(box)
    # Generated transparent art contains almost-opaque interiors and soft halos.
    # Threshold alpha only; never key out the actual navy or copper surfaces.
    art.putalpha(art.getchannel('A').point(lambda a: 255 if a >= 160 else 0))
    art = art.crop(art.getbbox())
    if name == 'progress_fill':
        # Remove the generated outline: the track owns the border.
        art = art.crop((6, 6, art.width - 6, art.height - 6))
    if name.startswith('button_'):
        art = art.resize((344, 124), Image.Resampling.NEAREST)
    canvas = Image.new('RGBA', (art.width + 4, art.height + 4))
    canvas.paste(art, (2, 2))
    save(name, canvas, list(box))

# These three small decorations are taken directly from the approved image.
for name, box in {'icon_swords': (885, 177, 924, 215),
                  'icon_coins': (167, 556, 204, 595),
                  'icon_chest': (878, 554, 923, 596)}.items():
    art = reference.crop(box)
    alpha = Image.new('L', art.size)
    alpha.putdata([255 if r > 90 and r > g * 1.12 and g > b * 1.35 else 0
                   for r, g, b, a in art.getdata()])
    art.putalpha(alpha)
    art = art.crop(art.getbbox())
    canvas = Image.new('RGBA', (art.width + 4, art.height + 4))
    canvas.paste(art, (2, 2))
    save(name, canvas, list(box))

manifest = {'reference': 'approved-mockup.png', 'source': 'components-source.png',
            'status': 'Prepared assets only; screen not wired to this skin yet.',
            'sampling': 'nearest; hard alpha; transparent 2px padding',
            'slicing': 'Use nine-slice for panels/slots, three-slice for plaques/buttons. '
                       'Source borders must be measured at final runtime size before integration.',
            'assets': assets}
(DOC / 'assets.json').write_text(json.dumps(manifest, indent=2) + '\n')

preview = Image.new('RGB', (1280, 5 * 220), '#161d28')
d = ImageDraw.Draw(preview)
for i, (name, art) in enumerate(images.items()):
    x, y = (i % 4) * 320, (i // 4) * 220
    for yy in range(y + 28, y + 214, 12):
        for xx in range(x + 8, x + 312, 12):
            d.rectangle((xx, yy, min(xx + 11, x + 311), min(yy + 11, y + 213)),
                        fill=('#344052' if ((xx-x)//12+(yy-y)//12)%2 else '#253043'))
    scale = min(296 / art.width, 170 / art.height, 1)
    thumb = art.resize((round(art.width * scale), round(art.height * scale)), Image.Resampling.NEAREST)
    preview.paste(thumb, (x + (320-thumb.width)//2, y + 32 + (174-thumb.height)//2), thumb)
    d.text((x + 10, y + 7), name + ' ' + str(art.size), fill='white')
preview.save(DOC / 'assets-preview.png')

for name, art in images.items():
    assert set(art.getchannel('A').getdata()) == {0, 255}, name
    assert art.getbbox()[0] >= 2 and art.getbbox()[1] >= 2, name
assert len({images[n].size for n in images if n.startswith('button_')}) == 1
print(f'Exported {len(images)} RGBA assets; binary alpha, padding and button dimensions verified.')
