"""Reproduce the tiny pixel arrows used inline with ordinary Subtitle text."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parents[4]
assets=root/'src/main/resources/assets/neoworld'
out=assets/'textures/font/pvp_titles'
out.mkdir(parents=True,exist_ok=True)
providers=[]
images=[]
for i,(name,bright,base,dark) in enumerate([
    ('rating_up','#B5FFAE','#48DD67','#178746'),
    ('rating_down','#FFC0B5','#F45E59','#9C273D')
]):
    image=Image.new('RGBA',(16,16))
    draw=ImageDraw.Draw(image)
    draw.polygon([(7,2),(8,2),(14,10),(14,12),(1,12),(1,10)],fill='#18252C')
    draw.polygon([(7,4),(8,4),(12,10),(3,10)],fill=base)
    draw.line([(7,4),(8,4),(11,8)],fill=bright,width=1)
    draw.line([(3,11),(12,11)],fill=dark,width=1)
    if i==1:
        image=image.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    image.save(out/f'{name}.png')
    (out/f'{name}.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":true}}\n')
    providers.append(dict(type='bitmap',file=f'neoworld:font/pvp_titles/{name}.png',
                          ascent=8,height=9,chars=[chr(0xE408+i)]))
    assert image.getchannel('A').getextrema()==(0,255)
    assert image.getbbox()[0]>0 and image.getbbox()[2]<16
    images.append(image)
(assets/'font/pvp_rating.json').write_text(json.dumps(dict(providers=providers),indent=2)+'\n')
preview=Image.new('RGB',(256,128),'#24272B')
for i,image in enumerate(images):
    enlarged=image.resize((96,96),Image.Resampling.NEAREST)
    preview.paste(enlarged,(i*128+16,16),enlarged)
preview.save(Path(__file__).with_name('rating-arrows-preview.png'))
print('Exported two 16x16 arrows; pvp_rating font height 9 / ascent 8.')
