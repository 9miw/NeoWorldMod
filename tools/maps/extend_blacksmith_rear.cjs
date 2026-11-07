const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'blacksmith-rear-extension-report.json');if(fs.existsSync(rf))throw Error('Already extended');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function put(x,y,z,n,p){if(x<55||x>107||z<99||z>122||y<96||y>126)throw Error('Outside rear extension');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
// Prepare a supported floor and soften its natural shoulders without touching the front court.
for(let z=100;z<=121;z++)for(let x=55;x<=107;x++){
 const dist=Math.hypot(Math.max(65-x,0,x-97),Math.max(111-z,0,z-121));if(dist>10)continue;
 let top=96,built=false;for(let y=96;y<m.high;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
 if(m.definitions[old[ix(x,96,z)]].Name==='minecraft:air')continue;const t=Math.min(1,dist/10),e=t*t*(3-2*t),target=Math.round(107+(top-107)*e);
 for(let y=Math.min(top,target)-2;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
// Extend the room by exactly ten rows north; the old rear facade becomes an internal partition.
for(let x=66;x<=96;x++)for(let z=112;z<=121;z++){
 put(x,107,z,(x+z)%8===0?'polished_andesite':'stone_bricks');
 for(let y=108;y<=115;y++){
  let n='air';if(x===66||x===96){const post=z===112||z%6===0;n=y<=109?'stone_bricks':post||y===115?'dark_oak_log':'calcite';
   if(z>=115&&z<=119&&y>=110&&y<=113)n=z===115||z===119||y===110||y===113?'spruce_planks':'light_blue_stained_glass';
  }put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);
 }
}
// Copy the original framed rear facade to its new location, preserving its exact design.
for(let x=66;x<=96;x++)for(let y=108;y<=124;y++){data[ix(x,y,112)]=old[ix(x,y,122)];planned.add(ix(x,y,112));}
// Remove the obsolete rear roof cap and gable, then carry the pitched roof continuously north.
for(let x=65;x<=97;x++)for(let z=111;z<=122;z++)for(let y=116;y<=126;y++)put(x,y,z,'air');
for(let x=66;x<=96;x++)for(let y=116;y<=124;y++){data[ix(x,y,112)]=old[ix(x,y,122)];planned.add(ix(x,y,112));}
for(let x=65;x<=97;x++)for(let z=111;z<=122;z++){
 const h=116+Math.floor(Math.max(0,16-Math.abs(x-81))*.6),edge=z===111;
 put(x,h,z,edge?'dark_oak_planks':'spruce_planks');
 if(!edge&&x!==81)put(x,h+1,z,'spruce_stairs',{facing:x<81?'east':'west',half:'bottom',shape:'straight',waterlogged:'false'});
 if(x===81)put(x,h+1,z,'dark_oak_slab',{type:'bottom',waterlogged:'false'});
}
for(let x=76;x<=82;x++)for(let y=108;y<=111;y++)put(x,y,122,'air');
for(const x of [75,83])for(let y=108;y<=112;y++)put(x,y,122,'dark_oak_log',{axis:'y'});
for(let x=75;x<=83;x++)put(x,112,122,'dark_oak_log',{axis:'x'});
// Extra work surfaces and materials flank the central clear aisle.
for(let z=115;z<=119;z++){put(69,108,z,'spruce_planks');put(93,108,z,'spruce_wood',{axis:'y'});}
put(70,108,117,'smithing_table');put(91,108,117,'anvil',{facing:'north'});
for(const [x,z]of [[74,115],[88,115]]){put(x,113,z,'dark_oak_planks');put(x,112,z,'lantern',{hanging:'true',waterlogged:'false'});}
// Verify a two-block-clear connection from the existing main room to the extension.
const seen=new Set(),queue=[[80,131]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<66||x>96||z<112||z>134||seen.has(key(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(107)==='minecraft:air'||n(108)!=='minecraft:air'||n(109)!=='minecraft:air')continue;seen.add(key(x,z));queue.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
for(const [x,z]of [[80,116],[71,117],[90,117]])if(!seen.has(key(x,z)))throw Error('Rear room inaccessible');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-blacksmith-rear-extension/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),smith=manifest.blacksmith;smith.buildingBounds=[66,112,96,146];smith.buildingSize=[31,35];smith.bounds[1]=111;smith.rearExtension={length:10,bounds:[66,112,96,121],floorY:107,clearAisle:[76,82],connection:[79,108,122]};smith.stations.push({type:'smithing_table',at:[70,108,117]},{type:'anvil',at:[91,108,117]});manifest.rooms=manifest.rooms.map(r=>r.id===smith.id?smith:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===smith.id?{...z,...smith}:z),null,2));fs.writeFileSync(rf,JSON.stringify({extension:smith.rearExtension,changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,buildingSize:smith.buildingSize});
