"""Offline native-part composition; does not establish in-game visual matching."""
from pathlib import Path
from PIL import Image, ImageDraw
import json

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
TIERS=['common','uncommon','rare','epic','legendary']
COLORS=[(174,183,191),(73,217,149),(49,198,239),(186,104,244),(246,204,88)]
GEOMETRY=json.loads((HERE/'geometry.json').read_text())

def flow_frame(ms):
    p=ROOT/'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip/arcane/rune_flow.png'
    im=Image.open(p).convert('RGBA');meta=json.loads(Path(str(p)+'.mcmeta').read_text())['animation']
    time=ms/(meta['frametime']*50);index=int(time)%16
    a=im.crop((0,index*4,32,index*4+4));b=im.crop((0,((index+1)%16)*4,32,((index+1)%16)*4+4))
    return Image.blend(a,b,time-int(time)) if meta['interpolate'] else a

def composition(ms):
    canvas=Image.new('RGBA',(1280,720),(18,23,28,255));draw=ImageDraw.Draw(canvas)
    draw.text((28,35),'ARCANE CRYSTAL A - native asset preview',fill='white')
    draw.text((28,57),'Animation only: Epic / Legendary | Offline composition, not an in-game screenshot',fill=(155,165,175))
    for index,tier in enumerate(TIERS):
        g=GEOMETRY[tier];n=g['corner'];pad=g['padding'];rail=g['rail_x']
        x=25+index*248;y=170;width=180+pad*2;height=210+pad*2
        draw.text((x,140),tier.upper(),fill=COLORS[index])
        draw.text((x,420),f'corner {n}px / inset {pad}px',fill=(155,165,175))
        left,top,right,bottom=x+rail,y+rail,x+width-rail,y+height-rail
        for l,t,r,b in [(left+2,top,right-2,top+1),(left+1,top+1,right-1,top+2),(left,top+2,right,bottom-2),(left+1,bottom-2,right-1,bottom-1),(left+2,bottom-1,right-2,bottom)]:
            draw.rectangle((l,t,r-1,b-1),fill=(25,31,35,255))
        atlas=Image.open(ROOT/f'src/main/resources/assets/neoworld/textures/gui/common/tooltip/arcane/{tier}_parts.png').convert('RGBA')
        for box,xy,size in [((0,0,n,n),(x,y),(n,n)),((16,0,16+n,n),(x+width-n,y),(n,n)),
                            ((0,16,n,16+n),(x,y+height-n),(n,n)),((16,16,16+n,16+n),(x+width-n,y+height-n),(n,n)),
                            ((32,0,33,n),(x+n,y),(width-2*n,n)),((33,0,34,n),(x+n,y+height-n),(width-2*n,n)),
                            ((32,16,32+n,17),(x,y+n),(n,height-2*n)),((32,17,32+n,18),(x+width-n,y+n),(n,height-2*n))]:
            canvas.alpha_composite(atlas.crop(box).resize(size,Image.Resampling.NEAREST),xy)
        edges=[(x+n,y+rail,width-2*n,0),(x+width-rail,y+n,height-2*n,90),
               (x+width-n,y+height-rail,width-2*n,180),(x+rail,y+height-n,height-2*n,270)]
        for ox,oy,length,angle in edges:
            layer=Image.new('RGBA',(length,4))
            for glyph,position in enumerate(range(6,length-6,12)):
                u=(glyph%3)*4;layer.paste(atlas.crop((u,32,u+3,35)),(position-1,1))
            for effect in [False,True] if index>=3 else [False]:
                current=layer
                if effect:
                    current=Image.new('RGBA',(length,4));tile=flow_frame(ms)
                    for offset in range(0,length,32): current.paste(tile,(offset,0))
                    red,green,blue,alpha=current.split();rgb=COLORS[index]
                    red=red.point(lambda v:v*rgb[0]//255);green=green.point(lambda v:v*rgb[1]//255);blue=blue.point(lambda v:v*rgb[2]//255)
                    opacity=0.85 if index==4 else 0.65
                    current=Image.merge('RGBA',(red,green,blue,alpha.point(lambda v:int(v*opacity))))
                if angle==0: xy=(ox,oy-2)
                elif angle==90: xy=(ox-2,oy)
                elif angle==180: xy=(ox-length,oy-2)
                else: xy=(ox-2,oy-length)
                if angle: current=current.rotate(-angle,expand=True)
                canvas.alpha_composite(current,xy)
        for row,label in enumerate(['Item name','Weapon','Durability: 2031 / 2031','Upgrade +7','Required level 15','','How to use','Hold in main hand']):
            draw.text((x+pad,y+pad+row*18),label,fill=(230,198,110) if row==0 else (208,217,225))
    return canvas.convert('RGB')

if __name__=='__main__':
    composition(0).save(HERE/'runtime-preview.png')
    frames=[composition(ms) for ms in range(0,1600,80)]
    frames[0].save(HERE/'runtime-preview.gif',save_all=True,append_images=frames[1:],duration=80,loop=0,disposal=2)
    print('Exported offline 1280x720 PNG/GIF; not in-game verification')
