"""Prepare native crystal corners, matched edges, rune tiles and a vertical flow animation."""
from pathlib import Path
from PIL import Image, ImageOps, ImageChops
import json

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PARTS = ROOT/'src/main/resources/assets/neoworld/textures/gui/common/tooltip/arcane'
SPRITES = ROOT/'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip/arcane'
TIERS = ['common', 'uncommon', 'rare', 'epic', 'legendary']

def metadata(path, extra=None):
    data = {'texture': {'blur': False, 'clamp': True}}
    data.update(extra or {})
    Path(str(path)+'.mcmeta').write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')

def main():
    PARTS.mkdir(parents=True, exist_ok=True)
    SPRITES.mkdir(parents=True, exist_ok=True)
    source = Image.open(HERE/'frame-source.png').convert('RGBA')
    metrics = {}
    for index, tier in enumerate(TIERS):
        left, right = round(index*source.width/5), round((index+1)*source.width/5)
        cell = source.crop((left, 0, right, source.height))
        bounds = cell.getchannel('A').point(lambda a: 255 if a>=128 else 0).getbbox()
        frame = cell.crop(bounds)
        size = 6+index
        corner = frame.crop((0, 0, 84, 84)).resize((size, size), Image.Resampling.NEAREST)
        corner.putalpha(corner.getchannel('A').point(lambda a: 255 if a>=128 else 0))
        tr, bl = ImageOps.mirror(corner), ImageOps.flip(corner)
        atlas = Image.new('RGBA', (64, 64))
        for piece, xy in [(corner,(0,0)),(tr,(16,0)),(bl,(0,16)),(ImageOps.flip(tr),(16,16)),
                          (corner.crop((size-1,0,size,size)),(32,0)),
                          (bl.crop((size-1,0,size,size)),(33,0)),
                          (corner.crop((0,size-1,size,size)),(32,16)),
                          (tr.crop((0,size-1,size,size)),(32,17))]:
            atlas.paste(piece,xy)
        for glyph, x in enumerate([80,108,136]):
            rune = frame.crop((x,24,x+16,40)).resize((3,3),Image.Resampling.NEAREST)
            rune.putalpha(rune.getchannel('A').point(lambda a:255 if a>=128 else 0))
            atlas.paste(rune,(glyph*4,32))
        def center(values):
            total=sum(values)
            assert total>0, (tier,'missing rail')
            return round(sum(i*a for i,a in enumerate(values))/total)
        tx=center([corner.getpixel((i,size-1))[3] for i in range(size)])
        ty=center([corner.getpixel((size-1,i))[3] for i in range(size)])
        path=PARTS/f'{tier}_parts.png'
        atlas.save(path); metadata(path)
        metrics[tier]={'corner':size,'padding':8+index,'rail_x':tx,'rail_y':ty,'source_bounds':[left+bounds[0],bounds[1],left+bounds[2],bounds[3]]}
    (HERE/'geometry.json').write_text(json.dumps(metrics,indent=2)+'\n',encoding='utf-8')
    light=Image.open(HERE/'flow-source.png').convert('RGBA')
    box=light.getchannel('A').point(lambda a:255 if a>=64 else 0).getbbox()
    light=light.crop(box).resize((32,4),Image.Resampling.NEAREST)
    strip=Image.new('RGBA',(32,64))
    for i in range(16):
        strip.paste(ImageChops.offset(light,i*2,0),(0,i*4))
    path=SPRITES/'rune_flow.png';strip.save(path)
    metadata(path,{'animation':{'width':32,'height':4,'frametime':2,'interpolate':True,'frames':list(range(16))},
                   'gui':{'scaling':{'type':'tile','width':32,'height':4}}})
    print(json.dumps(metrics));print('Exported 5 native atlases and 32x64 animated flow strip')

if __name__=='__main__':
    main()
