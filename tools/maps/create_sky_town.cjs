const fs=require('node:fs');
const path=require('node:path');
const {createTown}=require('./sky-town/core.cjs');
const {exportMap}=require('./create_ruined_castle.cjs');
const zones=[
  ['01-island','Island',[12,12,188,212],[100,97,156]],
  ['02-spawn','Spawn',[80,176,112,204],[96,97,190]],
  ['03-roads-square','Town Square',[76,76,116,175],[96,97,100]],
  ['04-market','Market',[28,96,75,147],[53,97,125]],
  ['05-blacksmith','Blacksmith',[28,60,75,95],[65,97,84]],
  ['06-adventurers-hall','Adventurers Hall',[117,76,168,147],[144,97,110]],
  ['07-castle','Castle',[60,20,132,75],[96,109,59]],
  ['08-dimensional-station','Portal Island',[169,108,244,152],[224,97,132]]
];
function validate(t){
  const {get}=t,walk=(x,z)=>{for(let y=108;y>=96;y--)if(get(x,y,z)&&!get(x,y+1,z)&&!get(x,y+2,z))return y+1;return null;};
  if(walk(96,190)!==97)throw Error('Unsafe spawn');
  const visited=new Set(['96,190']),queue=[[96,190,97]];
  for(let i=0;i<queue.length;i++){const [x,z,h]=queue[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz,key=nx+','+nz;if(nx<0||nz<0||nx>=t.S||nz>=t.S||visited.has(key))continue;const next=walk(nx,nz);if(next!==null&&Math.abs(next-h)<=1){visited.add(key);queue.push([nx,nz,next]);}}}
  const destinations={square:[96,100],market:[53,125],blacksmith:[48,80],hall:[144,101],castle:[96,59],portal:[224,132]};
  for(const [name,[x,z]]of Object.entries(destinations))if(!visited.has(x+','+z))throw Error('Unreachable '+name);
  for(let z=77;z<=190;z++)for(let x=92;x<=100;x++)if(get(x,97,z)||get(x,98,z)||!get(x,96,z))throw Error('Main avenue blocked '+x+','+z);
  for(let x=169;x<=214;x++)for(let z=130;z<=134;z++)if(get(x,97,z)||get(x,98,z)||!get(x,96,z))throw Error('Bridge blocked '+x+','+z);
  return {reachableFloorCells:visited.size,destinations};
}
function buildTown(){
  const t=createTown();for(const [id,name,bounds,center]of zones){require('./sky-town/zones/'+id+'.cjs')(t);t.zones.push({id,name,bounds,center,source:'tools/maps/sky-town/zones/'+id+'.cjs'});}
  const checks=validate(t);
  return {...t,name:'NeoWorld - Starter Sky Town v1',spawn:[96,97,190],tiles:[...t.tiles.values()],rooms:t.zones.filter(z=>z.id!=='01-island'),...checks};
}
function generate(destination='maps/starter_sky_town_v1'){
  if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
  const model=buildTown(),result=exportMap(model,destination),file=path.join(destination,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(manifest,{name:'Starter Sky Town · Draft 1',description:'เมืองเริ่มต้นเกาะลอยฟ้า · แยก 8 โซน',gameplay:'Architecture preview. NPC services, interdimensional travel and upgrade-table marker are not connected.',zones:model.zones,design:{forward:'north (-Z)',mainPathWidth:9,sidePathWidth:5,baseFloorY:96,castleFloorY:108,plan:'docs/maps/floating-starter-town-zones.md',stage:'First architectural draft'},serviceMarkers:[{type:'upgrade_table',position:[65,97,84],previewBlock:'minecraft:enchanting_table',targetBlock:'neoworld:upgrade_table',connected:false}],navigationChecks:model.destinations});
  fs.writeFileSync(file,JSON.stringify(manifest,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(model.zones,null,2));
  fs.writeFileSync(path.join(destination,'README.md'),'# Starter Sky Town · Draft 1\n\nMinecraft Java 1.21.1. 256 × 256 save footprint, including void margin. Spawn 96 97 190 facing north. Main town floor Y96; castle Y108.\n\nEight separately editable source modules: tools/maps/sky-town/zones/. zones.json records their boundaries. Regenerate to a NEW folder to keep previous drafts.\n\nArchitecture only: no active NPCs or portal scripts. The enchanting table at 65 97 84 marks a future neoworld:upgrade_table. Fountain uses glass, forge uses glass over light blocks. World uses void generation; production protection and service connections still need setup.\n');
  return {...result,zones:model.zones.length};
}
module.exports={buildTown,validate,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
