"""Index existing bitmap icons without rewriting fonts or runtime textures."""
import argparse
import base64
import hashlib
import html
import io
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path('docs/gui/common/bitmap-icons')
FONTS = ('icons', 'currency', 'ranks', 'gui_icons', 'gui_controls', 'dungeon_hud',
         'fashion_tooltip', 'activity_icons', 'pvp_titles', 'pvp_rating')
SYSTEM = {0xE300: 'PROFILE', 0xE301: 'TITLES', 0xE004: 'MARKET', 0xE200: 'FASHION',
          0xE302: 'PARTY', 0xE303: 'DUNGEON', 0xE304: 'RESUME', 0xE006: 'SETTINGS',
          0xE305: 'MODS', 0xE306: 'ADVANCEMENTS', 0xE307: 'STATISTICS', 0xE308: 'LAN', 0xE309: 'EXIT'}
GUI = dict(enumerate(('LEADER', 'MEMBER', 'INVITE', 'KICK', 'READY', 'LEAVE', 'DISBAND', 'REFRESH'), 0xE100))


def resource(root, identifier, kind):
    namespace, name = identifier.split(':', 1)
    relative = Path('src/main/resources/assets') / namespace / kind / name
    resolved = (root / relative).resolve()
    if not resolved.is_relative_to((root / 'src/main/resources/assets').resolve()):
        raise ValueError(f'Invalid resource path: {identifier}')
    return relative


def font_file(root, font):
    return resource(root, font + '.json', 'font')


def read_font(root, font, stack=()):
    if font in stack:
        raise ValueError(f'Font reference cycle: {stack + (font,)}')
    path = font_file(root, font)
    providers = json.loads((root / path).read_text(encoding='utf-8'))['providers']
    entries = []
    seen = set()
    for index, provider in enumerate(providers):
        kind = provider['type'].removeprefix('minecraft:')
        if kind == 'reference':
            entries.extend(read_font(root, provider['id'], stack + (font,)))
            continue
        if kind != 'bitmap':
            continue
        image_path = resource(root, provider['file'], 'textures')
        if not (root / image_path).is_file():
            raise ValueError(f'Missing image: {image_path}')
        with Image.open(root / image_path) as source:
            image = source.convert('RGBA')
        rows = provider['chars']
        columns = len(rows[0]) if rows else 0
        if not columns or any(len(row) != columns for row in rows):
            raise ValueError(f'Invalid glyph grid: {font} provider {index}')
        if image.width % columns or image.height % len(rows):
            raise ValueError(f'Non-integral glyph cells: {font} provider {index}')
        cw, ch = image.width // columns, image.height // len(rows)
        height, ascent = provider.get('height', 8), provider['ascent']
        if height <= 0 or ascent > height:
            raise ValueError(f'Invalid metrics: {font} provider {index}')
        for row_index, row in enumerate(rows):
            for col, glyph in enumerate(row):
                if glyph == '\0':
                    continue
                cp = ord(glyph)
                if cp in seen:
                    raise ValueError(f'Duplicate glyph: {font} U+{cp:04X}')
                seen.add(cp)
                x, y = col * cw, row_index * ch
                bounds = image.crop((x, y, x + cw, y + ch)).getchannel('A').getbbox()
                entries.append(dict(id=f'{font}/u{cp:04x}', name=Path(provider['file']).stem,
                    category=font.split(':')[1], font_id=font, codepoint=f'U+{cp:04X}', glyph=glyph,
                    java_escape=f'\\u{cp:04X}', image_resource=provider['file'],
                    image_path=image_path.as_posix(), image_size=[image.width, image.height],
                    source_rect=[x,y,cw,ch], alpha_bounds=list(bounds) if bounds else None,
                    height=height, ascent=ascent, provider_index=index,
                    source_font=path.as_posix()))
    return entries


