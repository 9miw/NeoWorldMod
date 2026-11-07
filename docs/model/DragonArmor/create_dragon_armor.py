"""Run inside Blender with the existing Epic Fight humanoid rig active."""
import bpy
import math
import json
from pathlib import Path
from mathutils import Vector

OUT = Path('C:/Users/miw/Documents/NeoWorldMod/docs/model/DragonArmor')
source = bpy.context.scene
source_rig = next(o for o in source.objects if o.type == 'ARMATURE')
if bpy.context.object and bpy.context.object.mode != 'OBJECT':
    bpy.ops.object.mode_set(mode='OBJECT')
scene = bpy.data.scenes.new('Dragon Armor • prototype')
bpy.context.window.scene = scene
rig = source_rig.copy()
rig.data = source_rig.data.copy()
rig.name = 'Dragon_EpicFight_Rig'
scene.collection.objects.link(rig)
rig.animation_data_clear()
for p in rig.pose.bones:
    p.matrix_basis.identity()
rig.data.pose_position = 'REST'
rig.show_in_front = True

colors = [(0.035,0.045,0.065,1), (0.65,0.36,0.095,1), (0.32,0.012,0.025,1), (1,0.18,0.025,1), (0.012,0.016,0.023,1)]
atlas = bpy.data.images.new('Dragon_palette', width=80, height=16)
atlas.pixels = [v for y in range(16) for x in range(80) for v in colors[x//16]]
atlas.filepath_raw = str(OUT/'dragon_palette.png')
atlas.file_format = 'PNG'
atlas.save()
atlas.pack()
mats = []
for i, name in enumerate(['Obsidian steel','Antique gold','Crimson scales','Dragon fire','Dark underlayer']):
    m = bpy.data.materials.new(name)
    m.diffuse_color = colors[i]
    m.use_nodes = True
    n = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    n.inputs['Metallic'].default_value = 0.75 if i < 3 else 0.1
    n.inputs['Roughness'].default_value = 0.35
    tex = m.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = atlas
    tex.interpolation = 'Closest'
    m.node_tree.links.new(tex.outputs['Color'], n.inputs['Base Color'])
    mats.append(m)

parts = {k: [] for k in ['helmet','chestplate','leggings','boots']}
def finish(o, name, mat, bone, part):
    o.name = name
    bpy.context.view_layer.objects.active = o
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    o.data.materials.append(mats[mat])
    uv = o.data.uv_layers.active or o.data.uv_layers.new()
    for d in uv.data:
        d.uv = ((mat+0.5)/5, 0.5)
    o.vertex_groups.new(name=bone).add(list(range(len(o.data.vertices))),1,'REPLACE')
    parts[part].append(o)
    return o

def box(name, loc, size, mat, bone, part, bevel=0.012):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    o = bpy.context.object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = o.modifiers.new('Forged edges','BEVEL')
        mod.width = bevel
        mod.segments = 1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(o,name,mat,bone,part)

def spike(name, a, b, radius, mat, bone, part):
    a,b = Vector(a),Vector(b)
    bpy.ops.mesh.primitive_cone_add(vertices=6,radius1=radius,radius2=0,depth=(b-a).length,location=(a+b)/2)
    o = bpy.context.object
    o.rotation_euler = (b-a).to_track_quat('Z','Y').to_euler()
    return finish(o,name,mat,bone,part)

def plate(name,x,y,z,w,h,mat,bone,part):
    # Shield-shaped scale with a raised central ridge, front toward -Y.
    verts=[(x-w/2,y,z+h/2),(x+w/2,y,z+h/2),(x+w/2,y,z),(x,y,z-h/2),(x-w/2,y,z),(x,y-0.023,z+h*0.1)]
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(verts,[],[(0,1,5),(1,2,5),(2,3,5),(3,4,5),(4,0,5),(4,3,2,1,0)])
    mesh.update()
    o=bpy.data.objects.new(name,mesh)
    scene.collection.objects.link(o)
    return finish(o,name,mat,bone,part)

# Head: open visor, crown, swept horns, snout and narrow fire eyes.
box('Crown',(0,0,1.995),(.57,.55,.12),0,'Head','helmet')
box('Nape',(0,.245,1.77),(.56,.08,.39),0,'Head','helmet')
for s in [-1,1]:
    box('Temple',(s*.265,0,1.79),(.085,.51,.36),0,'Head','helmet')
    plate('Gold cheek',s*.20,-.283,1.67,.16,.24,1,'Head','helmet')
    box('Brow',(s*.14,-.285,1.91),(.27,.075,.065),1,'Head','helmet')
    box('Fire visor',(s*.13,-.291,1.855),(.19,.025,.035),3,'Head','helmet',.003)
    spike('Horn root',(s*.23,.035,2.02),(s*.37,.09,2.24),.085,1,'Head','helmet')
    spike('Swept horn',(s*.37,.09,2.24),(s*.29,.23,2.40),.045,1,'Head','helmet')
plate('Dragon face',0,-.31,1.75,.19,.30,0,'Head','helmet')
for z in [2.04,2.12]:
    plate('Crown crimson',0,-.18,z,.12,.14,2,'Head','helmet')

# Chest: articulated torso and breastplate; avoid bridging the waist joint.
box('Breastplate',(0,0,1.32),(.54,.32,.34),0,'Chest','chestplate')
box('Backplate',(0,.15,1.30),(.53,.085,.37),0,'Chest','chestplate')
box('Abdomen',(0,0,1.00),(.47,.30,.23),4,'Torso','chestplate')
for row in range(3):
    for col in range(5):
        plate('Crimson lamella', (col-2)*.092, -.173,1.13-row*.085,.091,.12,2,'Torso','chestplate')
for s in [-1,1]:
    plate('Breast gold',s*.145,-.185,1.35,.25,.29,1,'Chest','chestplate')
    plate('Breast obsidian',s*.145,-.21,1.36,.20,.22,0,'Chest','chestplate')
    # A dragon head on each pauldron: skull, muzzle, eyes and swept horn.
    side='R' if s>0 else 'L'
    arm='Arm_'+side
    hand='Hand_'+side
    box('Dragon shoulder skull',(s*.42,0,1.48),(.32,.36,.22),0,arm,'chestplate')
    box('Dragon shoulder muzzle',(s*.43,-.215,1.44),(.23,.20,.12),1,arm,'chestplate')
    for dx in [-.085,.085]:
        box('Shoulder eye',(s*.42+dx,-.187,1.52),(.055,.018,.028),3,arm,'chestplate',.002)
    spike('Shoulder horn',(s*.45,.065,1.57),(s*.61,.18,1.78),.06,1,arm,'chestplate')
    for j in range(2):
        plate('Upper arm scale',s*.385,-.17,1.32-j*.105,.25,.14,2,arm,'chestplate')
    box('Bracer',(s*.375,-.015,.94),(.27,.30,.23),0,hand,'chestplate')
    plate('Bracer gold',s*.375,-.18,.95,.22,.23,1,hand,'chestplate')
    plate('Bracer scale',s*.375,-.202,.96,.15,.17,2,hand,'chestplate')
    spike('Wrist talon',(s*.49,.035,1.01),(s*.58,.08,1.17),.04,1,hand,'chestplate')
# Raised dragon crest: central snout, eyes, horns and stylized wings.
plate('Dragon crest gold',0,-.245,1.35,.18,.22,1,'Chest','chestplate')
plate('Dragon crest crimson',0,-.275,1.35,.105,.16,2,'Chest','chestplate')
for s in [-1,1]:
    spike('Crest horn',(s*.04,-.265,1.43),(s*.09,-.27,1.50),.024,1,'Chest','chestplate')
    for j in range(3):
        plate('Crest wing',s*(.10+j*.045),-.24,1.40-j*.028,.07,.12-j*.014,1,'Chest','chestplate')

box('Belt',(0,0,.825),(.52,.34,.085),1,'Torso','leggings')
plate('Belt jewel',0,-.195,.825,.11,.09,3,'Torso','leggings')
for s in [-1,1]:
    side='R' if s>0 else 'L'
    thigh='Thigh_'+side
    leg='Leg_'+side
    box('Thigh underplate',(s*.132,0,.62),(.235,.28,.29),0,thigh,'leggings')
    for row in range(3):
        plate('Tasset gold',s*.14,-.18,.76-row*.09,.245,.14,1,thigh,'leggings')
        plate('Tasset crimson',s*.14,-.202,.765-row*.09,.205,.105,2,thigh,'leggings')
    plate('Knee shield',s*.13,-.183,.385,.24,.19,1,leg,'leggings')
    plate('Knee obsidian',s*.13,-.207,.39,.17,.13,0,leg,'leggings')
    box('Greave',(s*.13,0,.23),(.24,.29,.24),0,leg,'boots')
    plate('Greave gold',s*.13,-.17,.23,.21,.25,1,leg,'boots')
    plate('Greave crimson',s*.13,-.194,.24,.14,.19,2,leg,'boots')
    box('Sabatons',(s*.13,-.055,.065),(.25,.40,.12),0,leg,'boots')
    for dx in [-.065,0,.065]:
        spike('Toe claw',(s*.13+dx,-.20,.08),(s*.13+dx,-.30,.035),.026,1,leg,'boots')

armor=[]
for part,objects in parts.items():
    bpy.ops.object.select_all(action='DESELECT')
    for o in objects: o.select_set(True)
    bpy.context.view_layer.objects.active=objects[0]
    bpy.ops.object.join()
    o=bpy.context.object
    o.name='dragon_'+part
    mod=o.modifiers.new('Epic Fight skin','ARMATURE')
    mod.object=rig
    armor.append(o)

report={'source_file':bpy.data.filepath,'parts':{},'status':'Blender prototype; not exported or tested in Minecraft'}
for o in armor:
    missing=[v.index for v in o.data.vertices if not any(g.weight>0 and o.vertex_groups[g.group].name in rig.data.bones for g in v.groups)]
    assert not missing,(o.name,missing)
    assert all(math.isfinite(c) for v in o.data.vertices for c in v.co)
    report['parts'][o.name]={'vertices':len(o.data.vertices),'faces':len(o.data.polygons),'unweighted':len(missing),'bones':[g.name for g in o.vertex_groups]}
# Prove lower limb and arm plates move under the copied rig, then restore rest.
rig.data.pose_position='POSE'
deps=bpy.context.evaluated_depsgraph_get()
def coords(o):
    e=o.evaluated_get(deps)
    return [v.co.copy() for v in e.data.vertices]
before={o.name:coords(o) for o in armor}
for name in ['Head','Hand_R','Leg_L']:
    p=rig.pose.bones[name]
    p.rotation_mode='XYZ'
    p.rotation_euler.x=.35
bpy.context.view_layer.update()
movement={o.name:max((v-u).length for v,u in zip(coords(o),before[o.name])) for o in armor}
assert all(movement[o.name]>0.001 for o in armor),movement
report['pose_test_max_displacement']=movement
for p in rig.pose.bones: p.matrix_basis.identity()
rig.data.pose_position='REST'
bpy.context.view_layer.update()
report['restored_rest_pose']=True
(OUT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

# Presentation camera and neutral studio, kept separate from armor geometry.
bpy.ops.object.camera_add(location=(3,-6,2.65))
cam=bpy.context.object
cam.name='Preview camera'
cam.rotation_euler=(Vector((0,0,1.17))-cam.location).to_track_quat('-Z','Y').to_euler()
cam.data.type='ORTHO'
cam.data.ortho_scale=2.85
scene.camera=cam
scene.render.engine='BLENDER_WORKBENCH'
scene.display.shading.light='STUDIO'
scene.display.shading.color_type='MATERIAL'
scene.display.shading.show_shadows=True
scene.display.shading.show_cavity=True
scene.display.shading.cavity_type='BOTH'
scene.display.shading.background_type='WORLD'
scene.world=bpy.data.worlds.new('Dragon studio')
scene.world.color=(.055,.055,.065)
scene.render.resolution_x=1000
scene.render.resolution_y=1100
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.filepath=str(OUT/'dragon_armor_preview.png')
rig.hide_set(True)
bpy.ops.object.select_all(action='DESELECT')
for o in armor:o.select_set(True)
bpy.context.view_layer.objects.active=armor[1]
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.shading.color_type='MATERIAL'
            area.spaces.active.region_3d.view_perspective='CAMERA'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'dragon_armor.blend'))
bpy.ops.render.render(write_still=True)
print(json.dumps(report,indent=2))
