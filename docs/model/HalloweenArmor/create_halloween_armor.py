"""Author the approved pumpkin-knight set in the user's live Blender.

Creates a separate scene/project and staged Epic Fight assets. Never writes
DragonArmor sources or the mod's active resources.
"""
import bpy
import bmesh
import math
import json
import random
import importlib
import sys
import types
import zipfile
from pathlib import Path
from mathutils import Vector

OUT=Path('C:/Users/miw/Documents/NeoWorldMod/docs/model/HalloweenArmor')
assert OUT.is_dir()
assert not (OUT/'halloween_armor.blend').exists(), 'Refusing to overwrite an existing project'
source_file=bpy.data.filepath
source_rig=next(o for o in bpy.context.scene.objects if o.type=='ARMATURE')
if bpy.context.object and bpy.context.object.mode!='OBJECT':
    bpy.ops.object.mode_set(mode='OBJECT')
scene=bpy.data.scenes.new('Halloween • Pumpkin Knight')
bpy.context.window.scene=scene
rig=source_rig.copy()
rig.data=source_rig.data.copy()
rig.name='Halloween_EpicFight_Rig'
rig.animation_data_clear()
scene.collection.objects.link(rig)
for p in rig.pose.bones:p.matrix_basis.identity()
rig.data.pose_position='REST'
rig.show_in_front=True

# Shared pixel atlas. All visible modeling is real low-poly geometry.
palette=[(.19,.17,.22),(.82,.31,.055),(.27,.095,.30),(.20,.085,.035),
         (.24,.40,.065),(1,.64,.12),(.028,.021,.035),(.40,.36,.43)]
labels=['Night steel','Pumpkin bronze','Torn violet','Crooked stem',
        'Witch vines','Amber lantern','Carved shadow','Forged edge']
