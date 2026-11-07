// Local road/square elevation edit, preserving the existing island and spawn pillars.
const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const at=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
 function put(x,y,z,name,properties){if(x<130||x>190||z<246||z>301||y<97||y>104)throw Error('Outside road/square edit');const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[at(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 function column(x,z,y,name,properties){for(let yy=97;yy<y;yy++)put(x,yy,z,'stone_bricks');put(x,y,z,name,properties);}
 const stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),profile=[];
 // Six rises from Y97 to Y103, each followed by two full landing rows.
 for(let z=275;z<=301;z++){
  const step=z<=294&&z>=279&&(294-z)%3===0,level=z>=295?97:Math.min(103,98+Math.floor((294-z)/3));
  for(let x=154;x<=166;x++){if(z>=299&&(x<158||x>162))continue;const edge=x===154||x===166;column(x,z,level,step?'stone_brick_stairs':edge?'polished_andesite':Math.abs(x-160)<=1?'smooth_stone':'stone_bricks',step?stair('north'):undefined);}
  profile.push({z,level,stairs:step});
 }
 // A plain upper square reserves room for later fountain/services without adding them now.
 for(let z=246;z<=274;z++)for(let x=144;x<=176;x++){
  const edge=x===144||x===176||z===246||z===274;
  column(x,z,103,edge?'polished_andesite':Math.abs(x-160)<=1||Math.abs(z-260)<=1?'smooth_stone':'stone_bricks');
  const opening=(z===274&&x>=154&&x<=166)||((x===144||x===176)&&z>=258&&z<=262);
  if(edge&&!opening)put(x,104,z,'stone_brick_wall',{up:'true',north:'none',south:'none',east:'none',west:'none',waterlogged:'false'});
 }
 // Side branches descend seven blocks to the unchanged Y96 ground.
 for(const direction of [-1,1])for(let n=0;n<13;n++)for(let z=258;z<=262;z++){
  const x=direction<0?143-n:177+n,level=103-Math.ceil(n/2),step=n%2===0;
  column(x,z,level,step?'stone_brick_stairs':'stone_bricks',step?stair(direction<0?'east':'west'):undefined);
 }
 // Edge rails of the main stair flight leave all thirteen walking columns clear.
 for(let z=275;z<=298;z++){const y=profile.find(p=>p.z===z).level;for(const x of [153,167]){column(x,z,y,'stone_bricks');put(x,y+1,z,'stone_brick_wall',{up:'true',north:'low',south:'low',east:'none',west:'none',waterlogged:'false'});}}
 return {data,original,changes:[...changes.values()],profile};
}
function run(){
 const reportFile=path.join(MAP,'spawn-square-report.json');if(fs.existsSync(reportFile))throw Error('Connection already applied; inspect before rerunning');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=edit(m),regionFile=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,result.data,result.original,fs.readFileSync(regionFile));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-spawn-square/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(regionFile+'.pending',changed.bytes);fs.renameSync(regionFile+'.pending',regionFile);
 const square={id:'03-streets-square',name:'Streets / Square',bounds:[130,246,190,301],center:[160,104,260],floorY:103,squareBounds:[144,246,176,274],mainPathWidth:13,sidePathWidth:5,spawnBaseY:97,heightDifference:6,status:'Plain elevated square and approach; fountain and services pending'};
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.rooms.push(square);manifest.description='ฐานเกิด Y97 · บันไดสลับชานพัก · ลานกลาง Y103';manifest.streetsSquare=square;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));zones.push(square);fs.writeFileSync(zf,JSON.stringify(zones,null,2));
 const report={changedChunks:changed.changed,scheduledVoxels:result.changes.length,backup:path.relative(ROOT,backup),square,profile:result.profile,changes:result.changes};fs.writeFileSync(reportFile,JSON.stringify(report,null,2));console.log(JSON.stringify({changedChunks:changed.changed,scheduledVoxels:result.changes.length,backup}));
}
module.exports={edit};if(require.main===module)run();
