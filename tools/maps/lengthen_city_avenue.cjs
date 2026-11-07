// Correct zone 03 from a broad square to a long avenue with a small fountain widening.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const key=d=>JSON.stringify([d.Name,Object.entries(d.Properties||{}).sort()]);
function roadLevel(z){return z>=169?103:Math.min(108,104+Math.floor((168-z)/3));}
function halfWidth(z){return z>=242&&z<=278?6+Math.round(5*Math.sin(Math.PI*(z-242)/36)**2):6;}
function edit(m,base,oldReport){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[data[index(x,y,z)]],baseBytes=Buffer.from(base.blocks,'base64');
 function putState(x,y,z,d){if(x<96||x>224||z<130||z>278||y<96||y>116)throw Error('Outside avenue edit');const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[index(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const put=(x,y,z,name,properties)=>putState(x,y,z,{Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});
 const fountain=(x,z)=>x>=155&&x<=165&&z>=255&&z<=265;
 // Remove only owned decoration voxels that still match the previous recorded state.
 for(const c of oldReport.changes){if(c.y<104||fountain(c.x,c.z))continue;const d=get(c.x,c.y,c.z);if(key(d)!==key(c.state))throw Error('Manual change in old decoration; inspect '+[c.x,c.y,c.z]);const bi=(c.y-base.low)*base.size*base.size+c.z*base.size+c.x;putState(c.x,c.y,c.z,base.definitions[baseBytes.readUInt16LE(bi*2)]);}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
 // Grade the narrow northern approach and green verges into the existing hill.
 for(let z=130;z<=245;z++)for(let x=126;x<=194;x++){
  let top=96,built=false;for(let y=96;y<m.high;y++){const n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built||top>112)continue;
  const sampleZ=Math.max(150,z),distance=Math.max(0,Math.abs(x-160)-18)+Math.max(0,150-z),target=Math.max(top,roadLevel(sampleZ)-Math.ceil(distance/3));if(target<=top)continue;
  for(let y=96;y<=target;y++)put(x,y,z,y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 // Return the oversized square's corners and former fixtures to grass.
 for(let z=246;z<=274;z++)for(let x=144;x<=176;x++){const paved=Math.abs(x-160)<=halfWidth(z)||(z>=257&&z<=263);if(!paved)put(x,103,z,'grass_block',{snowy:'false'});}
 const profile=[];for(let z=150;z<=278;z++){
  const y=roadLevel(z),half=halfWidth(z),stairs=z>=156&&z<=168&&(168-z)%3===0;profile.push({z,y,width:half*2+1,stairs});
  for(let x=160-half;x<=160+half;x++){
   if(fountain(x,z))continue;for(let yy=97;yy<y;yy++)put(x,yy,z,'stone');
   const edge=x===160-half||x===160+half;put(x,y,z,stairs?'stone_brick_stairs':edge?'polished_andesite':Math.abs(x-160)<=1?'smooth_stone':(x+z)%17===0?'andesite':'stone_bricks',stairs?{facing:'north',half:'bottom',shape:'straight',waterlogged:'false'}:undefined);
  }
 }
 function lamp(x,z){const y=roadLevel(z);put(x,y,z,'polished_andesite');put(x,y+1,z,'chiseled_stone_bricks');for(let yy=y+2;yy<=y+4;yy++)put(x,yy,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,y+5,z,'lantern',{hanging:'false',waterlogged:'false'});}
 const lamps=[];for(const z of [155,175,195,215,235,249,272])for(const x of [151,169]){lamp(x,z);lamps.push([x,roadLevel(z),z]);}for(const [x,z]of [[123,255],[197,255]]){lamp(x,z);lamps.push([x,103,z]);}
 const benches=[];for(const z of [232,244])for(const x of [150,170]){for(let dz=-1;dz<=4;dz++){for(let dx=-1;dx<=1;dx++)put(x+dx,103,z+dz,'smooth_stone');if(dz>=0&&dz<=3)put(x,104,z+dz,'spruce_stairs',{facing:x<160?'west':'east',half:'bottom',shape:'straight',waterlogged:'false'});else put(x,104,z+dz,'spruce_slab',{type:'bottom',waterlogged:'false'});}benches.push([x,104,z]);}
 const beds=[];for(const x of [149,169])for(const z of [205,237]){for(let dx=0;dx<3;dx++)for(let dz=0;dz<3;dz++){const edge=dx===0||dx===2||dz===0||dz===2;put(x+dx,103,z+dz,edge?'stone_bricks':'dirt');put(x+dx,104,z+dz,edge?'stone_brick_slab':'poppy',edge?{type:'bottom',waterlogged:'false'}:undefined);}beds.push([x,z]);}
 const flags=[];for(const x of [151,169]){const z=178;for(let y=104;y<=111;y++)put(x,y,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});const side=x<160?1:-1;for(let dx=1;dx<=2;dx++)for(let y=107;y<=110;y++)put(x+side*dx,y,z,dx===1?'white_wool':'blue_wool');put(x,112,z,'lantern',{hanging:'false',waterlogged:'false'});flags.push([x,104,z]);}
 const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures/t199.nbt')))),trees=[];
 for(const [cx,cz,turn]of [[146,190,0],[174,190,1],[146,225,2],[174,225,3]]){for(const b of asset.blocks){let[x,y,z]=b.pos;for(let n=0;n<turn;n++)[x,z]=[8-z,x];const d=asset.palette[b.state],properties=d.Properties?{...d.Properties}:undefined;if(properties?.axis&&turn%2&&properties.axis!=='y')properties.axis=properties.axis==='x'?'z':'x';const wx=cx-4+x,wz=cz-4+z,wy=104+y;if(get(wx,wy,wz).Name!=='minecraft:air')throw Error('Tree collision');put(wx,wy,wz,d.Name.slice(10),properties);}trees.push({asset:'t199',center:[cx,104,cz],rotation:turn*90});}
 return {data,original,changes:[...changes.values()],details:{axis:[160,150,160,301],mainWidth:13,mainLength:152,fountainWidening:{z:[242,278],maxWidth:23,shape:'tapered curved widening'},profile,lamps,benches,beds,flags,trees,fountain:oldReport.details.fountain,sideRoads:[[112,257,143,263],[177,257,208,263]]}};
}
function run(){
 const reportFile=path.join(MAP,'avenue-shape-report.json');if(fs.existsSync(reportFile))throw Error('Avenue edit already applied; inspect before repeating');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),base=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-square-details')).load(path.basename(MAP)),oldReport=JSON.parse(fs.readFileSync(path.join(MAP,'square-details-report.json'))),r=edit(m,base,oldReport),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,r.data,r.original,fs.readFileSync(file));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-avenue-shape/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.description='ถนนแนวยาว 152 บล็อก · ช่วงขยายโค้งรอบน้ำพุ · ทางแยกซ้ายขวา';Object.assign(manifest.streetsSquare,{bounds:[112,150,208,301],squareBounds:[149,242,171,278],status:'Long avenue with tapered fountain widening; surrounding buildings pending',details:r.details});for(const room of manifest.rooms||[])if(room.id==='03-streets-square')Object.assign(room,manifest.streetsSquare);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));for(const zone of zones)if(zone.id==='03-streets-square')Object.assign(zone,manifest.streetsSquare);fs.writeFileSync(zf,JSON.stringify(zones,null,2));const report={changedChunks:changed.changed,scheduledVoxels:r.changes.length,backup:path.relative(ROOT,backup),details:r.details,changes:r.changes};fs.writeFileSync(reportFile,JSON.stringify(report,null,2));console.log(JSON.stringify({changedChunks:changed.changed,scheduledVoxels:r.changes.length,axis:r.details.axis,maxWidth:23}));
}
module.exports={edit,roadLevel,halfWidth};if(require.main===module)run();
