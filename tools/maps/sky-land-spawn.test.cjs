const {test}=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {buildLand}=require('./create_sky_land.cjs'),{buildSpawnLand}=require('./create_sky_land_spawn.cjs'),{createLibrary}=require('./viewer/map-library.cjs');
test('spawn has clear access and changes only its declared zone on the terrain study',()=>{
  const fixture=require('node:fs').existsSync('archive/map-edit-backups/before-terrain-smooth/starter_sky_land_spawn_v1')?'archive/map-edit-backups/before-terrain-smooth':'maps';
  const base=buildLand(),t=buildSpawnLand(),m=createLibrary(path.resolve(fixture)).load('starter_sky_land_spawn_v1'),cells=Buffer.from(m.blocks,'base64');
  assert.deepEqual(t.spawn,[160,97,310]);assert.equal(t.spawnZone.exitWidth,13);assert.ok(t.spawnZone.reachableCells>1000);
  assert.equal(t.get(...t.spawn),0);assert.equal(t.get(160,98,310),0);assert.ok(t.get(160,96,310));
  const lookup=m.definitions.map(d=>t.definitions.findIndex(e=>JSON.stringify(e)===JSON.stringify(d)));assert.ok(lookup.every(i=>i>=0));
  for(let y=t.LOW;y<t.HIGH;y++)for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++){
    if(x<134||x>186||z<281||z>333||y<93)if(t.get(x,y,z)!==base.get(x,y,z))assert.fail('Terrain outside zone changed '+[x,y,z]);
    const actual=y>=m.low&&y<m.high?lookup[cells.readUInt16LE(((y-m.low)*t.S*t.S+z*t.S+x)*2)]:0;
    if(actual!==t.get(x,y,z))assert.fail('Export mismatch '+[x,y,z]);
  }
});
