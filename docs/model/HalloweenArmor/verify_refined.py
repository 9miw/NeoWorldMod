"""Read-only verification of the saved revision and staged Epic Fight meshes."""
import bpy, json, zipfile, math
from pathlib import Path
from mathutils import Vector
ROOT=Path('C:/Users/miw/Documents/NeoWorldMod/docs/model/HalloweenArmor')
scene=bpy.data.scenes['Halloween • Mockup revision']
rig=next(o for o in scene.objects if o.type=='ARMATURE')
assert rig.data.pose_position=='REST'
assert all(p.matrix_basis.is_identity for p in rig.pose.bones)
jar=next((Path.home()/'.gradle/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8175609').rglob('*.jar'))
with zipfile.ZipFile(jar) as archive:
    names=json.loads(archive.read('assets/epicfight/animmodels/entity/biped.json'))['armature']['joints']
results={}
for part in ['helmet','chestplate','leggings','boots']:
    name='halloween_refined_'+part
    mesh=bpy.data.objects[name]
    p=ROOT/f'export_refined/assets/neoworld/animmodels/armor/{name}.json'
    data=json.loads(p.read_text());v=data['vertices']
    assert mesh.matrix_world.is_identity
    assert v['positions']['count']==len(mesh.data.vertices)
    for vertex in mesh.data.vertices:
        expected=[vertex.co.x,-vertex.co.y,vertex.co.z]
        actual=v['positions']['array'][vertex.index*3:vertex.index*3+3]
        assert max(abs(a-b) for a,b in zip(expected,actual))<1e-6
        group=max(vertex.groups,key=lambda g:g.weight)
        assert v['vindices']['array'][vertex.index*2]==names.index(mesh.vertex_groups[group.group].name)
    triangles=0
    for indices in v['parts'].values():
        a=indices['array']
        for i in range(0,len(a),9):
            ids=a[i:i+9]
            points=[Vector(v['positions']['array'][ids[j]*3:ids[j]*3+3]) for j in [0,3,6]]
            cross=(points[1]-points[0]).cross(points[2]-points[0])
            assert cross.length>1e-10,(name,i,'degenerate triangle')
            n=Vector(v['normals']['array'][ids[2]*3:ids[2]*3+3])
            assert cross.normalized().dot(n)>.99,(name,i,'winding/normal mismatch')
            triangles+=1
    texture=data['render_properties']['texture_path'].split(':')[1]
    assert (ROOT/'export_refined/assets/neoworld'/texture).is_file()
    assert all(math.isfinite(x) for k in ['positions','normals','uvs'] for x in v[k]['array'])
    results[name]={'triangles':triangles,'source_roundtrip':'PASS','joint_mapping':'PASS','winding':'PASS'}
(ROOT/'export_refined_verification.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))
