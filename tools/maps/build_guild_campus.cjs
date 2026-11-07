// Guarded redevelopment of zone 06 only. No server writes, entities, or world regeneration.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{patchRegion}=require('./nbt-edit.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-guild-campus/starter_sky_land_spawn_v1'),STAGE=path.join(ROOT,'archive/map-edit-staging/guild-campus-20261030'),REPORT=path.join(DIR,'guild-campus-report.json');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'),'Use --apply');const observed=fs.readFileSync(FILE),restage=process.argv.includes('--restage');
if(fs.existsSync(REPORT)){assert(restage,'Already built; inspect instead of repeating');assert.equal(hash(observed),JSON.parse(fs.readFileSync(REPORT)).resultHash,'Concurrent change; cannot restage');}
else if(fs.existsSync(BACKUP))assert.equal(hash(observed),hash(fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca'))),'Backup differs');
const source=restage?fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca')):observed,m=createLibrary(restage?path.dirname(BACKUP):path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),bytes=Buffer.from(m.blocks,'base64'),S=m.size;
assert(m.high>=160);const data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);const original=data.slice(),originalDefinitions=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
const at=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>data[at(x,y,z)],def=(x,y,z)=>m.definitions[get(x,y,z)],name=(x,y,z)=>def(x,y,z).Name.slice(10);
function id(n,p){if(n==='grass_block'&&!p)p={snowy:'false'};const d={Name:n.startsWith('minecraft:')?n:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){assert(m.definitions.length<65535);ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const AIR=id('air'),slab={type:'bottom',waterlogged:'false'},fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'},stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'});
function allowed(x,y,z){return x>=203&&x<=313&&z>=102&&z<=246&&y>=24&&y<=155&&!(x<=220&&z<=124)&&!(x<=208&&z>=150)&&!(x>=277&&z>=214);}
const live=JSON.parse(fs.readFileSync(path.join(STAGE,'live-protected-points.json'))),protectedNPCs=live.npcs.map(n=>({name:n.name,cell:n.pos.map(Math.floor),home:n.home})),npcColumns=new Map();
for(const n of protectedNPCs){const [cx,feet,cz]=n.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)npcColumns.set((cx+dx)+','+(cz+dz),feet);}
function put(x,y,z,n,p){assert(allowed(x,y,z),'Outside edit mask '+[x,y,z]);const feet=npcColumns.get(x+','+z);let v=id(n,p);if(feet&&y>=feet&&y<=feet+2)v=AIR;if(feet&&y===feet-1){const old=original[at(x,y,z)];v=originalDefinitions[old].Name==='minecraft:air'?id('dark_oak_planks'):old;}
 if(x===244&&y===110&&z===195)v=original[at(x,y,z)];data[at(x,y,z)]=v;}
