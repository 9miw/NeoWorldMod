"""Reference-inspired revision; run inside Blender. Keeps original files intact."""
from pathlib import Path

ROOT = Path('C:/Users/miw/Documents/NeoWorldMod/docs/model/DragonArmor')
base = (ROOT/'create_dragon_armor.py').read_text(encoding='utf-8')
base = base.replace("Dragon Armor • prototype", "Dragon Armor • pixel knight")
base = base.replace("source_rig = next(o for o in source.objects if o.type == 'ARMATURE')", "source_rig = next(o for o in source.objects if o.type == 'ARMATURE')")
base = base.replace("dragon_palette.png", "dragon_pixel_atlas.png")
base = base.replace("dragon_armor.blend", "dragon_armor_pixel.blend")
base = base.replace("validation.json", "validation_pixel.json")
base = base.replace("dragon_armor_preview.png", "dragon_pixel_preview.png")
base = base.replace("bevel=0.012", "bevel=0")
base = base.replace("(0.035,0.045,0.065,1), (0.65,0.36,0.095,1), (0.32,0.012,0.025,1)", "(0.19,0.23,0.28,1), (0.85,0.59,0.23,1), (0.58,0.12,0.16,1)")
base = base.replace("scene.display.shading.color_type='MATERIAL'", "scene.display.shading.color_type='TEXTURE'")
base = base.replace("area.spaces.active.shading.color_type='MATERIAL'", "area.spaces.active.shading.color_type='TEXTURE'")
base = base.replace("scene.display.shading.show_cavity=True", "scene.display.shading.show_cavity=False")

texture_setup = r'''
# Five 32px tiles with discrete material highlights, dark borders and motifs.
# Atlas sampling is nearest-neighbour, no painted gradients or random noise.
bpy.data.images.remove(atlas)
atlas=bpy.data.images.new('Dragon knight pixel atlas',width=160,height=32)
pixels=[]
for y in range(32):
    for x in range(160):
        tile=x//32
        u=x%32
        col=colors[tile]
        edge=min(u,31-u,y,31-y)
        shade=0.58 if edge<2 else (1.38 if y>27 or u<4 else 1.0)
        if 5<u<27 and 5<y<27:
            # Deliberate 4px clusters, sparse broad material variation.
            shade *= [0.91,1.0,1.09,1.0][((u//4)+(y//4)*3)%4]
        if tile==2:
            # Staggered scale chevrons, each 8x8 pixels.
            xx=(u+(4 if (y//8)%2 else 0))%8
            yy=y%8
            if yy==abs(xx-4):shade*=1.55
            elif yy==abs(xx-4)+1:shade*=0.6
        if tile==1 and ((u in [5,26] and y in [5,26])):shade=1.8
        pixels.extend([min(1,max(0,c*shade)) for c in col[:3]]+[1])
atlas.pixels=pixels
atlas.filepath_raw=str(OUT/'dragon_pixel_atlas.png')
atlas.file_format='PNG'
atlas.save()
atlas.pack()
for m in mats:
    next(n for n in m.node_tree.nodes if n.type=='TEX_IMAGE').image=atlas

original_finish=finish
def finish(o,name,mat,bone,part):
    o=original_finish(o,name,mat,bone,part)
    uv=o.data.uv_layers.active
    for face in o.data.polygons:
        axes=sorted(range(3),key=lambda a:abs(face.normal[a]))[:2]
        points=[o.data.vertices[o.data.loops[i].vertex_index].co for i in face.loop_indices]
        lows=[min(p[a] for p in points) for a in axes]
        spans=[max(p[a] for p in points)-lo for a,lo in zip(axes,lows)]
        for i,p in zip(face.loop_indices,points):
            q=[(p[a]-lo)/max(span,1e-8) for a,lo,span in zip(axes,lows,spans)]
            uv.data[i].uv=((mat*32+0.5+q[0]*31)/160,(0.5+q[1]*31)/32)
    return o

def plate(name,x,y,z,w,h,mat,bone,part):
    # Two stepped rectangular tiers, no triangulated jewel-shaped scales.
    box(name+' rim',(x,y,z),(w,.032,h),mat,bone,part)
    return box(name+' inset',(x,y-.022,z+.006),(w*.76,.025,h*.69),2 if mat==2 else mat,bone,part)

def spike(name,a,b,radius,mat,bone,part):
    # Continuous square-section horn: four connected tapered segments.
    a,b=Vector(a),Vector(b)
    direction=(b-a).normalized()
    tangent=direction.cross(Vector((0,1,0))).normalized()
    bitangent=direction.cross(tangent).normalized()
    verts=[]
    for j in range(5):
        t=j/4
        center=a.lerp(b,t)
        if name=='Swept crown horn':
            center.x+=(1 if a.x>0 else -1)*math.sin(t*math.pi)*.09
        r=max(.002,radius*(1-t))
        for u,v in [(-1,-1),(1,-1),(1,1),(-1,1)]:
            verts.append(tuple(center+tangent*r*u+bitangent*r*v))
    faces=[(3,2,1,0)]
    for j in range(4):
        for k in range(4):faces.append((j*4+k,j*4+(k+1)%4,(j+1)*4+(k+1)%4,(j+1)*4+k))
    faces.append((16,17,18,19))
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(verts,[],faces)
    mesh.update()
    o=bpy.data.objects.new(name,mesh)
    scene.collection.objects.link(o)
    return finish(o,name,mat,bone,part)
'''
base = base.replace('# Head: open visor',texture_setup+'\n# Head: open visor')
# Single connected swept horn replaces the old separated cone segments.
base = base.replace("spike('Horn root',(s*.23,.035,2.02),(s*.37,.09,2.24),.085,1,'Head','helmet')", "spike('Swept crown horn',(s*.23,.035,2.02),(s*.37,.17,2.31),.075,1,'Head','helmet')")
base = base.replace("spike('Swept horn',(s*.37,.09,2.24),(s*.29,.23,2.40),.045,1,'Head','helmet')", "box('Crown side trim',(s*.27,0,1.99),(.06,.57,.055),1,'Head','helmet')")
base = base.replace("# Raised dragon crest:", """# Layered shoulder rims and lower lames, each attached to its own upper arm.
for s in [-1,1]:
    arm='Arm_R' if s>0 else 'Arm_L'
    for j in range(3):
        box('Pauldron gold rim',(s*(.41+j*.025),.01,1.52-j*.085),(.34+j*.025,.39,.055),1,arm,'chestplate')
        box('Pauldron crimson tier',(s*(.41+j*.025),.01,1.487-j*.085),(.31+j*.025,.36,.055),2,arm,'chestplate')
# Raised dragon crest:""")
base = base.replace("spike('Crest horn',(s*.04,-.265,1.43),(s*.09,-.27,1.50),.024,1,'Chest','chestplate')", "box('Crest horn',(s*.055,-.29,1.47),(.025,.025,.08),1,'Chest','chestplate')")
base = base.replace("report={'source_file'", "report={'reference':'User attached knight armor showcase image; original geometry and procedural pixel texture, no downloaded model','source_file'")
exec(compile(base,'dragon_pixel_generated','exec'))
