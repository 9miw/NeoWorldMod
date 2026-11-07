"""Normalize the new item icons from saved sources without changing their art."""
from pathlib import Path
from PIL import Image
import json

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/neoworld'
preview = Image.new('RGBA', (512, 256), (25, 29, 37, 255))
for index, name in enumerate(['protection_scroll', 'success_booster']):
    with Image.open(root / f'docs/items/{name}-source.png') as source:
        source = source.convert('RGBA')
        bounds = source.getchannel('A').point(lambda value: 255 if value > 32 else 0).getbbox()
        assert bounds is not None
        icon = source.crop(bounds)
        factor = 56 / max(icon.size)
        icon = icon.resize((max(1, round(icon.width * factor)), max(1, round(icon.height * factor))), Image.Resampling.NEAREST)
        output = Image.new('RGBA', (64, 64))
        output.alpha_composite(icon, ((64-icon.width)//2, (64-icon.height)//2))
        output.save(assets / f'textures/item/{name}.png')
        model = json.loads((assets / f'models/item/{name}.json').read_text())
        assert model['textures']['layer0'] == f'neoworld:item/{name}'
        assert output.getchannel('A').getextrema()[0] == 0
        preview.alpha_composite(output.resize((256,256), Image.Resampling.NEAREST), (index*256,0))
preview.save(root / 'docs/items/upgrade-consumables-preview.png')
print('Two normalized 64x64 transparent icons and model references verified')
