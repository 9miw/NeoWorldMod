const fs=require('node:fs'),path=require('node:path');
const {buildLand}=require('./create_sky_land.cjs'),{exportMap}=require('./create_ruined_castle.cjs');
function buildSpawnLand(){
  const t=buildLand(),zone=require('./sky-town/land-spawn.cjs')(t);
  return {...t,name:'NeoWorld - Sky Land Spawn v1',spawnZone:zone,rooms:[zone],reachableFloorCells:zone.reachableCells};
}
function generate(destination='maps/starter_sky_land_spawn_v1'){
  if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
  const t=buildSpawnLand(),result=exportMap(t,destination),file=path.join(destination,'map-manifest.json'),m=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(m,{name:'Sky Land · Spawn 1',description:'จุดเกิดบนแผ่นดินใหม่ · ลานโค้ง ซุ้มด้านหลัง และสวนริมลาน',zones:[{id:'01-island',name:'Mainland',bounds:[24,24,303,343],outline:t.outline},{...t.spawnZone,footprint:undefined}],terrain:t.terrainStats,gameplay:'Only the spawn area has been built. No NPCs, functional portals or other city zones. North opening is a connection point, not a completed city road.'});
  fs.writeFileSync(file,JSON.stringify(m,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(m.zones,null,2));
  fs.writeFileSync(path.join(destination,'README.md'),'# Sky Land — Spawn 1\n\nSpawn 160 97 310 facing north. Rounded arrival terrace approximately 53 × 49 blocks, with a 13-block north opening. Stone arch behind spawn, gardens, lanterns and benches at the edges.\n\nOnly source zone tools/maps/sky-town/land-spawn.cjs is added to the terrain study. Original terrain-only world is unchanged. No other city zones or gameplay services.\n');
  return {...result,spawnZone:{bounds:t.spawnZone.bounds,exitWidth:t.spawnZone.exitWidth}};
}
module.exports={buildSpawnLand,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
