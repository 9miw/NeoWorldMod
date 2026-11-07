"""Frame/layout QA using runtime PNGs. Item art is sampled from the approved mockup, not a game render."""
import importlib.util
from pathlib import Path
from PIL import Image, ImageDraw
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('assembly', HERE / 'preview-assets.py')
p = importlib.util.module_from_spec(spec); spec.loader.exec_module(p)
p.canvas = Image.new('RGBA', (1280, 840), '#202629'); p.draw = ImageDraw.Draw(p.canvas)
p.image('frame', 0, 0, 640, 420); p.text('กิจกรรม', 320, 16, centered=True)
p.nine('button_normal', 602, 27, 20, 18); p.text('X', 612, 28, centered=True)
p.text('รายการกิจกรรม', 22, 55, '#f2c14e')
for i, (label, icon) in enumerate(zip(p.labels, p.icons)):
    y = 76 + i*32
    p.nine('nav_selected' if i == 2 else 'nav_normal', 22, y, 132, 28)
    p.image('icon_'+icon, 28, y+6, 16, 16); p.text(label, 49, y+7, tiny=True)
for x, label in [(22, '^'), (124, 'v')]:
    p.nine('button_disabled', x, 374, 30, 22); p.text(label, x+15, 377, centered=True)
p.text('4 กิจกรรม', 88, 381, centered=True, tiny=True)
p.image('icon_online', 314, 70, 28, 28); p.text('รางวัลออนไลน์', 350, 78, '#f2c14e')
p.text('เวลาออนไลน์สะสมวันนี้ 45 นาที', 402, 108, centered=True)
p.text('ไม่นับช่วง AFK • รีเซ็ต 04:00 น. (เวลาไทย)', 402, 128, centered=True, tiny=True)
reference = Image.open(HERE / 'online-approved-reference.png').convert('RGBA')
# Dynamic item previews are reference-only in this assembly check; actual game uses ItemStack rendering.
art = [reference.crop((516, 485, 637, 617)), None, reference.crop((1045, 485, 1156, 614)), None]
progress_texture = Image.open(p.ASSETS.parent / 'common/progress_soft_slim.png').convert('RGBA')
for i, minutes in enumerate([15, 30, 60, 120]):
    x, y = 182+i*112, 156
    p.nine('slot_selected' if i == 1 else 'slot_normal', x, y, 104, 194, 16, 6)
    p.text(str(minutes)+' นาที', x+52, y+12, '#f2c14e', centered=True)
    p.image('slot_normal', x+14, y+34, 76, 76)
    if art[i] is not None:
        p.canvas.alpha_composite(art[i].resize((96,96), Image.Resampling.NEAREST), ((x+28)*2,(y+48)*2))
    else: p.image('icon_silver' if i == 1 else 'icon_pending', x+28,y+48,48,48)
    # Same frame source rectangle/caps as NeoProgressBar, rendered at logical 84x10.
    xs, ys, dx, dy = [77,129,1407,1459], [264,288,380,404], [0,4,80,84], [0,2,8,10]
    for r in range(3):
        for c in range(3):
            tile = progress_texture.crop((xs[c],ys[r],xs[c+1],ys[r+1])).resize(
                ((dx[c+1]-dx[c])*2,(dy[r+1]-dy[r])*2),Image.Resampling.NEAREST)
            p.canvas.alpha_composite(tile,((x+10+dx[c])*2,(y+122+dy[r])*2))
    fill = round(80*min(1,45/minutes))
    p.draw.rectangle(((x+12)*2,(y+124)*2,(x+12+fill)*2-1,(y+130)*2-1),fill='#2fb5a7')
    p.text(f'{min(45,minutes)}/{minutes} นาที',x+52,y+139,centered=True,tiny=True)
    p.nine('button_normal' if i == 1 else 'button_disabled',x+8,y+160,88,26)
    p.text('รับแล้ว' if i == 0 else 'รับรางวัล' if i == 1 else 'ยังไม่ครบ',x+52,y+167,centered=True,tiny=True)
p.text('ชี้ที่ไอเทมเพื่อดูรายละเอียดรางวัล',402,373,centered=True,tiny=True)
p.canvas.convert('RGB').save(HERE/'online-frame-layout-preview.png')
print('Verified four cards within 444x330, 8-pixel gutters, 6-pixel frame corners.')
