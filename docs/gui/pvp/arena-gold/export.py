"""Export measured disjoint glyphs from the approved Arena Gold source; run at repo root."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[4]
SOURCE = Path(__file__).with_name('source.png')
OUT = ROOT / 'src/main/resources/assets/neoworld/textures/font/pvp_titles'
FONT = ROOT / 'src/main/resources/assets/neoworld/font/pvp_titles.json'
REGIONS = [
    ('victory', (0, 0, 628, 390)), ('defeat', (628, 0, 1254, 390)),
    ('5', (0, 390, 628, 675)), ('4', (628, 390, 1254, 675)),
    ('3', (0, 675, 628, 945)), ('2', (628, 675, 1254, 945)),
    ('1', (0, 945, 628, 1254)), ('fight', (628, 945, 1254, 1254)),
]

def main():
    source = Image.open(SOURCE).convert('RGBA')
    assert source.size == (1254, 1254)
    assert source.getchannel('A').getextrema() == (0, 255)
    OUT.mkdir(parents=True, exist_ok=True)
    providers, manifest, images = [], [], []
    for i, (name, rect) in enumerate(REGIONS):
        image = source.crop(rect)
        # Alpha-only fringe cleanup; never color-key dark lettering or shadows.
        image.putalpha(image.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
        bounds = image.getbbox()
        assert bounds is not None
        image = image.crop(bounds)
        factor = (124 if name.isdigit() else 252) / image.width
        size = (round(image.width * factor), round(image.height * factor))
        image = image.resize(size, Image.Resampling.NEAREST)
        canvas = (128, 96) if name.isdigit() else (size[0]+4, size[1]+4)
        assert size[0]+4 <= canvas[0] and size[1]+4 <= canvas[1]
        padded = Image.new('RGBA', canvas)
        padded.paste(image, ((canvas[0]-size[0])//2, (canvas[1]-size[1])//2))
        assert set(padded.getchannel('A').get_flattened_data()) == {0, 255}
        padded.save(OUT / f'{name}.png')
        (OUT / f'{name}.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":true}}\n')
        glyph = chr(0xE400 + i)
        providers.append(dict(type='bitmap', file=f'neoworld:font/pvp_titles/{name}.png',
                              ascent=14, height=18, chars=[glyph]))
        manifest.append(dict(name=name, glyph=f'U+{0xE400+i:04X}', source_rect=rect,
                             alpha_crop=bounds, output_size=padded.size,
                             title_gui_width=round(18*padded.width/padded.height)*4))
        images.append(padded)
    FONT.write_text(json.dumps(dict(providers=providers), ensure_ascii=True, indent=2)+'\n')
    SOURCE.with_name('manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    preview = Image.new('RGB', (840, 720))
    draw = ImageDraw.Draw(preview)
    for y in range(0, 720, 12):
        for x in range(0, 840, 12):
            draw.rectangle((x, y, x+11, y+11), fill='#24272b' if (x//12+y//12)%2 else '#34383d')
    for i, image in enumerate(images):
        col, row = i%2, i//2
        scale = min(1.4, 140/image.height)
        display = image.resize((round(image.width*scale), round(image.height*scale)), Image.Resampling.NEAREST)
        preview.paste(display, (col*420+(420-display.width)//2, row*180+(180-display.height)//2), display)
    preview.save(SOURCE.with_name('runtime-preview.png'))
    print(json.dumps(manifest, indent=2))

if __name__ == '__main__':
    main()
