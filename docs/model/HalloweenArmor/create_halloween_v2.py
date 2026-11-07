"""Re-author silhouette and pumpkin forms from the approved reference mockup.

Reuses only the verified rig/export/validation helpers, not the rejected geometry.
"""
from pathlib import Path
ROOT=Path('C:/Users/miw/Documents/NeoWorldMod/docs/model/HalloweenArmor')
base=(ROOT/'create_halloween_armor.py').read_text(encoding='utf-8')
for old,new in [('halloween_armor.blend','halloween_armor_v2.blend'),
                ('halloween_atlas.png','halloween_atlas_v2.png'),
                ('halloween_front.png','halloween_v2_front.png'),
                ('halloween_back.png','halloween_v2_back.png'),
                ('halloween_pose_test.png','halloween_v2_pose.png'),
                ('validation.json','validation_v2.json'),
                ("OUT/'export/assets/neoworld'","OUT/'export_v2/assets/neoworld'"),
                ('textures/models/armor/halloween.png','textures/models/armor/halloween_v2.png'),
                ('Halloween • Pumpkin Knight','Halloween • Mockup revision')]:
    base=base.replace(old,new)
# Deliberate material texture: broad shade planes and sparse pixels, no checker noise.
base=base.replace("(.19,.17,.22),(.82,.31,.055),(.27,.095,.30)","(.075,.06,.085),(.72,.205,.018),(.20,.045,.22)")
base=base.replace("(.40,.36,.43)","(.15,.12,.17)")
base=base.replace("rng.choice([.82,.94,1,1.10,1.18])","rng.choice([.88,.95,1,1,1.08])")
base=base.replace("if edge<1:shade*=.58","if edge<1:shade*=.83")
base=base.replace("elif v>28 or u<3:shade*=1.25","elif v>28 or u<3:shade*=1.10")
base=base.replace("if mat==2 and (u%8==0 or v%12==0):shade*=.83","if mat==2 and u in [7,22]:shade*=.90")
base=base.replace("if mat==1 and u%8 in [0,1]:shade*=.83","if mat==1 and u in [6,14,23]:shade*=.84")
base=base.replace("bsdf.inputs['Emission Strength'].default_value=2","bsdf.inputs['Emission Strength'].default_value=.65")
base=base.replace("(1,.64,.12)","(1,.35,.025)")
start=base.index('# Helmet: hollow')
end=base.index('\narmor=[]',start)
model=r'''
# Direct data construction avoids thousands of full-scene operator updates.
def finish(o,name,mat,bone,part):
    from mathutils import Matrix
    o.name='HW2 / '+name
    o.data.transform(o.matrix_world)
    o.matrix_world=Matrix.Identity(4)
    o.data.materials.append(mats[mat])
    uv=o.data.uv_layers.active or o.data.uv_layers.new()
    for f in o.data.polygons:
        axes=sorted(range(3),key=lambda a:abs(f.normal[a]))[:2]
        points=[o.data.vertices[o.data.loops[i].vertex_index].co for i in f.loop_indices]
        lows=[min(v[a] for v in points) for a in axes]
        spans=[max(v[a] for v in points)-lo for a,lo in zip(axes,lows)]
        extent=[max(2,min(30,round(s*64))) for s in spans]
        for li,v in zip(f.loop_indices,points):
            q=[(v[a]-lo)/max(s,1e-8) for a,lo,s in zip(axes,lows,spans)]
            uv.data[li].uv=((mat*32+(32-extent[0])/2+q[0]*extent[0])/256,
                            (16-extent[1]/2+q[1]*extent[1])/256)
    o.vertex_groups.new(name=bone).add(list(range(len(o.data.vertices))),1,'REPLACE')
    pieces[part].append(o)
    return o

def box(name,pos,size,mat,bone,part,bevel=0,rot=0):
    from mathutils import Matrix
    verts=[(sx*size[0]/2,sy*size[1]/2,sz*size[2]/2)
           for sx,sy,sz in [(-1,-1,-1),(-1,-1,1),(-1,1,-1),(-1,1,1),
                            (1,-1,-1),(1,-1,1),(1,1,-1),(1,1,1)]]
    faces=[(0,4,5,1),(2,3,7,6),(0,1,3,2),(4,6,7,5),(1,5,7,3),(0,2,6,4)]
    mesh=bpy.data.meshes.new(name)
    mesh.from_pydata(verts,[],faces);mesh.update()
    if bevel:
        bm=bmesh.new();bm.from_mesh(mesh)
        bmesh.ops.bevel(bm,geom=list(bm.edges),offset=bevel,segments=1,affect='EDGES')
        bm.to_mesh(mesh);bm.free();mesh.update()
    mesh.transform(Matrix.Translation(Vector(pos))@Matrix.Rotation(rot,4,'Y'))
    o=bpy.data.objects.new(name,mesh);scene.collection.objects.link(o)
    return finish(o,name,mat,bone,part)

def inside(x,y,poly):
    odd=False
    for (a,b),(c,d) in zip(poly,poly[1:]+poly[:1]):
        if (b>y)!=(d>y) and x<(c-a)*(y-b)/(d-b)+a:odd=not odd
    return odd

def shell(name,loc,size,mat,bone,part,bevel):
    o=box(name,loc,size,mat,bone,part,bevel)
    bm=bmesh.new();bm.from_mesh(o.data)
    # Remove the large -Y face: cutouts look into a hollow shell, not a painted box.
    bmesh.ops.delete(bm,geom=[f for f in bm.faces if f.normal.y<-.95],context='FACES')
    bm.to_mesh(o.data);bm.free()
    return o

def lantern(name,x,y,z,w,h,depth,bone,part):
    # Faceted pumpkin volume with a sculpted, convex, voxel-carved front.
    shell(name+' faceted pumpkin',(x,y,z),(w,depth,h),1,bone,part,min(w,h)*.12)
    front=y-depth/2
    box(name+' dark interior',(x,front+.032,z),(w*.89,.025,h*.85),6,bone,part)
    eye=[(-.85,.60),(-.14,.20),(-.62,.06)]
    mirror=[(-a,b) for a,b in eye]
    mouth=[(-.82,-.10),(-.55,-.28),(-.27,-.11),(0,-.30),(.27,-.11),
           (.55,-.28),(.82,-.10),(.68,-.48),(.45,-.37),(.17,-.61),
           (-.08,-.43),(-.40,-.57),(-.68,-.38)]
    # Small voxels trace real holes; their depth follows the pumpkin curvature.
    nx,ny=20,18
    dx,dz=w/nx,h/ny
    for i in range(nx):
        for j in range(ny):
            u=(i+.5)/nx*2-1;v=(j+.5)/ny*2-1
            if abs(u)**4+abs(v)**4>.95:continue
            iseye=inside(u,v,eye) or inside(u,v,mirror)
            ismouth=inside(u,v,mouth)
            bulge=.09*(1-u*u)*(1-v*v)*(w/.55)
            yy=front-bulge
            if iseye:
                box(name+' inset amber eye',(x+u*w/2,front+.005,z+v*h/2),
                    (dx*1.01,.014,dz*1.01),5,bone,part)
                continue
            if ismouth:continue
            # Bright pumpkin lips surround the open black mouth.
            edge=any(inside(u+du,v+dv,mouth) for du,dv in [(2/nx,0),(-2/nx,0),(0,2/ny),(0,-2/ny)])
            material=5 if edge and v<.02 else 1
            box(name+' carved pumpkin skin',(x+u*w/2,yy,z+v*h/2),
                (dx*1.005,.026,dz*1.005),material,bone,part)
    # Actual pumpkin lobes wrap over the crown and down the rear side.
    for i in range(5):
        xx=x+(i-2)*w*.16
        box(name+' crown rib',(xx,y,z+h*.465),
            (w*.145,depth*.77,h*.12),1,bone,part,min(.01,w*.025))

def cuff(name,x,z,w,d,h,bone,part):
    box(name+' steel shell',(x,0,z),(w,d,h),0,bone,part,.012)
    for zz in [z-h*.44,z+h*.44]:
        box(name+' orange ring',(x,0,zz),(w+.018,d+.018,.026),1,bone,part)
    for s in [-1,1]:
        box(name+' face edge',(x+s*w*.44,-d/2-.011,z),(.023,.023,h*.83),1,bone,part)
    box(name+' raised panel',(x,-d/2-.013,z),(w*.72,.022,h*.66),7,bone,part)

def tabard(name,x,y,top,bottom,width,bone,part,piping=True):
    # A long, tapered cloth tongue with uneven shredded teeth.
    mid=(top+bottom)/2
    pts=[(x-width*.50,top),(x+width*.50,top),(x+width*.42,bottom+.055),
         (x+width*.23,bottom+.075),(x+width*.12,bottom+.013),
         (x-width*.02,bottom+.043),(x-width*.19,bottom),
         (x-width*.28,bottom+.05),(x-width*.46,bottom+.025)]
    panel(name,pts,y,.025,2,bone,part)
    if piping:
        for s in [-1,1]:
            strip(name+' tapered orange edging',(x+s*width*.46,y-.014,top-.025),
                (x+s*width*.36,y-.014,bottom+.045),.018,.016,1,bone,part)
    # Fold planes are restrained strips, not noisy checkerboard.
    box(name+' cloth fold',(x-width*.13,y-.015,mid+.025),(.025,.012,(top-bottom)*.65),2,bone,part)

# Larger faceted pumpkin head enclosed by an angular iron cage.
lantern('Helmet pumpkin',0,-.012,1.79,.565,.49,.53,'Head','helmet')
for s in [-1,1]:
    box('Helmet side iron cage',(s*.269,.04,1.79),(.065,.35,.40),0,'Head','helmet',.035)
    box('Helmet temple raised plate',(s*.304,.05,1.79),(.025,.22,.20),7,'Head','helmet',.009)
    strip('Swept iron brow',(s*.018,-.342,1.92),(s*.263,-.300,2.015),.062,.044,0,'Head','helmet')
    strip('Angular jaw cage',(s*.258,-.30,1.72),(s*.23,-.285,1.55),.035,.036,0,'Head','helmet')
    box('Crown iron side rail',(s*.255,.01,2.023),(.065,.50,.045),0,'Head','helmet')
box('Rear cage plate',(0,.249,1.79),(.51,.065,.43),0,'Head','helmet',.015)
box('Central forehead keystone',(0,-.353,1.98),(.065,.052,.22),7,'Head','helmet')
box('Lower pumpkin chin',(0,-.296,1.56),(.30,.047,.035),1,'Head','helmet')
box('Stem base',(.035,.02,2.09),(.09,.09,.11),3,'Head','helmet')
strip('Crooked stem segment',(.035,.02,2.12),(-.01,.02,2.23),.075,.075,3,'Head','helmet')
box('Hooked stalk',(-.075,.02,2.245),(.13,.075,.055),3,'Head','helmet')
for x in [-.14,.12]:
    box('Crown foliage',(x,-.02,2.065),(.075,.065,.027),4,'Head','helmet')
    box('Leaf sprout',(x-.025,-.015,2.10),(.027,.025,.05),4,'Head','helmet')

# Chest: full orange-bordered steel shield with a pointed lower edge.
box('Chest armored base',(0,.01,1.34),(.53,.31,.30),0,'Chest','chestplate',.018)
shield=[(-.25,1.48),(-.235,1.24),(-.15,1.16),(0,1.10),(.15,1.16),(.235,1.24),(.25,1.48),(0,1.41)]
panel('Breastplate thick pumpkin rim',shield,-.20,.045,1,'Chest','chestplate')
panel('Breastplate forged inset',[(x*.86,1.31+(z-1.31)*.86) for x,z in shield],-.238,.027,0,'Chest','chestplate')
panel('Breastplate central plane',[(-.10,1.385),(-.08,1.205),(0,1.17),(.08,1.205),(.10,1.385),(0,1.35)],-.25,.02,7,'Chest','chestplate')
for s in [-1,1]:
    strip('V collar orange border',(0,-.266,1.395),(s*.24,-.215,1.49),.043,.024,1,'Chest','chestplate')
    strip('Collar dark lip',(0,-.27,1.425),(s*.25,-.21,1.515),.026,.025,0,'Chest','chestplate')
box('Waist violet underlayer',(0,0,1.00),(.46,.29,.23),2,'Torso','chestplate')
for s in [-1,1]:
    box('Abdomen side plate',(s*.20,-.01,1.02),(.065,.31,.19),0,'Torso','chestplate')
    strip('Abdomen orange piping',(s*.20,-.18,1.09),(s*.17,-.18,.90),.025,.02,1,'Torso','chestplate')
box('Backplate',(0,.18,1.32),(.51,.065,.33),0,'Chest','chestplate',.014)
tabard('Rear upper violet banner',0,.224,1.46,1.16,.23,'Chest','chestplate',False)
for s in [-1,1]:
    strip('Back orange frame',(s*.23,.225,1.46),(s*.17,.225,1.15),.029,.025,1,'Chest','chestplate')

# Bulky pumpkin pauldrons follow sloping upper-arm plates, not flat badges.
from mathutils import Matrix
for s in [-1,1]:
    side='R' if s>0 else 'L';arm='Arm_'+side;hand='Hand_'+side
    first=len(pieces['chestplate'])
    lantern('Shoulder jack',s*.425,-.014,1.47,.335,.275,.345,arm,'chestplate')
    for t in [-1,1]:
        box('Shoulder iron vertical cage',(s*.425+t*.153,-.145,1.47),(.04,.12,.26),0,arm,'chestplate',.009)
    box('Pauldron crown iron',(s*.425,.0,1.608),(.24,.23,.033),0,arm,'chestplate')
    for j in range(3):
        box('Layered pauldron orange hem',(s*(.43+j*.018),.0,1.365-j*.035),(.34+j*.02,.37,.022),1,arm,'chestplate')
        if j<2:box('Layered pauldron dark lame',(s*(.43+j*.018),.0,1.347-j*.035),(.32+j*.02,.35,.018),0,arm,'chestplate')
    pivot=Vector((s*.405,0,1.48))
    M=Matrix.Translation(pivot)@Matrix.Rotation(s*.20,4,'Y')@Matrix.Translation(-pivot)
    for o in pieces['chestplate'][first:]:o.data.transform(M)
    tabard('Torn violet upper sleeve',s*.385,-.153,1.325,1.13,.245,arm,'chestplate',False)
    box('Sleeve rear',(s*.375,.12,1.23),(.24,.04,.19),2,arm,'chestplate')
    cuff('Framed bracer',s*.375,.945,.255,.29,.24,hand,'chestplate')
    vine(s*.30,-.24,1.35,.18,arm,'chestplate')
    vine(s*.225,-.24,1.42,.13,'Chest','chestplate')

# Long layered skirt. Cloth tongues attach separately to each thigh.
box('Dark belt',(0,0,.855),(.53,.35,.08),0,'Torso','leggings')
for s in [-1,1]:
    for x in [.115,.24]:box('Belt orange rivet',(s*x,-.188,.855),(.035,.02,.055),1,'Torso','leggings')
lantern('Small pumpkin clasp',0,-.19,.855,.12,.095,.07,'Torso','leggings')
for s in [-1,1]:
    side='R' if s>0 else 'L';thigh='Thigh_'+side;leg='Leg_'+side
    box('Dark thigh underarmor',(s*.132,.01,.63),(.235,.28,.28),0,thigh,'leggings')
    tabard('Long front violet tabard',s*.115,-.205,.82,.41,.225,thigh,'leggings')
    tabard('Long rear violet tabard',s*.115,.172,.82,.42,.225,thigh,'leggings',False)
    # Flared layered side panels frame the longer central cloth.
    for j in range(3):
        x=s*(.225+j*.014);top=.80-j*.085;bot=top-.15
        tabard('Flared side skirt panel',x,-.192,top,bot,.18,thigh,'leggings')
        box('Hip side steel backing',(s*.25,.065,(top+bot)/2),(.07,.25,.10),0,thigh,'leggings')
    vine(s*.19,-.232,.795,.20,thigh,'leggings')
    # Angular kneecap and pointed orange rim, with room at the bend.
    pts=[(s*.132-.115,.43),(s*.132+.115,.43),(s*.132+.085,.365),(s*.132,.335),(s*.132-.085,.365)]
    panel('Pointed kneecap bronze',pts,-.19,.04,1,leg,'leggings')
    panel('Pointed kneecap dark insert',[(s*.132+(x-s*.132)*.72,.39+(z-.39)*.65) for x,z in pts],-.218,.02,0,leg,'leggings')
    cuff('Tall shin armor',s*.132,.225,.235,.28,.245,leg,'boots')
    box('Boot dark foot',(s*.132,-.058,.065),(.25,.39,.12),0,leg,'boots',.008)
    box('Boot stepped toe',(s*.132,-.192,.10),(.22,.12,.07),7,leg,'boots')
    box('Toe orange edging',(s*.132,-.258,.055),(.25,.025,.045),1,leg,'boots')
    box('Boot sole',(s*.132,-.055,.015),(.26,.405,.024),6,leg,'boots')
'''
base=base[:start]+model+base[end:]
fast_export=r'''
def export_rigid_mesh(obj,bones,apply_modifiers=False):
    # This authored set has exactly one unit-weight influence per vertex and
    # flat-shaded faces, so triangulate in one pass without quadratic face matching.
    mesh=obj.data
    mesh.calc_loop_triangles()
    positions=[round(c,6) for v in mesh.vertices for c in v.co]
    normals=[];uvs=[];indices=[];nd={};ud={}
    for tri in mesh.loop_triangles:
        n=tuple(round(c,6) for c in mesh.polygons[tri.polygon_index].normal)
        if n not in nd:nd[n]=len(normals)//3;normals.extend(n)
        for vi,li in zip(tri.vertices,tri.loops):
            uv=mesh.uv_layers.active.data[li].uv
            key=(round(uv.x,6),round(1-uv.y,6))
            if key not in ud:ud[key]=len(uvs)//2;uvs.extend(key)
            indices.extend([vi,ud[key],nd[n]])
    joint_indices=[]
    for v in mesh.vertices:
        gs=[g for g in v.groups if g.weight>0]
        assert len(gs)==1 and abs(gs[0].weight-1)<1e-6
        joint_indices.extend([bones.index(obj.vertex_groups[gs[0].group].name),0])
    def arr(stride,values):return {'stride':stride,'count':len(values)//stride,'array':values}
    return {'positions':arr(3,positions),'uvs':arr(2,uvs),'normals':arr(3,normals),
            'vcounts':arr(1,[1]*len(mesh.vertices)),'weights':arr(1,[1.0]),
            'vindices':arr(1,joint_indices),'parts':{'noGroups':arr(3,indices)}}
exporter.export_mesh=export_rigid_mesh
'''
base=base.replace("jar=next((Path.home()",fast_export+"\njar=next((Path.home()")
base=base.replace("o.name='halloween_'+part","o.name='halloween_v2_'+part")
base=base.replace('halloween_armor_v2.blend','halloween_armor_refined.blend')
base=base.replace('halloween_atlas_v2.png','halloween_atlas_refined.png')
base=base.replace('halloween_v2_','halloween_refined_')
base=base.replace('validation_v2.json','validation_refined.json')
base=base.replace('export_v2/','export_refined/')
base=base.replace('halloween_v2.png','halloween_refined.png')
# Match the slightly elevated three-quarter angle of the approved image.
base=base.replace('(2.8,-5.8,2.8)','(3.4,-6.3,2.95)')
base=base.replace('scene.cycles.samples=24','scene.cycles.samples=32')
exec(compile(base,'halloween_v2_generated','exec'))
