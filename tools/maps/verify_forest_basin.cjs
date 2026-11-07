const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/leveling_island_01'),manifest=JSON.parse(fs.readFileSync(path.join(dir,'map-manifest.json')));
assert.equal(manifest.designId,'forest-basin-terrain-01');
const m=createLibrary(path.join(root,'maps')).load('leveling_island_01'),b=Buffer.from(m.blocks,'base64'),S=m.size;
const g=(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)].Name;
assert.equal(S,384);assert.equal(manifest.chunks,576);assert.deepEqual(m.spawn,[193,89,353]);
assert.notEqual(g(193,88,353),'minecraft:air');for(let y=89;y<=91;y++)assert.equal(g(193,y,353),'minecraft:air');
for(let z=319;z<=337;z++)for(let x=191;x<=195;x++){assert.equal(g(x,88,z),'minecraft:spruce_planks');for(let y=89;y<=91;y++)assert.equal(g(x,y,z),'minecraft:air');}
const layout=JSON.parse(fs.readFileSync(path.join(dir,'layout.json')));assert.equal(layout.buildingReserves.length,6);assert.equal(layout.zones.length,5);
const top=(x,z)=>{for(let y=m.high-1;y>=m.low;y--)if(g(x,y,z)!=='minecraft:air')return y;return null;};
let routeSamples=0,maxStep=0;for(const points of layout.paths){let prev;for(let j=1;j<points.length;j++){const a=points[j-1],c=points[j],n=Math.ceil(Math.hypot(c[0]-a[0],c[1]-a[1])*2);for(let i=0;i<=n;i++){const t=i/n,x=Math.round(a[0]+(c[0]-a[0])*t),z=Math.round(a[1]+(c[1]-a[1])*t),y=top(x,z);assert(y!==null,'Route leaves land');for(let yy=1;yy<=2;yy++)assert.equal(g(x,y+yy,z),'minecraft:air');if(prev)maxStep=Math.max(maxStep,Math.abs(y-prev[2]));prev=[x,z,y];routeSamples++;}}}
if(layout.walkRoutes){
  console.log(JSON.stringify(require('./verify_forest_walk_surfaces.cjs')(m,layout),null,2));
}else assert(maxStep<=1,'Route contains a two-block step');
console.log(JSON.stringify({designId:manifest.designId,spawnClear:true,bridgeFloorAndHeadroom:true,buildingReserves:6,routeSamples,maxRouteCellDelta:maxStep,limitation:layout.walkRoutes?'Cell delta includes slab/stair block coordinates. Use the half-step surface results above; in-game collision not tested.':'Terrain layout only; one-block steps may require jumping. In-game not tested.'},null,2));
