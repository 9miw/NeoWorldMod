"""Reproduce measured crops from the generated sources; no color-key removal."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = ROOT / 'src/main/resources/assets/neoworld/textures/gui/activity'
OUT.mkdir(parents=True, exist_ok=True)
source = Image.open(HERE / 'components-source.png').convert('RGBA')
frame = Image.open(HERE / 'frame-source.png').convert('RGBA')
cols = [(20, 400), (400, 776), (776, 1140), (1140, 1520)]
rows = [(40, 240), (280, 450), (470, 740), (750, 1000)]
names = ['normal', 'hover', 'selected', 'disabled']
manifest = {}

def save(name, image, size, rectangle):
    image = image.resize(size, Image.Resampling.NEAREST)
    assert image.mode == 'RGBA'
    if isinstance(rectangle, tuple):
        alpha = image.getchannel('A')
        edge = max(alpha.crop((0, 0, image.width, 1)).getextrema()[1],
                   alpha.crop((0, image.height-1, image.width, image.height)).getextrema()[1],
                   alpha.crop((0, 0, 1, image.height)).getextrema()[1],
                   alpha.crop((image.width-1, 0, image.width, image.height)).getextrema()[1])
        assert edge < 128, f'{name}: opaque artwork touches a crop edge'
    image.save(OUT / (name + '.png'))
    (OUT / (name + '.png.mcmeta')).write_text(json.dumps({'texture': {'blur': False, 'clamp': True}}))
    manifest[name] = {'source_rectangle': rectangle, 'size': size,
                      'alpha_extrema': image.getchannel('A').getextrema()}
    return image

sprites = {}
for row, (top, bottom) in enumerate(rows):
    for col, (left, right) in enumerate(cols):
        tile = source.crop((left, top, right, bottom))
        bounds = tile.getchannel('A').point(lambda v: 255 if v >= 128 else 0).getbbox()
        assert bounds is not None
        x0, y0, x1, y1 = bounds
        # All visible solid pixels must have a transparent safety gutter in the source cell.
        assert x0 > 4 and y0 > 4 and x1 < tile.width - 4 and y1 < tile.height - 4
        box = (left+x0-4, top+y0-4, left+x1+4, top+y1+4)
        name = (['nav_', 'button_', 'slot_'][row] + names[col]) if row < 3 else ['icon_daily', 'icon_monthly', 'icon_online', 'icon_pending'][col]
        size = [(320, 128), (280, 100), (96, 96), (32, 32)][row]
        sprites[name] = save(name, source.crop(box), size, box)

save('frame', frame.crop((37, 22, 1500, 986)), (1280, 840), (37, 22, 1500, 986))
# Track and thumb are derived from the same blank button art, rather than unrelated flat fills.
save('scroll_track', sprites['slot_disabled'].crop((40, 8, 56, 88)), (16, 160), 'slot_disabled:40,8,56,88')
save('scroll_thumb', sprites['button_disabled'].rotate(90, expand=True), (32, 128), 'button_disabled:rotate90')
save('icon_silver', Image.open(ROOT / 'src/main/resources/assets/neoworld/textures/gui/market/silver_coin.png').convert('RGBA'),
     (32, 32), 'shared market/silver_coin.png')
(HERE / 'asset-manifest.json').write_text(json.dumps(manifest, indent=2))

# Inspect exported parts together at their actual logical dimensions on a neutral background.
preview = Image.new('RGBA', (960, 640), '#24282c')
for i, (name, info) in enumerate(manifest.items()):
    if name == 'frame': continue
    img = Image.open(OUT / (name + '.png'))
    img.thumbnail((180, 100), Image.Resampling.NEAREST)
    x, y = (i % 4) * 240 + 16, (i // 4) * 120 + 18
    preview.alpha_composite(img, (x, y))
    ImageDraw.Draw(preview).text((x, y+101), name, fill='white')
preview.convert('RGB').save(HERE / 'exported-parts-preview.png')
print(f'Exported and checked {len(manifest)} PNGs; all component crops contain their solid alpha bounds.')
