const {test}=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {buildReferenceTown}=require('./create_sky_town_v3.cjs'),{createLibrary}=require('./viewer/map-library.cjs');
test('reference revision exports actual terraces and preserves all interior access points',()=>{
  const t=buildReferenceTown(),m=createLibrary(path.resolve('archive/maps-before-single-world')).load('starter_sky_town_v3'),cells=Buffer.from(m.blocks,'base64');
  assert.equal(t.checkedTargets.length,19);assert.equal(t.services[0].upperFloorY,141);assert.deepEqual(t.spawn,m.spawn);
  const floors=new Set(t.services.map(s=>s.entrance[1]));assert.ok(floors.size>=4,'services must stand on different terraces');
  const lookup=m.definitions.map(d=>t.definitions.findIndex(e=>JSON.stringify(d)===JSON.stringify(e)));assert.ok(lookup.every(i=>i>=0));
  for(let y=m.low;y<m.high;y++)for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++)if(lookup[cells.readUInt16LE(((y-m.low)*t.S*t.S+z*t.S+x)*2)]!==t.get(x,y,z))assert.fail('Voxel mismatch '+[x,y,z]);
  for(const {point:[x,y,z]}of t.checkedTargets){assert.equal(t.get(x,y,z),0);assert.equal(t.get(x,y+1,z),0);assert.ok(t.get(x,y-1,z));}
});
