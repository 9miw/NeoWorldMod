// Latest-world patch only. Never replays the earlier market/guild generators.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{decode,patchRegion}=require('./nbt-edit.cjs'),{verifyExpansion}=require('./verify_market_expansion.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),RF=path.join(DIR,'market-expansion-v2-report.json'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-market-expansion-20261031/starter_sky_land_spawn_v1'),STAGE=path.join(ROOT,'archive/map-edit-staging/market-expansion-20261031');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),observed=fs.readFileSync(FILE),restage=process.argv.includes('--restage');
if(fs.existsSync(RF)){assert(restage,'Already expanded; do not replay');assert.equal(hash(observed),JSON.parse(fs.readFileSync(RF)).resultHash,'Concurrent edit since expansion');}else{assert(!restage);assert.equal(hash(observed),'3de64fce5d84eaea219f737a267a8863ccec7bea2c4449828b755fb82ad78f02','Baseline changed: inspect latest map');}
const source=restage?fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca')):observed,m=createLibrary(restage?path.dirname(BACKUP):path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),bytes=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
const original=data.slice(),defs=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>m.definitions[data[ix(x,y,z)]],before=(x,y,z)=>defs[original[ix(x,y,z)]],name=(x,y,z)=>get(x,y,z).Name.slice(10);
const manifest=JSON.parse(fs.readFileSync(path.join(restage?BACKUP:DIR,'map-manifest.json'))),market=manifest.market,live=JSON.parse(fs.readFileSync(path.join(STAGE,'protected-points.json'))),protectedPoints=[],reserved=new Set();
for(const p of live.npcs){protectedPoints.push({name:p.name,cell:[Math.floor(p.pos[0]),104,Math.floor(p.pos[2])]});if(p.home?.length===3)protectedPoints.push({name:p.name+' home',cell:[p.home[0],104,p.home[2]]});for(const a of p.patrol||[])protectedPoints.push({name:p.name+' patrol',cell:[a.Array[0],104,a.Array[2]]});}
for(const p of protectedPoints){const [x,,z]=p.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)reserved.add((x+dx)+','+(z+dz));}
const markerPoints=live.blockEntities.filter(p=>p.id==='neoworld:navigation_marker'),markerKeys=new Set(live.blockEntities.map(p=>[p.x,p.y,p.z].join(',')));
const relocatedCells=new Set();
function allowed(x,y,z){return relocatedCells.has([x,y,z].join(','))||x>=46&&x<=141&&z>=175&&z<=220&&y>=99&&y<=128&&!(x>=90&&x<=100&&z>=219);}
function id(n,p){if(n==='grass_block'&&!p)p={snowy:'false'};const d={Name:n.startsWith('minecraft:')?n:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const AIR=id('air'),slab={type:'bottom',waterlogged:'false'},stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'};
function put(x,y,z,n,p){assert(allowed(x,y,z),'Outside approved mask '+[x,y,z]);if(x>=111&&x<=113&&y<=106)return;let value=id(n,p);if(y>=104&&y<=106&&(reserved.has(x+','+z)||markerKeys.has([x,y,z].join(','))))value=original[ix(x,y,z)];data[ix(x,y,z)]=value;}
function fill(x1,y1,z1,x2,y2,z2,n,p){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}
// The low foliage at the new west corner is an isolated five-block shrub, not a trunked tree.
const shrub=[],shrubSeen=new Set(),shrubQueue=[[51,103,194]];for(let i=0;i<shrubQueue.length;i++){
 const p=shrubQueue[i],k=p.join(',');if(shrubSeen.has(k))continue;shrubSeen.add(k);const [x,y,z]=p;if(x<30||x>60||z<180||z>210||y<97||y>110)continue;
 const d=before(x,y,z);if(!['minecraft:oak_log','minecraft:oak_leaves'].includes(d.Name))continue;shrub.push({position:p,state:d});for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)for(let dy=-1;dy<=1;dy++)if(dx||dy||dz)shrubQueue.push([x+dx,y+dy,z+dz]);
}assert.equal(shrub.length,5);assert(shrub.every(p=>p.state.Name==='minecraft:oak_leaves'),'Inspect trunked tree before relocating');
const shrubOffset=[-8,-2,0];for(const e of shrub){const target=e.position.map((v,i)=>v+shrubOffset[i]);assert.equal(before(...target).Name,'minecraft:air','Shrub target occupied');relocatedCells.add(target.join(','));}
const shrubSourceCells=new Set(shrub.map(e=>e.position.join(',')));
for(const e of shrub)put(...e.position,'air');for(const e of shrub){const p=e.position.map((v,i)=>v+shrubOffset[i]);put(...p,e.state.Name,e.state.Properties);if(p[1]===99)assert(['minecraft:grass_block','minecraft:coarse_dirt','minecraft:dirt'].includes(before(p[0],98,p[2]).Name),'Shrub root lacks ground');}
const C=Math.cos(Math.PI/6),SN=.5,local=(x,z)=>[C*(x-70)-SN*(z-197),SN*(x-70)+C*(z-197)],oldLocal=(x,z)=>[C*(x-76)-SN*(z-197),SN*(x-76)+C*(z-197)],world=(u,v)=>[Math.round(70+C*u+SN*v),Math.round(197-SN*u+C*v)];
function bat(u,y,v,n,p){const [x,z]=world(u,v);put(x,y,z,n,p);}
function bfill(u1,y1,v1,u2,y2,v2,n,p){for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){const [u,v]=local(x,z);if(u>=u1-.5&&u<=u2+.5&&v>=v1-.5&&v<=v2+.5)for(let y=y1;y<=y2;y++)if(allowed(x,y,z))put(x,y,z,n,p);}}
function core(x,z){const [u,v]=local(x,z);return Math.abs(u)<=15.5&&Math.abs(v)<=14.5||x>=94&&x<=110&&z>=177&&z<=197||x>=115&&x<=131&&z>=179&&z<=197||x>=132&&x<=138&&z>=182&&z<=196;}
function oldArchitecture(x,z){const [u,v]=oldLocal(x,z);return Math.abs(u)<=13.7&&v>=-12.7&&v<=13.7||x>=93&&x<=111&&z>=180&&z<=203||x>=114&&x<=132&&z>=180&&z<=202;}
const barrels=[],signs=[];for(let slot=0;slot<1024;slot++){const l=source.readUInt32BE(slot*4);if(!l)continue;const off=(l>>>8)*4096,len=source.readUInt32BE(off),t=decode(zlib.inflateSync(source.subarray(off+5,off+4+len)));for(const e of t.value.block_entities?.value.values||[]){const x=e.x.value,y=e.y.value,z=e.z.value;if(!allowed(x,y,z))continue;const block=before(x,y,z);if(e.id.value==='minecraft:barrel')barrels.push({position:[x,y,z],state:block});else if(e.id.value==='minecraft:sign')signs.push({from:[x,y,z]});else throw Error('Unrecognized local block entity in patch: '+e.id.value);}}
assert.equal(barrels.length,8);assert.equal(signs.length,3);
// Clear only current shops and newly occupied rooms; tree canopies always cause an abort.
for(let z=175;z<=220;z++)for(let x=46;x<=141;x++){if(!allowed(x,104,z)||!core(x,z)&&!oldArchitecture(x,z))continue;for(let y=104;y<=127;y++){
 const n=before(x,y,z).Name;assert(!n.endsWith('_leaves'),'Canopy collision');if(!oldArchitecture(x,z))assert(!n.endsWith('_log'),'New area tree collision');put(x,y,z,'air');
 }if(!core(x,z)&&oldArchitecture(x,z)&&before(x,103,z).Name==='minecraft:spruce_planks')put(x,103,z,'grass_block');}
