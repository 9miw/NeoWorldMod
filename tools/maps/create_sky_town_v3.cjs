const fs=require('node:fs'),path=require('node:path');
const {createTown}=require('./sky-town/core.cjs'),{buildLargeTown,navigation}=require('./create_sky_town_v2.cjs'),{terrain,lift}=require('./sky-town/reference-terrain.cjs'),{buildings}=require('./sky-town/reference-buildings.cjs'),{landscape}=require('./sky-town/reference-landscape.cjs'),{exportMap}=require('./create_ruined_castle.cjs');
function buildReferenceTown(){
  const old=buildLargeTown(),t=createTown({size:416,high:224});terrain(t,old);buildings(t);landscape(t);
  const move=([x,y,z])=>[x,y+lift(x,z),z];t.spawn=move(old.spawn);
  const zones=old.zones.map(z=>({...z,center:move(z.center),referenceRevision:3})),services=old.services.map(s=>({...s,entrance:move(s.entrance),...(s.upperFloorY?{upperFloorY:s.upperFloorY+lift(s.entrance[0],s.entrance[2])}:{}),checkpoints:s.checkpoints.map(move),interiorBounds:s.interiorBounds.map((v,i)=>i===1||i===4?v+lift(s.entrance[0],s.entrance[2]):v)}));
  const targets=old.checkedTargets.map(c=>({...c,point:move(c.point)}));
  const checks=navigation(t,targets);
  return {...t,...checks,tiles:[...t.tiles.values()],name:'NeoWorld - Starter Sky Town v3 Reference',zones,services,rooms:zones.filter(z=>z.id!=='01-island')};
}
function generate(destination='maps/starter_sky_town_v3'){
  if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
  const t=buildReferenceTown(),result=exportMap(t,destination),file=path.join(destination,'map-manifest.json'),m=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(m,{name:'Starter Sky Town · V3 Reference',description:'หน้าผาหลายระดับ · ปราสาทเพิ่มหอคอย · อาคารมุขยื่น · สวนหนาแน่น',zones:t.zones,services:t.services,navigationChecks:t.checkedTargets,design:{reference:'User zoning mockup, 8 zones',revision:3,mainIslandApprox:[283,322],terrainLevels:[96,100,102,108,114,126],notes:'Concept-driven architecture revision, not a claim of pixel-identical reconstruction.'},gameplay:'Architectural preview; NPC actions and portal travel need server setup.'});
  fs.writeFileSync(file,JSON.stringify(m,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(t.zones,null,2));
  fs.writeFileSync(path.join(destination,'README.md'),'# Starter Sky Town V3 Reference\n\nReference-driven revision: terraced rock island, expanded castle skyline, projecting hall porch, merchant awnings, dense gardens and hanging roots. Original V1/V2 preserved.\n\nEditable modules: tools/maps/sky-town/reference-terrain.cjs, reference-buildings.cjs, reference-landscape.cjs. All services retain physical access checks. Gameplay connections remain pending.\n');
  return {...result,checkedTargets:t.checkedTargets.length};
}
module.exports={buildReferenceTown,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
