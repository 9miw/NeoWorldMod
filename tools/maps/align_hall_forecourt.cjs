// Translate the saved hall north, preserving its geometry, and align its monument to the door.
const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),DZ=-12;
const key=d=>JSON.stringify([d.Name,Object.entries(d.Properties||{}).sort()]);
function edit(m,base,courtReport){
 const b=Buffer.from(m.blocks,'base64'),original=new Uint16Array(b.length/2);for(let i=0;i<original.length;i++)original[i]=b.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),bb=Buffer.from(base.blocks,'base64');
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[data[index(x,y,z)]],old=(x,y,z)=>m.definitions[original[index(x,y,z)]];
 function putState(x,y,z,d){if(x<196||x>287||z<145||z>265||y<96||y>136)throw Error('Outside alignment edit');let k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[index(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const put=(x,y,z,name,properties)=>putState(x,y,z,{Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});
 const monument=[];for(let z=230;z<=234;z++)for(let x=252;x<=256;x++)for(let y=108;y<=116;y++)monument.push({x,y,z,state:old(x,y,z)});
 for(const c of courtReport.changes){if(key(get(c.x,c.y,c.z))!==key(c.state))throw Error('Court changed since last edit');const i=((c.y-base.low)*base.size*base.size+c.z*base.size+c.x)*2;putState(c.x,c.y,c.z,base.definitions[bb.readUInt16LE(i)]);}
 // Snapshot union of actual building volumes, including interior air, before removing it.
 const cells=new Map();function capture(x1,y1,z1,x2,y2,z2){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)cells.set([x,y,z].join(','),{x,y,z,state:get(x,y,z)});}
 capture(221,108,177,267,136,211);capture(219,119,175,269,136,213);capture(237,108,212,251,124,217);capture(240,104,215,248,110,220);capture(232,111,214,234,119,214);capture(256,111,214,258,119,214);
 const backLamps=[];for(const x of [226,262])for(let y=108;y<=112;y++)backLamps.push({x,y,z:175,state:get(x,y,175)});
 for(const c of cells.values())put(c.x,c.y,c.z,c.y<107?'stone':c.y===107?'stone_bricks':'air');
 for(let z=177;z<=211;z++)for(let x=221;x<=267;x++)put(x,107,z,'grass_block',{snowy:'false'});
 for(const c of backLamps)put(c.x,c.y,c.z,'air');
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone']);
 for(let z=145;z<=180;z++)for(let x=196;x<=282;x++){if(get(x,96,z).Name==='minecraft:air')continue;let top=96,built=false;for(let y=96;y<m.high;y++){let n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;let distance=Math.max(221-x,0,x-267)+Math.max(161-z,0,z-177),target=Math.max(top,107-Math.ceil(distance/2));if(target<=top)continue;for(let y=96;y<=target;y++)put(x,y,z,y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);}
 for(const c of cells.values()){const d=get(c.x,c.y,c.z+DZ);if(c.state.Name!=='minecraft:air'&&!['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:stone_bricks'].includes(d.Name))throw Error('Hall destination obstruction '+[c.x,c.y,c.z+DZ]);putState(c.x,c.y,c.z+DZ,c.state);}
 for(const c of backLamps)putState(c.x,c.y,c.z+DZ,c.state);
 // Continuous upper forecourt from the relocated entrance stair to the seating circle.
 for(let z=209;z<=237;z++)for(let x=228;x<=260;x++){
  const r=((x-244)/16)**2+((z-224)/14)**2;if(r>1)continue;let occupied=false;for(let y=108;y<=116;y++)if(get(x,y,z).Name!=='minecraft:air'){occupied=true;break;}if(occupied)continue;
  for(let y=97;y<107;y++)put(x,y,z,y>=105?'dirt':'stone');put(x,107,z,r>.86?'polished_andesite':Math.abs(x-244)<=2?'smooth_stone':'stone_bricks');
 }
 for(let x=240;x<=248;x++)put(x,107,209,Math.abs(x-244)<=2?'smooth_stone':'stone_bricks');
 // Remove the two old straight seating strips; replace with symmetric side/south seating.
 for(const x0 of [232,252])for(let x=x0;x<x0+5;x++)put(x,108,227,'air');
 for(const c of monument)if(c.state.Name!=='minecraft:air')putState(c.x-10,c.y,c.z-8,c.state);
 const seats=[];for(const [x,facing]of [[232,'west'],[256,'east']]){for(let z=219;z<=229;z++)put(x,108,z,'spruce_stairs',{facing,half:'bottom',shape:'straight',waterlogged:'false'});seats.push({bounds:[x,219,x,229],faces:'monument'});}for(let x=238;x<=250;x++)put(x,108,234,'spruce_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});seats.push({bounds:[238,234,250,234],faces:'monument'});
 return {data,original,changes:[...changes.values()],buildingVoxels:cells.size,court:{center:[244,108,224],radius:[16,14],floorY:107,pillar:[244,108,224,244,116,224],seats,alignment:'same X244 axis as south entrance',entranceApproach:'open north side; bypass monument on west/east'}};
}
function run(){const rf=path.join(MAP,'hall-alignment-report.json');if(fs.existsSync(rf))throw Error('Alignment already applied');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),base=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-hall-seating')).load(path.basename(MAP)),courtReport=JSON.parse(fs.readFileSync(path.join(MAP,'hall-seating-report.json'))),r=edit(m,base,courtReport),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,r.data,r.original,fs.readFileSync(file));const backup=path.join(ROOT,'archive/map-edit-backups/before-hall-alignment/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),h=manifest.adventurersHall;h.buildingBounds[2]+=DZ;h.buildingBounds[5]+=DZ;h.bounds[1]=161;h.bounds[3]=238;h.center[2]+=DZ;h.entrance[2]+=DZ;h.interiorBounds[2]+=DZ;h.interiorBounds[5]+=DZ;h.counters=h.counters.map(c=>({...c,servicePoint:[c.servicePoint[0],c.servicePoint[1],c.servicePoint[2]+DZ],bounds:c.bounds.map((v,i)=>i===2||i===5?v+DZ:v)}));h.lamps=h.lamps.map(p=>p[2]===175?[p[0],p[1],p[2]+DZ]:p);h.seatingCourt=r.court;manifest.rooms=manifest.rooms.filter(room=>room.id!=='06-adventurers-hall'&&!['Dungeon','Party','PvP'].includes(room.name));manifest.rooms.push(h,...h.counters.map(c=>({name:c.name,center:c.servicePoint,bounds:c.bounds})));fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf)).filter(z=>z.id!=='06-adventurers-hall');zones.push(h);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({shiftZ:DZ,buildingVoxels:r.buildingVoxels,changedChunks:changed.changed,backup:path.relative(ROOT,backup),hall:h,changes:r.changes},null,2));console.log(JSON.stringify({shiftZ:DZ,changedChunks:changed.changed,entrance:h.entrance,court:r.court}));}
if(require.main===module)run();
