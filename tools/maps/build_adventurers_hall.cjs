// Zone 06, local saved-world architecture. No NPCs or gameplay commands are installed.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
function edit(m){
 const bytes=Buffer.from(m.blocks,'base64'),original=new Uint16Array(bytes.length/2);for(let i=0;i<original.length;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[data[index(x,y,z)]];
 function put(x,y,z,name,properties){if(x<206||x>276||z<181||z>249||y<96||y>=128)throw Error('Outside hall edit bounds');const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[index(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 function fill(x1,y1,z1,x2,y2,z2,name,properties){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,name,properties);}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
 // A contiguous landscaped site, blended at its edge; do not expand island footprint.
 for(let z=181;z<=249;z++)for(let x=206;x<=276;x++){
  if(get(x,96,z).Name==='minecraft:air')continue;let top=96,built=false;for(let y=96;y<m.high;y++){let n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
  const dist=Math.max(216-x,0,x-264)+Math.max(191-z,0,z-239),target=dist===0?103:Math.max(top,103-Math.ceil(dist/3));if(top>105&&dist>0)continue;
  for(let y=96;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 function pave(x1,z1,x2,z2){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){for(let y=97;y<103;y++)put(x,y,z,'stone');put(x,103,z,x===x1||x===x2||z===z1||z===z2?'polished_andesite':Math.abs(z-215)<=1?'smooth_stone':'stone_bricks');}}
 pave(208,212,235,218);pave(216,204,231,226);pave(226,226,232,235);pave(230,233,264,237);
 // 25 X by 31 Z footprint, facing west; raised two blocks above the approach.
 fill(236,102,199,260,104,229,'stone_bricks');fill(236,105,199,260,105,229,'dark_oak_planks');
 for(let y=106;y<=114;y++)for(let z=199;z<=229;z++)for(let x=236;x<=260;x++)if(x===236||x===260||z===199||z===229)put(x,y,z,y<108?'stone_bricks':y===114?'dark_oak_log':'calcite',y===114?{axis:'x'}:undefined);
 for(const x of [236,242,248,254,260])for(const z of [199,229])fill(x,106,z,x,114,z,'dark_oak_log',{axis:'y'});
 for(const z of [199,205,214,223,229])for(const x of [236,260])fill(x,106,z,x,114,z,'dark_oak_log',{axis:'y'});
 for(const z of [199,229])for(const x of [239,250]){fill(x,108,z,x+4,111,z,'purple_stained_glass');fill(x-1,107,z,x+5,107,z,'polished_andesite');}
 // Main purple gable, with a capped ridge and pale stone border on its front/back.
 for(let z=197;z<=231;z++){const h=115+Math.floor((16-Math.abs(z-214))*.65);for(const x of [236,260])if(h>=115)fill(x,115,z,x,h,z,'calcite');for(let x=234;x<=262;x++){put(x,h,z,x===234||x===262?'polished_andesite':'purple_concrete');put(x,h+1,z,x===234||x===262?'stone_brick_slab':'purpur_slab',{type:'bottom',waterlogged:'false'});}}
 fill(234,126,214,262,126,214,'purpur_pillar',{axis:'x'});
 // Front facade: tall central window, doorway, buttresses, porch and entrance stairs.
 fill(236,115,212,236,119,216,'purple_stained_glass');fill(236,120,213,236,120,215,'chiseled_stone_bricks');
 fill(236,106,212,236,110,216,'air');
 for(const z of [201,205,223,227]){fill(235,104,z,235,110,z,'stone_bricks');put(235,111,z,'stone_brick_slab',{type:'bottom',waterlogged:'false'});}
 fill(232,104,209,235,105,219,'stone_bricks');for(const z of [209,219]){fill(232,106,z,232,112,z,'dark_oak_log',{axis:'y'});put(232,113,z,'chiseled_stone_bricks');}
 fill(232,113,209,236,113,219,'dark_oak_planks');for(let z=208;z<=220;z++){const h=114+Math.floor((6-Math.abs(z-214))*.8);for(let x=231;x<=237;x++)put(x,h,z,x===231?'polished_andesite':'purple_concrete');}
 for(let z=212;z<=216;z++){put(232,105,z,'stone_brick_stairs',{facing:'east',half:'bottom',shape:'straight',waterlogged:'false'});put(231,104,z,'stone_brick_stairs',{facing:'east',half:'bottom',shape:'straight',waterlogged:'false'});put(230,103,z,'smooth_stone');}
 // Keep the real hall empty before furnishing; central aisle is five blocks wide.
 fill(237,106,200,259,113,228,'air');
 const counters=[];for(const [z,name]of [[204,'Dungeon'],[214,'Party'],[224,'PvP']]){fill(254,106,z-2,256,106,z+2,'spruce_planks');fill(254,107,z-2,254,107,z+2,'spruce_slab',{type:'bottom',waterlogged:'false'});put(256,107,z,'lantern',{hanging:'false',waterlogged:'false'});counters.push({name,servicePoint:[252,106,z],bounds:[254,106,z-2,256,107,z+2],connected:false});}
 for(const z of [202,208,220,226]){fill(259,106,z,259,109,z+1,'bookshelf');put(258,110,z,'lantern',{hanging:'true',waterlogged:'false'});}
 // Announcement board is physical scenery, no embedded sign commands or entities.
 fill(240,107,200,247,109,200,'dark_oak_planks');fill(241,108,200,246,108,200,'birch_planks');
 for(let z=218;z<=222;z++)put(239,106,z,'spruce_stairs',{facing:'west',half:'bottom',shape:'straight',waterlogged:'false'});
 fill(242,106,225,247,106,225,'spruce_planks');for(const x of [242,245,247])put(x,107,225,'flower_pot');
 for(const x of [241,248])for(const z of [204,214,224])put(x,113,z,'lantern',{hanging:'true',waterlogged:'false'});
 // Purple heraldry on the entrance pillars leaves a five-block passage.
 for(const z of [204,224]){fill(233,106,z,233,112,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});fill(233,109,z+1,233,112,z+2,'purple_wool');fill(233,110,z+1,233,111,z+1,'white_wool');put(233,113,z,'lantern',{hanging:'false',waterlogged:'false'});}
 const lamps=[];for(const [x,z]of [[216,207],[216,223],[229,198],[229,232],[262,232]]){put(x,104,z,'chiseled_stone_bricks');fill(x,105,z,x,107,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,108,z,'lantern',{hanging:'false',waterlogged:'false'});lamps.push([x,104,z]);}
 for(const z of [202,228])for(let x=218;x<=223;x++)put(x,104,z,'spruce_stairs',{facing:z===202?'north':'south',half:'bottom',shape:'straight',waterlogged:'false'});
 for(const z of [196,230])for(let x=227;x<=231;x++)for(let zz=z;zz<=z+2;zz++){const edge=x===227||x===231||zz===z||zz===z+2;put(x,103,zz,edge?'stone_bricks':'dirt');put(x,104,zz,edge?'stone_brick_slab':x%2?'allium':'azure_bluet',edge?{type:'bottom',waterlogged:'false'}:undefined);}
 const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures/t199.nbt')))),trees=[];for(const [cx,cz]of [[221,196],[221,234],[260,194]]){for(const b of asset.blocks){const [x,y,z]=b.pos,wx=cx-4+x,wy=104+y,wz=cz-4+z,d=asset.palette[b.state];if(get(wx,wy,wz).Name!=='minecraft:air')throw Error('Hall tree collision '+[wx,wy,wz]);put(wx,wy,wz,d.Name.slice(10),d.Properties);}trees.push({asset:'t199',base:[cx,104,cz]});}
 return {data,original,changes:[...changes.values()],zone:{id:'06-adventurers-hall',name:'Adventurers Hall',bounds:[216,191,264,239],buildingBounds:[236,105,199,260,126,229],center:[248,106,214],entrance:[236,106,214],front:'west toward fountain',buildingSize:[25,31],floorY:105,courtyardFloorY:103,interiorBounds:[237,106,200,259,113,228],interiorFloorArea:667,aisleWidth:5,counters,lamps,trees,portalConnection:[264,104,235],gameplayConnected:false}};
}
function run(){const rf=path.join(MAP,'adventurers-hall-report.json');if(fs.existsSync(rf))throw Error('Hall already created; edit in place instead');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),r=edit(m),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,r.data,r.original,fs.readFileSync(file));const backup=path.join(ROOT,'archive/map-edit-backups/before-adventurers-hall/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.rooms.push(r.zone,...r.zone.counters.map(c=>({name:c.name,center:c.servicePoint,bounds:c.bounds})));manifest.adventurersHall=r.zone;manifest.description='ถนนยาว ลานน้ำพุ และโถงนักผจญภัยหลังคาม่วงฝั่งขวา';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));zones.push(r.zone);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({changedChunks:changed.changed,scheduledVoxels:r.changes.length,backup:path.relative(ROOT,backup),zone:r.zone,changes:r.changes},null,2));console.log(JSON.stringify({changedChunks:changed.changed,scheduledVoxels:r.changes.length,zone:r.zone}));}
module.exports={edit};if(require.main===module)run();