def usage(entry):
    font, cp = entry['font_id'], int(entry['codepoint'][2:], 16)
    if font == 'neoworld:gui_icons':
        return f'NeoGuiIcons.icon(NeoGuiIcons.{GUI[cp]})'
    if font == 'neoworld:gui_controls':
        return f'NeoGuiIcons.label(NeoGuiIcons.{GUI[cp]}, "ข้อความ")'
    if cp in SYSTEM and font == ('neoworld:fashion_tooltip' if cp == 0xE200 else 'neoworld:icons'):
        return f'NeoSystemIcons.icon(NeoSystemIcons.Icon.{SYSTEM[cp]})'
    if font in {'neoworld:icons','neoworld:currency','neoworld:ranks','neoworld:fashion_tooltip','neoworld:activity_icons'}:
        return f'NeoBitmapFonts.text("{entry["java_escape"]}")'
    if font == 'neoworld:pvp_titles':
        if cp in (0xE400,0xE401):
            return f'PvpTitles.result({str(cp == 0xE400).lower()})'
        if cp == 0xE407:
            return 'PvpTitles.fight()'
        return f'PvpTitles.countdown({5 - (cp - 0xE402)})'
    return f'NeoBitmapIcon.{constant(entry)}.component()'


def constant(entry):
    return re.sub(r'[^A-Z0-9_]', '_', f'{entry["category"]}_{entry["name"]}'.upper())


def java_registry(data):
    constants = [constant(entry) for entry in data['entries']]
    if len(set(constants)) != len(constants):
        raise ValueError('Duplicate Java icon name')
    rows = [f'    {constant(e)}("{e["font_id"]}", 0x{e["codepoint"][2:]}, {e["height"]}, {e["ascent"]})'
            for e in data['entries']]
    return ('''package com.ambition.neoworld.client.gui.common;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Generated by tools/fonts/icon_catalog.py from existing icon providers. Do not edit by hand. */
public enum NeoBitmapIcon {
''' + ',\n'.join(rows) + ''';

    private final ResourceLocation font;
    private final int codepoint, height, ascent;
    NeoBitmapIcon(String font, int codepoint, int height, int ascent) {
        this.font = ResourceLocation.parse(font);
        this.codepoint = codepoint;
        this.height = height;
        this.ascent = ascent;
    }
    public ResourceLocation font() { return font; }
    public int codepoint() { return codepoint; }
    public int height() { return height; }
    public int ascent() { return ascent; }
    public String glyph() { return Character.toString(codepoint); }
    /** Only the glyph gets the bitmap font; surrounding labels keep their ordinary font. */
    public MutableComponent component() {
        return Component.literal(glyph()).withStyle(style -> style.withFont(font));
    }
}
''').encode('utf-8')


def collect(root):
    entries = [entry for name in FONTS for entry in read_font(root, f'neoworld:{name}')]
    # Follow the actual default provider order: the first matching glyph wins.
    defaults = {}
    default_json = root / 'src/main/resources/assets/minecraft/font/default.json'
    for provider in json.loads(default_json.read_text(encoding='utf-8'))['providers']:
        if provider['type'].removeprefix('minecraft:') == 'reference':
            font = provider['id']
            if font.split(':')[1] in FONTS and font.startswith('neoworld:'):
                for entry in read_font(root, font):
                    previous = defaults.setdefault(entry['codepoint'], entry)
                    if (previous['image_resource'], previous['source_rect']) != (entry['image_resource'], entry['source_rect']):
                        raise ValueError(f'Default glyph image collision: {entry["codepoint"]}')
    for entry in entries:
        default = defaults.get(entry['codepoint'])
        entry['default_font'] = None if default is None else dict(font_id=default['font_id'],
            height=default['height'], ascent=default['ascent'],
            same_metrics=default['height'] == entry['height'] and default['ascent'] == entry['ascent'])
        entry['usage_java'] = usage(entry)
        entry['java_constant'] = 'NeoBitmapIcon.' + constant(entry)
    sources = sorted({e['source_font'] for e in entries} | {e['image_path'] for e in entries}
                     | {'src/main/resources/assets/minecraft/font/default.json',
                        'src/main/java/com/ambition/neoworld/client/gui/common/NeoSystemIcons.java',
                        'src/main/java/com/ambition/neoworld/client/gui/common/NeoGuiIcons.java',
                        'src/main/java/com/ambition/neoworld/client/gui/common/NeoBitmapFonts.java',
                        'src/main/java/com/ambition/neoworld/pvp/room/PvpTitles.java'})
    return dict(schema_version=1, scope='Existing icon fonts only; excludes thai and roboto',
                fonts=[f'neoworld:{name}' for name in FONTS], entry_count=len(entries),
                unique_codepoints=len({e['codepoint'] for e in entries}),
                sources={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources}, entries=entries)


def thumbnail(root, entry):
    with Image.open(root / entry['image_path']) as source:
        x,y,w,h = entry['source_rect']
        image = source.convert('RGBA').crop((x,y,x+w,y+h))
    ratio = min(112 / image.width, 48 / image.height)
    return image.resize((max(1,round(image.width*ratio)),max(1,round(image.height*ratio))), Image.Resampling.NEAREST)


