"""Crop the approved transparent sheet; no color-keying or repainting of artwork."""
from pathlib import Path
from PIL import Image, ImageOps
import json

HERE = Path(__file__).resolve().parent
DEST = HERE.parents[2] / 'src/main/resources/assets/neoworld/textures/gui/common/tooltip'
REGIONS = {
    'common': (16, 45, 388, 670),
    'uncommon': (455, 45, 827, 670),
    'rare': (900, 45, 1270, 670),
    'epic': (1328, 40, 1715, 670),
    'legendary': (1776, 12, 2164, 679),
}

def export_legendary_parts(frame):
    # Normalize the old top clearance once, then reflect ONE canonical corner.
    # Bake GUI-sized pieces so runtime sampling cannot shift their shared attachment lines.
    corner = frame.crop((0, 12, 32, 44)).resize((8, 8), Image.Resampling.NEAREST)
    right = ImageOps.mirror(corner)
    bottom = ImageOps.flip(corner)
    bottom_right = ImageOps.flip(right)
    atlas = Image.new('RGBA', (32, 32))
    for image, position in [(corner, (0, 0)), (right, (8, 0)),
                            (bottom, (0, 8)), (bottom_right, (8, 8)),
                            (corner.crop((7, 0, 8, 8)), (16, 0)),
                            (bottom.crop((7, 0, 8, 8)), (17, 0)),
                            (corner.crop((0, 7, 8, 8)), (16, 8)),
                            (right.crop((0, 7, 8, 8)), (16, 9))]:
        atlas.paste(image, position)
    crest = Image.open(HERE / 'gold-crest-source.png').convert('RGBA')
    bounds = crest.getchannel('A').point(lambda alpha: 255 if alpha >= 64 else 0).getbbox()
    assert bounds is not None, 'Missing crest artwork'
    crest = ImageOps.contain(crest.crop(bounds), (12, 16), Image.Resampling.NEAREST)
    atlas.paste(crest, (20 + (12-crest.width)//2, 16 + (16-crest.height)//2))
    atlas.save(DEST / 'legendary_parts.png')
    (DEST / 'legendary_parts.png.mcmeta').write_text(
        json.dumps({'texture': {'blur': False, 'clamp': True}}) + '\n', encoding='utf-8')

def export_aligned_parts(name, frame):
    size = 8 if name == 'epic' else 6
    if size == 8:
        corner = frame.crop((0, 0, 32, 32)).resize((8, 8), Image.Resampling.NEAREST)
    else:
        # Nearest source pixels, chosen to retain the narrow silver/color highlights at native GUI scale.
        # The final sample is the attachment edge, not an interior point of the corner decoration.
        samples = [0, 5, 10, 16, 24, 31]
        corner = Image.new('RGBA', (size, size))
        for y, source_y in enumerate(samples):
            for x, source_x in enumerate(samples):
                corner.putpixel((x, y), frame.getpixel((source_x, source_y)))
    right, bottom = ImageOps.mirror(corner), ImageOps.flip(corner)
    atlas = Image.new('RGBA', (32, 32))
    for image, position in [(corner, (0, 0)), (right, (8, 0)), (bottom, (0, 8)),
                            (ImageOps.flip(right), (8, 8)),
                            (corner.crop((size-1, 0, size, size)), (16, 0)),
                            (bottom.crop((size-1, 0, size, size)), (17, 0)),
                            (corner.crop((0, size-1, size, size)), (16, 8)),
                            (right.crop((0, size-1, size, size)), (16, 9))]:
        atlas.paste(image, position)
    atlas.save(DEST / f'{name}_parts.png')
    (DEST / f'{name}_parts.png.mcmeta').write_text(
        json.dumps({'texture': {'blur': False, 'clamp': True}}) + '\n', encoding='utf-8')

def main():
    source = Image.open(HERE / 'rarity-frames-source.png').convert('RGBA')
    assert source.size == (2172, 724), source.size
    DEST.mkdir(parents=True, exist_ok=True)
    for name, region in REGIONS.items():
        frame = source.crop(region).resize((128, 224), Image.Resampling.NEAREST)
        assert frame.getpixel((64, 112))[3] == 0, name
        assert frame.getchannel('A').getextrema() == (0, 255), name
        frame.save(DEST / f'{name}.png')
        (DEST / f'{name}.png.mcmeta').write_text(
            json.dumps({'texture': {'blur': False, 'clamp': True}}) + '\n', encoding='utf-8')
        print(name, frame.size, 'alpha bounds', frame.getchannel('A').getbbox())
        if name == 'legendary':
            export_legendary_parts(frame)
        else:
            export_aligned_parts(name, frame)

if __name__ == '__main__':
    main()
