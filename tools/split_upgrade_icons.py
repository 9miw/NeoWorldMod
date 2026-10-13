"""Extract the approved 3x2 upgrade atlas without regenerating Java or translations."""
from pathlib import Path
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
NAMES = (
    'upgrade_stone', 'advanced_upgrade_stone', 'supreme_upgrade_stone',
    'downgrade_protection_scroll', 'break_protection_scroll', 'upgrade_table',
)


def main():
    assets = ROOT / 'src/main/resources/assets/neoworld'
    with Image.open(ROOT / 'docs/items/upgrade-items-preview.png') as source:
        if source.mode != 'RGBA' or source.width % 3 or source.height % 2:
            raise ValueError('Expected an RGBA atlas with three columns and two rows')
        width, height = source.width // 3, source.height // 2
        preview = Image.new('RGBA', (768, 512), (24, 28, 36, 255))
        for index, name in enumerate(NAMES):
            x, y = index % 3, index // 3
            icon = source.crop((x * width, y * height, (x + 1) * width, (y + 1) * height))
            icon = icon.resize((64, 64), Image.Resampling.NEAREST)
            alpha_min, alpha_max = icon.getchannel('A').getextrema()
            if alpha_min != 0 or alpha_max == 0:
                raise ValueError(f'{name}: missing transparent margin or visible pixels')
            path = assets / f'textures/item/{name}.png'
            path.parent.mkdir(parents=True, exist_ok=True)
            icon.save(path)
            model = json.loads((assets / f'models/item/{name}.json').read_text(encoding='utf-8'))
            if model.get('textures', {}).get('layer0') != f'neoworld:item/{name}':
                raise ValueError(f'{name}: item model does not reference its icon')
            preview.alpha_composite(icon.resize((256, 256), Image.Resampling.NEAREST), (x * 256, y * 256))
            print(f'{name}: 64x64 RGBA, model reference OK')
        preview.save(ROOT / 'docs/items/upgrade-items-ingame-icons.png')


if __name__ == '__main__':
    main()