def previews(root, data):
    cards = []
    sheet = Image.new('RGB', (1000, ((len(data['entries'])+3)//4)*100), '#171e24')
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for index, entry in enumerate(data['entries']):
        image = thumbnail(root, entry)
        buffer = io.BytesIO()
        image.save(buffer, format='PNG')
        uri = 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode('ascii')
        x,y = index%4*250, index//4*100
        sheet.paste(image, (x+12,y+5), image)
        draw.text((x+12,y+56), entry['name'][:32], fill='white', font=font)
        draw.text((x+12,y+72), f'{entry["category"]} {entry["codepoint"]} H{entry["height"]} A{entry["ascent"]}', fill='#8ed8ca', font=font)
        esc = html.escape
        default = entry['default_font']
        note = 'Explicit font required' if not default else f'Default: H{default["height"]} A{default["ascent"]}'
        cards.append(f'<article><div class="image"><img src="{uri}" alt="{esc(entry["name"])}"></div>'
            f'<b>{esc(entry["name"])}</b><p>{esc(entry["font_id"])} · {entry["codepoint"]}</p>'
            f'<p>Height {entry["height"]} · Ascent {entry["ascent"]} · {note}</p>'
            f'<code>{esc(entry["image_resource"])}</code><pre>{esc(entry["usage_java"])}</pre></article>')
    page = '''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>NeoWorld Bitmap Icon Catalogue</title><style>
body{background:#12191d;color:#e7ecef;font:15px system-ui;margin:24px} input{padding:12px;width:min(90%,600px)}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:12px;margin-top:20px}
article{border:1px solid #51626b;padding:16px;min-width:0} p{font-size:12px;color:#9dc6bd} code,pre{font-size:12px;overflow-wrap:anywhere;white-space:pre-wrap}
.image{height:64px;display:flex;align-items:center;background:repeating-conic-gradient(#26323a 0% 25%,#35434c 0% 50%) 0/16px 16px;margin-bottom:12px}
img{image-rendering:pixelated;margin:8px} [hidden]{display:none}</style>
<h1>NeoWorld · Bitmap Icons</h1><p>ค้นหาไอคอนเดิมก่อนสร้างใหม่ · ไม่รวมฟอนต์ไทย/อังกฤษ · ภาพจาก PNG จริง</p>
<input id="search" aria-label="ค้นหาไอคอน" placeholder="ค้นหาชื่อ / font / Unicode / พาธ / helper"><span id="count"></span><main>'''
    page += ''.join(cards) + '''</main><script>
const cards=[...document.querySelectorAll('article')];const search=document.querySelector('#search');
function filter(){let n=0;for(const card of cards){card.hidden=!card.textContent.toLowerCase().includes(search.value.toLowerCase());if(!card.hidden)n++;}document.querySelector('#count').textContent=' '+n+' entries';}
search.addEventListener('input',filter);filter();</script></html>'''
    buffer = io.BytesIO()
    sheet.save(buffer, format='PNG')
    return page.encode('utf-8'), buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate sources and reject stale generated outputs')
    args = parser.parse_args()
    data = collect(ROOT)
    page, sheet = previews(ROOT, data)
    outputs = {'catalog.json':(json.dumps(data, ensure_ascii=False, indent=2)+'\n').encode('utf-8'),
               'index.html':page, 'contact-sheet.png':sheet}
    for name, content in outputs.items():
        path = ROOT / OUT / name
        if args.check:
            if not path.is_file() or path.read_bytes() != content:
                raise SystemExit(f'Stale/missing catalogue output: {path}; regenerate with tools/fonts/icon_catalog.py')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    java_path = ROOT / 'src/main/java/com/ambition/neoworld/client/gui/common/NeoBitmapIcon.java'
    java_content = java_registry(data)
    if args.check:
        if not java_path.is_file() or java_path.read_bytes() != java_content:
            raise SystemExit('Stale/missing NeoBitmapIcon.java; regenerate icon catalogue')
    else:
        java_path.write_bytes(java_content)
    print(f'Icon catalogue {"validated" if args.check else "generated"}: {len(FONTS)} fonts, '
          f'{data["entry_count"]} variants, {data["unique_codepoints"]} codepoints')


if __name__ == '__main__':
    main()
