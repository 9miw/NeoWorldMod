"""Slice the generated 4x4 source into a Minecraft vertical animation strip."""
from pathlib import Path
from PIL import Image
import json

HERE = Path(__file__).resolve().parent
DEST = HERE.parents[2] / 'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip'

def main():
    source = Image.open(HERE / 'gold-aura-animation-source.png').convert('RGBA')
    stars = []
    for row in range(4):
        for column in range(4):
            bounds = (round(column*source.width/4), round(row*source.height/4),
                      round((column+1)*source.width/4), round((row+1)*source.height/4))
            cell = source.crop(bounds).resize((16, 16), Image.Resampling.NEAREST)
            stars.append(cell.crop((4, 4, 12, 12)))
    frames = []
    for index in range(16):
        frame = Image.new('RGBA', (32, 32))
        # Pack disjoint corner art with staggered animation phases. No light is tiled across text.
        for corner, position in enumerate([(0, 0), (24, 0), (24, 24), (0, 24)]):
            frame.paste(stars[(index+corner*4) % 16], position)
        assert frame.getchannel('A').crop((8, 8, 24, 24)).getextrema()[1] == 0
        frames.append(frame)
    assert len({frame.tobytes() for frame in frames}) > 1, 'No animation variation'
    strip = Image.new('RGBA', (32, 32 * len(frames)))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, index*32))
    DEST.mkdir(parents=True, exist_ok=True)
    strip.save(DEST / 'legendary_aura.png')
    metadata = {
        'texture': {'blur': False, 'clamp': True},
        'animation': {'width': 32, 'height': 32, 'frametime': 2, 'interpolate': True,
                      'frames': list(range(len(frames)))},
        'gui': {'scaling': {'type': 'nine_slice', 'width': 32, 'height': 32, 'border': 8}},
    }
    (DEST / 'legendary_aura.png.mcmeta').write_text(json.dumps(metadata, indent=2)+'\n', encoding='utf-8')
    print('Exported 32x512 RGBA strip, 16 frames, 2 ticks/frame, GUI nine-slice metadata')

if __name__ == '__main__':
    main()
