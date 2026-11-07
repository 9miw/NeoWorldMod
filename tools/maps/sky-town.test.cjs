const {test}=require('node:test');
const assert=require('node:assert/strict');
const path=require('node:path');
const {buildTown}=require('./create_sky_town.cjs');
const {createLibrary}=require('./viewer/map-library.cjs');
test('saved sky town preserves every zone and voxel, with reachable services and a detached portal island',()=>{
  const expected=buildTown(),actual=createLibrary(path.resolve('archive/maps-before-single-world')).load('starter_sky_town_v1');
  assert.equal(expected.zones.length,8);assert.deepEqual(actual.spawn,expected.spawn);assert.equal(actual.floor,24);
  const cells=Buffer.from(actual.blocks,'base64'),lookup=actual.definitions.map(d=>expected.definitions.findIndex(e=>JSON.stringify(e)===JSON.stringify(d)));
  assert.ok(lookup.every(n=>n>=0));
  for(let y=actual.low;y<actual.high;y++)for(let z=0;z<256;z++)for(let x=0;x<256;x++){
    const a=lookup[cells.readUInt16LE(((y-actual.low)*256*256+z*256+x)*2)],e=y>=expected.LOW&&y<expected.HIGH?expected.get(x,y,z):0;
    if(a!==e)assert.fail('Export mismatch at '+[x,y,z]);
  }
  assert.equal(expected.get(195,96,120),0,'gap between islands must remain void');
  for(let x=169;x<=214;x++)for(let z=130;z<=134;z++){assert.ok(expected.get(x,96,z));assert.equal(expected.get(x,97,z),0);assert.equal(expected.get(x,98,z),0);}
  assert.ok(expected.reachableFloorCells>20000);assert.ok(actual.stats.blocks>100000);
});
