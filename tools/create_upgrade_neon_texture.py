"""A white sample texture for code-rendered emissive mesh shells."""
from pathlib import Path
from PIL import Image
import json

target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/neoworld/textures/effect/neon_white.png'
target.parent.mkdir(parents=True, exist_ok=True)
Image.new('RGBA', (1, 1), (255, 255, 255, 255)).save(target)
Path(str(target) + '.mcmeta').write_text(json.dumps({'texture': {'blur': False, 'clamp': True}}, indent=2) + '\n')
with Image.open(target) as image:
    assert image.mode == 'RGBA' and image.getpixel((0, 0)) == (255, 255, 255, 255)
print('Neon mesh texture verified')
