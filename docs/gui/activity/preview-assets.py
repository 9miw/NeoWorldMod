"""Assembly QA at the runtime's logical coordinates. This is not an in-game screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[2] / 'src/main/resources/assets/neoworld/textures/gui/activity'
canvas = Image.new('RGBA', (1280, 840), '#202629')
draw = ImageDraw.Draw(canvas)
font = ImageFont.truetype('C:/Windows/Fonts/tahoma.ttf', 19)
small = ImageFont.truetype('C:/Windows/Fonts/tahoma.ttf', 16)

def image(name, x, y, w, h):
    img = Image.open(ASSETS / (name + '.png')).convert('RGBA')
    canvas.alpha_composite(img.resize((w*2, h*2), Image.Resampling.NEAREST), (x*2, y*2))

def nine(name, x, y, w, h, edge=24, corner=None):
    img = Image.open(ASSETS / (name + '.png')).convert('RGBA')
    sw, sh = img.size
    if corner is None: corner = min(max(1, h*edge//sh), min(w, h)//2)
    xs, ys = [0, edge, sw-edge, sw], [0, edge, sh-edge, sh]
    dxs, dys = [0, corner, w-corner, w], [0, corner, h-corner, h]
    for r in range(3):
        for c in range(3):
            if dxs[c+1] == dxs[c] or dys[r+1] == dys[r]: continue
            tile = img.crop((xs[c], ys[r], xs[c+1], ys[r+1]))
            tile = tile.resize(((dxs[c+1]-dxs[c])*2, (dys[r+1]-dys[r])*2), Image.Resampling.NEAREST)
            canvas.alpha_composite(tile, ((x+dxs[c])*2, (y+dys[r])*2))

def text(s, x, y, color='#e7ecef', centered=False, tiny=False):
    f = small if tiny else font
    if centered: x -= draw.textlength(s, font=f)/4
    draw.text((int(x*2), int(y*2)), s, font=f, fill=color)

image('frame', 0, 0, 640, 420)
text('กิจกรรม', 320, 16, centered=True)
nine('button_normal', 602, 27, 20, 18); text('X', 612, 28, centered=True)
text('รายการกิจกรรม', 22, 55, '#f2c14e')
labels = ['เช็กอินรายวัน','เช็กอินรายเดือน','รางวัลออนไลน์','รางวัลค้างรับ']
icons = ['daily','monthly','online','pending']
for i, label in enumerate(labels):
    y = 76+i*32
    nine('nav_selected' if i == 1 else 'nav_normal', 22, y, 132, 28)
    image('icon_'+icons[i], 28, y+6, 16, 16)
    text(label, 49, y+7, tiny=True)
nine('button_disabled', 22, 374, 30, 22); text('^', 37, 377, centered=True)
nine('button_disabled', 124, 374, 30, 22); text('v', 139, 377, centered=True)
text('4 กิจกรรม', 88, 381, centered=True, tiny=True)
image('icon_monthly', 180, 58, 22, 22); text('เช็กอินรายเดือน', 208, 62, '#f2c14e')
text('รับได้เฉพาะวันนี้ • วันที่ผ่านมาแล้วรับย้อนหลังไม่ได้', 180, 85, tiny=True)
for cell in range(18):
    day = cell+1; x = 180+(cell%6)*70; y = 58+42+(cell//6)*84
    today = day == 8
    text('วันที่ '+str(day), x+35, y, '#f2c14e' if today else '#abb4ba', centered=True, tiny=True)
    image('slot_selected' if today else 'slot_disabled', x+9, y+10, 48, 48)
    # A bundle preview and a money-only preview demonstrate both runtime paths.
    image('icon_pending' if day%7 == 0 else 'icon_silver', x+18, y+19, 30, 30)
    if day%7 != 0: text('50', x+46, y+42, centered=True, tiny=True)
    nine('button_normal' if today else 'button_disabled', x+4, y+60, 62, 20)
    text('รับแล้ว' if day < 8 else 'รับ', x+35, y+63, '#e7ecef' if today else '#9a9fa3', centered=True, tiny=True)
nine('button_disabled', 610, 100, 14, 18); text('^', 617, 102, centered=True, tiny=True)
nine('button_normal', 610, 334, 14, 18); text('v', 617, 336, centered=True, tiny=True)
image('scroll_track', 612, 120, 10, 212)
nine('scroll_thumb', 612, 120, 10, 126, 8)
text('รีเซ็ต 04:00 ไทย • 12:30:00', 180, 372, tiny=True)
canvas.convert('RGB').save(HERE / 'assembled-runtime-preview.png')
print('Saved assembled-runtime-preview.png at 2x logical scale (1280x840).')
