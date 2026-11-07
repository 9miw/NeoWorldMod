const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
function run(){
 const rf=path.join(MAP,'market-expansion-report.json');if(fs.existsSync(rf))throw Error('Expansion already applied');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);
 const data=old.slice(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
 const get=(x,y,z)=>m.definitions[old[ix(x,y,z)]].Name;
 function put(x,y,z,n,p){if(x<42||x>150||z<172||z>262||y<90||y>120)throw Error('Outside edit');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone']);
 for(let z=172;z<=262;z++)for(let x=42;x<=150;x++){
  if(get(x,90,z)==='minecraft:air')continue;let top=90,built=false;for(let y=90;y<m.high;y++){let n=get(x,y,z);if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
  let dist=Math.hypot(Math.max(60-x,0,x-132),Math.max(188-z,0,z-244));if(dist>18)continue;let t=Math.max(0,1-dist/18),target=Math.round(top+(103-top)*t*t*(3-2*t));
  for(let y=Math.min(top,target)-2;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 const manifestFile=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(manifestFile)),previous=manifest.market;
 for(let z=188;z<=244;z++)for(let x=60;x<=132;x++){
  if(Math.hypot(Math.max(66-x,0,x-126),Math.max(194-z,0,z-238))>6)continue;
  if(get(x,90,z)==='minecraft:air')throw Error('Paving exceeds island');
  const n=get(x,103,z);if(n==='minecraft:air'||n==='minecraft:grass_block'||n==='minecraft:dirt'||n==='minecraft:orange_terracotta')put(x,103,z,'stone_bricks');
 }
 const shop={id:'supplies',bounds:[115,191,131,207],floorY:103,entrance:[123,104,207],roofRidgeY:115,stage:'open timber frame; roof outline'};
 const [x0,z0,x1,z1]=shop.bounds;
 for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)put(x,103,z,'spruce_planks');
 for(const x of [x0,x1])for(const z of [z0,199,z1])for(let y=104;y<=110;y++)put(x,y,z,'dark_oak_log',{axis:'y'});
 for(let z=z0;z<=z1;z++)for(const x of [x0,x1])put(x,110,z,'dark_oak_log',{axis:'z'});
 for(const z of [z0,z1])for(let x=x0;x<=x1;x++){put(x,110,z,'dark_oak_log',{axis:'x'});put(x,111+Math.floor(Math.min(x-x0,x1-x)/2),z,'spruce_planks');}
 for(let z=z0;z<=z1;z++)put(123,115,z,'dark_oak_log',{axis:'z'});
 // Relocate the original empty plots into two rows, keeping a wide central fountain court.
 for(const [a,c,b,d]of previous.stallPlots)for(let x=a;x<=b;x++)for(let z=c;z<=d;z++)if(get(x,103,z)==='minecraft:orange_terracotta')put(x,103,z,'stone_bricks');
 const stalls=[];for(const x of [66,116])for(const z of [215,226,237]){
  const bounds=[x,z,x+8,z+6],color=stalls.length%3===0?'blue':stalls.length%3===1?'orange':'red';stalls.push({bounds,color});
  for(let a=x;a<=x+8;a++)for(let c=z;c<=z+6;c++)if(a===x||a===x+8||c===z||c===z+6)put(a,103,c,'orange_terracotta');
  for(const a of [x,x+8])for(const c of [z,z+6])for(let y=104;y<=107;y++)put(a,y,c,'oak_log',{axis:'y'});
  for(let a=x;a<=x+8;a++)for(let c=z;c<=z+6;c++)put(a,108,c,(a-x)%2===0?color+'_wool':'white_wool');
  for(let a=x+1;a<x+8;a++)put(a,104,z,'spruce_planks');
 }
 // Closed basin: floor, perimeter at water level, and a two-tier central jet.
 for(let z=219;z<=229;z++)for(let x=90;x<=100;x++){
  const r=Math.hypot(x-95,z-224);if(r>5.3)continue;
  put(x,103,z,'stone_bricks');put(x,104,z,r>4?'polished_andesite':'water',r>4?undefined:{level:'0'});
 }
 for(let y=104;y<=107;y++)put(95,y,224,'chiseled_stone_bricks');
 for(let x=94;x<=96;x++)for(let z=223;z<=225;z++)put(x,108,z,'polished_andesite');
 put(95,108,224,'water',{level:'0'});put(95,109,224,'water',{level:'0'});
 const layout={...previous,bounds:[60,181,132,244],shops:[...previous.shops,shop],stallPlots:stalls.map(s=>s.bounds),stalls,fountain:{center:[95,104,224],radius:5,floorY:103,topY:109},stage:'three shop frames; six covered stalls; market fountain'};
 // Verify two-block clearance from entrance to every shop and both sides of the fountain.
 const key=(x,z)=>x+','+z,seen=new Set(),queue=[[112,215]];for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<60||x>132||z<181||z>244||seen.has(key(x,z)))continue;const state=y=>m.definitions[data[ix(x,y,z)]].Name;if(state(103)==='minecraft:air'||state(104)!=='minecraft:air'||state(105)!=='minecraft:air')continue;seen.add(key(x,z));queue.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
 for(const [x,z]of [...layout.shops.map(s=>[s.entrance[0],s.entrance[2]+1]),[88,224],[102,224],[101,181],...stalls.map(s=>[s.bounds[0]+4,s.bounds[1]+3])])if(!seen.has(key(x,z)))throw Error('Unreachable '+key(x,z));
 let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;
 const file=path.join(MAP,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(ROOT,'archive/map-edit-backups/before-market-expansion/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
 manifest.market=layout;manifest.rooms=manifest.rooms.map(r=>r.id===layout.id?layout:r);fs.writeFileSync(manifestFile,JSON.stringify(manifest,null,2));const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===layout.id?{...z,...layout}:z),null,2));
 fs.writeFileSync(rf,JSON.stringify({layout,changedBlocks:count,changedChunks:result.changed,walkableCells:seen.size,backup:path.relative(ROOT,backup)},null,2));console.log(JSON.stringify({changedBlocks:count,changedChunks:result.changed,walkableCells:seen.size}));
}
if(require.main===module)run();
