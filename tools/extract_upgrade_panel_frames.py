"""Extract reusable decorative pieces from the original approved empty frame."""
from pathlib import Path
from PIL import Image
import json

root = Path(__file__).resolve().parents[1]
source = Image.open(root / 'docs/gui/upgrade/02-frame-source.png').convert('RGBA')
out = root / 'src/main/resources/assets/neoworld/textures/gui/upgrade'
def save(name, image):
    image.save(out / (name + '.png'))
    (out / (name + '.png.mcmeta')).write_text(json.dumps({'texture': {'blur': False, 'clamp': True}}, indent=2) + '\n')

# Corners and strips come from the original equipment panel, avoiding its heading plaque.
panel = Image.new('RGBA', (128, 128))
regions = [
    ((92,156,116,180),(0,0,8,8)), ((608,156,632,180),(120,0,128,8)),
    ((92,648,116,672),(0,120,8,128)), ((608,648,632,672),(120,120,128,128)),
    ((116,156,148,180),(8,0,120,8)), ((260,648,300,672),(8,120,120,128)),
    ((92,210,116,250),(0,8,8,120)), ((608,210,632,250),(120,8,128,120)),
    ((260,300,300,340),(8,8,120,120)),
]
for crop, target in regions:
    x0,y0,x1,y1 = target
    panel.paste(source.crop(crop).resize((x1-x0,y1-y0),Image.Resampling.NEAREST),(x0,y0))
save('panel_frame', panel)
save('heading_plaque', source.crop((156,138,574,192)).resize((256,36),Image.Resampling.NEAREST))
save('title_plaque', source.crop((466,14,982,134)).resize((256,60),Image.Resampling.NEAREST))
assert all(Image.open(out / (name+'.png')).mode == 'RGBA' for name in ['panel_frame','heading_plaque','title_plaque'])
print('Exported panel nine-slice, silver heading plaque and gold title plaque from original artwork')
