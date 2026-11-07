"""Export approved pixel armor from Blender, preserving all source geometry."""
import bpy, json, importlib, sys, types, zipfile, math, shutil
from pathlib import Path
ROOT=Path('C:/Users/miw/Documents/NeoWorldMod')
OUT=ROOT/'docs/model/DragonArmor'
ASSETS=ROOT/'src/main/resources/assets/neoworld'
if bpy.context.object and bpy.context.object.mode!='OBJECT':bpy.ops.object.mode_set(mode='OBJECT')
scene=next(s for s in bpy.data.scenes if s.name=='Dragon Armor • pixel knight')
bpy.context.window.scene=scene
rig=next(o for o in scene.objects if o.type=='ARMATURE')
rig.data.pose_position='REST'
package=types.ModuleType('dragon_exporter')
package.__path__=[str(Path.home()/'AppData/Roaming/Blender Foundation/Blender/5.2/scripts/addons/blender-json-addon-main')]
sys.modules[package.__name__]=package
compat=importlib.import_module('dragon_exporter.compat')
exporter=importlib.import_module('dragon_exporter.export_mc_json')
jar=next((Path.home()/'.gradle/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8175609').rglob('*.jar'))
with zipfile.ZipFile(jar) as z:baseline=json.loads(z.read('assets/epicfight/animmodels/entity/biped.json'))['armature']
def transforms(nodes):
 r={}
 for n in nodes:r[n['name']]=n['transform'];r.update(transforms(n.get('children',[])))
 return r
actual=transforms(json.loads(json.dumps(compat.export_armature(rig,False,'MAT'),cls=compat.NoIndentEncoder))['hierarchy'])
expected=transforms(baseline['hierarchy'])
assert set(actual)==set(expected)
err=max(abs(a-b) for k in expected for a,b in zip(actual[k],expected[k]))
assert err<1e-4,err
for folder in ['animmodels/armor','models/armor','models/item','textures/models/armor','textures/item/dragon']:(ASSETS/folder).mkdir(parents=True,exist_ok=True)
shutil.copy2(OUT/'dragon_pixel_atlas.png',ASSETS/'textures/models/armor/dragon.png')
report={'rig_matrix_error':err,'parts':{}}
for part in ['helmet','chestplate','leggings','boots']:
 o=next(o for o in scene.objects if o.type=='MESH' and o.name.split('.')[0]=='dragon_'+part)
 assert o.matrix_world.is_identity
 assert len([m for m in o.modifiers if m.type=='ARMATURE' and m.object==rig])==1
 assert all(any(g.weight>0 and o.vertex_groups[g.group].name in baseline['joints'] for g in v.groups) for v in o.data.vertices)
 data=json.loads(json.dumps({'vertices':exporter.export_mesh(o,baseline['joints'],apply_modifiers=False)},cls=compat.NoIndentEncoder))
 # The approved artist source faces -Y. Epic Fight's humanoid faces +Y in
 # Blender space (its loader then rotates X by -90 degrees). Reflect only
 # the exported mesh, not the shared canonical skeleton or source .blend.
 for key in ['positions','normals']:
  a=data['vertices'][key]['array']
  for i in range(1,len(a),3):a[i]=-a[i]
 for indices in data['vertices']['parts'].values():
  a=indices['array']
  for i in range(0,len(a),9):a[i+3:i+9]=a[i+6:i+9]+a[i+3:i+6]
 data['render_properties']={'texture_path':'neoworld:textures/models/armor/dragon.png'}
 (ASSETS/f'animmodels/armor/dragon_{part}.json').write_text(json.dumps(data),encoding='utf-8')
 # Vanilla renderer uses the identical mesh and UVs, with rigid bone-to-body-part mapping.
 o.data.calc_loop_triangles()
 faces=[]
 for tri in o.data.loop_triangles:
  verts=[]
  bone=None
  for vi,li in zip(tri.vertices,tri.loops):
   v=o.data.vertices[vi]
   group=max(v.groups,key=lambda g:g.weight)
   name=o.vertex_groups[group.group].name
   assert bone is None or bone==name
   bone=name
   uv=o.data.uv_layers.active.data[li].uv
   verts.append([-v.co.x,1.5-v.co.z,v.co.y,uv.x,1-uv.y,-tri.normal.x,-tri.normal.z,tri.normal.y])
  faces.append({'bone':bone,'vertices':verts})
 (ASSETS/f'models/armor/dragon_{part}.json').write_text(json.dumps(faces),encoding='utf-8')
 # Inventory artwork: render each actual part over transparency, no approximated icon.
 for mesh in [a for a in scene.objects if a.type=='MESH']:mesh.hide_render=mesh!=o
 from mathutils import Vector
 coords=[o.matrix_world@Vector(c) for c in o.bound_box]
 center=sum(coords,Vector())/8
 cam=scene.camera
 cam.location=center+Vector((2,-4,1.4))
 cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler()
 cam.data.ortho_scale=max(o.dimensions)*1.3
 scene.render.film_transparent=True
 scene.render.resolution_x=128;scene.render.resolution_y=128
 scene.render.filepath=str(ASSETS/f'textures/item/dragon/{part}.png')
 bpy.ops.render.render(write_still=True)
 (ASSETS/f'models/item/dragon_{part}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'neoworld:item/dragon/{part}'}}),encoding='utf-8')
 report['parts'][part]={'vertices':len(o.data.vertices),'triangles':len(faces),'unweighted':0}
for mesh in [a for a in scene.objects if a.type=='MESH']:mesh.hide_render=False
(OUT/'game_export_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
