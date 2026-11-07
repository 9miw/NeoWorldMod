// Replace the owned draft with a larger south-facing hall on a raised landscaped site.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const key=d=>JSON.stringify([d.Name,Object.entries(d.Properties||{}).sort()]);
function edit(m,base,oldReport){
 const bytes=Buffer.from(m.blocks,'base64');m.high=Math.max(m.high,144);const original=new Uint16Array(m.size*m.size*(m.high-m.low));for(let i=0;i<bytes.length/2;i++)original[i]=bytes.readUInt16LE(i*2);const data=original.slice(),changes=new Map(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),baseBytes=Buffer.from(base.blocks,'base64');
 const index=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,get=(x,y,z)=>m.definitions[data[index(x,y,z)]];
 function putState(x,y,z,d){if(x<179||x>309||z<140||z>262||y<96||y>136)throw Error('Outside approved hall replacement');let k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[index(x,y,z)]=ids.get(k);changes.set([x,y,z].join(','),{x,y,z,state:d});}
 const put=(x,y,z,name,properties)=>putState(x,y,z,{Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});
 function fill(x1,y1,z1,x2,y2,z2,name,properties){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,name,properties);}
 // Restore only the previously owned voxels, refusing to overwrite intervening edits.
 for(const c of oldReport.changes){if(key(get(c.x,c.y,c.z))!==key(c.state))throw Error('Modified draft voxel; review '+[c.x,c.y,c.z]);const i=((c.y-base.low)*base.size*base.size+c.z*base.size+c.x)*2;putState(c.x,c.y,c.z,base.definitions[baseBytes.readUInt16LE(i)]);}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
 for(let z=140;z<=262;z++)for(let x=179;x<=309;x++){
  if(get(x,96,z).Name==='minecraft:air')continue;let top=96,built=false;for(let y=96;y<m.high;y++){const n=get(x,y,z).Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
  const distance=Math.max(212-x,0,x-276)+Math.max(173-z,0,z-229),target=distance===0?107:Math.max(top,107-Math.ceil(distance/3));if(target===top)continue;
  for(let y=96;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 function pave(x1,z1,x2,z2,y=107){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){for(let yy=97;yy<y;yy++)put(x,yy,z,'stone');put(x,y,z,x===x1||x===x2||z===z1||z===z2?'polished_andesite':x>=242&&x<=246?'smooth_stone':'stone_bricks');}}
 pave(216,212,233,220);pave(229,218,259,229);pave(259,217,276,219);
 // Four east-facing rises link the Y103 cross street to the Y107 courtyard.
 for(let x=209;x<=215;x++)for(let z=212;z<=218;z++){const n=x-209,y=104+Math.floor(n/2);fill(x,97,z,x,y-1,z,'stone');put(x,y,z,n%2===0?'stone_brick_stairs':'stone_bricks',n%2===0?{facing:'east',half:'bottom',shape:'straight',waterlogged:'false'}:undefined);for(let yy=y+1;yy<=109;yy++)put(x,yy,z,'air');}
 // 47 X by 35 Z, floor Y110. Main high nave and two lower purple roof wings.
 fill(221,104,177,267,109,211,'stone_bricks');fill(221,110,177,267,110,211,'dark_oak_planks');
 const roof=x=>x>=229&&x<=259?122+Math.floor((15-Math.abs(x-244))*.8):119+Math.floor((6-Math.abs(x-(x<244?225:263)))*.6);
 for(let z=177;z<=211;z++)for(let x=221;x<=267;x++)if(x===221||x===267||z===177||z===211){const h=Math.min(121,roof(x)-1);for(let y=111;y<=h;y++)put(x,y,z,y<113?'stone_bricks':y===h?'dark_oak_log':'calcite',y===h?{axis:'x'}:undefined);}
 for(const z of [177,183,190,197,204,211])for(const x of [221,267])fill(x,111,z,x,119,z,'dark_oak_log',{axis:'y'});
 for(const x of [221,229,237,244,251,259,267])for(const z of [177,211])fill(x,111,z,x,Math.min(121,roof(x)-1),z,'dark_oak_log',{axis:'y'});
 for(const z of [177,211])for(let x=221;x<=267;x++)for(let y=Math.min(121,roof(x)-1)+1;y<roof(x);y++)put(x,y,z,'calcite');
 for(const x of [221,267])for(const z of [181,192,203])fill(x,113,z,x,117,z+4,'purple_stained_glass');
 for(const z of [177,211])for(const x of [223,260])fill(x,113,z,x+4,117,z,'purple_stained_glass');
 for(let z=175;z<=213;z++)for(let x=219;x<=269;x++){const y=roof(x);put(x,y,z,z===175||z===213?'polished_andesite':'purple_concrete');put(x,y+1,z,z===175||z===213?'stone_brick_slab':'purpur_slab',{type:'bottom',waterlogged:'false'});}
 fill(244,135,175,244,135,213,'purpur_pillar',{axis:'z'});for(const z of [175,213])put(244,136,z,'chiseled_stone_bricks');
 fill(241,122,211,247,128,211,'purple_stained_glass');fill(243,129,211,245,130,211,'chiseled_stone_bricks');
 // South entrance, seven clear blocks wide, below a projecting porch gable.
 fill(241,111,211,247,116,211,'air');fill(238,108,212,250,110,215,'stone_bricks');
 for(const x of [238,250]){fill(x,111,215,x,118,215,'dark_oak_log',{axis:'y'});put(x,119,215,'chiseled_stone_bricks');}
 for(let z=212;z<=217;z++)for(let x=237;x<=251;x++){const h=119+Math.floor((7-Math.abs(x-244))*.75);put(x,h,z,z===217?'polished_andesite':'purple_concrete');}
 for(let z=215;z<=220;z++)for(let x=240;x<=248;x++){const n=220-z,y=108+Math.floor(n/2);fill(x,104,z,x,y-1,z,'stone_bricks');put(x,y,z,n%2===0?'stone_brick_stairs':'stone_bricks',n%2===0?{facing:'north',half:'bottom',shape:'straight',waterlogged:'false'}:undefined);}
 // Real open interior, with three north-wall counters and generous circulation.
 fill(222,111,178,266,118,210,'air');const counters=[];
 for(const [x,name]of [[229,'Dungeon'],[244,'Party'],[259,'PvP']]){fill(x-4,111,181,x+4,111,183,'spruce_planks');fill(x-4,112,183,x+4,112,183,'spruce_slab',{type:'bottom',waterlogged:'false'});put(x,112,182,'lantern',{hanging:'false',waterlogged:'false'});counters.push({name,servicePoint:[x,111,185],bounds:[x-4,111,181,x+4,112,183],connected:false});}
 for(const x of [225,236,251,262]){fill(x,111,178,x+2,115,178,'bookshelf');put(x+1,116,179,'lantern',{hanging:'true',waterlogged:'false'});}
 fill(222,113,185,222,116,193,'dark_oak_planks');fill(222,114,186,222,115,192,'birch_planks');
 for(const x of [225,263])for(let z=196;z<=203;z++)put(x,111,z,'spruce_stairs',{facing:x<244?'west':'east',half:'bottom',shape:'straight',waterlogged:'false'});
 for(const x of [230,258]){fill(x-2,111,199,x+2,111,201,'spruce_planks');put(x,112,200,'flower_pot');}
 for(const x of [232,244,256])for(const z of [188,199,207])put(x,119,z,'lantern',{hanging:'true',waterlogged:'false'});
 for(const x of [232,256]){fill(x,111,214,x,118,214,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});fill(x+1,114,214,x+2,117,214,'purple_wool');fill(x+1,115,214,x+1,116,214,'white_wool');put(x,119,214,'lantern',{hanging:'false',waterlogged:'false'});}
 const lamps=[];for(const [x,z]of [[218,213],[218,220],[227,226],[261,226],[270,218],[226,175],[262,175]]){put(x,108,z,'chiseled_stone_bricks');fill(x,109,z,x,111,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,112,z,'lantern',{hanging:'false',waterlogged:'false'});lamps.push([x,108,z]);}
 for(const x of [232,252])for(let dx=0;dx<5;dx++)put(x+dx,108,227,'spruce_stairs',{facing:'south',half:'bottom',shape:'straight',waterlogged:'false'});
 for(const x of [222,262])for(let xx=x;xx<x+5;xx++)for(let z=222;z<=224;z++){const edge=xx===x||xx===x+4||z===222||z===224;put(xx,107,z,edge?'stone_bricks':'dirt');put(xx,108,z,edge?'stone_brick_slab':'allium',edge?{type:'bottom',waterlogged:'false'}:undefined);}
 const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures/t199.nbt')))),trees=[];
 for(const [cx,cz]of [[216,177],[272,177],[216,225],[272,225]]){for(const b of asset.blocks){const[x,y,z]=b.pos,wx=cx-4+x,wy=108+y,wz=cz-4+z,d=asset.palette[b.state];if(get(wx,wy,wz).Name!=='minecraft:air')throw Error('New hall tree collision '+[wx,wy,wz]);put(wx,wy,wz,d.Name.slice(10),d.Properties);}trees.push({asset:'t199',base:[cx,108,cz]});}
 return {data,original,changes:[...changes.values()],zone:{id:'06-adventurers-hall',name:'Adventurers Hall',bounds:[212,173,276,229],buildingBounds:[221,110,177,267,136,211],buildingSize:[47,35],center:[244,111,194],entrance:[244,111,211],front:'south (+Z) toward spawn',floorY:110,courtyardFloorY:107,streetFloorY:103,interiorBounds:[222,111,178,266,118,210],interiorFloorArea:1485,aisleWidth:7,counters,lamps,trees,portalConnection:[276,108,218],gameplayConnected:false}};
}
function run(){const rf=path.join(MAP,'adventurers-hall-revision-report.json');if(fs.existsSync(rf))throw Error('Revision already applied; inspect before repeating');const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),base=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-adventurers-hall')).load(path.basename(MAP)),oldReport=JSON.parse(fs.readFileSync(path.join(MAP,'adventurers-hall-report.json'))),r=edit(m,base,oldReport),file=path.join(MAP,'region/r.0.0.mca'),changed=patch(m,r.data,r.original,fs.readFileSync(file));const backup=path.join(ROOT,'archive/map-edit-backups/before-hall-revision/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',changed.bytes);fs.renameSync(file+'.pending',file);
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.adventurersHall=r.zone;manifest.rooms=manifest.rooms.filter(room=>room.id!=='06-adventurers-hall'&&!['Dungeon','Party','PvP'].includes(room.name));manifest.rooms.push(r.zone,...r.zone.counters.map(c=>({name:c.name,center:c.servicePoint,bounds:c.bounds})));manifest.description='โถงนักผจญภัยขนาดใหญ่ หันใต้ พื้น Y110 เหนือถนน 7 บล็อก';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf)).filter(zone=>zone.id!=='06-adventurers-hall');zones.push(r.zone);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({changedChunks:changed.changed,scheduledVoxels:r.changes.length,backup:path.relative(ROOT,backup),zone:r.zone,changes:r.changes},null,2));console.log(JSON.stringify({changedChunks:changed.changed,scheduledVoxels:r.changes.length,buildingSize:r.zone.buildingSize,front:r.zone.front,floorY:110}));}
module.exports={edit};if(require.main===module)run();