// Blend the new building pads into the existing island; no new unsupported island columns.
const pad=new Map(),terrain=new Set(['grass_block','dirt','coarse_dirt','dirt_path','stone','andesite','granite','diorite','podzol']);
for(let z=175;z<=220;z++)for(let x=46;x<=141;x++)if(core(x,z)){for(let dz=-4;dz<=4;dz++)for(let dx=-4;dx<=4;dx++){const xx=x+dx,zz=z+dz;if(!allowed(xx,103,zz)||xx>=111&&xx<=113)continue;const h=103-Math.max(0,Math.max(Math.abs(dx),Math.abs(dz))-1),k=xx+','+zz;pad.set(k,Math.max(pad.get(k)||0,h));}}
let raisedColumns=0;for(const [k,h]of pad){const [x,z]=k.split(',').map(Number);let ground=null;for(let y=104;y>=96;y--)if(terrain.has(before(x,y,z).Name.slice(10))){ground=y;break;}assert(ground!==null,'Pad lacks existing land '+k);if(ground<98){assert(!core(x,z),'Core needs deeper foundation');continue;}if(!core(x,z)&&ground>=h)continue;
 if(!core(x,z)&&Array.from({length:Math.max(0,h-98)},(_,i)=>i+99).some(y=>/_(leaves|log)$/.test(before(x,y,z).Name)&&!shrubSourceCells.has([x,y,z].join(','))))continue;
 if(!core(x,z)&&before(x,104,z).Name!=='minecraft:air'&&!['short_grass','fern'].includes(before(x,104,z).Name.slice(10)))continue;
 if(ground<h)raisedColumns++;for(let y=ground+1;y<h;y++)put(x,y,z,'dirt');put(x,h,z,core(x,z)?'spruce_planks':'grass_block');if(!core(x,z)&&h>=104&&['short_grass','fern'].includes(name(x,h+1,z)))put(x,h+1,z,'air');
}
for(let z=175;z<=220;z++)for(let x=46;x<=141;x++)if(core(x,z))put(x,103,z,'spruce_planks');
function lamp(x,y,z,hanging=true){put(x,y,z,'lantern',{hanging:String(hanging),waterlogged:'false'});}
function pot(x,y,z){put(x,y,z,'potted_fern');}
function windowPanel(x,z,axis,t,top,wall){
 const post=Math.min(...[-15,-9,-3,3,9,15].map(v=>Math.abs(t-v)))<.8;
 for(let y=104;y<=top;y++){let n=post?'dark_oak_log':y===104?'stone_bricks':y===top?'dark_oak_planks':wall;if(!post&&y>=106&&y<=108)n=Math.abs((t+3)%6)<1?'spruce_planks':'glass';put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);}
}
// Full merchant ground floor. Upper room and forward wing have their own masks, balcony stays usable.
for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){
 const [u,v]=local(x,z),au=Math.abs(u),av=Math.abs(v);if(au>15.5||av>14.5)continue;put(x,110,z,'spruce_planks');
 const edge=au>=14.5||av>=13.5;if(edge)windowPanel(x,z,av>=13.5?'x':'z',av>=13.5?u:v,109,(x+z)%5===0?'mud_bricks':'white_terracotta');
 if(v>13.5&&Math.abs(u-6)<2.5)for(let y=104;y<=108;y++)put(x,y,z,'air');
 const inUpper=v<=9.5||u<=-5.5,upperEdge=inUpper&&(au>=14.5||v<=-13.5||v>=8.5&&v<=9.5&&u> -6.5||u>=-6.5&&u<=-5.5&&v>9.5||v>=13.5);
 if(upperEdge)for(let y=111;y<=115;y++){const t=av>=13.5||v>=8.5&&v<=9.5?u:v,post=Math.min(...[-15,-9,-3,3,9,15].map(v=>Math.abs(t-v)))<.8;const isGlass=!post&&y>=112&&y<=114&&Math.abs(t-Math.round(t/6)*6)<=1.7;const n=post||y===115?'dark_oak_log':isGlass?'glass':y===111?'spruce_planks':'white_terracotta';put(x,y,z,n,n==='dark_oak_log'?{axis:y===115?'x':'y'}:undefined);}
}
// Roof union is independent of the wall footprint, with one-block eaves and a defined cross-gable join.
const roofColumns={merchant:[],food:[],herbs:[],glass:[]};
for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){
 if(!allowed(x,116,z))continue;const [u,v]=local(x,z),au=Math.abs(u),candidates=[];
 if(au<=16.5&&v>=-15.5&&v<=10.5)candidates.push({y:116+Math.floor((15-au)*.6),f:u<0?'east':'west',ridge:au<.8});
 if(u>=-16.5&&u<=-4.5&&v>=8.5&&v<=15.5)candidates.push({y:116+Math.floor((5.5-Math.abs(u+10.5))*.8),f:u< -10.5?'east':'west',ridge:Math.abs(u+10.5)<.6});
 if(!candidates.length)continue;const roof=candidates.sort((a,b)=>b.y-a.y)[0];put(x,roof.y,z,'orange_terracotta');put(x,roof.y+1,z,roof.ridge?'red_sandstone_slab':'red_sandstone_stairs',roof.ridge?slab:stair(roof.f));roofColumns.merchant.push([x,roof.y,z]);
 // Gable infill belongs only to wall planes, not to the projecting roof perimeter.
 if(au<=15.5&&(v>=8.5&&v<=9.5&&u> -5.5||v>=-14.5&&v<=-13.5))for(let y=116;y<roof.y;y++){const wood=au<.8||y===119||Math.abs(au-(y-115)*1.5)<.7;put(x,y,z,wood?'dark_oak_log':'white_terracotta',wood?{axis:'x'}:undefined);}
 if(u>=-15.5&&u<=-5.5&&v>=13.5&&v<=14.5)for(let y=116;y<roof.y;y++)put(x,y,z,Math.abs(u+10.5)<.8?'dark_oak_log':'white_terracotta',Math.abs(u+10.5)<.8?{axis:'y'}:undefined);
}
// Broad front shop bays, layered plinth, separate awnings below the balcony and timber brackets.
for(const u of [-12,-6,0,12]){
 bfill(u-2,104,13,u+2,107,14,'air');for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){const [a,v]=local(x,z);if(a>=u-2.5&&a<=u+2.5&&v>=14.5&&v<=17.5&&allowed(x,109,z))put(x,109,z,((Math.round(a-u)+2)%4<2?'orange':'white')+'_wool');}
 for(const side of [-3,3]){bat(u+side,104,15,'chiseled_stone_bricks');bat(u+side,108,15,'spruce_stairs',{...stair('north'),half:'top'});}const [lx,lz]=world(u,15);lamp(lx,108,lz);
}
// A continuous cloth strip reads as a storefront canopy rather than isolated diamond-shaped patches.
for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){const [u,v]=local(x,z);if(u>=-15.5&&u<=15.5&&v>=14.5&&v<=17.5&&allowed(x,109,z))put(x,109,z,(Math.floor((u+16)/2)%2?'orange':'white')+'_wool');}
// Frame actual upper gable windows; leave the roof voxels above them intact.
for(let z=175;z<=220;z++)for(let x=46;x<=94;x++){
 const [u,v]=local(x,z);if(v>=8.5&&v<=9.5&&Math.abs(u)<=3.5){const roofY=116+Math.floor((15-Math.abs(u))*.6);for(let y=117;y<=122&&y<roofY;y++)put(x,y,z,Math.abs(u)>2.5||y===117||y===122?'spruce_planks':'glass');}
 if(v>=8.5&&v<=9.5&&u>=5.5&&u<=8.5){const roofY=116+Math.floor((15-Math.abs(u))*.6);for(let y=116;y<=119&&y<roofY;y++)put(x,y,z,u<6.5||u>7.5||y===116||y===119?'spruce_planks':'glass');}
 if(v>=13.5&&v<=14.5&&Math.abs(u+10.5)<=2){const roofY=116+Math.floor((5.5-Math.abs(u+10.5))*.8);for(let y=116;y<=119&&y<roofY;y++)put(x,y,z,Math.abs(u+10.5)>1||y===116||y===119?'spruce_planks':'glass');}
}
// Balcony rails are rasterized as a two-cell-thick boundary, then native fence connections resolved.
bfill(-5,111,14,15,111,14,'spruce_fence',fence);bfill(15,111,10,15,111,14,'spruce_fence',fence);
bfill(4,111,8,8,114,10,'air');bfill(-7,111,10,-5,114,12,'air');
for(const u of [-3,3,9,14]){bat(u,111,14,'spruce_planks');bat(u,112,14,'potted_fern');}
for(const u of [-12,-6,0,6,12]){bat(u,114,9,'spruce_trapdoor',{facing:'south',half:'bottom',open:'true',powered:'false',waterlogged:'false'});bat(u,113,10,'spruce_slab',slab);}
// Shelves/counter sit at the rear; recorded service aisle remains open.
bfill(-10,104,-10,11,104,-10,'spruce_planks');bfill(-10,105,-10,11,105,-10,'spruce_slab',slab);
for(const u of [-13,13])for(const v of [-9,-6,-3])for(let y=104;y<=107;y++)bat(u,y,v,'bookshelf');
for(const u of [-11,10]){bat(u,111,-10,'bookshelf');bat(u,112,-10,'bookshelf');bat(u,111,-8,'spruce_stairs',stair('south'));}
const stairTreads=[];for(let i=0;i<4;i++){const z=200-i,y=104+i;for(let x=66;x<=68;x++){put(x,y,z,'spruce_stairs',stair('north'));fill(x,y+1,z,x,115,z,'air');}stairTreads.push({cell:[67,y,z],facing:'north'});}
fill(66,107,194,72,107,196,'spruce_planks');fill(66,108,194,72,115,196,'air');
for(let i=0;i<3;i++){const z=197+i,y=108+i;for(let x=70;x<=72;x++){put(x,y,z,'spruce_stairs',stair('south'));fill(x,y+1,z,x,115,z,'air');}stairTreads.push({cell:[71,y,z],facing:'south'});}fill(70,110,200,73,110,202,'spruce_planks');fill(70,111,200,73,115,202,'air');
function shell(r,top,material){const [x1,z1,x2,z2]=r;for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){if(x!==x1&&x!==x2&&z!==z1&&z!==z2)continue;const t=x===x1||x===x2?z-z1:x-x1,post=t%5===0||x===x2&&z===z2;for(let y=104;y<=top;y++){let n=post?'dark_oak_log':y===104?'mud_bricks':y===top?'spruce_planks':material;if(!post&&y>=106&&y<=108&&t%5>=2&&t%5<=3)n='glass';put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);}if(z===z2){put(x,105,z+1,'spruce_slab',slab);if(post)put(x,104,z+1,'stone_brick_stairs',stair('north'));}}}
shell([94,177,110,197],110,'mud_bricks');
for(let z=176;z<=198;z++)for(let x=93;x<=111;x++){const y=111+Math.floor((8-Math.abs(x-102))*.75);put(x,y,z,'orange_terracotta');put(x,y+1,z,x===102?'red_sandstone_slab':'red_sandstone_stairs',x===102?slab:stair(x<102?'east':'west'));roofColumns.food.push([x,y,z]);if(x>=94&&x<=110&&(z===177||z===197))for(let yy=111;yy<y;yy++)put(x,yy,z,x===102||yy===113?'dark_oak_log':'white_terracotta',x===102||yy===113?{axis:'x'}:undefined);}
fill(95,104,185,109,107,185,'mud_bricks');fill(101,104,185,103,107,185,'air');fill(95,104,178,109,104,179,'stone_bricks');for(const x of [96,100,104,108])put(x,105,178,'cauldron');fill(106,111,181,106,120,181,'bricks');put(106,121,181,'brick_slab',slab);
for(const r of [[95,104,186,100,104,186],[104,104,186,109,104,186],[96,104,196,99,104,196],[105,104,196,108,104,196]])fill(...r,'spruce_planks');
fill(101,104,197,103,108,197,'air');for(let x=95;x<=109;x++)for(let z=198;z<=200;z++)put(x,109,z,(Math.floor((x-95)/2)%2?'orange':'white')+'_wool');for(const x of [95,109]){fill(x,104,200,x,108,200,'spruce_fence',fence);lamp(x,108,199);}
for(const [a,c]of [[96,99],[105,108]]){fill(a,105,197,c,108,197,'air');fill(a,109,197,c,109,197,'dark_oak_log',{axis:'x'});}
for(const x of [95,100,104,109])fill(x,104,197,x,108,197,'dark_oak_log',{axis:'y'});
for(const x of [97,107]){put(x,107,197,'chain',{axis:'y',waterlogged:'false'});put(x,108,197,'chain',{axis:'y',waterlogged:'false'});lamp(x,106,197);}
for(const [x,z]of [[96,193],[108,193]]){put(x,104,z,'spruce_fence',fence);put(x,105,z,'spruce_slab',slab);put(x,104,z+2,'spruce_stairs',stair('north'));}
shell([115,179,131,197],112,'white_terracotta');
for(let z=178;z<=198;z++)for(let x=114;x<=132;x++){
 if(x===132&&z>=181&&z<=197)continue;const y=113+Math.floor((8-Math.abs(x-123))*.875);put(x,y,z,'orange_terracotta');put(x,y+1,z,x===123?'red_sandstone_slab':'red_sandstone_stairs',x===123?slab:stair(x<123?'east':'west'));roofColumns.herbs.push([x,y,z]);if(x>=115&&x<=131&&(z===179||z===197))for(let yy=113;yy<y;yy++)put(x,yy,z,x===123||yy===116?'dark_oak_log':'white_terracotta',x===123||yy===116?{axis:'x'}:undefined);
}
// A true external glass bay: no main-room floor is subtracted.
for(let z=182;z<=196;z++)for(let x=132;x<=138;x++){const y=113-Math.floor((x-132)/2);if(x===138||z===182||z===196){put(x,104,z,'mud_bricks');for(let yy=105;yy<y;yy++)put(x,yy,z,(x===138&&(z-182)%5===0)?'dark_oak_log':'glass',(x===138&&(z-182)%5===0)?{axis:'y'}:undefined);}}
for(let z=181;z<=197;z++)for(let x=132;x<=139;x++){const y=113-Math.floor((x-132)/2);put(x,y,z,'glass');roofColumns.glass.push([x,y,z]);}
for(let x=121;x<=125;x++)for(let y=114;y<=118;y++){const roofY=113+Math.floor((8-Math.abs(x-123))*.875);if(y<roofY)put(x,y,197,x===121||x===125||y===114||y===118?'spruce_planks':'glass');}
fill(131,104,187,132,108,189,'air');for(const z of [184,188,192,194]){put(137,104,z,'dirt');pot(137,105,z);}
for(const x of [116,129])for(const z of [181,183,185]){put(x,104,z,'bookshelf');put(x,105,z,'bookshelf');pot(x,106,z);}
fill(117,104,191,119,104,191,'spruce_planks');fill(126,104,191,128,104,191,'spruce_planks');fill(122,104,197,124,109,197,'air');
for(let x=116;x<=130;x++)for(let z=198;z<=201;z++)put(x,110,z,(Math.floor((x-116)/2)%2?'green':'white')+'_wool');for(const x of [116,130]){fill(x,104,201,x,109,201,'spruce_fence',fence);lamp(x,109,200);}
for(const x of [118,128]){put(x,108,197,'chain',{axis:'y',waterlogged:'false'});lamp(x,107,197);pot(x,106,198);}
// Exterior decoration remains beside paths rather than consuming the three-block entrances.
for(const [x,z]of [[96,202],[108,202],[117,203],[129,203]]){put(x,104,z,'mud_bricks');pot(x,105,z);}
for(const [x1,z1,x2,z2]of [[95,204,98,206],[105,204,108,206],[126,204,130,206]])for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)if(name(x,103,z)==='grass_block'&&name(x,104,z)==='air'&&name(x,105,z)==='air'&&!reserved.has(x+','+z)){put(x,103,z,'dirt');put(x,104,z,(x+z)%3===0?'allium':(x+z)%3===1?'white_tulip':'poppy');}
// Sills stop at each doorway; clear the complete entrance depth through the projecting trim.
fill(101,104,196,103,106,201,'air');fill(122,104,196,124,106,202,'air');
// Preserve barrel contents and exact coordinates; cosmetic sign data moves within its existing chunks.
for(const e of barrels)put(...e.position,e.state.Name,e.state.Properties);
const signTargets=[[80,114,203],[102,112,198],[123,113,198]],signMoves=signs.map(s=>({...s,to:s.from[0]===80?signTargets[0]:s.from[0]===102?signTargets[1]:signTargets[2]}));
for(const p of signMoves){const [x,y,z]=p.to;put(x,y,z-1,'spruce_planks');put(x,y,z,'oak_wall_sign',{facing:'south',waterlogged:'false'});assert.equal(Math.floor(p.from[0]/16),Math.floor(x/16));assert.equal(Math.floor(p.from[2]/16),Math.floor(z/16));}
// Restore recorded service/nav approach as an open three-wide path, using existing ground outside buildings.
for(const p of markerPoints.filter(p=>p.y===104))for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const x=p.x+dx,z=p.z+dz;if(!allowed(x,104,z))continue;for(let y=104;y<=106;y++)if(!reserved.has(x+','+z))put(x,y,z,'air');}
for(const p of protectedPoints){const [cx,,cz]=p.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const x=cx+dx,z=cz+dz;if(!allowed(x,104,z))continue;for(let y=104;y<=106;y++)data[ix(x,y,z)]=AIR;assert(name(x,103,z)!=='air','Unsupported NPC pad');}}
for(const p of live.blockEntities)if(allowed(p.x,p.y,p.z))data[ix(p.x,p.y,p.z)]=original[ix(p.x,p.y,p.z)];
// Join diagonal raster fence runs with inward corner cells, keeping the balcony rail continuous.
function railLine(a,c){const p=world(...a),q=world(...c),steps=Math.max(Math.abs(q[0]-p[0]),Math.abs(q[1]-p[1]));let prev;
 for(let i=0;i<=steps;i++){const x=Math.round(p[0]+(q[0]-p[0])*i/Math.max(1,steps)),z=Math.round(p[1]+(q[1]-p[1])*i/Math.max(1,steps));if(prev&&x!==prev[0]&&z!==prev[1]){
  const choices=[[prev[0],z],[x,prev[1]]].sort((a,b)=>Math.abs(local(...a)[1]-13.5)-Math.abs(local(...b)[1]-13.5));const [xx,zz]=choices[0];if(name(xx,111,zz)==='air'&&name(xx,110,zz)==='spruce_planks')put(xx,111,zz,'spruce_fence',fence);
 }if(name(x,111,z)==='air'&&name(x,110,z)==='spruce_planks')put(x,111,z,'spruce_fence',fence);prev=[x,z];}
}
railLine([-5,14],[15,14]);railLine([15,10],[15,14]);
// Resolve connections for all new market fences; leave tables supported by their deliberately separate legs.
for(let z=175;z<=220;z++)for(let x=46;x<=141;x++)for(let y=104;y<=112;y++){if(!allowed(x,y,z)||!name(x,y,z).endsWith('_fence'))continue;const n=name(x,y,z),p={...get(x,y,z).Properties};for(const [side,dx,dz]of [['north',0,-1],['south',0,1],['east',1,0],['west',-1,0]]){const other=name(x+dx,y,z+dz);p[side]=String(other.endsWith('_fence')||other.endsWith('_log')||other.endsWith('_planks'));}put(x,y,z,n,p);}
const allKeys=m.definitions.map(key),oldKeys=defs.map(key);let net=0;for(let i=0;i<data.length;i++){if(allKeys[data[i]]===oldKeys[original[i]]){data[i]=original[i];continue;}net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(allowed(x,y,z),'Out of expansion bounds');}
const report={sourceHash:hash(source),backup:path.relative(ROOT,BACKUP),netChangedBlocks:net,dimensions:{merchant:[31,29],food:[17,21],herbsMain:[17,19],herbsBay:[7,15]},floorY:103,upperFloorY:110,roofColumns,raisedColumns,protectedPoints,markerPoints,barrels,signMoves,stairTreads,shrubRelocation:{blocks:shrub,offset:shrubOffset},verification:{webGL:false,inGame:false,deployed:false}};
const baseline={...m,definitions:defs,blocks:bytes.toString('base64')},staged={...m,blocks:Buffer.from(data.buffer).toString('base64')};report.validation=verifyExpansion(staged,baseline,report);
const patched=patch(m,data,original,source);let moved=0;const result=patchRegion(patched.bytes,t=>{let dirty=false;for(const e of t.value.block_entities?.value.values||[]){const from=[e.x.value,e.y.value,e.z.value],s=signMoves.find(s=>s.from.every((v,i)=>v===from[i]));if(s){assert.equal(e.id.value,'minecraft:sign');[e.x.value,e.y.value,e.z.value]=s.to;dirty=true;moved++;}}return dirty;});assert.equal(moved,3);report.changedChunks=patched.changed;report.resultHash=hash(result.bytes);
fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'dry-run-report.json'),JSON.stringify(report,null,2));fs.writeFileSync(path.join(STAGE,'planned-expansion.mca'),result.bytes);
if(!process.argv.includes('--apply')){console.log(JSON.stringify({mode:'dry-run',netChangedBlocks:net,changedChunks:patched.changed,validation:report.validation}));process.exit(0);}
assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent edit before backup');if(!fs.existsSync(BACKUP)){fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});}else assert.equal(hash(fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca'))),'3de64fce5d84eaea219f737a267a8863ccec7bea2c4449828b755fb82ad78f02','Backup changed');assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent edit before write');fs.writeFileSync(FILE+'.pending',result.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),sb=Buffer.from(saved.blocks,'base64'),want=m.definitions.map(key),got=saved.definitions.map(key);for(let i=0;i<data.length;i++)assert.equal(got[sb.readUInt16LE(i*2)],want[data[i]],'Saved semantic mismatch '+i);report.validation=verifyExpansion(saved,baseline,report);fs.writeFileSync(RF,JSON.stringify(report,null,2));
market.bounds=[41,175,141,244];market.stage='expanded merchant/food/herb market; external glass bay; pending visual acceptance';market.expansion={report:'market-expansion-v2-report.json',dimensions:report.dimensions,floorCells:report.validation.floorCells};market.shops[0]={...market.shops[0],bounds:[50,177,90,217],localSize:[31,29],center:[70,103,197],entrance:[82,104,206],corners:[[-15,-14],[15,-14],[15,14],[-15,14]].map(p=>world(...p)),roofRidgeY:126};market.shops[1]={...market.shops[1],bounds:[94,177,110,197],localSize:[17,21],center:[102,103,187],entrance:[102,104,197],roofRidgeY:118,chimneyTopY:121};market.shops[2]={...market.shops[2],bounds:[115,179,138,197],localSize:[17,19],center:[123,103,188],entrance:[123,104,197],bayBounds:[132,182,138,196],roofRidgeY:121};
for(const s of market.shops){delete s.foundationTrim;s.stage='expanded furnished market shop';s.decoration={postStyle:'timber bays',roofOverhang:1,windowFrames:true,entranceCanopy:true};}
market.eaves={supersededReport:'market-eaves-report.json',currentReport:'market-expansion-v2-report.json',projection:1};market.buildingDetails=market.shops.map(s=>({id:s.id,localSize:s.localSize,bounds:s.bounds,roofTopY:s.roofRidgeY,...(s.chimneyTopY?{chimneyTopY:s.chimneyTopY}:{})}));
manifest.rooms=manifest.rooms.map(r=>r.id==='04-market'?market:r);manifest.blockStates=saved.definitions;fs.writeFileSync(path.join(DIR,'map-manifest.json'),JSON.stringify(manifest,null,2));const zf=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id==='04-market'?market:z),null,2));console.log(JSON.stringify({mode:'applied',netChangedBlocks:net,changedChunks:patched.changed,resultHash:report.resultHash,validation:report.validation}));
