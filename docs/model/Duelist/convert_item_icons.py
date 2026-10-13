"""Convert the supplied item models to NeoForge OBJ without compiling the mod.

Run with Blender --background --python to use its Euler rotation math.
"""
import json
import math
import sys
from pathlib import Path
from mathutils import Euler, Vector

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'src/main/resources/assets/neoworld'
TEXTURE = 'neoworld:item/duelist/netherite_duelist_armor'

def corners(direction, lo, hi):
    x, y, z = lo
    X, Y, Z = hi
    return {
        'north': [(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],
        'south': [(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
        'east': [(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],
        'west': [(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
        'up': [(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],
        'down': [(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],
    }[direction]

for source_name, item in [('DuelistArmorChest - Converted', 'duelist_armor_chest'),
                          ('DuelistArmorBoot - Converted', 'duelist_armor_boots'),
                          ('DuelistArmorHelmet - Converted', 'duelist_armor_helmet')]:
    if '--helmet-only' in sys.argv and item != 'duelist_armor_helmet':
        continue
    source = json.loads(Path(__file__).with_name(source_name + '.json').read_text(encoding='utf-8'))
    obj_path = ASSETS / 'models/item' / (item + '.obj')
    lines = ['# Baked rotations from the supplied Blockbench item model',
             'mtllib duelist_icons.mtl', 'usemtl duelist']
    index = 1
    face_count = 0
    degenerate_count = 0
    for element_index, element in enumerate(source['elements']):
        rotation = element.get('rotation', {})
        angles = [rotation.get(axis, 0) for axis in 'xyz']
        if 'angle' in rotation:
            angles['xyz'.index(rotation['axis'])] = rotation['angle']
        matrix = Euler(tuple(math.radians(angle) for angle in angles), 'XYZ').to_matrix()
        origin = Vector(rotation.get('origin', [8,8,8]))
        lines.append(f'g element_{element_index}')
        for direction, face in element['faces'].items():
            assert face['texture'] == '#0'
            points = corners(direction, element['from'], element['to'])
            # Minecraft FaceInfo orders vertices counterclockwise as viewed from outside.
            points.reverse()
            u1,v1,u2,v2 = face['uv']
            uvs = [(u1,v1),(u2,v1),(u2,v2),(u1,v2)]
            quarter_turns = int(face.get('rotation', 0)) // 90
            uvs = uvs[quarter_turns:] + uvs[:quarter_turns]
            uvs.reverse()
            transformed = [(matrix @ (Vector(point)-origin)+origin)/16 for point in points]
            normal = (transformed[1]-transformed[0]).cross(transformed[2]-transformed[0])
            if normal.length <= 1e-12:
                degenerate_count += 1
                continue
            normal.normalize()
            for point in transformed:
                assert all(math.isfinite(value) for value in point)
                lines.append('v ' + ' '.join(f'{value:.9f}' for value in point))
            for u,v in uvs:
                lines.append(f'vt {u/16:.9f} {1-v/16:.9f}')
            for _ in range(4):
                lines.append('vn ' + ' '.join(f'{value:.9f}' for value in normal))
            lines.append('f ' + ' '.join(f'{i}/{i}/{i}' for i in range(index,index+4)))
            index += 4
            face_count += 1
    assert face_count + degenerate_count == sum(len(element['faces']) for element in source['elements'])
    obj_path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    model = {'loader': 'neoforge:obj', 'model': f'neoworld:models/item/{item}.obj',
             'automatic_culling': False, 'flip_v': True, 'emissive_ambient': False,
             'textures': {'particle': TEXTURE}, 'display': source.get('display', {})}
    obj_path.with_suffix('.json').write_text(json.dumps(model, indent=2)+'\n', encoding='utf-8')
    print(f'Converted {item}: {len(source["elements"])} elements, {face_count} faces; display preserved')

(ASSETS / 'models/item/duelist_icons.mtl').write_text(
    f'newmtl duelist\nKa 1 1 1\nKd 1 1 1\nmap_Kd {TEXTURE}\n', encoding='utf-8')