atlas=bpy.data.images.new('Halloween pixel atlas',width=256,height=256)
rng=random.Random(1030)
clusters={(t,x,y):rng.choice([.82,.94,1,1.10,1.18]) for t in range(8) for x in range(16) for y in range(16)}
pixels=[]
for y in range(256):
    for x in range(256):
        tile=(y//32)*8+x//32
        mat=tile%8
        u,v=x%32,y%32
        edge=min(u,v,31-u,31-v)
        shade=clusters[mat,u//2,v//2]
        if edge<1:shade*=.58
        elif v>28 or u<3:shade*=1.25
        if mat==2 and (u%8==0 or v%12==0):shade*=.83
        if mat==1 and u%8 in [0,1]:shade*=.83
        pixels.extend([min(1,c*shade) for c in palette[mat]]+[1])
atlas.pixels=pixels
atlas.filepath_raw=str(OUT/'halloween_atlas.png')
atlas.file_format='PNG'
atlas.save()
atlas.pack()
mats=[]
for i,name in enumerate(labels):
    m=bpy.data.materials.new('Halloween / '+name)
    m.diffuse_color=(*palette[i],1)
    m.use_nodes=True
    bsdf=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    bsdf.inputs['Roughness'].default_value=.72
    bsdf.inputs['Metallic'].default_value=.3 if i in [0,1,7] else 0
    tex=m.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image=atlas
    tex.interpolation='Closest'
    m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    if i==5:
        bsdf.inputs['Emission Color'].default_value=(1,.26,.015,1)
        bsdf.inputs['Emission Strength'].default_value=2
    mats.append(m)

pieces={p:[] for p in ['helmet','chestplate','leggings','boots']}
def finish(o,name,mat,bone,part):
    o.name='HW / '+name
    bpy.context.view_layer.objects.active=o
    bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
    o.data.materials.append(mats[mat])
    uv=o.data.uv_layers.active or o.data.uv_layers.new()
    for f in o.data.polygons:
        axes=sorted(range(3),key=lambda a:abs(f.normal[a]))[:2]
        points=[o.data.vertices[o.data.loops[i].vertex_index].co for i in f.loop_indices]
        lows=[min(v[a] for v in points) for a in axes]
        spans=[max(v[a] for v in points)-lo for a,lo in zip(axes,lows)]
        # Discrete texel density; center each face in its material tile.
        extent=[max(2,min(30,round(s*64))) for s in spans]
        for li,v in zip(f.loop_indices,points):
            q=[(v[a]-lo)/max(s,1e-8) for a,lo,s in zip(axes,lows,spans)]
            uv.data[li].uv=((mat*32+(32-extent[0])/2+q[0]*extent[0])/256,
                            (16-extent[1]/2+q[1]*extent[1])/256)
    o.vertex_groups.new(name=bone).add(list(range(len(o.data.vertices))),1,'REPLACE')
    pieces[part].append(o)
    return o

def box(name,pos,size,mat,bone,part,bevel=0,rot=0):
    bpy.ops.mesh.primitive_cube_add(size=1,location=pos)
    o=bpy.context.object
    o.scale=size
    o.rotation_euler.y=rot
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        mod=o.modifiers.new('Block chamfer','BEVEL')
        mod.width=bevel;mod.segments=1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o,name,mat,bone,part)

def panel(name,points,y,depth,mat,bone,part):
    verts=[(x,y,z) for x,z in points]+[(x,y+depth,z) for x,z in points]
    n=len(points)
    faces=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]
    faces += [(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(verts,[],faces)
    mesh.update()
    # Recalculate closed solid normals; winding is independent of author order.
    bm=bmesh.new();bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm,faces=bm.faces)
    bm.to_mesh(mesh);bm.free()
    o=bpy.data.objects.new(name,mesh)
    scene.collection.objects.link(o)
    return finish(o,name,mat,bone,part)

def strip(name,a,b,width,depth,mat,bone,part):
    a,b=Vector(a),Vector(b)
    o=box(name,(a+b)/2,(width,depth,(b-a).length),mat,bone,part)
    # Already world-space mesh: rotate around the strip's center directly.
    from mathutils import Matrix
    center=(a+b)/2
    R=(b-a).to_track_quat('Z','Y').to_matrix().to_4x4()
    M=Matrix.Translation(center)@R@Matrix.Translation(-center)
    o.data.transform(M)
    return o

def pumpkin_face(name,cx,cy,cz,w,h,bone,part):
    # Pumpkin cheeks form a thick frame around dark cutouts and inset eyes.
    box(name+' orange shell',(cx,cy+.028,cz),(w,.075,h),1,bone,part,.018)
    box(name+' carved cavity',(cx,cy-.014,cz),(w*.84,.025,h*.77),6,bone,part)
    for side in [-1,1]:
        ex=cx+side*w*.20
        points=[(ex-side*w*.14,cz+h*.22),(ex+side*w*.13,cz+h*.13),(ex+side*w*.10,cz+h*.03)]
        panel(name+' eye socket',points,cy-.032,.014,5,bone,part)
        # Diagonal brows imply the angry carved face without smooth curves.
        strip(name+' brow',(cx+side*w*.07,cy-.047,cz+h*.15),
              (cx+side*w*.36,cy-.047,cz+h*.27),h*.08,.025,1,bone,part)
    mouth=[]
    for x,z in [(-.35,-.10),(-.23,-.22),(-.12,-.12),(0,-.24),(.12,-.12),(.23,-.22),(.35,-.10),(.28,-.33),(.16,-.28),(0,-.38),(-.16,-.28),(-.28,-.33)]:
        mouth.append((cx+x*w,cz+z*h))
    panel(name+' zigzag mouth',mouth,cy-.037,.012,5,bone,part)
    for side in [-1,1]:
        box(name+' corner',(cx+side*w*.435,cy-.032,cz),(w*.075,.025,h*.65),1,bone,part)

def rag(name,x,y,z,w,h,bone,part,orange=True):
    points=[(x-w/2,z+h/2),(x+w/2,z+h/2),(x+w/2,z-h*.28),
            (x+w*.28,z-h*.26),(x+w*.18,z-h*.47),(x+w*.05,z-h*.34),
            (x-w*.10,z-h*.53),(x-w*.22,z-h*.37),(x-w/2,z-h*.44)]
    panel(name,points,y,.024,2,bone,part)
    if orange:
        for s in [-1,1]:
            box(name+' piping',(x+s*w*.43,y-.013,z+.015),(w*.075,.019,h*.84),1,bone,part)

def vine(x,y,z,length,bone,part):
    for i in range(max(2,int(length/.035))):
        xx=x+(.013 if (i//2)%2 else 0)
        box('Pixel vine',(xx,y,z-i*.035),(.025,.018,.042),4,bone,part)
        if i%3==0:box('Vine leaf',(xx+.021,y-.009,z-i*.035),(.04,.016,.022),4,bone,part)

# Helmet: hollow lower opening, pumpkin front, layered steel cage and stem.
box('Helmet crown',(0,0,2.00),(.57,.53,.12),0,'Head','helmet',.024)
box('Helmet nape',(0,.245,1.79),(.55,.065,.37),0,'Head','helmet',.012)
for s in [-1,1]:
    box('Helmet side',(s*.255,0,1.79),(.07,.49,.37),0,'Head','helmet')
    for i in range(3):
        box('Pumpkin crown segment',(s*(.055+i*.075),-.012,2.05-i*.012),(.073,.41,.08),1,'Head','helmet',.009)
    box('Temple orange edging',(s*.272,-.07,1.74),(.026,.41,.28),1,'Head','helmet')
pumpkin_face('Jack lantern',0,-.285,1.78,.51,.39,'Head','helmet')
for s in [-1,1]:
    strip('Heavy steel brow',(s*.02,-.346,1.89),(s*.27,-.326,1.99),.058,.048,0,'Head','helmet')
box('Helmet central ridge',(0,-.349,1.965),(.06,.06,.22),7,'Head','helmet')
box('Stem base',(.035,.025,2.13),(.09,.09,.13),3,'Head','helmet')
box('Crooked stem',(.003,.025,2.215),(.085,.075,.10),3,'Head','helmet',rot=-.45)
box('Stem hook',(-.065,.025,2.25),(.10,.075,.055),3,'Head','helmet')
for s in [-1,1]:
    box('Pumpkin leaf',(s*.12,-.05,2.11),(.07,.06,.025),4,'Head','helmet')

# Breastplate: dark stepped shield with orange V borders and a violet collar.
box('Chest shell',(0,.01,1.325),(.53,.31,.31),0,'Chest','chestplate',.018)
shield=[(-.25,1.47),(-.21,1.24),(0,1.15),(.21,1.24),(.25,1.47),(0,1.40)]
panel('Breastplate orange rim',shield,-.181,.055,1,'Chest','chestplate')
panel('Breastplate inset',[(x*.85,1.32+(z-1.32)*.80) for x,z in shield],-.217,.027,0,'Chest','chestplate')
box('Backplate',(0,.18,1.32),(.51,.065,.32),0,'Chest','chestplate')
for s in [-1,1]:
    strip('Collar trim',(0,-.218,1.405),(s*.245,-.17,1.49),.035,.025,1,'Chest','chestplate')
    strip('Back rim',(s*.23,.226,1.46),(s*.17,.226,1.17),.035,.025,1,'Chest','chestplate')
rag('Rear violet tabard',0,.222,1.32,.22,.31,'Chest','chestplate',False)
box('Flexible abdomen',(0,0,1.02),(.47,.29,.24),2,'Torso','chestplate')
for row in range(3):
    box('Abdominal steel lame',(0,-.157,1.12-row*.075),(.47,.035,.055),0,'Torso','chestplate')
for s in [-1,1]:
    box('Waist orange piping',(s*.21,-.18,1.02),(.027,.025,.22),1,'Torso','chestplate')

# Pauldrons and bracers, with cloth cuffs kept clear of elbow pivots.
for s in [-1,1]:
    side='R' if s>0 else 'L'
    arm='Arm_'+side;hand='Hand_'+side
    box('Pauldron core',(s*.405,0,1.465),(.31,.36,.23),0,arm,'chestplate',.025)
    for j in range(2):
        box('Shoulder lower trim',(s*(.405+j*.018),.015,1.40-j*.055),(.33+j*.025,.385,.045),1,arm,'chestplate')
    pumpkin_face('Shoulder pumpkin',s*.415,-.211,1.47,.285,.205,arm,'chestplate')
    for zz in [1.39,1.56]:box('Shoulder steel band',(s*.415,-.267,zz),(.33,.03,.032),0,arm,'chestplate')
    box('Shoulder outer frame',(s*.565,-.045,1.47),(.035,.33,.205),1,arm,'chestplate')
    rag('Ragged sleeve',s*.385,-.155,1.25,.245,.20,arm,'chestplate',False)
    box('Sleeve back',(s*.375,.12,1.25),(.235,.04,.17),2,arm,'chestplate')
    vine(s*.30,-.254,1.38,.17,arm,'chestplate')
    box('Bracer bronze shell',(s*.375,-.01,.94),(.26,.30,.235),1,hand,'chestplate',.009)
    box('Bracer face',(s*.375,-.173,.945),(.205,.035,.185),0,hand,'chestplate')
    box('Bracer rear inset',(s*.375,.152,.945),(.205,.022,.17),0,hand,'chestplate')
    for z in [.855,1.035]:box('Bracer band',(s*.375,-.01,z),(.272,.318,.028),1,hand,'chestplate')
    vine(s*.23,-.224,1.41,.18,'Chest','chestplate')

# Belt: torse attachment plus thigh-bound skirt pieces to avoid bridging legs.
box('Belt foundation',(0,0,.85),(.525,.35,.087),0,'Torso','leggings')
for s in [-1,1]:
    for x in [.09,.23]:box('Belt bronze fitting',(s*x,-.189,.85),(.035,.025,.063),1,'Torso','leggings')
pumpkin_face('Pumpkin belt clasp',0,-.208,.85,.135,.10,'Torso','leggings')
for s in [-1,1]:
    side='R' if s>0 else 'L';thigh='Thigh_'+side;leg='Leg_'+side
    box('Thigh shell',(s*.132,0,.625),(.235,.275,.28),0,thigh,'leggings')
    rag('Front split tabard',s*.12,-.19,.65,.23,.34,thigh,'leggings')
    rag('Rear split tabard',s*.12,.167,.64,.23,.35,thigh,'leggings')
    for j in range(2):
        # Side tassets are short, leaving the knee free to bend.
        box('Hip orange tier',(s*.245,.015,.74-j*.105),(.09,.30,.04),1,thigh,'leggings')
        box('Hip violet tier',(s*.245,.015,.705-j*.105),(.075,.27,.05),2,thigh,'leggings')
    vine(s*.20,-.221,.79,.22,thigh,'leggings')
    box('Knee cap rim',(s*.132,-.166,.405),(.235,.07,.10),1,leg,'leggings',.012)
    box('Knee cap steel',(s*.132,-.209,.405),(.17,.025,.066),0,leg,'leggings')
    box('Boot greave',(s*.132,.01,.24),(.24,.29,.255),0,leg,'boots',.01)
    box('Greave bronze top',(s*.132,.01,.345),(.25,.31,.035),1,leg,'boots')
    box('Greave bronze base',(s*.132,-.01,.14),(.25,.32,.033),1,leg,'boots')
    box('Greave front inset',(s*.132,-.15,.235),(.18,.03,.16),7,leg,'boots')
    box('Boot foot',(s*.132,-.055,.065),(.25,.39,.12),0,leg,'boots',.008)
    box('Toe orange bumper',(s*.132,-.255,.06),(.25,.035,.055),1,leg,'boots')
    box('Boot sole',(s*.132,-.055,.017),(.258,.405,.025),6,leg,'boots')

armor=[]
for part,objects in pieces.items():
    bpy.ops.object.select_all(action='DESELECT')
    for o in objects:o.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.object.join()
    o=bpy.context.object;o.name='halloween_'+part
    mod=o.modifiers.new('Epic Fight skin','ARMATURE');mod.object=rig
    armor.append(o)

# Canonical rig and mesh checks before any export or completion claims.
addon=Path.home()/'AppData/Roaming/Blender Foundation/Blender/5.2/scripts/addons/blender-json-addon-main'
package=types.ModuleType('halloween_exporter');package.__path__=[str(addon)]
sys.modules[package.__name__]=package
compat=importlib.import_module('halloween_exporter.compat')
exporter=importlib.import_module('halloween_exporter.export_mc_json')
jar=next((Path.home()/'.gradle/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8175609').rglob('*.jar'))
with zipfile.ZipFile(jar) as z:
    baseline=json.loads(z.read('assets/epicfight/animmodels/entity/biped.json'))['armature']
def transforms(nodes):
    result={}
    for n in nodes:
        result[n['name']]=n['transform'];result.update(transforms(n.get('children',[])))
    return result
expected=transforms(baseline['hierarchy'])
actual=transforms(json.loads(json.dumps(compat.export_armature(rig,False,'MAT'),cls=compat.NoIndentEncoder))['hierarchy'])
assert set(actual)==set(expected)
error=max(abs(a-b) for name in expected for a,b in zip(actual[name],expected[name]))
assert error<1e-4,error
report={'source_rig_file':source_file,'rig_matrix_error':error,'parts':{},'runtime_tested':False,
        'reference':'reference_mockup.png','source_faces':'-Y','epic_export':'Y reflected, triangle winding reversed'}
target=OUT/'export/assets/neoworld'
(target/'animmodels/armor').mkdir(parents=True,exist_ok=True)
(target/'textures/models/armor').mkdir(parents=True,exist_ok=True)
import shutil
shutil.copy2(OUT/'halloween_atlas.png',target/'textures/models/armor/halloween.png')
for o in armor:
    assert o.matrix_world.is_identity
    assert len([m for m in o.modifiers if m.type=='ARMATURE' and m.object==rig])==1
    assert all(math.isfinite(c) for v in o.data.vertices for c in v.co)
    assert all(any(g.weight>0 and o.vertex_groups[g.group].name in baseline['joints'] for g in v.groups) for v in o.data.vertices)
    data=json.loads(json.dumps({'vertices':exporter.export_mesh(o,baseline['joints'],False)},cls=compat.NoIndentEncoder))
    vs=data['vertices']
    for name in ['positions','normals']:
        arr=vs[name]['array']
        for i in range(1,len(arr),3):arr[i]=-arr[i]
    for idx in vs['parts'].values():
        arr=idx['array']
        for i in range(0,len(arr),9):arr[i+3:i+9]=arr[i+6:i+9]+arr[i+3:i+6]
    data['render_properties']={'texture_path':'neoworld:textures/models/armor/halloween.png'}
    for name in ['positions','normals','uvs','weights','vcounts','vindices']:
        a=vs[name];assert len(a['array'])==a['count']*a['stride']
    assert all(vs['vcounts']['array'])
    assert sum(vs['vcounts']['array'])*2==len(vs['vindices']['array'])
    assert all(0<=i<len(baseline['joints']) for i in vs['vindices']['array'][::2])
    ns=vs['normals']['array']
    assert all(abs(sum(n*n for n in ns[i:i+3])-1)<.001 for i in range(0,len(ns),3))
    for idx in vs['parts'].values():
        a=idx['array'];assert len(a)%9==0
        assert all(0<=n<vs[['positions','uvs','normals'][i%3]]['count'] for i,n in enumerate(a))
    (target/f'animmodels/armor/{o.name}.json').write_text(json.dumps(data),encoding='utf-8')
    report['parts'][o.name]={'vertices':len(o.data.vertices),'faces':len(o.data.polygons),'unweighted':0,
        'bones':[g.name for g in o.vertex_groups], 'triangles':sum(len(a['array'])//9 for a in vs['parts'].values())}

# Studio lights show actual texture and emissive eyes, not material-color proxies.
scene.render.engine='CYCLES'
scene.cycles.samples=24
scene.cycles.use_denoising=True
scene.world=bpy.data.worlds.new('Halloween studio')
scene.world.use_nodes=True
bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND')
bg.inputs['Color'].default_value=(.10,.10,.12,1)
bg.inputs['Strength'].default_value=.65
for name,loc,power,size in [('Key',(-3,-4,5),450,4),('Fill',(3,-2,3),260,3),('Rim',(1,3,4),500,3)]:
    d=bpy.data.lights.new('Halloween '+name,'AREA');d.energy=power;d.shape='DISK';d.size=size
    o=bpy.data.objects.new('Halloween '+name,d);scene.collection.objects.link(o);o.location=loc
    o.rotation_euler=(Vector((0,0,1.1))-o.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add(location=(2.8,-5.8,2.8))
cam=bpy.context.object;cam.name='Halloween preview camera'
cam.data.type='ORTHO';cam.data.ortho_scale=2.68
scene.camera=cam
scene.render.resolution_x=1000;scene.render.resolution_y=1100;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
rig.hide_set(True)
def render(name,loc):
    cam.location=loc
    cam.rotation_euler=(Vector((0,0,1.13))-cam.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath=str(OUT/name)
    bpy.ops.render.render(write_still=True)
render('halloween_front.png',(2.8,-5.8,2.8))
render('halloween_back.png',(-2.8,5.8,2.8))

# Bend validation is evaluated in Blender, then all pose transforms are reset.
rig.data.pose_position='POSE'
deps=bpy.context.evaluated_depsgraph_get()
def coords(o):return [v.co.copy() for v in o.evaluated_get(deps).data.vertices]
before={o.name:coords(o) for o in armor}
for name,angle in [('Head',.20),('Arm_R',-.40),('Hand_R',-.85),('Thigh_L',-.50),('Leg_L',.90)]:
    p=rig.pose.bones[name];p.rotation_mode='XYZ';p.rotation_euler.x=angle
bpy.context.view_layer.update()
movement={o.name:max((a-b).length for a,b in zip(coords(o),before[o.name])) for o in armor}
assert all(v>.001 for v in movement.values()),movement
report['pose_max_displacement']=movement
render('halloween_pose_test.png',(2.8,-5.8,2.8))
for p in rig.pose.bones:p.matrix_basis.identity()
rig.data.pose_position='REST';bpy.context.view_layer.update()
assert all(max((a-b).length for a,b in zip(coords(o),before[o.name]))<1e-5 for o in armor)
report['restored_rest_pose']=True
cam.location=(2.8,-5.8,2.8)
cam.rotation_euler=(Vector((0,0,1.13))-cam.location).to_track_quat('-Z','Y').to_euler()
scene.render.filepath=str(OUT/'halloween_front.png')
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.shading.type='MATERIAL'
            area.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.object.select_all(action='DESELECT')
for o in armor:o.select_set(True)
bpy.context.view_layer.objects.active=armor[1]
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'halloween_armor.blend'))
(OUT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
