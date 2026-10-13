"""Prepare separate Epic Fight armor files without changing the originals."""
import importlib
import json
from pathlib import Path
import sys
import types
import zipfile

import bpy

FOLDER = Path(__file__).resolve().parent
ADDON = Path.home() / 'AppData/Roaming/Blender Foundation/Blender/5.2/scripts/addons/blender-json-addon-main'
package = types.ModuleType('armor_audit_exporter')
package.__path__ = [str(ADDON)]
sys.modules[package.__name__] = package
compat = importlib.import_module('armor_audit_exporter.compat')
epic_jar = next((Path.home() / '.gradle/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8175609').rglob('*.jar'))
with zipfile.ZipFile(epic_jar) as archive:
    baseline = json.loads(archive.read('assets/epicfight/animmodels/entity/biped.json'))['armature']

def transforms(nodes):
    result = {}
    for node in nodes:
        result[node['name']] = node['transform']
        result.update(transforms(node.get('children', [])))
    return result

expected = transforms(baseline['hierarchy'])
for source in sorted(FOLDER.glob('*.blend')):
    if source.stem.endswith('_fixed'):
        continue
    bpy.ops.wm.open_mainfile(filepath=str(source))
    if bpy.context.object is not None and bpy.context.object.mode != 'OBJECT':
        bpy.ops.object.mode_set(mode='OBJECT')
    rigs = [obj for obj in bpy.data.objects if obj.type == 'ARMATURE']
    assert len(rigs) == 1, f'{source.name}: expected one rig'
    rig = rigs[0]
    actual = transforms(json.loads(json.dumps(
        compat.export_armature(rig, False, 'MAT'), cls=compat.NoIndentEncoder))['hierarchy'])
    assert set(actual) == set(expected), f'{source.name}: joint names differ'
    error = max(abs(a - b) for name in expected for a, b in zip(expected[name], actual[name]))
    assert error < 1e-4, f'{source.name}: rest transforms differ ({error})'
    rig.data.pose_position = 'REST'
    armor_meshes = [obj for obj in bpy.data.objects if obj.type == 'MESH'
                   and any(mod.type == 'ARMATURE' and mod.object == rig for mod in obj.modifiers)]
    assert armor_meshes, f'{source.name}: no armor mesh bound to the rig'
    for mesh in armor_meshes:
        modifiers = [mod for mod in mesh.modifiers if mod.type == 'ARMATURE']
        valid = [mod for mod in modifiers if mod.object == rig]
        assert len(valid) == 1, f'{source.name}: ambiguous armature modifiers'
        before = [mesh.matrix_world @ vertex.co for vertex in mesh.data.vertices]
        weights_before = [[(group.group, group.weight) for group in vertex.groups]
                          for vertex in mesh.data.vertices]
        for modifier in modifiers:
            if modifier != valid[0]:
                mesh.modifiers.remove(modifier)
        bpy.ops.object.select_all(action='DESELECT')
        mesh.select_set(True)
        bpy.context.view_layer.objects.active = mesh
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
        bpy.context.view_layer.update()
        assert max((mesh.matrix_world @ vertex.co - point).length
                   for vertex, point in zip(mesh.data.vertices, before)) < 1e-6
        assert mesh.rotation_euler.to_matrix().is_identity
        assert all(abs(value - 1) < 1e-6 for value in mesh.scale)
        assert weights_before == [[(group.group, group.weight) for group in vertex.groups]
                                  for vertex in mesh.data.vertices]
        for vertex in mesh.data.vertices:
            assert any(group.weight > 0 and mesh.vertex_groups[group.group].name in expected
                       for group in vertex.groups), f'{source.name}: unweighted vertex {vertex.index}'
    # Keep the original reference objects; select the armor mesh for editing.
    bpy.ops.object.select_all(action='DESELECT')
    armor_meshes[0].select_set(True)
    bpy.context.view_layer.objects.active = armor_meshes[0]
    destination = source.with_name(source.stem + '_fixed.blend')
    # Existing fixed files are updated without producing a .blend1 backup.
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(destination))
    print(f'FIXED {source.name} -> {destination.name}: '
          f'{sum(len(mesh.data.vertices) for mesh in armor_meshes)} vertices, '
          f'rig error={error}, world geometry and weights preserved')
