"""Blender Python or ordinary Python: validate both render paths against source geometry."""
import json, math, struct
from pathlib import Path
ROOT=Path('C:/Users/miw/Documents/NeoWorldMod')
A=ROOT/'src/main/resources/assets/neoworld'
for part in ['helmet','chestplate','leggings','boots']:
    item='dragon_'+part
    data=json.loads((A/f'animmodels/armor/{item}.json').read_text())
    v=data['vertices']
    for key in ['positions','normals','uvs','vcounts','weights','vindices']:
        a=v[key]
        assert len(a['array'])==a['count']*a['stride'],(item,key)
        assert all(math.isfinite(x) for x in a['array'])
    assert min(v['vcounts']['array'])>0
    assert sum(v['vcounts']['array'])*2==len(v['vindices']['array'])
    assert all(0<=n<20 for n in v['vindices']['array'][::2])
    assert all(0<=n<v['weights']['count'] for n in v['vindices']['array'][1::2])
    ns=v['normals']['array']
    assert all(abs(sum(x*x for x in ns[i:i+3])-1)<1e-3 for i in range(0,len(ns),3))
    for p in v['parts'].values():
        assert len(p['array'])==p['count']*3
        assert all(0<=x<v[['positions','uvs','normals'][i%3]]['count'] for i,x in enumerate(p['array']))
    faces=json.loads((A/f'models/armor/{item}.json').read_text())
    positions=[v['positions']['array'][i:i+3] for i in range(0,len(v['positions']['array']),3)]
    # Vanilla Z negates Epic Fight's exported Blender-space Y. Artist source
    # faces -Y; Epic export reflects Y, while vanilla retains artist Y as Z.
    for face in faces:
        for x,y,z,u,t,nx,ny,nz in face['vertices']:
            assert min(sum((a-b)**2 for a,b in zip([-x,-z,1.5-y],p)) for p in positions)<1e-10
            assert 0<=u<=1 and 0<=t<=1
    icon=(A/f'textures/item/dragon/{part}.png').read_bytes()
    assert icon[:8]==b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II',icon[16:24])==(128,128)
    for locale in ['en_us','th_th']:
        lang=json.loads((A/f'lang/{locale}.json').read_text(encoding='utf-8'))
        assert 'item.neoworld.'+item in lang
    print('PASS',item,'mesh, skinning, normalized normals, UVs, vanilla axes, icons, translations')
print('PASS all four armor items')
