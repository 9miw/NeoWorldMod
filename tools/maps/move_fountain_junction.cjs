// Move the complete fountain and its avenue widening/junction north; retain the saved city.
const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),DELTA=-45;
const key=d=>JSON.stringify([d.Name,Object.entries(d.Properties||{}).sort()]);
const half=(z,center)=>Math.abs(z-center)<=18?6+Math.round(5*Math.cos(Math.PI*(z-center)/36)**2):6;
function edit(m,details){
 const b=Buffer.from(m.blocks,'base64'),original=new Uint16Array(b.length/2);for(let i=0;i<original.length;i++)original[i]=b.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[data[index(x,y,z)]],old=(x,y,z)=>m.definitions[original[index(x,y,z)]];
 function putState(x,y,z,d){if(x<96||x>224||z<192||z>278||y<96||y>110)throw Error('Outside fountain relocation');let k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[index(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const put=(x,y,z,name,properties)=>putState(x,y,z,{Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});
 const fountain=[];for(let z=255;z<=265;z++)for(let x=155;x<=165;x++)for(let y=103;y<=110;y++)fountain.push({x,y,z,state:old(x,y,z)});
 // Shift only the lamps adjacent to the moved junction; all others stay in place.
 const movedLamps=[];for(const [x,y,z]of details.lamps){let nx=x,nz=z;if(z===215&&(x===151||x===169)){nx=x<160?146:174;nz=210;}else if(z===255&&(x===123||x===197))nz=210;else continue;
  for(let yy=y+1;yy<=y+5;yy++){const d=old(x,yy,z);if(d.Name==='minecraft:air')throw Error('Missing lamp');if(old(nx,yy,nz).Name!=='minecraft:air')throw Error('Lamp destination occupied');put(x,yy,z,'air');putState(nx,yy,nz,d);}put(x,y,z,'grass_block',{snowy:'false'});for(let yy=97;yy<y;yy++)put(nx,yy,nz,yy>=y-2?'dirt':'stone');put(nx,y,nz,'polished_andesite');movedLamps.push({from:[x,y,z],to:[nx,y,nz]});
 }
 // Blend the new cross street into land rather than leaving an elevated strip.
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
 for(let z=192;z<=238;z++)for(let x=96;x<=224;x++){
  if(get(x,96,z).Name==='minecraft:air')continue;let top=96,built=false;for(let y=96;y<m.high;y++){const n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built||top>103)continue;const distance=Math.max(112-x,0,x-208)+Math.max(212-z,0,z-218),target=Math.max(top,103-Math.ceil(distance/3));if(target<=top)continue;for(let y=96;y<=target;y++)put(x,y,z,y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 // Remove the original square-like widening and old cross street paving, preserving fixtures.
 const fixtures=(x,z)=>details.benches.some(([cx,y,cz])=>Math.abs(x-cx)<=1&&z>=cz-1&&z<=cz+4)||details.beds.some(([cx,cz])=>x>=cx&&x<cx+3&&z>=cz&&z<cz+3)||details.lamps.some(([cx,y,cz])=>cx===x&&cz===z);
 for(let z=242;z<=278;z++)for(let x=149;x<=171;x++)if(Math.abs(x-160)>6&&Math.abs(x-160)<=half(z,260)&&!fixtures(x,z))put(x,103,z,'grass_block',{snowy:'false'});
 for(let z=257;z<=263;z++)for(let x=112;x<=208;x++)if(x<154||x>166)put(x,103,z,'grass_block',{snowy:'false'});
 for(const f of fountain)if(f.y>103)put(f.x,f.y,f.z,'air');
 // Repave the continuous original centerline and the new tapered widening.
 for(let z=197;z<=278;z++)for(let x=160-half(z,215);x<=160+half(z,215);x++){
  if(z>=210&&z<=220&&x>=155&&x<=165)continue;const edge=Math.abs(x-160)===half(z,215);for(let y=97;y<103;y++)put(x,y,z,'stone');put(x,103,z,edge?'polished_andesite':Math.abs(x-160)<=1?'smooth_stone':'stone_bricks');
 }
 for(let z=212;z<=218;z++)for(let x=112;x<=208;x++){
  if(x>=149&&x<=171)continue;for(let y=97;y<103;y++)put(x,y,z,'stone');put(x,103,z,z===212||z===218?'polished_andesite':Math.abs(z-215)<=1?'smooth_stone':'stone_bricks');
 }
 for(const f of fountain){if(f.y>103&&get(f.x,f.y,f.z+DELTA).Name!=='minecraft:air')throw Error('Fountain destination occupied');putState(f.x,f.y,f.z+DELTA,f.state);}
 return {data,original,changes:[...changes.values()],fountain,movedLamps};
}
function run(){const rf=path.join(MAP,'fountain-relocation-report.json');if(fs.existsSync(rf))throw Error('Relocation already applied');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),r=edit(m,manifest.streetsSquare.details),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,r.data,r.original,fs.readFileSync(file));const backup=path.join(ROOT,'archive/map-edit-backups/before-fountain-relocation/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const d=manifest.streetsSquare.details;d.fountain={...d.fountain,center:[160,104,215],bounds:[155,103,210,165,110,220]};d.fountainWidening={z:[197,233],maxWidth:23,shape:'tapered curved widening'};d.sideRoads=[[112,212,148,218],[172,212,208,218]];d.lamps=d.lamps.map(p=>r.movedLamps.find(l=>l.from.join(',')===p.join(','))?.to||p);d.profile=d.profile.map(p=>({...p,width:half(p.z,215)*2+1}));manifest.streetsSquare.squareBounds=[149,197,171,233];manifest.description='น้ำพุและทางแยกย้ายขึ้นไปด้านหน้า · ถนนยาวเชื่อมจุดเกิด';for(const room of manifest.rooms||[]){if(room.id==='03-streets-square')Object.assign(room,manifest.streetsSquare);if(room.name==='Fountain')Object.assign(room,{center:[160,104,215],bounds:[155,210,165,220]});}fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));for(const zone of zones)if(zone.id==='03-streets-square')Object.assign(zone,manifest.streetsSquare);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({from:[160,104,260],to:[160,104,215],deltaZ:DELTA,changedChunks:changed.changed,scheduledVoxels:r.changes.length,backup:path.relative(ROOT,backup),movedLamps:r.movedLamps,changes:r.changes},null,2));console.log(JSON.stringify({fromZ:260,toZ:215,changedChunks:changed.changed}));}
module.exports={edit};if(require.main===module)run();
