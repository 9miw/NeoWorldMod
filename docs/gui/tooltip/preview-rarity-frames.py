"""Asset/layout preview of the Java renderer, not a screenshot or runtime gameplay test."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
import json

ROOT = Path(__file__).resolve().parents[3]
TIERS = ['common', 'uncommon', 'rare', 'epic', 'legendary']
COLORS = [(174, 183, 191), (73, 217, 149), (49, 198, 239), (186, 104, 244), (246, 204, 88)]

def gold_effect_frame(milliseconds):
    path = ROOT / 'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip/legendary_aura.png'
    strip = Image.open(path).convert('RGBA')
    animation = json.loads(Path(str(path)+'.mcmeta').read_text())['animation']
    sequence = animation['frames']
    elapsed = milliseconds / (animation['frametime'] * 50)
    current = int(elapsed) % len(sequence)
    def frame(index):
        y = sequence[index] * 32
        return strip.crop((0, y, 32, y+32))
    image = frame(current)
    if animation.get('interpolate'):
        image = Image.blend(image, frame((current+1) % len(sequence)), elapsed-int(elapsed))
    image.putalpha(image.getchannel('A').point(lambda value: int(value * 0.5)))
    return image

def preview(milliseconds):
    canvas = Image.new('RGBA', (1130, 230), (20, 25, 29, 255))
    draw = ImageDraw.Draw(canvas)
    for i, name in enumerate(TIERS):
        premium = i >= 3
        border = 8 if premium else 6
        parts_atlas = Image.open(ROOT / f'src/main/resources/assets/neoworld/textures/gui/common/tooltip/{name}_parts.png').convert('RGBA')
        parts_regions = {
            (0, 0): (0, 0, border, border), (0, 2): (8, 0, 8+border, border),
            (2, 0): (0, 8, border, 8+border), (2, 2): (8, 8, 8+border, 8+border),
            (0, 1): (16, 0, 17, border), (2, 1): (17, 0, 18, border),
            (1, 0): (16, 8, 16+border, 9), (1, 2): (16, 9, 16+border, 10),
        }
        padding = 8
        x, y = 22 + i * 222, 30
        width, height = 170 + padding * 2, 120 + padding * 2
        if name == 'legendary':
            animated = gold_effect_frame(milliseconds)
            # The source's edges and center are transparent; GUI nine-slice preserves these four corners.
            for source, destination in [
                ((0, 0, 8, 8), (x-4, y-4)), ((24, 0, 32, 8), (x+width-4, y-4)),
                ((0, 24, 8, 32), (x-4, y+height-4)), ((24, 24, 32, 32), (x+width-4, y+height-4)),
            ]:
                canvas.alpha_composite(animated.crop(source), destination)
        elif premium:
            effect = Image.new('RGBA', canvas.size)
            ed = ImageDraw.Draw(effect)
            pulse = int(4 + 2.5 * math.sin(milliseconds / 700.0))
            for spread in range(3, 0, -1):
                ed.rectangle((x-spread, y-spread, x+width+spread-1, y+height+spread-1),
                             outline=COLORS[i] + ((4-spread)*4+pulse,), width=1)
            canvas.alpha_composite(effect)
        top, bottom = y + 2, y + height - 2
        for left, right, start, end in [(4, 4, top, top+1), (3, 3, top+1, top+2),
                                        (2, 2, top+2, bottom-2), (3, 3, bottom-2, bottom-1),
                                        (4, 4, bottom-1, bottom)]:
            draw.rectangle((x+left, start, x+width-right-1, end-1), fill=(25, 31, 35, 255))
        dx, dy = [border, width-2*border, border], [border, height-2*border, border]
        ty = y
        for row in range(3):
            tx = x
            for col in range(3):
                if (row, col) != (1, 1):
                    piece = parts_atlas.crop(parts_regions[(row, col)])
                    canvas.alpha_composite(piece.resize((dx[col], dy[row]), Image.Resampling.NEAREST), (tx, ty))
                tx += dx[col]
            ty += dy[row]
        if name == 'legendary':
            crest = parts_atlas.crop((20, 16, 32, 32))
            canvas.alpha_composite(crest, (x+width//2-6, y-8))
        if name == 'epic':
            effect = Image.new('RGBA', canvas.size)
            ed = ImageDraw.Draw(effect)
            points = [(x-2, y-2), (x+width+2, y+6), (x-2, y+height-6), (x+width+2, y+height+2)]
            for index, (px, py) in enumerate(points):
                brightness = (1+math.sin(milliseconds/480.0+index*1.7))/2
                if brightness < 0.4:
                    continue
                color = COLORS[i] + (int(brightness*130),)
                ed.rectangle((px-1, py, px+1, py), fill=color)
                ed.rectangle((px, py-1, px, py+1), fill=color)
            canvas.alpha_composite(effect)
        draw.text((x+padding, y+padding), 'Dynamic item name', fill=(232, 196, 107, 255))
        draw.text((x+padding, y+padding+20), 'Details and usage', fill=(212, 217, 222, 255))
        draw.text((x+padding, y+padding+40), 'Separate text layer', fill=(102, 221, 213, 255))
        draw.text((x+50, y+height+10), name, fill=(210, 215, 220, 255))
    return canvas.convert('RGB').resize((2260, 460), Image.Resampling.NEAREST)

if __name__ == '__main__':
    output = ROOT / 'docs/gui/tooltip'
    preview(0).save(output / 'rarity-runtime-preview.png')
    frames = [preview(i*80) for i in range(60)]
    frames[0].save(output / 'rarity-animation-preview.gif', save_all=True, append_images=frames[1:],
                   duration=80, loop=0, disposal=2)
    print('Static and animated asset previews exported; not in-game verification')
