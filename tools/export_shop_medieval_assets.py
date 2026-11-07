"""Extract generated medieval art; normalize registration and validate actual PNGs."""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/gui/shop/medieval'
OUT = ROOT / 'src/main/resources/assets/neoworld/textures/gui/shop/medieval'
STATES = ('normal', 'hover', 'pressed', 'selected', 'disabled')


def main():
    sources = {n: Image.open(DOC / 'sources' / (n+'.png')).convert('RGBA') for n in ('panels', 'widgets')}
    assets = {}
    manifest = {'stage': 'exported assets; not integrated', 'reference': 'docs/gui/shop/medieval/approved-mockup.png', 'alpha_threshold': 224, 'assets': {}}

    def extract(name, source, roi):
        im = sources[source].crop(roi)
        im.putalpha(im.getchannel('A').point(lambda a: 255 if a >= 224 else 0))
        bounds = im.getchannel('A').getbbox()
        assert bounds, name
        assets[name] = im.crop(bounds)
        manifest['assets'][name] = {'source': source, 'roi_xyxy': roi, 'source_bounds_xyxy': bounds}

    panels = {
        'frame': (75, 35, 840, 335),
        'panels/title': (880, 20, 1490, 305),
        'panels/products': (75, 345, 995, 690),
        'panels/details': (1035, 345, 1465, 825),
        'panels/inventory': (75, 700, 995, 985),
        'panels/section': (1040, 840, 1465, 980),
    }
    for name, roi in panels.items():
        extract(name, 'panels', roi)
    rows = ((45,175),(180,285),(290,400),(405,565),(570,730),(735,930))
    columns = ((25,345),(345,660),(660,970),(970,1280),(1280,1600))
    groups = ('tabs','buttons/buy','buttons/sell','buttons/utility','buttons/close','slots')
    for group, (y1,y2) in zip(groups, rows):
        for state, (x1,x2) in zip(STATES, columns):
            extract(group+'/'+state, 'widgets', (x1,y1,x2,y2))
    # Close is wood at rest and gray wood when disabled; red is reserved for hover/press.
    for state in ('normal','disabled'):
        name = 'buttons/close/'+state
        assets[name] = assets['buttons/utility/'+state].copy()
        manifest['assets'][name] = dict(manifest['assets']['buttons/utility/'+state], reused_from='buttons/utility/'+state)
    for group in groups:
        target = assets[group+'/normal'].size
        if group in ('buttons/utility','buttons/close','slots'):
            target = (max(target),)*2
        for state in STATES:
            name = group+'/'+state
            im = assets[name]
            manifest['assets'][name]['native_size'] = list(im.size)
            assets[name] = im.resize(target,Image.Resampling.NEAREST)
    # Reuse the existing foreground art instead of allocating new icon-font glyphs.
    for icon in ('left','right','close'):
        name = 'icons/'+icon
        path = ROOT / 'src/main/resources/assets/neoworld/textures/gui/shop/obsidian' / (name+'.png')
        assets[name] = Image.open(path).convert('RGBA')
        manifest['assets'][name] = {'reused_from': path.relative_to(ROOT).as_posix()}
    for name, im in assets.items():
        result = Image.new('RGBA',(im.width+4,im.height+4))
        result.paste(im,(2,2))
        # Clear hidden RGB at transparent pixels.
        result.paste((0,0,0,0),mask=result.getchannel('A').point(lambda a:255-a))
        path = OUT / (name+'.png')
        path.parent.mkdir(parents=True,exist_ok=True)
        result.save(path)
        path.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":true}}\n')
        levels = [i for i,n in enumerate(result.getchannel('A').histogram()) if n]
        assert levels == [0,255], (name,levels)
        manifest['assets'][name].update(file=path.relative_to(ROOT).as_posix(),size=list(result.size),opaque_bounds_xyxy=list(result.getchannel('A').getbbox()),sha256=hashlib.sha256(path.read_bytes()).hexdigest())
        assets[name] = result
    for group in groups:
        assert len({assets[group+'/'+s].size for s in STATES}) == 1
        assert len({assets[group+'/'+s].getchannel('A').getbbox() for s in STATES}) == 1
    assert len(assets)==39
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    preview=Image.new('RGB',(1200,((len(assets)+4)//5)*170))
    d=ImageDraw.Draw(preview)
    for i,(name,im) in enumerate(assets.items()):
        x,y=(i%5)*240,(i//5)*170
        for yy in range(22,170,10):
            for xx in range(0,240,10):
                c=(80,85,92) if (xx//10+yy//10)%2 else (160,164,170)
                d.rectangle((x+xx,y+yy,x+xx+9,y+yy+9),fill=c)
        d.text((x+4,y+4),name,fill='white')
        scale=min(225/im.width,138/im.height)
        thumb=im.resize((max(1,round(im.width*scale)),max(1,round(im.height*scale))),Image.Resampling.NEAREST)
        preview.paste(thumb,(x+(240-thumb.width)//2,y+24+(140-thumb.height)//2),thumb)
    preview.save(DOC/'export-preview.png')
    print('PASS: 39 PNGs, binary alpha, matching state bounds/canvases, sampling metadata and manifest.')


if __name__ == '__main__':
    main()
