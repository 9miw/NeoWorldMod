"""Derive a blade-only edge mask from the actual vanilla sprite used by the example sword."""
import io
import json
import zipfile
from pathlib import Path
from PIL import Image, ImageFilter

root = Path(__file__).resolve().parents[1]
cache = Path.home() / '.gradle/caches/neoformruntime/intermediate_results'
entry = 'assets/minecraft/textures/item/diamond_sword.png'
source = None
for path in cache.glob('stripClient*resourcesOutput.jar'):
    with zipfile.ZipFile(path) as jar:
        if entry in jar.namelist():
            source = Image.open(io.BytesIO(jar.read(entry))).convert('RGBA')
            break
if source is None:
    raise FileNotFoundError('Vanilla diamond sword texture is not present in the local game cache')
mask = Image.new('L', source.size)
for y in range(source.height):
    for x in range(source.width):
        r, g, b, a = source.getpixel((x, y))
        # Only the upper-right blade; excludes the crossguard and brown grip.
        if a and x >= 7 and y <= 8 and g > r * 1.1 and b > 60:
            mask.putpixel((x, y), 255)
assert mask.getbbox() is not None
mask = mask.resize((64, 64), Image.Resampling.NEAREST)
source = source.resize((64, 64), Image.Resampling.NEAREST)
inner = mask.filter(ImageFilter.MinFilter(3))
edge = Image.new('RGBA', source.size)
halo = Image.new('RGBA', source.size)
expanded = mask.filter(ImageFilter.MaxFilter(3))
for y in range(source.height):
    for x in range(source.width):
        value = mask.getpixel((x, y))
        edge.putpixel((x, y), (255, 255, 255, 255 if value and not inner.getpixel((x, y)) else 0))
        halo.putpixel((x, y), (255, 255, 255, 70 if expanded.getpixel((x, y)) and not value else 0))
output = root / 'src/main/resources/assets/neoworld/textures/effect'
output.mkdir(parents=True, exist_ok=True)
edge.save(output / 'example_sword_edge.png')
halo.save(output / 'example_sword_halo.png')
for filename in ('example_sword_edge.png', 'example_sword_halo.png'):
    with Image.open(output / filename) as image:
        assert image.size == source.size and image.getchannel('A').getextrema()[0] == 0

# Asset-only review sheet; this is not a screenshot of the in-game renderer.
colors = [(0, 0, 0), (234, 244, 255), (66, 207, 255), (189, 112, 255),
          (255, 206, 85), (255, 101, 64), (255, 231, 147)]
preview = Image.new('RGBA', (7 * 192, 224), (23, 27, 34, 255))
from PIL import ImageDraw
draw = ImageDraw.Draw(preview)
labels = ['+0', '+1-3', '+4-6', '+7-9', '+10-12', '+13-14', '+15']
for index, color in enumerate(colors):
    sprite = source.copy()
    if index >= 2:
        for layer in (halo, edge):
            overlay = Image.new('RGBA', source.size, (*color, 0))
            overlay.putalpha(layer.getchannel('A'))
            sprite.alpha_composite(overlay)
    preview.alpha_composite(sprite.resize((160, 160), Image.Resampling.NEAREST), (index * 192 + 16, 32))
    draw.text((index * 192 + 72, 8), labels[index], fill='white')
draw.text((16, 205), 'BLADE MASK ASSET CHECK - not an in-game screenshot; attached runes/sparks are rendered in Java', fill='white')
preview.save(root / 'docs/items/upgrade-sword-mask-preview.png')

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AssertionError('Duplicate mixin JSON key: ' + key)
        result[key] = value
    return result
mixins = json.loads((root / 'src/main/resources/neoworld.mixins.json').read_text(), object_pairs_hook=unique_object)
assert 'client.UpgradeTrailMixin' in mixins['client']
assert 'client.UpgradeItemRenderMixin' in mixins['client']
assert 'ThaiBitmapDefinitionMixin' in mixins['client']
print('Blade masks, asset preview and unique client mixin entries verified')
