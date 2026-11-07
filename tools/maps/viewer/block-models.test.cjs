const {test}=require('node:test');
const assert=require('node:assert/strict');
const {GameAssets}=require('./game-assets.cjs');
const {makeBlockModels,matches}=require('./block-models.cjs');
const assets=new GameAssets(),models=makeBlockModels(assets);
const definition=(name,Properties={})=>({Name:'minecraft:'+name,Properties});
const bounds=state=>{const points=state.faces.flatMap(f=>f.positions);return [0,1,2].map(i=>[Math.min(...points.map(p=>p[i])),Math.max(...points.map(p=>p[i]))]);};

test('real models resolve inheritance, slab halves, connected fences and open doors',()=>{
  const r=models.compile([
    definition('stone'),definition('oak_slab',{type:'bottom'}),definition('oak_slab',{type:'top'}),definition('oak_slab',{type:'double'}),
    definition('oak_fence',{north:'false',east:'false',south:'false',west:'false'}),definition('oak_fence',{north:'true',east:'false',south:'false',west:'false'}),
    definition('oak_door',{facing:'east',half:'lower',hinge:'left',open:'false'}),definition('oak_door',{facing:'east',half:'lower',hinge:'left',open:'true'}),
    definition('oak_stairs',{facing:'east',half:'bottom',shape:'straight'}),definition('oak_stairs',{facing:'north',half:'bottom',shape:'straight'})
  ]);
  assert.deepEqual(r.fallbacks,[]);assert.deepEqual(r.missingTextures,[]);
  assert.deepEqual(bounds(r.states[1])[1],[0,.5]);assert.deepEqual(bounds(r.states[2])[1],[.5,1]);assert.equal(r.states[3].solid,true);assert.equal(r.states[1].solid,false);
  assert.ok(r.states[5].faces.length>r.states[4].faces.length);assert.equal(bounds(r.states[4])[2][0],.375);assert.equal(bounds(r.states[5])[2][0],0);
  assert.notDeepEqual(bounds(r.states[6]),bounds(r.states[7]));assert.ok(r.states[8].faces.some(f=>f.positions.every(p=>p[0]===.5)));assert.ok(r.states[9].faces.some(f=>f.positions.every(p=>Math.abs(p[2]-.5)<1e-6)));
  assert.ok(r.textures.includes('minecraft:block/oak_planks'));
  for(const state of r.states)for(const face of state.faces){assert.ok(face.positions.flat().every(Number.isFinite));assert.ok(face.uv.flat().every(Number.isFinite));assert.ok(Math.abs(Math.hypot(...face.normal)-1)<1e-6);}
});

test('transparent glass, foliage tint and resource paths use installed game assets',()=>{
  const r=models.compile([definition('glass'),definition('oak_leaves'),definition('grass_block',{snowy:'false'}),definition('chest',{facing:'north',type:'single'})]);
  assert.equal(r.states[0].transparent,true);assert.equal(r.states[0].solid,false);assert.ok(r.states[1].faces.some(f=>f.tint[1]>f.tint[0]));assert.ok(r.states[2].faces.some(f=>f.tint.every(n=>n===1)));
  assert.deepEqual(r.fallbacks,['minecraft:chest']);assert.deepEqual(r.missingTextures,[]);
  const png=assets.texture('minecraft:block/stone');assert.equal(png.subarray(1,4).toString(),'PNG');assert.equal(assets.texture('minecraft:../../version'),null);assert.equal(assets.texture('external:block/stone'),null);
  assert.equal(matches({OR:[{north:'true'},{east:'true'}]},{east:'true'}),true);assert.equal(matches({AND:[{north:'true'},{east:'true'}]},{east:'true'}),false);
});

test('chunk meshing culls cube interiors and emits compact typed vertices',async()=>{
  const {buildChunk}=await import('./textured-mesh.mjs'),r=models.compile([definition('air'),definition('stone')]);
  const m={size:2,low:0,high:1,floor:0},cells=new Uint16Array([1,1,0,0]),chunk=buildChunk(m,cells,r.states,0,0,0);
  assert.equal(chunk.solid.byteLength,10*6*24);assert.equal(chunk.transparent.length,0);
  const view=new DataView(chunk.solid.buffer);for(let offset=0;offset<chunk.solid.byteLength;offset+=24){for(let i=0;i<3;i++)assert.ok(Number.isFinite(view.getFloat32(offset+i*4,true)));assert.ok(view.getUint16(offset+16,true)<r.textures.length);}
  assert.equal(buildChunk(m,cells,r.states,0,0,-1).solid.length,0);
});

test('voxel decoding crosses base64 chunk boundaries',async()=>{
  const {decodeCells}=await import('./textured-mesh.mjs');
  const input=new Uint16Array(70000);for(let i=0;i<input.length;i++)input[i]=(i*31)%65536;
  assert.deepEqual(decodeCells(Buffer.from(input.buffer).toString('base64')),input);assert.throws(()=>decodeCells('AA=='),/byte count/);
});
