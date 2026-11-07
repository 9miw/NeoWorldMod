"""Run with Blender 5.2 in background to prepare and export the test chestplate."""
import importlib
import json
from pathlib import Path
import sys
import types
import zipfile

import bpy

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/epicfight tool/armor_chest2.blend'
FIXED = SOURCE.with_name('armor_chest2_fixed.blend')
EXPORT = SOURCE.with_name('duelist_armor_chest_fixed.json')
TARGET = ROOT / 'src/main/resources/assets/neoworld/animmodels/armor/duelist_armor_chest.json'
ADDON = Path.home() / 'AppData/Roaming/Blender Foundation/Blender/5.2/scripts/addons/blender-json-addon-main'
EPIC_JAR = next((Path.home() / '.gradle/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8175609').rglob('*.jar'))

bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
mesh = bpy.data.objects['cube']
rig = bpy.data.objects['Armature']
world_before = [mesh.matrix_world @ vertex.co for vertex in mesh.data.vertices]
armature_modifiers = [modifier for modifier in mesh.modifiers if modifier.type == 'ARMATURE']
valid_modifiers = [modifier for modifier in armature_modifiers if modifier.object == rig]
assert len(valid_modifiers) == 1
for modifier in armature_modifiers:
    if modifier == valid_modifiers[0]:
        continue
    mesh.modifiers.remove(modifier)
rig.data.pose_position = 'REST'
bpy.ops.object.select_all(action='DESELECT')
mesh.select_set(True)
bpy.context.view_layer.objects.active = mesh
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
bpy.context.view_layer.update()
assert max((mesh.matrix_world @ vertex.co - before).length
           for vertex, before in zip(mesh.data.vertices, world_before)) < 1e-6
assert mesh.rotation_euler.to_matrix().is_identity
assert len([modifier for modifier in mesh.modifiers if modifier.type == 'ARMATURE']) == 1

package = types.ModuleType('chest_exporter')
package.__path__ = [str(ADDON)]
sys.modules[package.__name__] = package
compat = importlib.import_module('chest_exporter.compat')
exporter = importlib.import_module('chest_exporter.export_mc_json')
with zipfile.ZipFile(EPIC_JAR) as archive:
    baseline = json.loads(archive.read('assets/epicfight/animmodels/entity/biped.json'))['armature']
bone_names = baseline['joints']
assert set(bone_names) == {bone.name for bone in rig.data.bones if bone.use_deform}
rig_export = json.loads(json.dumps(compat.export_armature(rig, False, 'MAT'), cls=compat.NoIndentEncoder))
def transforms(nodes):
    result = {}
    for node in nodes:
        result[node['name']] = node['transform']
        result.update(transforms(node.get('children', [])))
    return result
expected = transforms(baseline['hierarchy'])
actual = transforms(rig_export['hierarchy'])
matrix_error = max(abs(a - b) for name in expected for a, b in zip(expected[name], actual[name]))
assert matrix_error < 1e-4, f'Rig rest transforms differ from Epic Fight: {matrix_error}'
for vertex in mesh.data.vertices:
    assert any(group.weight > 0 and mesh.vertex_groups[group.group].name in bone_names
               for group in vertex.groups), f'Unweighted vertex {vertex.index}'
vertices = exporter.export_mesh(mesh, bone_names, apply_modifiers=False)
export_text = json.dumps({'vertices': vertices}, cls=compat.NoIndentEncoder, indent=4) + '\n'
data = json.loads(export_text)
positions = data['vertices']['positions']['array']
assert data['vertices']['positions']['count'] == len(mesh.data.vertices)
assert max(abs(a - round(b, 6)) for vertex, offset in zip(mesh.data.vertices, range(0, len(positions), 3))
           for a, b in zip(positions[offset:offset + 3], vertex.co)) < 1e-6
texture_properties = json.loads(TARGET.read_text(encoding='utf-8'))['render_properties']
data['render_properties'] = texture_properties
bpy.ops.wm.save_as_mainfile(filepath=str(FIXED))
EXPORT.write_text(export_text, encoding='utf-8')
TARGET.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
print('VERIFIED: world geometry preserved, identity mesh rotation, single armature modifier, REST pose,')
print(f'Epic Fight rest transforms match (max error {matrix_error}), all vertices weighted, canonical bone indices.')
print(f'EXPORTED: {len(mesh.data.vertices)} vertices; {FIXED}; {EXPORT}; {TARGET}')
