// Zone 03 only: saved-world edit, real water fountain, route stubs and reusable trees.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[original[index(x,y,z)]];
 function put(x,y,z,name,properties){if(x<96||x>224||z<190||z>278||y<96||y>116)throw Error('Outside zone 03 edit');const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}const i=index(x,y,z),id=ids.get(k);data[i]=id;changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
 const roads=[[154,210,166,245],[112,257,143,263],[177,257,208,263]],trees=[[138,250,0],[182,250,1],[138,272,2],[182,272,3]];
 // Shape shoulders into existing land, so extended roads do not sit on isolated plinths.
 for(let z=190;z<=278;z++)for(let x=96;x<=224;x++){
  if(get(x,96,z).Name==='minecraft:air')continue;let top=96,built=false;for(let y=96;y<m.high;y++){const n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built||top>103)continue;
  let target=top;for(const [x1,z1,x2,z2]of roads){const distance=Math.max(x1-x,0,x-x2)+Math.max(z1-z,0,z-z2);if(distance<=20)target=Math.max(target,103-Math.ceil(distance/3));}
  for(const [cx,cz]of trees){const distance=Math.hypot(x-cx,z-cz);if(distance<=9)target=Math.max(target,103-Math.max(0,Math.ceil(distance-6)));}
  if(target<=top)continue;for(let y=96;y<=target;y++)put(x,y,z,y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 for(const [x1,z1,x2,z2]of roads)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){
  for(let y=97;y<103;y++)put(x,y,z,'stone');const edge=x===x1||x===x2||z===z1||z===z2;
  put(x,103,z,edge?'polished_andesite':Math.abs(x-160)<=1||Math.abs(z-260)<=1?'smooth_stone':(x+z)%17===0?'andesite':'stone_bricks');
 }
 // Octagonal inlay and sealed lower basin, 11 blocks across.
 for(let dz=-8;dz<=8;dz++)for(let dx=-8;dx<=8;dx++){const r=Math.hypot(dx,dz);if(r<=8.3)put(160+dx,103,260+dz,r>=7?'polished_andesite':r>=5.5?'smooth_stone':'dark_prismarine');}
 const basin=(dx,dz)=>Math.max(Math.abs(dx),Math.abs(dz))<=5&&Math.abs(dx)+Math.abs(dz)<=7;
 for(let dz=-5;dz<=5;dz++)for(let dx=-5;dx<=5;dx++){
  if(!basin(dx,dz))continue;const rim=[[1,0],[-1,0],[0,1],[0,-1]].some(([x,z])=>!basin(dx+x,dz+z));
  put(160+dx,103,260+dz,'dark_prismarine');
  if(rim){put(160+dx,104,260+dz,'polished_andesite');put(160+dx,105,260+dz,'smooth_stone_slab',{type:'bottom',waterlogged:'false'});}
  else put(160+dx,104,260+dz,'water',{level:'0'});
 }
 for(let y=104;y<=109;y++)put(160,y,260,y===104?'sea_lantern':'quartz_pillar',{...(y===104?{}:{axis:'y'})});
 for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(dx*dx+dz*dz<=4){put(160+dx,108,260+dz,'smooth_quartz');if(dx*dx+dz*dz<=1)put(160+dx,109,260+dz,'water',{level:'0'});}
 put(160,110,260,'water',{level:'0'});
 for(const [dx,dz]of [[2,0],[-2,0],[0,2],[0,-2]])for(let y=105;y<=108;y++)put(160+dx,y,260+dz,'water',{level:'8'});
 function lamp(x,z){put(x,103,z,'polished_andesite');put(x,104,z,'chiseled_stone_bricks');for(let y=105;y<=107;y++)put(x,y,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,108,z,'lantern',{hanging:'false',waterlogged:'false'});}
 const lamps=[[146,248],[174,248],[146,272],[174,272],[151,214],[169,214],[151,230],[169,230],[123,255],[197,255]];for(const [x,z]of lamps)lamp(x,z);
 const benches=[];for(const z of [249,271])for(const start of [148,169]){for(let x=start;x<start+4;x++)put(x,104,z,'spruce_stairs',{facing:z===249?'north':'south',half:'bottom',shape:'straight',waterlogged:'false'});for(const x of [start-1,start+4])put(x,104,z,'spruce_slab',{type:'bottom',waterlogged:'false'});benches.push({bounds:[start-1,z,start+4,z]});}
 const beds=[];for(const x of [147,169])for(const z of [252,266]){for(let dz=0;dz<3;dz++)for(let dx=0;dx<5;dx++){const edge=dx===0||dx===4||dz===0||dz===2;put(x+dx,103,z+dz,edge?'stone_bricks':'dirt');if(edge)put(x+dx,104,z+dz,'stone_brick_slab',{type:'bottom',waterlogged:'false'});else put(x+dx,104,z+dz,dx%2?'poppy':'azure_bluet');}beds.push({bounds:[x,z,x+4,z+2]});}
 const flags=[];for(const x of [151,169]){put(x,103,238,'polished_andesite');for(let y=104;y<=111;y++)put(x,y,238,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});const direction=x<160?1:-1;for(let dx=1;dx<=2;dx++)for(let y=107;y<=110;y++)put(x+direction*dx,y,238,dx===1?'white_wool':'blue_wool');put(x,112,238,'lantern',{hanging:'false',waterlogged:'false'});flags.push([x,104,238]);}
 // Place the converted t199 asset, rotated around its 9x9 footprint; exclude plot/labels.
 const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures/t199.nbt'))));const treePlacements=[];
 for(const [cx,cz,rotation]of trees){for(const b of asset.blocks){let [x,y,z]=b.pos;for(let i=0;i<rotation;i++)[x,z]=[8-z,x];const d=asset.palette[b.state],props=d.Properties?{...d.Properties}:undefined;if(props?.axis&&rotation%2&&props.axis!=='y')props.axis=props.axis==='x'?'z':'x';const wx=cx-4+x,wz=cz-4+z,wy=104+y;const existing=m.definitions[data[index(wx,wy,wz)]];if(existing.Name!=='minecraft:air')throw Error('Tree placement collision '+wx+','+wy+','+wz);put(wx,wy,wz,d.Name.slice(10),props);}treePlacements.push({asset:'t199',center:[cx,104,cz],rotation:rotation*90,source:'exsilit'});}
 return {data,original,changes:[...changes.values()],details:{fountain:{center:[160,104,260],bounds:[155,103,255,165,110,265],water:'real water; sealed lower basin'},roads,lamps,flags,benches,beds,trees:treePlacements}};
}
function run(){
 const reportFile=path.join(MAP,'square-details-report.json');if(fs.existsSync(reportFile))throw Error('Details already applied; inspect before another edit');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=edit(m),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,result.data,result.original,fs.readFileSync(file));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-square-details/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));Object.assign(manifest.streetsSquare,{bounds:[112,210,208,301],status:'Fountain, lighting, flags, planted beds, benches, reusable trees and three road stubs; surrounding buildings pending',details:result.details});manifest.description='ลานน้ำพุ · ถนนหลักและทางแยก · สวน โคมไฟ ธง และม้านั่ง';for(const r of manifest.rooms||[])if(r.id==='03-streets-square')Object.assign(r,manifest.streetsSquare);manifest.rooms.push({name:'Fountain',center:[160,104,260],bounds:[155,255,165,265]});fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));for(const zone of zones)if(zone.id==='03-streets-square')Object.assign(zone,manifest.streetsSquare);fs.writeFileSync(zf,JSON.stringify(zones,null,2));
 const report={changedChunks:changed.changed,scheduledVoxels:result.changes.length,backup:path.relative(ROOT,backup),details:result.details,changes:result.changes};fs.writeFileSync(reportFile,JSON.stringify(report,null,2));console.log(JSON.stringify({changedChunks:changed.changed,scheduledVoxels:result.changes.length,details:result.details}));
}
module.exports={edit};if(require.main===module)run();
