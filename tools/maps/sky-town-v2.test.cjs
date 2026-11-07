const {test}=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {buildLargeTown,navigation}=require('./create_sky_town_v2.cjs');
const {createLibrary}=require('./viewer/map-library.cjs');
test('expanded town exports real larger footprints and usable interiors, including castle upstairs',()=>{
  const t=buildLargeTown(),m=createLibrary(path.resolve('archive/maps-before-single-world')).load('starter_sky_town_v2'),cells=Buffer.from(m.blocks,'base64');
  assert.equal(t.S,416);assert.deepEqual(m.spawn,[161,97,312]);assert.equal(t.services.length,5);assert.equal(t.checkedTargets.length,19);
  for(const s of t.services){assert.ok(s.usableFloorArea>=300);const [x,y,z]=s.entrance;assert.equal(t.get(x,y,z),0);assert.equal(t.get(x,y+1,z),0);}
  assert.equal(t.services[0].upperFloorY,123);
  const lookup=m.definitions.map(a=>t.definitions.findIndex(b=>JSON.stringify(a)===JSON.stringify(b)));assert.ok(lookup.every(i=>i>=0));
  for(let y=m.low;y<m.high;y++)for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++){
    const a=lookup[cells.readUInt16LE(((y-m.low)*t.S*t.S+z*t.S+x)*2)],expected=t.get(x,y,z);if(a!==expected)assert.fail('Export mismatch '+[x,y,z]);
  }
  // Obstruct a real room destination to prove accessibility checks reject unusable interiors.
  const target=t.checkedTargets.find(c=>c.name==='npc-shop-2');t.put(...target.point,'stone');assert.throws(()=>navigation(t,[target]),/Unreachable usable area/);
});
