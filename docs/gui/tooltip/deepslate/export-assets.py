"""Native pixel panel slices and declarative geometric pixel-effect sprite sheets."""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
import json

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
PARTS=ROOT/'src/main/resources/assets/neoworld/textures/gui/common/tooltip/deepslate'
SPRITES=ROOT/'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip/deepslate'
TIERS=['common','uncommon','rare','epic','legendary']
# Tier-specific opaque palettes: dark reading surface, stone shadow, rim, highlight.
PALETTES={
    'common':[(25,27,30),(57,61,66),(112,119,126),(220,224,228)],
    'uncommon':[(15,32,21),(34,71,40),(77,151,61),(183,231,130)],
    'rare':[(12,24,46),(22,62,108),(38,150,222),(166,230,255)],
    'epic':[(31,15,44),(71,34,103),(151,67,205),(233,190,255)],
    'legendary':[(39,25,12),(103,64,21),(213,148,35),(255,232,153)],
}

def tier_palette(atlas,tier):
    anchors=[32,60,100,245];palette=PALETTES[tier]
    for y in range(atlas.height):
        for x in range(atlas.width):
            r,g,b,a=atlas.getpixel((x,y))
            if not a:continue
            value=max(r,g,b)
            if value<=anchors[0]:color=palette[0]
            elif value>=anchors[-1]:color=palette[-1]
            else:
                j=next(j for j in range(3) if value<=anchors[j+1])
                t=(value-anchors[j])/(anchors[j+1]-anchors[j])
                color=tuple(round(palette[j][k]*(1-t)+palette[j+1][k]*t) for k in range(3))
            atlas.putpixel((x,y),(*color,a))
    return atlas

def metadata(path,extra=None):
    data={'texture':{'blur':False,'clamp':True}};data.update(extra or {})
    Path(str(path)+'.mcmeta').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

def animation(name,frames,border=None,tile=None):
    w,h=frames[0].size;strip=Image.new('RGBA',(w,h*len(frames)))
    for i,frame in enumerate(frames):strip.paste(frame,(0,i*h))
    path=SPRITES/(name+'.png');strip.save(path)
    scaling={'type':'nine_slice','width':w,'height':h,'border':border} if border else {'type':'tile','width':tile[0],'height':tile[1]}
    metadata(path,{'animation':{'width':w,'height':h,'frametime':2,'interpolate':False,'frames':list(range(len(frames)))},'gui':{'scaling':scaling}})

def main():
    PARTS.mkdir(parents=True,exist_ok=True);SPRITES.mkdir(parents=True,exist_ok=True)
    source=Image.open(HERE/'frame-source.png').convert('RGBA');metrics={}
    for i,tier in enumerate(TIERS):
        l,r=round(i*source.width/5),round((i+1)*source.width/5)
        cell=source.crop((l,0,r,source.height))
        box=cell.getchannel('A').point(lambda a:255 if a>=128 else 0).getbbox()
        frame=cell.crop(box).resize((80,56),Image.Resampling.NEAREST)
        frame=frame.quantize(colors=12,method=Image.Quantize.FASTOCTREE,dither=Image.Dither.NONE).convert('RGBA')
        frame.putalpha(frame.getchannel('A').point(lambda a:255 if a>=128 else 0))
        # Include the complete gem before choosing attachment profiles; never tile a gem fragment.
        n=6+i;c=frame.crop((0,0,16,16)).resize((n,n),Image.Resampling.NEAREST)
        tr,bl=ImageOps.mirror(c),ImageOps.flip(c)
        top=frame.crop((28,0,52,16)).resize((12,n),Image.Resampling.NEAREST)
        profile=c.crop((n-1,0,n,n));top.paste(profile,(0,0));top.paste(profile,(11,0))
        left=frame.crop((0,20,16,36)).resize((n,12),Image.Resampling.NEAREST)
        profile=c.crop((0,n-1,n,n));left.paste(profile,(0,0));left.paste(profile,(0,11))
        atlas=Image.new('RGBA',(128,64))
        for piece,xy in [(c,(0,0)),(tr,(16,0)),(bl,(0,16)),(ImageOps.flip(tr),(16,16)),
                         (top,(32,0)),(ImageOps.flip(top),(48,0)),(left,(32,16)),(ImageOps.mirror(left),(48,16))]:atlas.paste(piece,xy)
        center=frame.getpixel((40,28));assert center[3]==255
        atlas.putpixel((127,63),center)
        path=PARTS/(tier+'_parts.png');tier_palette(atlas,tier).save(path);metadata(path)
        def rail(values):
            candidates=[j for j,p in enumerate(values) if p[3] and sum(p[:3])>100]
            return min(candidates) if candidates else 1
        rx=rail([c.getpixel((j,n-1)) for j in range(n)])
        ry=rail([c.getpixel((n-1,j)) for j in range(n)])
        colored=[]
        for y in range(n):
            for x in range(n):
                red,green,blue,alpha=c.getpixel((x,y))
                if alpha and ((tier=='epic' and red>green*1.4 and blue>green*1.3) or (tier=='legendary' and red>blue*1.4 and green>blue*1.2)):
                    colored.append((x,y))
        gx=round(sum(x for x,y in colored)/len(colored)) if colored else n//3
        gy=round(sum(y for x,y in colored)/len(colored)) if colored else n//3
        metrics[tier]={'corner':n,'padding':8+i,'rail_x':rx,'rail_y':ry,'gem_x':gx,'gem_y':gy,'source_bounds':[l+box[0],box[1],l+box[2],box[3]]}
    (HERE/'geometry.json').write_text(json.dumps(metrics,indent=2)+'\n',encoding='utf-8')
    for tier in ['epic','legendary']:
        gx,gy=metrics[tier]['gem_x'],metrics[tier]['gem_y'];frames=[]
        for i in range(16):
            frame=Image.new('RGBA',(48,48));draw=ImageDraw.Draw(frame)
            for j,(x,y) in enumerate([(gx,gy),(47-gx,gy),(47-gx,47-gy),(gx,47-gy)]):
                phase=(i+j*4)%16;alpha=[0,0,85,170,255,170,85,0,0,0,0,0,0,0,0,0][phase]
                draw.polygon([(x,y-1),(x+1,y),(x,y+1),(x-1,y)],fill=(255,255,255,alpha))
            frames.append(frame)
        animation(tier+'_pulse',frames,border=12)
    frames=[]
    for i in range(16):
        frame=Image.new('RGBA',(32,32));draw=ImageDraw.Draw(frame)
        for j,(x,y) in enumerate([(4,4),(27,4),(27,27),(4,27)]):
            phase=(i+j*3)%16
            if phase<3:
                alpha=[255,170,85][phase]
                draw.line((x-1,y,x+1,y),fill=(255,255,255,alpha))
                draw.line((x,y-1,x,y+1),fill=(255,255,255,alpha))
        frames.append(frame)
    animation('cross_spark',frames,border=8)
    frames=[]
    for i in range(16):
        frame=Image.new('RGBA',(32,4))
        for tail,alpha in enumerate([255,255,170,170,85,85]):frame.putpixel(((i*2-tail)%32,1),(255,255,255,alpha))
        frames.append(frame)
    animation('gold_flow',frames,tile=(32,4))
    print(json.dumps(metrics));print('Exported five panels with BG and four native pixel animation strips')

if __name__=='__main__':main()
