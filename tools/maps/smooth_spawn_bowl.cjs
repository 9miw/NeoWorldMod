// Round the arrival bowl's terrain rim without moving any built columns.
const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=[],columns=[],ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[original[index(x,y,z)]];
 function put(x,y,z,name,properties){const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}const i=index(x,y,z),id=ids.get(k);if(data[i]!==id){data[i]=id;changes.push({x,y,z,state:d});}}
 for(let z=264;z<=334;z++)for(let x=108;x<=212;x++){
  if(get(x,96,z).Name==='minecraft:air')continue;
  let built=false,top=96;for(let y=96;y<m.high;y++){const name=get(x,y,z).Name;if(!natural.has(name)){built=true;break;}if(name!=='minecraft:air')top=y;}if(built||top>103)continue;
  const radius=Math.hypot((x-160)/30,(z-309)/27),t=Math.max(0,Math.min(1,(radius-1)*27/18));
  const rim=96+7*t*t*(3-2*t);let target=Math.min(top,Math.round(rim));
  // Natural shoulders follow the preserved stair profile instead of leaving it on a ridge.
  if(z>=275&&z<=294){const road=Math.min(103,98+Math.floor((294-z)/3)),distance=Math.max(0,Math.abs(x-160)-7);target=Math.max(target,Math.min(103,road-distance));}
  const squareDistance=Math.max(144-x,0,x-176)+Math.max(246-z,0,z-274);
  if(squareDistance<7)target=Math.max(target,103-squareDistance);
  if(target===top)continue;columns.push({x,z,before:top,after:target});
  for(let y=96;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 return {data,original,changes,columns};
}
function run(){
 const reportPath=path.join(MAP,'spawn-bowl-smoothing-report.json');if(fs.existsSync(reportPath))throw Error('Smoothing already applied; inspect before repeating');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=edit(m),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,result.data,result.original,fs.readFileSync(file));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-bowl-smoothing/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.description='ลานเกิดเว้าต่ำ · ขอบเนินโค้งไล่ระดับ 18 บล็อก · พื้นเมือง Y103';manifest.spawnSurroundings.bowl.slopeWidth=18;manifest.spawnSurroundings.bowl.profile='smoothstep terrain rim; protected built columns; stair shoulders';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const report={changedChunks:changed.changed,changedBlocks:result.changes.length,changedColumns:result.columns.length,slopeWidth:18,backup:path.relative(ROOT,backup),columns:result.columns,changes:result.changes};fs.writeFileSync(reportPath,JSON.stringify(report,null,2));console.log(JSON.stringify({changedChunks:changed.changed,changedBlocks:result.changes.length,changedColumns:result.columns.length}));
}
module.exports={edit,natural};if(require.main===module)run();
