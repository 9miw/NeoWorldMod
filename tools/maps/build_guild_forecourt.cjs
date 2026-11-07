// One scoped forecourt patch against the latest city. Never replay the guild generator.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{decode,encode}=require('./nbt-edit.cjs'),{verifyCourt}=require('./verify_guild_forecourt.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),RF=path.join(DIR,'guild-forecourt-report.json'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-guild-forecourt-20261101/starter_sky_land_spawn_v1'),STAGE=path.join(ROOT,'archive/map-edit-staging/guild-forecourt-20261101');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),observed=fs.readFileSync(FILE),restage=process.argv.includes('--restage');
if(fs.existsSync(RF)){assert(restage,'Forecourt already applied; inspect instead of replaying');assert.equal(hash(observed),JSON.parse(fs.readFileSync(RF)).resultHash,'Concurrent map edit');}else{assert(!restage);assert.equal(hash(observed),'4ae29206247c08e05c537b98de5c88355164d3a86a48219ba4718c86e823e166','Inspect latest source before staging');}
const source=restage?fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca')):observed,m=createLibrary(restage?path.dirname(BACKUP):path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),bytes=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
const original=data.slice(),defs=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),at=(x,y,z)=>(y-m.low)*S*S+z*S+x,before=(x,y,z)=>defs[original[at(x,y,z)]],get=(x,y,z)=>m.definitions[data[at(x,y,z)]],name=(x,y,z)=>get(x,y,z).Name.slice(10);
const allowed=(x,y,z)=>x>=225&&x<=263&&z>=207&&z<=241&&y>=100&&y<=119;
function id(n,p){if(n==='grass_block'&&!p)p={snowy:'false'};const d={Name:n.startsWith('minecraft:')?n:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
function put(x,y,z,n,p){assert(allowed(x,y,z),'Outside forecourt '+[x,y,z]);data[at(x,y,z)]=id(n,p);}
function fill(x1,y1,z1,x2,y2,z2,n,p){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)for(let y=y1;y<=y2;y++)put(x,y,z,n,p);}
const slab={type:'bottom',waterlogged:'false'},stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'};
const plants=new Set(['air','short_grass','fern','allium','white_tulip','blue_orchid']),natural=new Set(['stone','dirt','grass_block','andesite','cobblestone','mossy_cobblestone']);
let supportColumns=0;const supported=new Set();
function ground(x,z,n='stone_bricks'){
 if(!supported.has(x+','+z)){let base=106;while(base>=100&&plants.has(name(x,base,z)))base--;assert(base>=100,'No existing land under '+[x,z]);assert(natural.has(name(x,base,z))||['stone_bricks','polished_andesite','smooth_stone'].includes(name(x,base,z)),'Unsafe foundation '+[x,base,z,name(x,base,z)]);if(base<106)supportColumns++;for(let y=base+1;y<=106;y++)put(x,y,z,y>=105?'dirt':'stone');supported.add(x+','+z);}put(x,107,z,n);
}
// Compare all typed block entities, not just counts. No entity block is in the approved mask.
function entities(src){const out=[];for(let i=0;i<1024;i++){const l=src.readUInt32BE(i*4);if(!l)continue;const o=(l>>>8)*4096,len=src.readUInt32BE(o),t=decode(zlib.inflateSync(src.subarray(o+5,o+4+len)));for(const e of t.value.block_entities?.value.values||[]){assert(!allowed(e.x.value,e.y.value,e.z.value),'Protected block entity in edit area');out.push(hash(encode({name:'',value:e})));}}return out.sort();}
const originalEntities=entities(source),monument=[];
for(let z=222;z<=226;z++)for(let x=242;x<=246;x++)for(let y=108;y<=117;y++){const state=before(x,y,z);if(state.Name!=='minecraft:air')monument.push({position:[x,y,z],state});}
assert.equal(before(244,110,224).Name,'minecraft:quartz_pillar');assert(monument.length>40);
const shrubs=[{from:[258,108,211],to:[262,108,211],state:before(258,108,211)},{from:[233,108,240],to:[233,108,240],state:before(233,108,240)}];for(const e of shrubs)assert(e.state.Name.endsWith('_leaves'));
const known=new Set([...plants,'oak_fence','lantern','stone_brick_slab','spruce_stairs','polished_andesite','chiseled_stone_bricks','smooth_stone_slab','quartz_pillar','sea_lantern','purpur_slab']);
for(let z=207;z<=241;z++)for(let x=225;x<=263;x++)for(let y=108;y<=119;y++){
 const n=before(x,y,z).Name.slice(10);if(n.endsWith('_leaves')){assert(shrubs.some(e=>e.from.join(',')===[x,y,z].join(',')),'Unsurveyed canopy');}else assert(known.has(n),'Unsurveyed block '+[x,y,z,n]);put(x,y,z,'air');
}
const box=(x,z,a,b,c,d)=>x>=a&&x<=c&&z>=b&&z<=d;
const polygon=(x,z)=>Math.abs(x-244)<=19&&Math.abs(z-224)<=17&&Math.abs(x-244)+Math.abs(z-224)<=29;
const roofBox=(x,z)=>box(x,z,226,225,233,233)||box(x,z,254,208,261,216);
const route=(x,z)=>x>=240&&x<=248||box(x,z,225,213,239,217)||box(x,z,249,217,263,220)||box(x,z,236,215,238,237)||box(x,z,250,215,252,237);
const memorialBox=(x,z)=>box(x,z,254,230,260,236);
const inside=(x,z)=>polygon(x,z)||roofBox(x,z)||route(x,z)||memorialBox(x,z);
let paved=0,gardens=0;const gardenCells=[];
for(let z=207;z<=241;z++)for(let x=225;x<=263;x++){
 if(!inside(x,z)){const n=name(x,107,z);if(['stone_bricks','polished_andesite','smooth_stone','sea_lantern'].includes(n))ground(x,z,'grass_block');continue;}
 const boundary=[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!inside(x+dx,z+dz));
 const nearEdge=Math.abs(x-244)>15||z>237||z<211;
 const garden=!route(x,z)&&!roofBox(x,z)&&!memorialBox(x,z)&&nearEdge;
 if(garden){ground(x,z,'grass_block');gardenCells.push([x,z]);gardens++;}
 else{ground(x,z,boundary&&!route(x,z)?'deepslate_tiles':(x+2*z)%9===0?'polished_andesite':'stone_bricks');paved++;}
 if(boundary&&!route(x,z)&&!roofBox(x,z))put(x,108,z,'stone_brick_slab',slab);
}
// Flush compass: filled eight-point star, not a cross with disconnected diagonal dots.
for(let dz=-7;dz<=7;dz++)for(let dx=-7;dx<=7;dx++){
 const r=Math.hypot(dx,dz);if(r>7.5)continue;let n=r>6.7?'deepslate_tiles':'smooth_stone';
 const hi=Math.max(Math.abs(dx),Math.abs(dz)),lo=Math.min(Math.abs(dx),Math.abs(dz));
 if(lo<=Math.max(0,2-hi*.3)||hi<=4&&lo>=2&&hi-lo<=1)n='deepslate_tiles';
 if(dx===0||dz===0)n=Math.abs(dx)+Math.abs(dz)<=2?'purple_terracotta':'yellow_terracotta';
 if(dx===0&&dz===0)n='purple_terracotta';put(244+dx,107,224+dz,n);
}
for(const e of monument){const [x,y,z]=e.position;ground(x+13,z+9,'polished_andesite');put(x+13,y,z+9,e.state.Name,e.state.Properties);}
// Timber pergolas. Core posts inset from roof envelope by one block.
function pergola(x1,z1,x2,z2){
 for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)ground(x,z,'polished_andesite');
 for(const [x,z]of [[x1+1,z1+1],[x2-1,z1+1],[x1+1,z2-1],[x2-1,z2-1]]){fill(x,108,z,x,111,z,'dark_oak_log',{axis:'y'});put(x,107,z,'chiseled_stone_bricks');}
 for(let z=z1;z<=z2;z+=2)for(let x=x1;x<=x2;x++)put(x,112,z,'spruce_slab',slab);
 for(const x of [x1+1,x2-1])for(let z=z1;z<=z2;z++)if(name(x,111,z)==='air')put(x,111,z,'spruce_planks');
 for(const [x,z]of [[x1+2,z1+2],[x2-2,z2-2]]){put(x,111,z,'spruce_slab',slab);put(x,110,z,'lantern',{hanging:'true',waterlogged:'false'});}
}
pergola(226,225,233,233);pergola(254,208,261,216);
// Notice board is decorative; no block entity or gameplay integration is introduced.
fill(227,109,227,227,110,231,'spruce_planks');for(const z of [227,229,231])put(228,110,z,'birch_trapdoor',{facing:'east',half:'bottom',open:'true',powered:'false',waterlogged:'false'});
for(let x=228;x<=231;x++)put(x,108,232,'spruce_stairs',stair('north'));
for(const x of [255,260]){put(x,108,211,'dark_oak_fence',fence);put(x,109,211,'spruce_slab',slab);put(x,108,213,'spruce_stairs',stair('north'));}
// Supported low garden edges, flowers and individual benches, with breaks at all routes.
for(const [x,z]of gardenCells){if(name(x,108,z)!=='air')continue;put(x,108,z,(x+z)%4===0?'oak_leaves':(x+z)%2?'allium':'white_tulip',(x+z)%4===0?{distance:'1',persistent:'true',waterlogged:'false'}:undefined);}
for(const [x1,x2,z,facing]of [[231,235,237,'north'],[253,257,239,'north'],[227,231,211,'south']])for(let x=x1;x<=x2;x++)if(!route(x,z)&&!roofBox(x,z)){ground(x,z,'stone_bricks');put(x,108,z,'spruce_stairs',stair(facing));}
function pedestalLamp(x,z){ground(x,z,'stone_bricks');put(x,108,z,'chiseled_stone_bricks');put(x,109,z,'stone_brick_wall',{north:'none',south:'none',east:'none',west:'none',up:'true',waterlogged:'false'});put(x,110,z,'lantern',{hanging:'false',waterlogged:'false'});}
for(const p of [[229,218],[229,235],[235,240],[253,240],[261,225],[261,230],[253,207],[235,207],[225,221],[263,222],[233,209],[253,209]])pedestalLamp(...p);
// Purple-cream fabric standards, no banner entities. Keep outside 9-wide clear lane.
for(const x of [238,250]){ground(x,209,'stone_bricks');fill(x,108,209,x,113,209,'dark_oak_log',{axis:'y'});put(x,114,209,'chiseled_stone_bricks');put(x,115,209,'lantern',{hanging:'false',waterlogged:'false'});const dir=x<244?-1:1;for(let dx=1;dx<=2;dx++){put(x+dir*dx,112,209,'spruce_planks');for(let y=109;y<=111;y++)put(x+dir*dx,y,209,y===109?'white_terracotta':'purple_terracotta');}}
for(const [x,z]of [[239,216],[249,216],[235,224],[253,224],[239,233],[249,233],[244,239],[244,211]]){ground(x,z,'sea_lantern');}
for(const e of shrubs){ground(e.to[0],e.to[2],'grass_block');put(...e.to,e.state.Name,e.state.Properties);}
const newKeys=m.definitions.map(key),oldKeys=defs.map(key);let net=0;for(let i=0;i<data.length;i++)if(newKeys[data[i]]!==oldKeys[original[i]])net++;
const report={sourceHash:hash(source),backup:path.relative(ROOT,BACKUP),bounds:[225,100,207,263,119,241],dimensions:[39,35],floorY:107,compass:{center:[244,107,224],size:[15,15]},mainLane:{x:[240,248],width:9},pergolas:[[226,225,233,233],[254,208,261,216]],monument:{from:[244,224],to:[257,233],offset:[13,0,9],voxels:monument},shrubs,supportColumns,pavedCells:paved,gardenCells:gardens,netChangedBlocks:net,verification:{webGL:false,inGame:false,deployed:false,serverNPCDataModified:false}};
const baseline={...m,definitions:defs,blocks:bytes.toString('base64')},staged={...m,blocks:Buffer.from(data.buffer).toString('base64')};report.validation=verifyCourt(staged,baseline,report);
const result=patch(m,data,original,source);assert.deepEqual(entities(result.bytes),originalEntities,'Block entity contents changed');report.blockEntitiesPreserved=originalEntities.length;report.changedChunks=result.changed;report.resultHash=hash(result.bytes);
fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'dry-run-report.json'),JSON.stringify(report,null,2));fs.writeFileSync(path.join(STAGE,'planned-forecourt.mca'),result.bytes);
if(!process.argv.includes('--apply')){console.log(JSON.stringify({mode:'dry-run',netChangedBlocks:net,changedChunks:result.changed,validation:report.validation}));process.exit(0);}
assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Source changed before backup');if(!restage){assert(!fs.existsSync(BACKUP),'Backup already exists');fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});}
assert.equal(hash(fs.readFileSync(FILE)),hash(observed),'Source changed before commit');fs.writeFileSync(FILE+'.pending',result.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1');report.validation=verifyCourt(saved,baseline,report);fs.writeFileSync(RF,JSON.stringify(report,null,2));
const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.guildCampus.forecourt={report:'guild-forecourt-report.json',bounds:report.bounds,floorY:107,compass:report.compass,monument:{from:[244,224],to:[257,233]},pergolas:report.pergolas,stage:'built; visual acceptance pending'};manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
console.log(JSON.stringify({mode:'applied',netChangedBlocks:net,resultHash:report.resultHash,changedChunks:result.changed,validation:report.validation}));
