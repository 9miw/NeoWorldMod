"""Compose installed Deepslate assets; this is not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw
import json

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
TIERS=['common','uncommon','rare','epic','legendary']
COLORS=[(174,183,191),(73,217,149),(49,198,239),(186,104,244),(246,204,88)]
GEOMETRY=json.loads((HERE/'geometry.json').read_text())
PARTS=ROOT/'src/main/resources/assets/neoworld/textures/gui/common/tooltip/deepslate'
SPRITES=ROOT/'src/main/resources/assets/neoworld/textures/gui/sprites/tooltip/deepslate'

def effect(name,ms,color,opacity):
    p=SPRITES/(name+'.png');meta=json.loads(Path(str(p)+'.mcmeta').read_text())['animation']
    w,h=meta['width'],meta['height'];i=int(ms/(meta['frametime']*50))%len(meta['frames'])
    sheet=Image.open(p).convert('RGBA');im=sheet.crop((0,i*h,w,(i+1)*h))
    channels=im.split();rgb=[channels[j].point(lambda v,j=j:v*color[j]//255) for j in range(3)]
    return Image.merge('RGBA',(*rgb,channels[3].point(lambda v:int(v*opacity))))

def corners(canvas,im,border,x,y,w,h):
    sw,sh=im.size
    for box,xy in [((0,0,border,border),(x,y)),((sw-border,0,sw,border),(x+w-border,y)),
                   ((0,sh-border,border,sh),(x,y+h-border)),((sw-border,sh-border,sw,sh),(x+w-border,y+h-border))]:
        canvas.alpha_composite(im.crop(box),xy)

def panel(tier,ms,content_width=180,content_height=210):
    index=TIERS.index(tier);g=GEOMETRY[tier];n=g['corner'];pad=g['padding']
    w=content_width+pad*2;h=content_height+pad*2;x=y=16
    canvas=Image.new('RGBA',(w+32,h+32));a=Image.open(PARTS/(tier+'_parts.png')).convert('RGBA')
    canvas.alpha_composite(a.crop((127,63,128,64)).resize((w-2*n,h-2*n),Image.Resampling.NEAREST),(x+n,y+n))
    for box,xy in [((0,0,n,n),(x,y)),((16,0,16+n,n),(x+w-n,y)),
                   ((0,16,n,16+n),(x,y+h-n)),((16,16,16+n,16+n),(x+w-n,y+h-n))]:canvas.alpha_composite(a.crop(box),xy)
    for u,yy in [(32,y),(48,y+h-n)]:
        for offset in range(0,w-2*n,12):
            count=min(12,w-2*n-offset);canvas.alpha_composite(a.crop((u,0,u+count,n)),(x+n+offset,yy))
        canvas.alpha_composite(a.crop((u+11,0,u+12,n)),(x+w-n-1,yy))
    for u,xx in [(32,x),(48,x+w-n)]:
        for offset in range(0,h-2*n,12):
            count=min(12,h-2*n-offset);canvas.alpha_composite(a.crop((u,16,u+n,16+count)),(xx,y+n+offset))
        canvas.alpha_composite(a.crop((u,27,u+n,28)),(xx,y+h-n-1))
    if index>=3:
        corners(canvas,effect(tier+'_pulse',ms,COLORS[index],0.65),12,x,y,w,h)
        corners(canvas,effect('cross_spark',ms,COLORS[index],0.85 if index==4 else 0.6),8,x-6,y-6,w+12,h+12)
    if index==4:
        rx,ry=g['rail_x'],g['rail_y']
        for ox,oy,length,angle in [(x+n,y+ry,w-2*n,0),(x+w-rx,y+n,h-2*n,90),
                                   (x+w-n,y+h-ry,w-2*n,180),(x+rx,y+h-n,h-2*n,270)]:
            line=Image.new('RGBA',(length,4));tile=effect('gold_flow',ms,COLORS[index],0.8)
            for offset in range(0,length,32):line.paste(tile,(offset,0))
            if angle==0:xy=(ox,oy-2)
            elif angle==90:xy=(ox-2,oy)
            elif angle==180:xy=(ox-length,oy-2)
            else:xy=(ox-2,oy-length)
            if angle:line=line.rotate(-angle,expand=True)
            canvas.alpha_composite(line,xy)
    d=ImageDraw.Draw(canvas)
    for row,text in enumerate(['Item name','Weapon','Durability: 2031 / 2031','Upgrade +7','Required level 15','','How to use','Hold in main hand']):
        d.text((x+pad,y+pad+row*18),text,fill=(230,198,110) if row==0 else (212,220,228))
    return canvas

def composition(ms):
    c=Image.new('RGBA',(1280,720),(18,23,28,255));d=ImageDraw.Draw(c)
    d.text((28,35),'DEEPSLATE - installed pixel asset preview',fill='white')
    d.text((28,57),'Epic: crystal pulse + cross sparks | Legendary: pulse + sparks + gold edge flow',fill=(155,165,175))
    d.text((28,78),'Offline composition, not an in-game screenshot or GUI-scale verification',fill=(155,165,175))
    for i,tier in enumerate(TIERS):
        x=10+i*248;d.text((x+16,140),tier.upper(),fill=COLORS[i]);c.alpha_composite(panel(tier,ms),(x,155))
    return c.convert('RGB')

if __name__=='__main__':
    composition(400).save(HERE/'runtime-preview.png')
    frames=[composition(i*100) for i in range(16)]
    frames[0].save(HERE/'runtime-preview.gif',save_all=True,append_images=frames[1:],duration=100,loop=0,disposal=2)
    board=Image.new('RGBA',(640,160))
    d=ImageDraw.Draw(board)
    for y in range(0,160,8):
        for x in range(0,640,8):d.rectangle((x,y,x+7,y+7),fill=(95,95,95) if (x//8+y//8)%2 else (145,145,145))
    for i,tier in enumerate(TIERS):board.alpha_composite(Image.open(PARTS/(tier+'_parts.png')).convert('RGBA'),(i*128,20))
    board.resize((1280,320),Image.Resampling.NEAREST).save(HERE/'parts-checkerboard.png')
    print('Exported native PNG/GIF and checkerboard preview; not in-game verification')
