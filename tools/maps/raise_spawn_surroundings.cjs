// Raise the saved terrain into a continuous city terrace around the lower arrival bowl.
const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const bounds=[98,210,230,334];
function protectedBowl(x,z){return z>=281&&((x-160)/30)**2+((z-309)/27)**2<=1;}
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[original[index(x,y,z)]];
 function put(x,y,z,name,properties){if(x<98||x>230||z<210||z>334||y<96||y>104)throw Error('Outside approved terrain edit');const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}const i=index(x,y,z),id=ids.get(k);if(data[i]!==id){data[i]=id;changes.set([x,y,z].join(','),{x,y,z,state:d});}}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);let raisedColumns=0;
 for(let z=210;z<=334;z++)for(let x=98;x<=230;x++){
  if(protectedBowl(x,z))continue;
  const sideRoad=z>=258&&z<=262&&((x>=130&&x<=143)||(x>=177&&x<=190));
  if(!sideRoad){let built=false;for(let y=96;y<m.high;y++)if(!natural.has(get(x,y,z).Name)){built=true;break;}if(built)continue;}
  // Do not expand the island footprint or fill void columns.
  if(get(x,96,z).Name==='minecraft:air')continue;
  let top=96;for(let y=97;y<m.high;y++)if(get(x,y,z).Name!=='minecraft:air')top=y;
  const blend=Math.max(0,Math.min(1,(x-98)/24,(230-x)/24,(z-210)/24,(334-z)/24));
  const target=sideRoad?103:Math.max(top,Math.round(96+7*blend));if(target>103||target<=top&&!sideRoad)continue;
  if(target>top)raisedColumns++;
  for(let y=96;y<=target;y++)put(x,y,z,y===target?(sideRoad?'stone_bricks':'grass_block'):y>=target-2?'dirt':'stone',y===target&&!sideRoad?{snowy:'false'}:undefined);
 }
 // The upper square now meets ground at the same height; remove its obsolete perimeter railing.
 for(let z=246;z<=274;z++)for(let x=144;x<=176;x++)if(x===144||x===176||z===246||z===274){if(get(x,104,z).Name==='minecraft:stone_brick_wall')put(x,104,z,'air');}
 return {data,original,changes:[...changes.values()],raisedColumns};
}
function run(){
 const reportPath=path.join(MAP,'spawn-surroundings-report.json');if(fs.existsSync(reportPath))throw Error('Terrain edit already applied; review before repeating');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=edit(m),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,result.data,result.original,fs.readFileSync(file));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-spawn-surroundings/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.description='ลานเกิดต่ำ Y97 · แผ่นดินรอบเมืองต่อเนื่อง Y103 · บันไดขึ้นจากลานเว้า';manifest.spawnSurroundings={bounds,cityFloorY:103,spawnBaseY:97,bowl:{center:[160,309],radius:[30,27],open:'south'},blendWidth:24};manifest.streetsSquare.status='Upper square joins raised terrain; side branches are level Y103; fountain/services pending';manifest.streetsSquare.sideFloorY=103;for(const r of manifest.rooms||[])if(r.id==='03-streets-square')Object.assign(r,manifest.streetsSquare);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));for(const zone of zones)if(zone.id==='03-streets-square')Object.assign(zone,manifest.streetsSquare);fs.writeFileSync(zf,JSON.stringify(zones,null,2));
 const report={bounds,cityFloorY:103,spawnBaseY:97,raisedColumns:result.raisedColumns,changedChunks:changed.changed,changedBlocks:result.changes.length,backup:path.relative(ROOT,backup),changes:result.changes};fs.writeFileSync(reportPath,JSON.stringify(report,null,2));console.log(JSON.stringify({raisedColumns:result.raisedColumns,changedChunks:changed.changed,changedBlocks:result.changes.length}));
}
module.exports={edit,protectedBowl,bounds};if(require.main===module)run();
