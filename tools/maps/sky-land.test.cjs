const {test}=require('node:test'),assert=require('node:assert/strict'),path=require('node:path');
const {buildLand}=require('./create_sky_land.cjs'),{createLibrary}=require('./viewer/map-library.cjs');
test('terrain-only island has the agreed real footprint, connected ground and exact exported voxels',()=>{
  const t=buildLand(),m=createLibrary(path.resolve('archive/maps-before-single-world')).load('starter_sky_land_v1'),cells=Buffer.from(m.blocks,'base64'),xs=[],zs=[];
  const allowed=new Set(['air','grass_block','dirt','stone','andesite','mossy_cobblestone','cobblestone']);
  for(const d of t.definitions)assert.ok(allowed.has(d.Name.slice(10)));assert.equal(t.tiles.length,0);assert.equal(t.rooms.length,0);
  for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++)if(t.heights[z*t.S+x]>=0){xs.push(x);zs.push(z);}
  assert.deepEqual([Math.min(...xs),Math.max(...xs),Math.min(...zs),Math.max(...zs)],[24,303,24,343]);
  assert.equal(t.terrainStats.maxSurfaceY-t.terrainStats.minSurfaceY,26);assert.ok(t.terrainStats.deepestColumn>=80&&t.terrainStats.deepestColumn<=110);
  assert.ok(t.heights[90*t.S+160]>t.heights[310*t.S+160],'rear hill above front peninsula');
  const lookup=m.definitions.map(d=>t.definitions.findIndex(e=>JSON.stringify(d)===JSON.stringify(e)));assert.ok(lookup.every(i=>i>=0));
  for(let y=m.low;y<m.high;y++)for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++)if(lookup[cells.readUInt16LE(((y-m.low)*t.S*t.S+z*t.S+x)*2)]!==t.get(x,y,z))assert.fail('Export mismatch '+[x,y,z]);
  assert.deepEqual(m.spawn,t.spawn);assert.equal(t.get(...t.spawn),0);assert.equal(t.get(t.spawn[0],t.spawn[1]+1,t.spawn[2]),0);
});
