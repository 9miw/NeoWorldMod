const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'blacksmith-rear-court-report.json');if(fs.existsSync(rf))throw Error('Rear court already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function put(x,y,z,n,p){if(x<54||x>108||z<96||z>122||y<96||y>111)throw Error('Outside rear court');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const pads=new Set();for(let z=104;z<=111;z++)for(let x=62;x<=100;x++){if(Math.hypot(Math.max(65-x,0,x-97),Math.max(107-z,0))<=3)pads.add(z*S+x);}
for(let z=112;z<=121;z++)for(const x of [62,63,64,65,97,98,99,100])pads.add(z*S+x);
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);let landscape=0;
for(let z=96;z<=122;z++)for(let x=54;x<=108;x++){
 const k=z*S+x;let distance=pads.has(k)?0:9;if(distance)for(const j of pads)distance=Math.min(distance,Math.hypot(x-j%S,z-Math.floor(j/S)));if(distance>8)continue;
 let top=96,built=false;for(let y=96;y<m.high;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
 if(m.definitions[old[ix(x,96,z)]].Name==='minecraft:air')continue;const t=distance/8,e=t*t*(3-2*t),target=Math.round(107+(top-107)*e);if(top!==target)landscape++;
 for(let y=Math.min(top,target)-2;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
for(const k of pads){const x=k%S,z=Math.floor(k/S);
 const floor=m.definitions[data[ix(x,107,z)]].Name;if(!natural.has(floor)&&!['minecraft:stone_bricks','minecraft:polished_andesite','minecraft:andesite'].includes(floor))throw Error('Protected floor');
 for(let y=96;y<107;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:air')put(x,y,z,y>=105?'dirt':'stone');
 for(let y=108;y<=109;y++){const n=m.definitions[data[ix(x,y,z)]].Name;if(n!=='minecraft:air'){if(!natural.has(n))throw Error('Court headroom obstructed');put(x,y,z,'air');}}
 const edge=![k-1,k+1,k-S,k+S].every(j=>pads.has(j));put(x,107,z,edge?'polished_andesite':(x+z)%9===0?'andesite':'stone_bricks');
}
// Rear court connects around the building to the existing working court, with two-block headroom.
const seen=new Set(),queue=[[100,130]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<61||x>115||z<103||z>151||seen.has(key(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(107)==='minecraft:air'||n(108)!=='minecraft:air'||n(109)!=='minecraft:air')continue;seen.add(key(x,z));queue.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
for(const [x,z]of [[81,107],[64,115],[99,115]])if(!seen.has(key(x,z)))throw Error('Rear court disconnected');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-blacksmith-rear-court/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),smith=manifest.blacksmith;smith.rearCourt={bounds:[62,104,100,111],floorY:107,depth:8,sidePaths:[[62,112,65,121],[97,112,100,121]],pavedCells:pads.size};smith.bounds[1]=104;manifest.rooms=manifest.rooms.map(r=>r.id===smith.id?smith:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===smith.id?{...z,...smith}:z),null,2));fs.writeFileSync(rf,JSON.stringify({court:smith.rearCourt,changedBlocks:count,changedChunks:result.changed,landscapeColumns:landscape,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,pavedCells:pads.size,landscapeColumns:landscape});
