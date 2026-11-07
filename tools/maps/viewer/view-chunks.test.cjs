const {test}=require('node:test');
const assert=require('node:assert/strict');

test('frustum rejects behind-camera boxes and keeps boxes crossing a view edge',async()=>{
  const {frustumPlanes,intersectsBox}=await import('./view-chunks.mjs');
  const near=1,far=100,f=1,projection=[f,0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0],planes=frustumPlanes(projection);
  assert.equal(intersectsBox(planes,[-1,-1,-10],[1,1,-8]),true);
  assert.equal(intersectsBox(planes,[-1,-1,1],[1,1,5]),false);
  assert.equal(intersectsBox(planes,[50,-1,-10],[60,1,-8]),false);
  assert.equal(intersectsBox(planes,[8,-1,-10],[12,1,-8]),true);
  assert.equal(intersectsBox(frustumPlanes(projection,1.18),[10.1,-1,-10],[11,1,-9.5]),true);
});

test('moving view prioritizes the new side and excludes the old side from retained chunks',async()=>{
  const {planChunks}=await import('./view-chunks.mjs');
  // A broad orthographic overview, used to isolate the moving render window from view direction.
  const vp=[1/1024,0,0,0,0,1/1024,0,0,0,0,1/1024,0,0,0,0,1];
  const options={size:512,floor:0,high:144,cut:143,mobile:true,vp};
  const left=planChunks({...options,focus:[96,30,256]}),right=planChunks({...options,focus:[416,30,256]});
  assert.ok(left.wanted.some(c=>c.key==='96,256'));assert.ok(!right.retain.has('96,256'));assert.ok(right.wanted.some(c=>c.key==='416,256'));
  assert.ok(left.wanted.length<32*32);assert.ok(right.wanted.length<32*32);
  assert.ok([...left.wanted,...right.wanted].every(c=>c.lod===0));
  assert.ok(left.wanted.every(c=>left.retain.has(c.key)));
  for(let i=1;i<right.wanted.length;i++)assert.ok(right.wanted[i].distance>=right.wanted[i-1].distance);
  assert.ok(planChunks({...options,mobile:false,focus:[256,30,256]}).wanted.length>planChunks({...options,focus:[256,30,256]}).wanted.length);
});