function fill(x1,y1,z1,x2,y2,z2,n,p){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}
const terrainNames=new Set(['grass_block','dirt','coarse_dirt','podzol','stone','andesite','granite','diorite']);
const ground=new Int16Array(S*S).fill(-1),bottom=new Int16Array(S*S).fill(-1),target=new Int16Array(S*S).fill(-1),hot=new Uint8Array(S*S),routes=[],signs=[],features=[],innRooms=[];
for(let z=102;z<=246;z++)for(let x=203;x<=313;x++)for(let y=m.low;y<=114;y++)if(terrainNames.has(name(x,y,z))){if(bottom[z*S+x]<0)bottom[z*S+x]=y;ground[z*S+x]=y;}
function mark(x,z,y,clear=true){if(!allowed(x,y,z))return;const k=z*S+x;target[k]=Math.max(target[k],y);if(clear)hot[k]=1;}
function rect(x1,z1,x2,z2,y){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)mark(x,z,y);}
function ellipse(cx,cz,rx,rz,y){for(let z=Math.ceil(cz-rz);z<=Math.floor(cz+rz);z++)for(let x=Math.ceil(cx-rx);x<=Math.floor(cx+rx);x++)if(((x-cx)/rx)**2+((z-cz)/rz)**2<=1)mark(x,z,y);}
// Complete t106 asset is transplanted, rather than slicing its crown around the training floor.
const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures/t106.nbt')))),treeFrom=[234,103,134],treeTo=[284,111,104];let removedTree=0;
for(const block of asset.blocks){const [x,y,z]=block.pos.map((v,i)=>v+treeFrom[i]);assert.equal(key(def(x,y,z)),key(asset.palette[block.state]),'Recorded tree modified; inspect before moving');put(x,y,z,'air');removedTree++;}
rect(230,169,258,203,107);rect(209,144,231,191,107);rect(227,149,247,169,107);rect(231,122,267,151,113);rect(270,125,298,149,113);ellipse(294,117,18,14,110);ellipse(244,224,21,19,107);
rect(222,188,239,201,110);rect(249,198,253,204,110);rect(249,149,253,171,110);rect(265,134,273,140,113);rect(257,176,272,182,110);rect(270,204,274,218,107);rect(263,216,276,220,107);
const radians=Math.PI/9,C=Math.cos(radians),SN=Math.sin(radians),innCenter=[282,183],toWorld=(u,v)=>[Math.round(innCenter[0]+u*C-v*SN),Math.round(innCenter[1]+u*SN+v*C)];
function innLocal(x,z){const dx=x-innCenter[0],dz=z-innCenter[1];return [Math.round(dx*C+dz*SN),Math.round(-dx*SN+dz*C)];}
for(let z=159;z<=205;z++)for(let x=261;x<=305;x++){const [u,v]=innLocal(x,z);if(Math.abs(u)<=14&&Math.abs(v)<=17)mark(x,z,107);}
// Old hall and its projecting porch must not survive between the new wings.
for(let z=163;z<=215;z++)for(let x=219;x<=269;x++){if(z>204&&(x<229||x>259))continue;hot[z*S+x]=1;target[z*S+x]=Math.max(target[z*S+x],z>=207?107:107);}
const terrainCore=target.slice();
// Six-block natural transitions around the core. Large height differences become intentional retaining terraces.
for(let z=102;z<=246;z++)for(let x=203;x<=313;x++){const k=z*S+x;if(terrainCore[k]<0)continue;for(let dz=-6;dz<=6;dz++)for(let dx=-6;dx<=6;dx++){const xx=x+dx,zz=z+dz;if(!allowed(xx,107,zz)||Math.hypot(dx,dz)>6)continue;const j=zz*S+xx;if(terrainCore[j]>=0)continue;const g=ground[j],influence=Math.max(0,1-Math.hypot(dx,dz)/7),height=g>=0?Math.round(g+(terrainCore[k]-g)*influence):Math.round(terrainCore[k]-Math.hypot(dx,dz)*.6);target[j]=Math.max(target[j],height);}}
let newLandColumns=0,raisedColumns=0;
for(let z=102;z<=246;z++)for(let x=203;x<=313;x++){
 const k=z*S+x,top=target[k],g=ground[k];if(top<0||!allowed(x,top,z))continue;
 // Never blanket-delete vegetation outside the actual development core.
 if(hot[k]){for(let y=Math.max(95,g+1);y<=145;y++)put(x,y,z,'air');}
 if(g<0){newLandColumns++;let edge=12;for(let dz=-12;dz<=12;dz++)for(let dx=-12;dx<=12;dx++){const xx=x+dx,zz=z+dz;if(xx<203||xx>313||zz<102||zz>246||target[zz*S+xx]<0)edge=Math.min(edge,Math.hypot(dx,dz));}const cliff=Math.max(24,Math.round(top-15-edge*4+3*Math.sin(x*.17+z*.13)));for(let y=cliff;y<=top;y++)put(x,y,z,y===top?'grass_block':y>=top-3?'dirt':(x+y+z)%17===0?'andesite':'stone');}
 else{if(top>g)raisedColumns++;for(let y=g+1;y<=top;y++)put(x,y,z,y===top?'grass_block':y>=top-3?'dirt':(x+y+z)%19===0?'andesite':'stone');if(hot[k]&&top<=g)put(x,top,z,'grass_block');}
}
// Stable building pads have stone plinths. Shells never rest on a one-layer air platform.
function support(x,z,floor,material='dark_oak_planks'){let y=floor-1;while(y>=24&&name(x,y,z)==='air'){put(x,y,z,'stone_bricks');y--;}put(x,floor,z,material);}
function clear(x,y1,z,y2){for(let y=y1;y<=y2;y++)put(x,y,z,'air');}
function carve(x1,y1,z1,x2,y2,z2){fill(x1,y1,z1,x2,y2,z2,'air');}
function boxRoom(label,b,f,h,options={}){
 const [x1,z1,x2,z2]=b,cx=(x1+x2)/2,cz=(z1+z2)/2,hw=(x2-x1)/2;
 for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){
  support(x,z,f);clear(x,f+1,z,f+h+2);const edge=x===x1||x===x2||z===z1||z===z2;if(!edge)continue;
  const post=(x===x1||x===x2)&&((z-z1)%6===0||z===z2)||(z===z1||z===z2)&&((x-x1)%6===0||x===x2);
  for(let y=f+1;y<=f+h;y++)put(x,y,z,post?'dark_oak_log':y<=f+1?'stone_bricks':y===f+7?'dark_oak_planks':'calcite',post?{axis:'y'}:undefined);
  if(!post){const t=x===x1||x===x2?z-z1:x-x1;if(t%6>=2&&t%6<=4){for(let y=f+3;y<=f+5;y++)put(x,y,z,'purple_stained_glass');put(x,f+2,z,'stone_brick_stairs',{...stair(x===x1?'west':x===x2?'east':z===z1?'north':'south'),half:'top'});put(x,f+6,z,'dark_oak_planks');}if(h>10&&t%6>=2&&t%6<=4){for(let y=f+10;y<=f+12;y++)put(x,y,z,'purple_stained_glass');put(x,f+9,z,'stone_brick_stairs',{...stair(x===x1?'west':x===x2?'east':z===z1?'north':'south'),half:'top'});put(x,f+13,z,'dark_oak_planks');}}
 }
 for(let z=z1-1;z<=z2+1;z++)for(let x=x1-1;x<=x2+1;x++){
  const yy=f+h+1+Math.floor(Math.max(0,hw-Math.abs(x-cx))*.75),rim=z===z1-1||z===z2+1;
  put(x,yy,z,rim?'stone_brick_stairs':'purple_concrete',rim?stair(x<=cx?'east':'west'):undefined);if(!rim&&Math.abs(x-cx)>hw-1)put(x,yy+1,z,'purpur_slab',slab);
  if((z===z1||z===z2)&&x>=x1&&x<=x2)for(let y=f+h+1;y<yy;y++){const win=Math.abs(x-cx)<=2&&y>=f+h+3&&y<=yy-2;put(x,y,z,win?'purple_stained_glass':Math.abs(x-cx)===3?'dark_oak_log':'calcite',Math.abs(x-cx)===3&&!win?{axis:'y'}:undefined);}
 }
 features.push({kind:label,bounds:b,floorY:f,clearInside:[x2-x1-1,z2-z1-1]});
}
function door(x,z,f,axis,width=3,height=5){const r=Math.floor(width/2);if(axis==='x')carve(x-r,f+1,z-1,x+r,f+height,z+1);else carve(x-1,f+1,z-r,x+1,f+height,z+r);}
function lamp(x,y,z){put(x,y,z,'stone_bricks');put(x,y+1,z,'oak_fence',fence);put(x,y+2,z,'oak_fence',fence);put(x,y+3,z,'lantern',{hanging:'false',waterlogged:'false'});}
function table(x,z,f,w=3,d=3){for(let dz=0;dz<d;dz++)for(let dx=0;dx<w;dx++){put(x+dx,f+1,z+dz,'spruce_fence',fence);put(x+dx,f+2,z+dz,'spruce_slab',slab);}}
function bench(x,z,f,facing,length=3){for(let i=0;i<length;i++)put(x+i,f+1,z,'spruce_stairs',stair(facing));}
function shelf(x,z,f,h=4){for(let y=f+1;y<=f+h;y++)put(x,y,z,'bookshelf');}
function sign(x,y,z,facing,text){put(x,y,z,'oak_wall_sign',{facing,waterlogged:'false'});signs.push({x,y,z,lines:text});}
// Main hall: double height with a real U mezzanine, four usable upper areas and a returned stair.
boxRoom('main-hall',[232,171,256,199],110,14);door(244,199,110,'x',7,7);door(251,171,110,'x',5,6);door(232,183,110,'z',5,5);door(256,179,110,'z',5,5);
for(let z=172;z<=198;z++)for(let x=233;x<=255;x++)if(x<=239||x>=249||z<=177)put(x,118,z,'dark_oak_planks');
for(let z=179;z<=198;z++)for(const x of [240,248])put(x,119,z,'dark_oak_fence',fence);
for(let i=0;i<4;i++){const z=181-i,y=111+i;for(let x=234;x<=236;x++){put(x,y,z,'spruce_stairs',stair('north'));clear(x,y+1,z,121);}}
fill(234,114,175,242,114,177,'dark_oak_planks');
for(let i=0;i<4;i++){const z=178+i,y=115+i;for(let x=240;x<=242;x++){put(x,y,z,'spruce_stairs',stair('south'));clear(x,y+1,z,122);}}
fill(237,118,182,242,118,184,'dark_oak_planks');carve(240,119,181,242,122,185);
// Upper partitioned office and archives in west gallery; meeting room and honor displays east.
for(const z of [190]){fill(233,119,z,239,122,z,'dark_oak_planks');fill(249,119,z,255,122,z,'dark_oak_planks');carve(236,119,z,237,121,z);carve(251,119,z,252,121,z);}
table(234,185,118,3,2);shelf(233,188,118);shelf(234,196,118);shelf(235,196,118);table(250,184,118,4,3);
for(const [x,z,n]of [[251,194,'gold_block'],[254,194,'cut_copper'],[251,197,'iron_block']]){put(x,119,z,'chiseled_stone_bricks');put(x,120,z,n);}
for(const [i,rank]of ['F','E','D','C','B','A','S'].entries()){const x=234+i*3;put(x,119,173,i<3?'cut_copper':i<5?'iron_block':'gold_block');sign(x,119,174,'south',['GUILD RANK '+rank]);}
sign(238,121,191,'south',['ARCHIVES']);sign(254,121,191,'south',['HALL OF HONOR']);
fill(240,111,173,248,111,175,'spruce_planks');fill(240,112,175,248,112,175,'spruce_slab',slab);sign(239,111,174,'west',['GUILD RECEPTION','REGISTER / REWARD','RANK EXAMS']);
for(const x of [234,254]){shelf(x,172,110,5);shelf(x,173,110,5);}
// Projecting porch and layered plinths give the facade depth without narrowing its entrance.
for(let z=200;z<=203;z++)for(let x=237;x<=251;x++)support(x,z,110,'stone_bricks');
for(const x of [238,250]){fill(x,111,202,x,117,202,'dark_oak_log',{axis:'y'});put(x,118,202,'chiseled_stone_bricks');}
for(let x=237;x<=251;x++)for(let z=200;z<=203;z++)put(x,118+Math.floor((7-Math.abs(x-244))*.6),z,'purple_concrete');
for(const x of [231,257])for(const z of [172,179,187,196]){fill(x,108,z,x,113,z,'stone_bricks');put(x,114,z,'stone_brick_stairs',stair(x<244?'east':'west'));}
for(let z=171;z<=199;z++)put(244,135,z,'dark_oak_log',{axis:'z'});for(const z of [171,199]){put(244,136,z,'chiseled_stone_bricks');put(244,137,z,'gold_block');}
for(const x of [236,252]){fill(x,113,200,x,117,200,'purple_wool');put(x,115,201,'white_wool');put(x,118,200,'stone_brick_slab',slab);}
// Western stepped wing, octagonal map bay and adjacent party room.
boxRoom('quest-wing',[211,164,229,188],110,7);door(229,183,110,'z',5);door(220,164,110,'x',5);
for(let n=0;n<6;n++){const z=167+n*3;fill(212,112,z,212,115,z+1,'birch_planks');sign(213,113,z,'east',[["HUNT","EXPLORE","RESCUE","SUPPLY","INVESTIGATE","CAMPAIGN"][n]]);}
for(let z=146;z<=162;z++)for(let x=210;x<=226;x++)if(Math.max(Math.abs(x-218),Math.abs(z-154))<=8&&Math.abs(x-218)+Math.abs(z-154)<=12){support(x,z,110);clear(x,111,z,126);const dx=x-218,dz=z-154,edge=Math.max(Math.abs(dx),Math.abs(dz))===8||Math.abs(dx)+Math.abs(dz)===12;if(edge)for(let y=111;y<=118;y++)put(x,y,z,y<=111?'stone_bricks':y>=113&&y<=115?'purple_stained_glass':'calcite');const r=Math.max(Math.abs(dx),Math.abs(dz),Math.ceil((Math.abs(dx)+Math.abs(dz))*2/3));put(x,119+Math.floor((8-r)*.65),z,'purple_concrete');}
door(218,162,110,'x',5);door(226,155,110,'z',3);table(216,152,110,5,4);sign(218,117,147,'south',['WORLD SITUATION']);
boxRoom('party-room',[230,152,244,164],110,7);door(230,155,110,'z',3);door(244,159,110,'z',3);door(238,164,110,'x',3);table(234,157,110,5,3);bench(235,161,110,'north',3);bench(235,155,110,'south',3);
// A map mosaic is decorative blocks, not a false claim of a working dynamic map system.
for(let x=214;x<=222;x++)for(let y=113;y<=116;y++)put(x,y,147,(x+y)%4===0?'green_wool':(x*3+y)%5===0?'sandstone':'blue_wool');
// Patio/lounge retains standing service points outside the narrower central atrium.
fill(223,110,189,239,110,200,'dark_oak_planks');table(224,191,110,3,2);bench(224,194,110,'north',3);shelf(224,198,110);lamp(225,110,199);
// Roofed circulation galleries join the open campus instead of disconnected mini-houses.
function gallery(x1,z1,x2,z2,f){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){support(x,z,f);clear(x,f+1,z,f+5);put(x,f+6,z,'spruce_slab',slab);}for(const [x,z]of [[x1,z1],[x1,z2],[x2,z1],[x2,z2]])fill(x,f+1,z,x,f+5,z,'dark_oak_log',{axis:'y'});}
gallery(227,154,229,156,110);gallery(230,181,231,185,110);gallery(245,157,248,161,110);gallery(236,165,240,168,110);gallery(257,177,270,181,110);
// East inn, rasterized in its local 23x29 axes: floors and all walls really turn by twenty degrees.
const innMask=new Set(),innBounds=[999,999,-999,-999];
for(let z=162;z<=204;z++)for(let x=263;x<=303;x++){
 const [u,v]=innLocal(x,z);if(Math.abs(u)>11||Math.abs(v)>14)continue;innMask.add(x+','+z);innBounds[0]=Math.min(innBounds[0],x);innBounds[1]=Math.min(innBounds[1],z);innBounds[2]=Math.max(innBounds[2],x);innBounds[3]=Math.max(innBounds[3],z);support(x,z,110);clear(x,111,z,134);put(x,117,z,'dark_oak_planks');
 const edge=Math.abs(u)===11||Math.abs(v)===14;if(edge){const post=Math.abs(u)===11&&(v+14)%6===0||Math.abs(v)===14&&(u+11)%5===0;for(let y=111;y<=123;y++){let n=post?'dark_oak_log':y===111?'stone_bricks':y===117?'dark_oak_planks':'calcite';const t=Math.abs(u)===11?v+14:u+11;if(!post&&t%6>=2&&t%6<=4&&(y>=113&&y<=115||y>=120&&y<=122))n='purple_stained_glass';put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);}}
 const yy=124+Math.floor((11-Math.abs(u))*.65);put(x,yy,z,'purple_concrete');if(Math.abs(v)===14){put(x,yy+1,z,'stone_brick_slab',slab);for(let y=124;y<yy;y++)put(x,y,z,Math.abs(u)<=2?'purple_stained_glass':'calcite');}
}
function localFill(u1,y1,v1,u2,y2,v2,n,p){for(const k of innMask){const [x,z]=k.split(',').map(Number),[u,v]=innLocal(x,z);if(u>=u1&&u<=u2&&v>=v1&&v<=v2)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}}
localFill(-11,111,-2,-10,115,2,'air');
// Ground floor: bar, kitchen, fireplace-style hearth, tables and supply shelving.
localFill(-8,111,-12,8,111,-10,'spruce_planks');localFill(-8,112,-10,8,112,-10,'spruce_slab',slab);localFill(8,111,-7,9,111,1,'smooth_stone');
for(const [u,v]of [[-6,-3],[3,-3],[4,5]]){const [x,z]=toWorld(u,v);table(x,z,110,3,2);bench(x,z+3,110,'north',3);}
localFill(-9,111,-12,-9,114,-11,'bookshelf');localFill(8,111,6,10,113,8,'stone_bricks');localFill(9,114,7,9,130,7,'brick_slab',{type:'double',waterlogged:'false'});
// Four proper guest rooms upstairs, two per side. Preserve a five-wide central corridor.
for(const [u1,u2]of [[-10,-3],[3,10]])for(const [v1,v2]of [[-12,-5],[-3,4]]){
 localFill(u1,118,v1,u2,121,v1,'spruce_planks');localFill(u1,118,v2,u2,121,v2,'spruce_planks');const wall=u2<0?u2:u1;localFill(wall,118,v1,wall,121,v2,'spruce_planks');const dv=Math.round((v1+v2)/2);localFill(wall,118,dv-1,wall,120,dv+1,'air');
 const [ax,az]=toWorld(wall<0?wall+1:wall-1,dv),[bx,bz]=toWorld(wall<0?u1+2:u2-2,dv);put(bx,118,bz,'white_wool');put(bx,118,bz+1,'purple_wool');put(bx,119,bz,'white_carpet');
 innRooms.push({localBounds:[u1,v1,u2,v2],floorY:117,clearArea:36,access:[ax,118,az],decorativeBed:[bx,118,bz]});
}
// Cardinal internal staircase fits in the inn's southern lobby, so stair treads do not rotate incorrectly.
for(let i=0;i<4;i++){const z=191-i,y=111+i;for(let x=272;x<=274;x++){assert(innMask.has(x+','+z));put(x,y,z,'spruce_stairs',stair('north'));clear(x,y+1,z,121);}}
fill(272,114,185,280,114,187,'dark_oak_planks');for(let i=0;i<3;i++){const z=188+i,y=115+i;for(let x=278;x<=280;x++){put(x,y,z,'spruce_stairs',stair('south'));clear(x,y+1,z,122);}}fill(278,117,191,281,117,193,'dark_oak_planks');
// Inn terrace with separate departure walk, without changing the portal bridge.
for(let z=200;z<=204;z++)for(let x=269;x<=278;x++)support(x,z,110);table(274,201,110,3,2);bench(274,204,110,'north',3);
// Rear training: entire 25x21 usable floor retained; spectator architecture is outside this rectangle.
for(let z=124;z<=148;z++)for(let x=234;x<=264;x++){support(x,z,113,x>=237&&x<=261&&z>=126&&z<=146?'sandstone':'stone_bricks');clear(x,114,z,135);}
for(let z=126;z<=146;z++)for(let x=237;x<=261;x++)put(x,113,z,'sandstone');
for(const x of [234,264])for(let z=124;z<=148;z++){put(x,114,z,'stone_brick_slab',slab);if(z%6===4){fill(x,114,z,x,119,z,'dark_oak_log',{axis:'y'});put(x,120,z,'lantern',{hanging:'false',waterlogged:'false'});}}
fill(234,114,124,264,114,125,'dark_oak_planks');for(let x=234;x<=264;x++)for(let z=124;z<=125;z++)put(x,120,z,'purple_concrete');bench(238,124,114,'south',5);bench(252,124,114,'south',5);
for(let z=126;z<=146;z++)for(const [x1,x2]of [[234,236],[262,264]])for(let x=x1;x<=x2;x++){put(x,114,z,x===236||x===262?'stone_brick_stairs':'dark_oak_planks',x===236?stair('west'):x===262?stair('east'):undefined);put(x,120,z,'purple_concrete');}
// Dummies and exam markers remain outside the through-walk strip in the open practice floor.
for(const [x,z]of [[240,130],[245,130],[250,130]]){put(x,114,z,'oak_fence',fence);put(x,115,z,'hay_block',{axis:'y'});put(x,116,z,'carved_pumpkin',{facing:'south'});}
for(let z=136;z<=143;z++)for(let x=239;x<=246;x++)if(x===239||x===246||z===136||z===143)put(x,114,z,'white_carpet');
// Supplies, first aid and token exchange share a small service hall at the same level as training.
boxRoom('supplies-first-aid',[273,128,295,146],113,8);door(273,137,113,'z',5);door(284,146,113,'x',3);fill(289,114,130,293,114,132,'spruce_planks');sign(291,114,133,'south',['GUILD EXCHANGE']);
for(const x of [275,277,279])shelf(x,130,113,4);for(const [x,z]of [[288,140],[291,140]]){put(x,114,z,'white_wool');put(x,114,z+1,'white_wool');put(x,115,z,'red_carpet');}sign(294,117,140,'west',['FIRST AID']);
gallery(265,135,272,139,113);
// Restore all required connecting surfaces AFTER shells, and record paths for independent surface checks.
function flatPath(label,points,f,width=3){const out=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],steps=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]));for(let i=0;i<=steps;i++){const x=Math.round(a[0]+(b[0]-a[0])*i/Math.max(1,steps)),z=Math.round(a[1]+(b[1]-a[1])*i/Math.max(1,steps));for(let dz=-Math.floor(width/2);dz<=Math.floor(width/2);dz++)for(let dx=-Math.floor(width/2);dx<=Math.floor(width/2);dx++){if(!allowed(x+dx,f,z+dz))continue;support(x+dx,z+dz,f,'stone_bricks');clear(x+dx,f+1,z+dz,f+3);}out.push([x,f,z]);}}routes.push({name:label,points:out});}
flatPath('main-aisle',[[244,198],[244,180]],110,7);flatPath('reception-access',[[244,180],[244,177]],110,3);
flatPath('west-services',[[244,183],[216,183],[216,173]],110,3);flatPath('party',[[251,167],[238,167],[238,162]],110,3);flatPath('map-room',[[218,172],[218,158]],110,3);flatPath('map-party',[[218,158],[222,158],[222,155],[237,155]],110,3);
flatPath('inn-entry',[[254,179],[274,179]],110,5);flatPath('rear-approach',[[251,174],[251,157]],110,5);
flatPath('training-service',[[259,137],[278,137]],113,5);flatPath('service-interior',[[278,137],[284,137]],113,3);
flatPath('inn-departure',[[272,198],[272,205]],110,3);flatPath('portal-connection',[[272,212],[272,218],[276,218]],107,3);
// Front court: oval paving, original memorial and bench voxels restored below after laying routes.
for(let z=207;z<=241;z++)for(let x=225;x<=263;x++)if(((x-244)/19)**2+((z-224)/17)**2<=1){support(x,z,107,(x+z)%8===0?'polished_andesite':'stone_bricks');clear(x,108,z,112);}
flatPath('west-city-link',[[216,215],[238,215]],107,3);flatPath('court-west-bypass',[[238,215],[237,224],[237,237]],107,3);flatPath('court-east-bypass',[[250,215],[251,224],[251,237]],107,3);flatPath('front-upper-landing',[[244,207],[244,212],[238,212]],107,3);
function stairRun(label,cx,z0,topFloor,rows,width,dir){const pts=[];for(const [z,y,isStair]of rows){for(let x=cx-Math.floor(width/2);x<=cx+Math.floor(width/2);x++){support(x,z,y,'stone_bricks');if(isStair)put(x,y,z,'stone_brick_stairs',stair(dir));clear(x,y+1,z,y+4);}pts.push([cx,y,z]);}routes.push({name:label,points:pts});}
stairRun('front-stair',244,207,110,[[207,107,0],[206,107,0],[205,108,1],[204,108,0],[203,109,1],[202,109,0],[201,110,1],[200,110,0],[199,110,0]],9,'north');
stairRun('rear-stair',251,157,113,[[157,110,0],[156,111,1],[155,111,0],[154,112,1],[153,112,0],[152,113,1],[151,113,0],[150,113,0],[149,113,0],[148,113,0],[147,113,0]],5,'north');
stairRun('east-departure-stair',272,212,110,[[212,107,0],[211,108,1],[210,108,0],[209,109,1],[208,109,0],[207,110,1],[206,110,0],[205,110,0]],5,'north');
for(let z=126;z<=146;z++)for(let x=237;x<=261;x++)put(x,113,z,'sandstone');
routes.push({name:'upper-main-flight1',points:[[235,110,182],[235,111,181],[235,112,180],[235,113,179],[235,114,178],[235,114,177],[241,114,177],[241,115,178],[241,116,179],[241,117,180],[241,118,181],[241,118,182],[238,118,182]]});
routes.push({name:'upper-inn-stair',points:[[273,110,192],[273,111,191],[273,112,190],[273,113,189],[273,114,188],[273,114,187],[279,114,187],[279,115,188],[279,116,189],[279,117,190],[279,117,191]]});
// Preserve original memorial precisely, rather than approximating its former shape.
for(let z=222;z<=226;z++)for(let x=242;x<=246;x++)for(let y=107;y<=117;y++)if(original[at(x,y,z)]!==AIR)put(x,y,z,originalDefinitions[original[at(x,y,z)]].Name,originalDefinitions[original[at(x,y,z)]].Properties);
for(const [x,z,facing,len]of [[229,224,'east',1],[237,239,'north',15],[257,224,'west',1]]){if(len===1)for(let zz=z-5;zz<=z+5;zz++)put(x,108,zz,'spruce_stairs',stair(facing));else bench(x,z,107,facing,len);}
// Low borders, planted corners and lamp rhythm are deliberate, not a blanket random decoration pass.
for(const [cx,cz]of [[226,209],[262,209],[227,238],[261,238],[269,153],[303,153],[209,193]]){
 const f=target[cz*S+cx]>=0?target[cz*S+cx]:107;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const x=cx+dx,z=cz+dz;if(!allowed(x,f,z)||npcColumns.has(x+','+z))continue;put(x,f,z,'dirt');put(x,f+1,z,(dx+dz)%2?'allium':'white_tulip');}lamp(cx+2,f,cz);
}
for(const [x,z,f]of [[236,207,107],[252,207,107],[224,211,107],[264,215,107],[234,233,107],[254,233,107],[232,151,113],[266,151,113],[271,128,113],[297,146,113]])lamp(x,f,z);
// Court curbs and surface lights sit outside approach gaps. They never reduce the stated clear walkway.
for(let z=207;z<=241;z++)for(let x=225;x<=263;x++){const r=((x-244)/19)**2+((z-224)/17)**2;if(r<.88||r>1)continue;if(Math.abs(x-244)<=6&&(z<=213||z>=238)||z>=212&&z<=220)continue;if(name(x,108,z)==='air')put(x,108,z,'stone_brick_slab',slab);}
for(const [x,z]of [[235,215],[253,215],[235,232],[253,232],[241,237],[247,237]])put(x,107,z,'sea_lantern');
const routeMask=new Set();for(const r of routes)for(const [x,y,z]of r.points)for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)routeMask.add((x+dx)+','+(z+dz));
// Colored block mosaics make the two planning tables recognizable without relying on item entities.
for(const [x1,z1,w,d]of [[216,152,5,4],[234,157,5,3]])for(let dz=0;dz<d;dz++)for(let dx=0;dx<w;dx++){put(x1+dx,111,z1+dz,(dx*3+dz*5)%5<2?'green_wool':(dx+dz)%7===0?'sandstone':'blue_wool');put(x1+dx,112,z1+dz,'air');}
for(let z=179;z<=197;z++)for(const x of [239,249])if(name(x,111,z)==='air'&&!npcColumns.has(x+','+z)&&!routeMask.has(x+','+z))put(x,111,z,'purple_carpet');
for(const z of [153,154,155])shelf(242,z,110,4);
let flowers=0,shrubs=0;for(let z=104;z<=243;z++)for(let x=205;x<=311;x++){
 const k=z*S+x,f=target[k];if(f<0||!allowed(x,f+2,z)||routeMask.has(x+','+z)||npcColumns.has(x+','+z)||name(x,f,z)!=='grass_block'||name(x,f+1,z)!=='air'||name(x,f+2,z)!=='air')continue;
 const n=(Math.imul(x,73856093)^Math.imul(z,19349663))>>>0;if(n%19===0){put(x,f+1,z,n%3===0?'allium':n%3===1?'white_tulip':'blue_orchid');flowers++;}else if(n%53===0){put(x,f+1,z,'oak_leaves',{distance:'7',persistent:'true',waterlogged:'false'});shrubs++;}
}
// Transplanted t106 keeps every original asset voxel. Root columns get explicit connected support.
let plantedTree=0;for(const block of asset.blocks){const [x,y,z]=block.pos.map((v,i)=>v+treeTo[i]),d=asset.palette[block.state];assert.equal(name(x,y,z),'air','Tree relocation collision '+[x,y,z]);put(x,y,z,d.Name,d.Properties);plantedTree++;if(block.pos[1]===0){for(let yy=y-1;yy>=24&&name(x,yy,z)==='air';yy--)put(x,yy,z,'dirt');}}
// Keep the actual saved guard patrol from the read-only server snapshot at its original floor level.
if(live.patrol?.length){const p=live.patrol.map(n=>n.Array);flatPath('existing-guard-patrol',p.map(n=>[n[0],n[2]]).concat([[p[0][0],p[0][2]]]),107,3);}
// Last operation: keep exact NPC positions and a connected standing pad around every service point.
for(const n of protectedNPCs){const [cx,feet,cz]=n.cell;for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const x=cx+dx,z=cz+dz;if(!allowed(x,feet,z))continue;support(x,z,feet-1,feet===111?'dark_oak_planks':'stone_bricks');clear(x,feet,z,feet+2);}}
// Semantic scope and unchanged guards before committing the staged region.
let net=0,erased=0;for(let i=0;i<data.length;i++)if(data[i]!==original[i]){net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(allowed(x,y,z));if(data[i]===AIR)erased++;}
assert(net>10000);assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent map edit');
if(!fs.existsSync(BACKUP)){fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});}
const patched=patch(m,data,original,source),tag=(type,value)=>({type,value});
const final=patchRegion(patched.bytes,t=>{const c=t.value,cx=c.xPos.value,cz=c.zPos.value,add=signs.filter(s=>Math.floor(s.x/16)===cx&&Math.floor(s.z/16)===cz);if(!add.length)return false;if(!c.block_entities)c.block_entities=tag(9,{type:10,values:[]});for(const s of add){const text=tag(10,{messages:tag(9,{type:8,values:Array.from({length:4},(_,i)=>JSON.stringify({text:s.lines[i]||''}))}),color:tag(8,'black'),has_glowing_text:tag(1,0)});c.block_entities.value.values.push({id:tag(8,'minecraft:sign'),x:tag(3,s.x),y:tag(3,s.y),z:tag(3,s.z),front_text:text,back_text:text,is_waxed:tag(1,1)});}return true;});
fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'planned-campus.mca'),final.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Concurrent edit before commit');fs.writeFileSync(FILE+'.pending',final.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),sb=Buffer.from(saved.blocks,'base64'),want=m.definitions.map(key),actual=saved.definitions.map(key);assert.equal(sb.length,bytes.length);for(let i=0;i<data.length;i++)assert.equal(actual[sb.readUInt16LE(i*2)],want[data[i]],'Saved mismatch '+i);
const report={dimensions:{main:[25,29],quest:[19,25],mapBay:[17,17],party:[15,13],innLocal:[23,29],innWorldBounds:innBounds,trainingClear:[25,21],trainingWithGallery:[31,25],services:[23,19],court:[39,35]},floors:{court:107,main:110,inn:110,training:113,services:113,innUpper:117,mezzanine:118},bounds:[203,24,102,313,155,246],backup:path.relative(ROOT,BACKUP),sourceHash:hash(source),resultHash:hash(final.bytes),netChangedBlocks:net,erasedOldBlocks:erased,changedChunks:patched.changed,newLandColumns,raisedColumns,features,innRooms,routes,protectedNPCs,liveBlockEntityPoints:live.blockEntities,originalWaypointFloor:originalDefinitions[original[at(244,110,195)]].Name.slice(10),treeRelocation:{asset:'t106',from:treeFrom,to:treeTo,removedTree,plantedTree},flowers,shrubs,signs:signs.length,savedSemanticComparison:true,outsideScopeChanges:0,verification:{webGL:false,inGame:false,deployed:false,serverNPCDataModified:false,localEntityFilesModified:false}};
fs.writeFileSync(REPORT,JSON.stringify(report,null,2));
const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));
const hall={id:'06-adventurers-hall',name:'Adventurers Guild Campus',bounds:[203,102,313,246],buildingBounds:[232,110,171,256,137,199],buildingSize:[25,29],center:[244,111,185],entrance:[244,111,199],front:'south (+Z)',floorY:110,courtyardFloorY:107,interiorBounds:[233,111,172,255,124,198],aisleWidth:7,gameplayConnected:false,report:'guild-campus-report.json'};
const roomLabels=[{id:'guild-quest-wing',name:'Quest Wing',center:[219,111,176],bounds:[211,164,229,188]},{id:'guild-map-room',name:'World Map',center:[218,111,154],bounds:[210,146,226,162]},{id:'guild-party-room',name:'Party Planning',center:[237,111,158],bounds:[230,152,244,164]},{id:'guild-inn',name:'Tavern & Inn',center:[282,111,183],bounds:innBounds},{id:'guild-training',name:'Training Court',center:[249,114,136],bounds:[234,124,264,148]},{id:'guild-supplies',name:'Supplies & First Aid',center:[284,114,137],bounds:[273,128,295,146]},{id:'guild-honor',name:'Hall of Honor',center:[252,119,194],bounds:[249,191,255,198]}];
manifest.adventurersHall=hall;manifest.guildCampus={bounds:hall.bounds,floors:report.floors,dimensions:report.dimensions,report:'guild-campus-report.json',status:'built-pending-verification'};
manifest.rooms=(manifest.rooms||[]).filter(r=>r.id!=='06-adventurers-hall'&&!String(r.id||'').startsWith('guild-')&&!['Dungeon','Party','PvP'].includes(r.name));manifest.rooms.push(hall,...roomLabels);manifest.blockStates=saved.definitions;manifest.description='Sky town · expanded adventurers guild campus, inn and rear training court';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
const zf=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id==='06-adventurers-hall'?hall:z),null,2));
console.log(JSON.stringify({netChangedBlocks:net,changedChunks:patched.changed,newLandColumns,raisedColumns,innRooms:innRooms.length,signs:signs.length,treeRelocation:report.treeRelocation,sourceHash:hash(source),resultHash:hash(final.bytes)}));
