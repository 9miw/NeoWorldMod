// Apply a local edit to the saved city; never regenerate the island.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary}=require('./viewer/map-library.cjs');
const {patch}=require('./smooth_active_land.cjs');
const {decode,encode}=require('./nbt-edit.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const vertices=[[152,302],[168,302],[170,314],[160,320],[150,314]];
function inside(x,z){let sign=0;for(let i=0;i<vertices.length;i++){const a=vertices[i],b=vertices[(i+1)%vertices.length],cross=(b[0]-a[0])*(z-a[1])-(b[1]-a[1])*(x-a[0]);if(cross){if(sign&&Math.sign(cross)!==sign)return false;sign=Math.sign(cross);}}return true;}
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),before=new Uint16Array(bytes.length/2);for(let i=0;i<before.length;i++)before[i]=bytes.readUInt16LE(i*2);const data=before.slice(),changes=new Map();
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
 const ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 function put(x,y,z,name,properties){if(x<149||x>171||z<301||z>320||y<97||y>99)throw Error('Outside approved local edit');const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},key=JSON.stringify(d);if(!ids.has(key)){ids.set(key,m.definitions.length);m.definitions.push(d);}const i=index(x,y,z);data[i]=ids.get(key);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const rows=[];for(let z=302;z<=320;z++){const xs=[];for(let x=150;x<=170;x++)if(inside(x,z))xs.push(x);if(!xs.length)continue;rows.push({z,min:xs[0],max:xs.at(-1)});for(const x of xs){const edge=!inside(x-1,z)||!inside(x+1,z)||!inside(x,z-1)||!inside(x,z+1);put(x,97,z,edge?'polished_andesite':Math.abs(x-160)<=1||z===310?'smooth_stone':'polished_diorite');}}
 for(const [x,z]of [[160,306],[156,310],[164,310],[160,315]])put(x,97,z,'sea_lantern');
 for(const [x,z]of vertices){put(x,98,z,'polished_andesite');put(x,99,z,'chiseled_stone_bricks');}
 const exits=[];for(let x=158;x<=162;x++){put(x,97,301,'stone_brick_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});exits.push([x,301]);}
 for(let z=308;z<=312;z++){const row=rows.find(r=>r.z===z);for(const [x,facing]of [[row.min-1,'east'],[row.max+1,'west']]){put(x,97,z,'stone_brick_stairs',{facing,half:'bottom',shape:'straight',waterlogged:'false'});exits.push([x,z]);}}
 for(const [x,z]of exits){const stair=m.definitions[data[index(x,97,z)]];if(!stair.Name.endsWith('_stairs')||!data[index(x,96,z)]||data[index(x,98,z)]||data[index(x,99,z)])throw Error('Obstructed stairs');}
 if(data[index(160,98,310)]||data[index(160,99,310)]||!data[index(160,97,310)])throw Error('Unsafe raised spawn');
 return {data,original:before,changes:[...changes.values()],vertices,rows,exits};
}
function run(){
 const reportFile=path.join(MAP,'spawn-platform-report.json');if(fs.existsSync(reportFile))throw Error('Platform already edited; review before applying again');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=edit(m),regionFile=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,result.data,result.original,fs.readFileSync(regionFile));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-spawn-platform/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists; review before applying again');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});
 fs.writeFileSync(regionFile+'.pending',changed.bytes);fs.renameSync(regionFile+'.pending',regionFile);
 const tag=(type,value)=>({type,value}),levelPath=path.join(MAP,'level.dat'),level=decode(zlib.gunzipSync(fs.readFileSync(levelPath)));level.value.Data.value.SpawnY=tag(3,98);fs.writeFileSync(levelPath,zlib.gzipSync(encode(level)));
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.spawn=[160,98,310];manifest.description='จุดเกิดฐานห้าเหลี่ยม · เสาสูง 2 บล็อก · บันไดหน้า ซ้าย ขวา';for(const r of manifest.rooms||[])if(r.name==='Spawn'||r.id==='02-spawn')r.center=[160,98,310];manifest.spawnPlatform={vertices,baseY:97,pillarHeight:2,stairs:['north','west','east']};fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));for(const zone of zones)if(zone.id==='02-spawn'){zone.center=[160,98,310];zone.platform=manifest.spawnPlatform;}fs.writeFileSync(zf,JSON.stringify(zones,null,2));
 const report={changedChunks:changed.changed,editedBlocks:result.changes.length,spawn:[160,98,310],vertices,baseY:97,pillarHeight:2,exits:result.exits,backup:path.relative(ROOT,backup),changes:result.changes};fs.writeFileSync(reportFile,JSON.stringify(report,null,2));console.log(JSON.stringify({changedChunks:changed.changed,editedBlocks:result.changes.length,backup}));
}
module.exports={edit,inside,vertices};if(require.main===module)run();
