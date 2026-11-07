// Local-only guarded market patch. Default is dry-run; no whole-world regeneration.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{verifyModels}=require('./verify_market_redesign.cjs');
const {patchRegion}=require('./nbt-edit.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),STAGE=path.join(ROOT,'archive/map-edit-staging/market-redesign-20261031'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-market-redesign-20261031/starter_sky_land_spawn_v1'),RF=path.join(DIR,'market-redesign-report.json');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),semantic=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),BASE='152512fe1a9ff21ea293a464c56af061565732e1299adc019105c77dc27af09b';
const observed=fs.readFileSync(FILE),restage=process.argv.includes('--restage'),apply=process.argv.includes('--apply');
if(fs.existsSync(RF)){assert(restage,'Already applied; do not replay');assert.equal(hash(observed),JSON.parse(fs.readFileSync(RF)).resultHash,'Concurrent change');}else{assert(!restage,'No prior report');assert.equal(hash(observed),BASE,'Baseline changed: inspect current map');}
const source=restage?fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca')):observed,m=createLibrary(restage?path.dirname(BACKUP):path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),b=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(b.length/2);
for(let i=0;i<data.length;i++)data[i]=b.readUInt16LE(i*2);const original=data.slice(),defs=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[semantic(d),i])),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x;
const before=(x,y,z)=>defs[original[ix(x,y,z)]],name=(x,y,z)=>m.definitions[data[ix(x,y,z)]].Name.slice(10);
const live=JSON.parse(fs.readFileSync(path.join(STAGE,'live-protected-points.json'))),manifest=JSON.parse(fs.readFileSync(path.join(restage?BACKUP:DIR,'map-manifest.json'))),market=manifest.market;
// NPC coordinates are never moved. Preserve their standing pads, homes and all saved patrol cells.
const protectedPoints=[];for(const npc of live.npcs){const feet=Math.round(npc.pos[1]),x=Math.floor(npc.pos[0]),z=Math.floor(npc.pos[2]);protectedPoints.push({name:npc.name,cell:[x,feet,z]});if(npc.home?.length===3)protectedPoints.push({name:npc.name+' home',cell:[npc.home[0],104,npc.home[2]]});for(const p of npc.patrol||[])protectedPoints.push({name:npc.name+' patrol',cell:[p.Array[0],104,p.Array[2]]});}
const reserved=new Set();for(const p of protectedPoints){const [x,,z]=p.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)reserved.add((x+dx)+','+(z+dz));}
const markers=new Set(live.blockEntities.map(p=>[p.x,p.y,p.z].join(',')));
function allowed(x,y,z){return x>=55&&x<=134&&z>=177&&z<=251&&y>=103&&y<=124&&!(x>=90&&x<=100&&z>=219&&z<=229);}
function id(n,p){if(n==='grass_block'&&!p)p={snowy:'false'};const d={Name:n.startsWith('minecraft:')?n:'minecraft:'+n,...(p?{Properties:p}:{})},k=semantic(d);if(!ids.has(k)){assert(m.definitions.length<65535);ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const AIR=id('air'),slab={type:'bottom',waterlogged:'false'},fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'},stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'});
function put(x,y,z,n,p){assert(allowed(x,y,z),'Outside market '+[x,y,z]);let v=id(n,p);if(y>=104&&y<=106&&(reserved.has(x+','+z)||markers.has([x,y,z].join(','))||x>=111&&x<=113&&z<=215))v=original[ix(x,y,z)];data[ix(x,y,z)]=v;}
function fill(x1,y1,z1,x2,y2,z2,n,p){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}
function clear(x,z,lo=104,hi=124){for(let y=lo;y<=hi;y++)put(x,y,z,'air');}
function lamp(x,y,z,hanging=false){put(x,y,z,'lantern',{hanging:String(hanging),waterlogged:'false'});}
function pot(x,y,z){put(x,y,z,'potted_fern');}
const C=Math.cos(Math.PI/6),SN=.5,world=(u,v)=>[Math.round(76+C*u+SN*v),Math.round(197-SN*u+C*v)],local=(x,z)=>[C*(x-76)-SN*(z-197),SN*(x-76)+C*(z-197)];
function bat(u,y,v,n,p){const [x,z]=world(u,v);put(x,y,z,n,p);}
function bfill(u1,y1,v1,u2,y2,v2,n,p){for(let z=178;z<=218;z++)for(let x=56;x<=96;x++){const [u,v]=local(x,z);if(u>=u1-.5&&u<=u2+.5&&v>=v1-.5&&v<=v2+.5)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}}
// Clear obsolete construction inside its actual rotated envelope, including the old roof/canopy.
for(let z=178;z<=218;z++)for(let x=56;x<=96;x++){const [u,v]=local(x,z);if(Math.abs(u)<=13.6&&v>=-12.6&&v<=13.6)clear(x,z);}
for(const r of [[93,180,111,198],[114,180,132,200]])for(let z=r[1];z<=r[3];z++)for(let x=r[0];x<=r[2];x++){
 // Leave the protected smith route at x111..113 completely unchanged.
 if(x>=111&&x<=113)continue;clear(x,z);
}
// Merchant floor keeps the entire 25x23 raster, although upper massing is L-shaped.
bfill(-12,103,-11,12,103,11,'spruce_planks');
for(let z=179;z<=216;z++)for(let x=57;x<=95;x++){
 const [u,v]=local(x,z),au=Math.abs(u),av=Math.abs(v);if(au>12.5||av>11.5)continue;
 const edge=au>=11.5||av>=10.5,post=au>=11.5&&(Math.abs(v)<1||av>9.5)||av>=10.5&&Math.min(...[-12,-6,0,6,12].map(t=>Math.abs(u-t)))<.8;
 if(edge)for(let y=104;y<=109;y++){
  let n=post?'dark_oak_log':y===104?'stone_bricks':y===109?'spruce_planks':'white_terracotta';
  const windows=!post&&y>=106&&y<=108&&(au>=11.5||v<-10.5);if(windows)n='glass';
  if(v>10.5&&Math.abs(u)<9&&y<=107)n='air';
  put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);
 }
 const upper=v<=7.5||u<=-4.5;
 if(upper)put(x,110,z,'spruce_planks');
 const upperEdge=upper&&(au>=11.5||v<=-10.5||(v>=6.5&&v<=7.5&&u>-5.5)||(u>=-5.5&&u<=-4.5&&v>7.5)||v>=10.5);
 if(upperEdge)for(let y=111;y<=114;y++){
  const bayPost=(v>=6.5&&v<=7.5||v>=10.5)&&Math.min(...[-12,-6,0,6,12].map(t=>Math.abs(u-t)))<.8;
  const timber=post||bayPost||y===114,n=timber?'dark_oak_log':y===112||y===113?'glass':'spruce_planks';put(x,y,z,n,timber?{axis:y===114?'x':'y'}:undefined);
 }
 // Main roof has a high gable, side wing a lower intersecting gable.
 if(v<=8.5){const roof=115+Math.floor(Math.max(0,12-au)*.6);put(x,roof,z,'orange_terracotta');put(x,roof+1,z,au<.9?'red_sandstone_slab':'red_sandstone_stairs',au<.9?slab:stair(u<0?'east':'west'));if(v<-10.5||v>6.5)for(let y=115;y<roof;y++)put(x,y,z,Math.abs(u)<.8?'dark_oak_log':'white_terracotta',Math.abs(u)<.8?{axis:'y'}:undefined);}
 if(v>7.5&&u<=-4.5){const du=Math.abs(u+8.5),roof=115+Math.floor(Math.max(0,4-du)*.75);put(x,roof,z,'orange_terracotta');put(x,roof+1,z,'red_sandstone_stairs',stair(u<-8.5?'east':'west'));}
}
// Real balcony floor, railing, open exits, and a low forward shop-wing canopy.
bfill(-4,110,8,12,110,11,'spruce_planks');bfill(-4,111,11,12,111,11,'spruce_fence',fence);
bfill(-4,111,8,-4,111,11,'spruce_fence',fence);bfill(12,111,8,12,111,11,'spruce_fence',fence);
bfill(-2,111,6,1,113,8,'air');bfill(-7,111,9,-4,113,10,'air');
for(let u=5;u<=12;u++)for(let v=12;v<=13;v++){bat(u,109,v,'red_sandstone_stairs',stair('north'));}
for(const u of [-12,-6,6,12]){for(let y=104;y<=109;y++)bat(u,y,11,'dark_oak_log',{axis:'y'});bat(u,104,12,'stone_bricks');bat(u,105,12,'stone_brick_stairs',stair('north'));bat(u,108,12,'spruce_stairs',{...stair('north'),half:'top'});lamp(...(()=>{const [x,z]=world(u,12);return [x,107,z,true];})());}
for(const u of [-7,0,7]){bat(u,105,-10,'bookshelf');bat(u,106,-10,'bookshelf');}
bfill(-7,104,-7,7,104,-7,'spruce_planks');bfill(-7,105,-7,7,105,-7,'spruce_slab',slab);
// Clear a connected path to the existing merchant service point; shelved side stock remains usable.
bfill(-2,104,-4,2,106,11,'air');
const upperRoute=[];
// Returned cardinal stair occupies actual world cells inside the northwestern part of the rotated shop.
for(let i=0;i<4;i++){const z=200-i,y=104+i;for(let x=66;x<=68;x++){put(x,y,z,'spruce_stairs',stair('north'));clear(x,z,y+1,114);}upperRoute.push({cell:[67,y,z],block:'spruce_stairs',facing:'north'});}
fill(66,107,194,72,107,196,'spruce_planks');
for(let z=194;z<=196;z++)for(let x=66;x<=72;x++)clear(x,z,108,114);
for(let i=0;i<3;i++){const z=197+i,y=108+i;for(let x=70;x<=72;x++){put(x,y,z,'spruce_stairs',stair('south'));clear(x,z,y+1,114);}upperRoute.push({cell:[71,y,z],block:'spruce_stairs',facing:'south'});}
fill(70,110,200,73,110,202,'spruce_planks');fill(70,111,200,73,114,202,'air');
for(const u of [-9,8]){bat(u,111,-8,'bookshelf');bat(u,112,-8,'bookshelf');bat(u,111,-6,'spruce_stairs',stair('south'));}
// Shop shells, inset windows, explicit floor and trimmed openings.
function shell(r,top,wall){const [x1,z1,x2,z2]=r;for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){
 put(x,103,z,'spruce_planks');if(x!==x1&&x!==x2&&z!==z1&&z!==z2)continue;
 const post=(x===x1||x===x2)&&(z===z1||z===z2)||(z===z1||z===z2)&&(x-x1)%5===0;
 for(let y=104;y<=top;y++){let n=post?'dark_oak_log':y===104?'stone_bricks':y===top?'spruce_planks':wall;if(!post&&y>=106&&y<=top-1)n='glass';put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);}
 if(!post){put(x,105,z,'spruce_stairs',{...stair(x===x1?'east':x===x2?'west':z===z1?'south':'north'),half:'top'});}
}}
shell([94,181,110,195],108,'mud_bricks');
// Food roof runs east-west: transverse to the other shop's north-south ridge.
for(let z=180;z<=196;z++)for(let x=93;x<=110;x++){const roof=109+Math.floor(Math.max(0,7-Math.abs(z-188))*.75);put(x,roof,z,'orange_terracotta');put(x,roof+1,z,z===188?'red_sandstone_slab':'red_sandstone_stairs',z===188?slab:stair(z<188?'south':'north'));if((x===94||x===110)&&z>=181&&z<=195)for(let y=109;y<roof;y++)put(x,y,z,'white_terracotta');}
// Low lean-to rear kitchen roof breaks the silhouette while staying inside the original footprint.
fill(95,104,187,109,107,187,'mud_bricks');fill(101,104,187,103,107,187,'air');
for(let x=95;x<=109;x++)for(let z=182;z<=186;z++)put(x,104,z,'spruce_planks');
fill(95,104,182,109,104,183,'stone_bricks');for(let x=96;x<=108;x+=4)put(x,105,182,'cauldron');
fill(103,109,184,103,117,184,'bricks');put(103,118,184,'brick_slab',slab);
fill(96,104,195,108,107,195,'air');fill(96,104,194,99,104,194,'spruce_planks');fill(105,104,194,108,104,194,'spruce_planks');
for(let x=95;x<=109;x++)for(let z=196;z<=198;z++)put(x,108,z,(Math.floor((x-95)/2)%2?'orange':'white')+'_wool');
for(const x of [95,109]){fill(x,104,198,x,107,198,'spruce_fence',fence);lamp(x,107,197,true);}
// Two seats in front corners, away from central service axis.
for(const x of [95,108]){put(x,104,201,'spruce_fence',fence);put(x,105,201,'spruce_slab',slab);put(x,104,203,'spruce_stairs',stair('north'));}
shell([115,181,131,197],110,'white_terracotta');
for(let z=180;z<=198;z++)for(let x=115;x<=131;x++){const roof=111+Math.floor(Math.max(0,10-Math.abs(x-121))*.7);put(x,roof,z,'orange_terracotta');put(x,roof+1,z,x===121?'red_sandstone_slab':'red_sandstone_stairs',x===121?slab:stair(x<121?'east':'west'));if(z===181||z===197)for(let y=111;y<roof;y++)put(x,y,z,Math.abs(x-121)<1?'dark_oak_log':'white_terracotta',Math.abs(x-121)<1?{axis:'y'}:undefined);}
// Lower eastern glass bay is contained within the original 17x17 footprint.
for(let z=183;z<=195;z++)for(let x=128;x<=131;x++){clear(x,z,104,120);put(x,103,z,'spruce_planks');if(x===131||z===183||z===195){put(x,104,z,'mud_bricks');for(let y=105;y<=109;y++)put(x,y,z,(z%4===3&&x===131)?'dark_oak_log':'glass',(z%4===3&&x===131)?{axis:'y'}:undefined);}put(x,110+Math.floor((131-x)*.5),z,'glass');}
for(let z=184;z<=194;z+=2){put(130,104,z,'dirt');pot(130,105,z);}
fill(121,104,197,125,108,197,'air');
for(let x=116;x<=130;x++)for(let z=198;z<=200;z++)put(x,109,z,(Math.floor((x-116)/2)%2?'green':'white')+'_wool');
for(const x of [116,130]){fill(x,104,200,x,108,200,'spruce_fence',fence);lamp(x,108,199,true);}
for(const x of [116,126])for(let z=182;z<=185;z++){put(x,104,z,'bookshelf');put(x,105,z,'bookshelf');pot(x,106,z);}
fill(117,104,192,119,104,192,'spruce_planks');fill(126,104,192,127,104,192,'spruce_planks');
for(const [x,z]of [[117,202],[129,202]]){put(x,104,z,'mud_bricks');pot(x,105,z);}
// Framed shopfront bays, shutter panels, lintels and low plinth trim.
for(const [x1,x2,z,top]of [[94,110,195,108],[115,131,197,110]]){
 for(const x of [x1,x1+5,x2-5,x2]){for(let y=104;y<=top;y++)put(x,y,z,'dark_oak_log',{axis:'y'});put(x,104,z+1,'stone_brick_stairs',stair('north'));put(x,top-1,z+1,'spruce_stairs',{...stair('north'),half:'top'});}
 for(const x of [x1+2,x2-2])for(let y=106;y<=107;y++)put(x,y,z,'spruce_trapdoor',{facing:'south',half:'bottom',open:'true',powered:'false',waterlogged:'false'});
 for(let x=x1;x<=x2;x++)if(Math.abs(x-(x1+x2)/2)>2)put(x,105,z+1,'spruce_slab',slab);
}
fill(101,104,195,103,107,195,'air');fill(122,104,197,124,108,197,'air');
for(const x of [97,107]){put(x,106,195,'chain',{axis:'y',waterlogged:'false'});lamp(x,105,195,true);}
for(const x of [118,128]){put(x,107,197,'chain',{axis:'y',waterlogged:'false'});lamp(x,106,197,true);}
// Merchant balcony planters and timber lintels use the existing upper mass, not a smaller shop.
for(const u of [-9,-3,3,9]){bat(u,114,7,'dark_oak_log',{axis:'x'});bat(u,111,11,'spruce_planks');bat(u,112,11,'potted_fern');}
for(const u of [-10,-4,4,10])for(let y=112;y<=113;y++)bat(u,y,7,'spruce_trapdoor',{facing:'south',half:'bottom',open:'true',powered:'false',waterlogged:'false'});
for(const u of [-8,0,8]){bat(u,108,10,'chain',{axis:'y',waterlogged:'false'});const [x,z]=world(u,10);lamp(x,107,z,true);}
const newBlockEntities=[];
function barrel(x,y,z){put(x,y,z,'barrel',{facing:'up',open:'false'});if(name(x,y,z)==='barrel')newBlockEntities.push({kind:'barrel',x,y,z});}
for(const [u,v]of [[-10,6],[-9,6],[9,-9],[10,-9]]){const [x,z]=world(u,v);barrel(x,104,z);}
for(const [x,z]of [[96,191],[108,191],[116,190],[126,183]])barrel(x,104,z);
for(const [x,z]of [[96,190],[108,190]]){put(x,104,z,'hay_block',{axis:'y'});put(x,105,z,'white_carpet');}
function sign(x,y,z,text){put(x,y,z,'oak_wall_sign',{facing:'south',waterlogged:'false'});if(name(x,y,z)==='oak_wall_sign')newBlockEntities.push({kind:'sign',x,y,z,text});}
put(102,110,195,'spruce_planks');sign(102,110,196,['DRAKE','FOOD & SUPPLIES']);
put(123,112,197,'spruce_planks');sign(123,112,198,['ELINA','HERBS & POTIONS']);
{const [x,z]=world(0,8);put(x,114,z-1,'spruce_planks');sign(x,114,z,['OSCAR','MERCHANT HOUSE']);}
// Stall footprints remain at their service NPCs. Rebuild only existing architectural pieces.
const stallPalette=new Set(['spruce_planks','spruce_fence','oak_fence','oak_planks','spruce_slab','oak_slab','lantern','chain','white_wool','blue_wool','red_wool','orange_wool','green_wool','yellow_wool']);
for(const [index,s]of market.stalls.entries()){
 const [x1,z1,x2,z2]=s.bounds,col=['blue','orange','red','orange','green','green'][index];
 for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)for(let y=104;y<=110;y++)if(stallPalette.has(before(x,y,z).Name.slice(10)))put(x,y,z,'air');
 for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){assert(before(x,103,z).Name!=='minecraft:air','Stall extends beyond its existing floor');put(x,103,z,'spruce_planks');if(z===z1||z===z2){if(x===x1||x===x2)fill(x,104,z,x,107,z,'spruce_fence',fence);}put(x,108,z,(Math.floor((x-x1)/2)%2?col:'white')+'_wool');}
 for(let x=x1+1;x<x2;x++)put(x,104,z2,'spruce_planks');
 // Counters face the fountain, with a three-block front opening at old service axis.
 const cx=Math.floor((x1+x2)/2);fill(cx-1,104,z2,cx+1,106,z2,'air');lamp(x1,107,z1+1,true);lamp(x2,107,z1+1,true);
 s.color=col;
}
// Soft landscape detail uses existing supported ground only; never clears trees or flattens the island.
const pathCells=new Set(),routes=[];
function route(label,points,width=3){const cells=[];for(let j=1;j<points.length;j++){const a=points[j-1],c=points[j],steps=Math.max(Math.abs(a[0]-c[0]),Math.abs(a[1]-c[1]));for(let i=0;i<=steps;i++){const x=Math.round(a[0]+(c[0]-a[0])*i/Math.max(1,steps)),z=Math.round(a[1]+(c[1]-a[1])*i/Math.max(1,steps));cells.push([x,z]);for(let dz=-Math.floor(width/2);dz<=Math.floor(width/2);dz++)for(let dx=-Math.floor(width/2);dx<=Math.floor(width/2);dx++){const xx=x+dx,zz=z+dz;if(!allowed(xx,103,zz))continue;const ground=name(xx,103,zz);if(['grass_block','dirt_path','coarse_dirt','dirt'].includes(ground)){put(xx,103,zz,'dirt_path');if(['short_grass','fern','poppy','dandelion'].includes(name(xx,104,zz)))put(xx,104,zz,'air');}pathCells.add(xx+','+zz);}}}routes.push({label,width,cells});}
for(const r of market.ground.routes)route(r.name,r.points,r.width);
route('merchant-stair',[[76,194],[76,201],[67,201]],3);
for(const p of protectedPoints)route(p.name,[[p.cell[0],p.cell[2]],[p.cell[0],Math.max(211,p.cell[2])]],1);
// Boundary stones and lamps sit to the side of route masks, never in the clear lane.
for(const [x,z]of [[91,206],[107,207],[126,208],[78,218],[109,224],[78,232],[109,243]]){
 if(pathCells.has(x+','+z)||reserved.has(x+','+z)||name(x,104,z)!=='air'||!['grass_block','dirt_path','coarse_dirt'].includes(name(x,103,z)))continue;
 put(x,104,z,'stone_bricks');put(x,105,z,'spruce_fence',fence);lamp(x,106,z);
}
let flowers=0;for(let z=204;z<=248;z++)for(let x=60;x<=130;x++){
 if(!allowed(x,104,z)||pathCells.has(x+','+z)||reserved.has(x+','+z)||name(x,103,z)!=='grass_block'||name(x,104,z)!=='air'||name(x,105,z)!=='air')continue;
 if((x*7+z*13)%41===0){put(x,104,z,(x+z)%2?'poppy':'dandelion');flowers++;}
}
// Standing pads and exact marker/sign cells win over every decoration; server blocks are not imported.
for(const p of protectedPoints){const [x,,z]=p.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,zz=z+dz;if(!allowed(xx,103,zz))continue;for(let y=104;y<=106;y++)data[ix(xx,y,zz)]=AIR;if(name(xx,103,zz)==='air')put(xx,103,zz,'spruce_planks');}}
for(const p of live.blockEntities){if(!allowed(p.x,p.y,p.z))continue;data[ix(p.x,p.y,p.z)]=original[ix(p.x,p.y,p.z)];}
// Old food-shop plinth occupied the western edge of the nominal 3-wide smith route.
// Remove only those obsolete trim stairs; keep every other existing route cell.
for(let z=181;z<=196;z++)if(before(111,104,z).Name==='minecraft:stone_brick_stairs')data[ix(111,104,z)]=AIR;
// Resolve saved fence connections so the viewer and game have continuous balcony/stall rails.
for(let z=177;z<=251;z++)for(let x=55;x<=134;x++)for(let y=104;y<=113;y++){
 if(!allowed(x,y,z)||data[ix(x,y,z)]===original[ix(x,y,z)]||!name(x,y,z).endsWith('_fence'))continue;
 const n=name(x,y,z),p={...fence};for(const [side,dx,dz]of [['north',0,-1],['south',0,1],['east',1,0],['west',-1,0]]){const other=name(x+dx,y,z+dz);p[side]=String(other.endsWith('_fence')||other.endsWith('_log')||other.endsWith('_planks')||other==='stone_bricks');}data[ix(x,y,z)]=id(n,p);
}
let net=0;for(let i=0;i<data.length;i++)if(data[i]!==original[i]){net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(allowed(x,y,z));}
const report={sourceHash:hash(source),bounds:[55,103,177,134,124,251],backup:path.relative(ROOT,BACKUP),netChangedBlocks:net,protectedPoints,serverBlockEntityPoints:live.blockEntities,stalls:market.stalls,upperRoute,routes,flowers,newBlockEntities,dimensions:{merchant:[25,23],food:[17,15],herbs:[17,17]},floors:{ground:103,merchantUpper:110},verification:{webGL:false,inGame:false,deployed:false}};
const stagedModel={...m,blocks:Buffer.from(data.buffer).toString('base64')},baseline={...m,definitions:defs,blocks:b.toString('base64')};
report.check=verifyModels(stagedModel,baseline,report);
const patched=patch(m,data,original,source),tag=(type,value)=>({type,value});
const entities=patchRegion(patched.bytes,t=>{const c=t.value,add=newBlockEntities.filter(p=>Math.floor(p.x/16)===c.xPos.value&&Math.floor(p.z/16)===c.zPos.value);if(!add.length)return false;if(!c.block_entities)c.block_entities=tag(9,{type:10,values:[]});for(const p of add){assert(!c.block_entities.value.values.some(e=>e.x.value===p.x&&e.y.value===p.y&&e.z.value===p.z),'Existing block entity collision');const base={id:tag(8,'minecraft:'+p.kind),x:tag(3,p.x),y:tag(3,p.y),z:tag(3,p.z)};if(p.kind==='barrel')base.Items=tag(9,{type:10,values:[]});else{const text=tag(10,{messages:tag(9,{type:8,values:Array.from({length:4},(_,i)=>JSON.stringify({text:p.text[i]||''}))}),color:tag(8,'black'),has_glowing_text:tag(1,0)});Object.assign(base,{front_text:text,back_text:text,is_waxed:tag(1,1)});}c.block_entities.value.values.push(base);}return true;});
patched.bytes=entities.bytes;report.resultHash=hash(patched.bytes);report.changedChunks=patched.changed;
fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'dry-run-report.json'),JSON.stringify(report,null,2));fs.writeFileSync(path.join(STAGE,'planned-market.mca'),patched.bytes);
if(!apply){console.log(JSON.stringify({mode:'dry-run',netChangedBlocks:net,changedChunks:patched.changed,check:report.check}));process.exit(0);}
assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent source edit');
if(!fs.existsSync(BACKUP)){fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});}else assert.equal(hash(fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca'))),BASE,'Unexpected backup');
assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent edit before commit');fs.writeFileSync(FILE+'.pending',patched.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),sb=Buffer.from(saved.blocks,'base64'),want=m.definitions.map(semantic),got=saved.definitions.map(semantic);for(let i=0;i<data.length;i++)assert.equal(got[sb.readUInt16LE(i*2)],want[data[i]],'Saved mismatch '+i);
market.stage='redesigned merchant, food and herb shops; service-aligned six stalls';market.report='market-redesign-report.json';market.shops[0].upperFloorY=110;market.shops[0].roofRidgeY=123;market.shops[1].roofRidgeY=115;market.shops[2].roofRidgeY=119;market.buildingDetails.forEach((v,i)=>{v.roofTopY=[123,118,119][i];v.style=['L-shaped upper merchant mass and balcony','transverse food roof with kitchen chimney','asymmetric herb roof and east glass bay'][i];});
manifest.rooms=manifest.rooms.map(r=>r.id===market.id?market:r);manifest.blockStates=saved.definitions;fs.writeFileSync(path.join(DIR,'map-manifest.json'),JSON.stringify(manifest,null,2));const zf=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===market.id?market:z),null,2));
fs.writeFileSync(RF,JSON.stringify(report,null,2));console.log(JSON.stringify({mode:'applied',netChangedBlocks:net,changedChunks:patched.changed,resultHash:report.resultHash,check:report.check}));


