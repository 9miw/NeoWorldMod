"""Draw small code-native geometric spark/rune sprites with transparent backgrounds."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
target = root / 'src/main/resources/assets/neoworld/textures/particle'
target.mkdir(parents=True, exist_ok=True)
spark = Image.new('RGBA', (16, 16))
draw = ImageDraw.Draw(spark)
draw.rectangle((7, 2, 8, 13), fill=(255, 255, 255, 150))
draw.rectangle((2, 7, 13, 8), fill=(255, 255, 255, 150))
draw.rectangle((6, 6, 9, 9), fill=(255, 255, 255, 255))
spark.save(target / 'upgrade_spark.png')
rune = Image.new('RGBA', (16, 16))
draw = ImageDraw.Draw(rune)
draw.line([(5, 13), (5, 3), (11, 3), (11, 7), (5, 7)], fill='white', width=2)
draw.line([(8, 10), (12, 10), (12, 13), (8, 13)], fill='white', width=2)
draw.rectangle((2, 4, 2, 5), fill=(255, 255, 255, 140))
rune.save(target / 'upgrade_rune.png')
for name in ('upgrade_spark', 'upgrade_rune'):
    with Image.open(target / (name + '.png')) as image:
        assert image.mode == 'RGBA' and image.size == (16, 16)
        assert image.getchannel('A').getextrema() == (0, 255)
print('Two 16x16 RGBA sprites created and checked')
