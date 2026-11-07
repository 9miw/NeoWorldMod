// One-shot scoped chapel patch. Verification can be rerun without modifying the map.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-ruined-chapel/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),load=root=>createLibrary(root).load('leveling_island_01');
const m=load(path.join(ROOT,'maps')),buf=Buffer.from(m.blocks,'base64'),S=m.size,at=(x,y,z)=>(y-m.low)*S*S+z*S+x,data=new Uint16Array(buf.length/2);
for(let i=0;i<data.length;i++)data[i]=buf.readUInt16LE(i*2);
const original=data.slice(),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),name=(x,y,z)=>m.definitions[data[at(x,y,z)]].Name.slice(10),inside=(x,y,z)=>x>=105&&x<=148&&z>=42&&z<=92&&y>=128&&y<=143;
const tree=d=>/_(log|wood|leaves)$/.test(d.Name),routes=new Set(layout.walkNodes.map(n=>n.x+','+n.z));
function verify(){
 const before=load(path.dirname(BACKUP)),bb=Buffer.from(before.blocks,'base64');assert.equal(before.low,m.low);assert.equal(before.high,m.high);let changed=0,trees=0;
 for(let i=0;i<data.length;i++){const d0=before.definitions[bb.readUInt16LE(i*2)],d1=m.definitions[data[i]];if(tree(d0)){assert.equal(key(d0),key(d1),'Existing tree changed');trees++;}if(key(d0)===key(d1))continue;changed++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(inside(x,y,z),'Outside chapel scope');}
 const pass=(x,z)=>x>=118&&x<=134&&z>=48&&z<=84&&name(x,132,z)!=='air'&&name(x,133,z)==='air'&&name(x,134,z)==='air';
 const q=[[126,84]],seen=new Set(['126,84']);assert(pass(126,84));for(let i=0;i<q.length;i++){const [x,z]=q[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const xx=x+dx,zz=z+dz,k=xx+','+zz;if(!seen.has(k)&&pass(xx,zz)){seen.add(k);q.push([xx,zz]);}}}
 assert(seen.has('126,53'),'Altar approach disconnected');assert(seen.size>200,'Insufficient usable hall');
 for(const n of layout.walkNodes)for(let dy=-1;dy<=1;dy++){const y=Math.floor(n.feet)+dy;if(y<m.low||y>=m.high)continue;assert.equal(key(m.definitions[data[at(n.x,y,n.z)]]),key(before.definitions[bb.readUInt16LE(at(n.x,y,n.z)*2)]),'Original walk node changed');}
 for(let z=51;z<=84;z++)for(let x=125;x<=127;x++)for(let y=133;y<=135;y++)assert.equal(name(x,y,z),'air','Aisle headroom');
 for(const [x,z]of [[141,58],[145,58],[141,64],[145,64],[141,70],[145,70]])assert.notEqual(name(x,132,z),'air','Grave support');
 return {netChangedBlocks:changed,reachableHallCells:seen.size,preservedTreeVoxels:trees,outsideScopeChanges:0,originalWalkNodesPreserved:layout.walkNodes.length,aisleWidth:3,aisleHeadroom:3,inGame:false};
}
if(process.argv.includes('--verify')){assert(fs.existsSync(BACKUP));console.log(JSON.stringify(verify()));process.exit(0);}
assert(process.argv.includes('--apply'),'Use --apply or --verify');const source=fs.readFileSync(FILE);if(fs.existsSync(BACKUP))assert.equal(hash(source),hash(fs.readFileSync(path.join(BACKUP,'region/r.0.0.mca'))),'Already applied or changed; do not rebuild');const ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
function put(x,y,z,n,p){assert(inside(x,y,z));const i=at(x,y,z),next=id(n,p);if(data[i]===next)return;assert(!tree(m.definitions[original[i]]),'Tree conflict');if(routes.has(x+','+z)&&y>=131&&y<=135)return;assert(['air','grass_block','dirt','stone','coarse_dirt','short_grass','fern'].includes(m.definitions[original[i]].Name.slice(10)),'Unexpected existing structure');data[i]=next;}
const slab={type:'bottom',waterlogged:'false'},stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),stone=(x,y,z)=>(x*7+y*3+z)%7===0?'mossy_stone_bricks':(x+y+z)%5===0?'cracked_stone_bricks':'stone_bricks';
// Nave plus polygonal apse. Existing approach remains the center aisle.
function footprint(x,z){return x>=117&&x<=135&&z>=53&&z<=81||z>=47&&z<53&&Math.abs(x-126)<=Math.min(9,5+z-47);}
for(let z=47;z<=81;z++)for(let x=117;x<=135;x++)if(footprint(x,z)){
 put(x,131,z,'stone_bricks');put(x,132,z,(x+z)%9===0?'mossy_stone_bricks':'stone_bricks');
 const edge=!footprint(x-1,z)||!footprint(x+1,z)||!footprint(x,z-1)||!footprint(x,z+1);
 if(!edge)continue;const doorway=z===81&&Math.abs(x-126)<=2,breach=x===135&&z>=63&&z<=66;
 const top=doorway?132:breach?133:136+((x*3+z*5)%4);
 for(let y=133;y<=top;y++){
  const window=(x===117||x===135)&&[57,58,69,70,75,76].includes(z)&&y>=135&&y<=137;
  if(!window)put(x,y,z,stone(x,y,z));
 }
 if(!doorway&&!breach)put(x,top+1,z,'stone_brick_slab',slab);
}
// Recessed bay surrounds, projecting buttresses and worn stair plinths.
for(const z of [54,61,68,75,80])for(const x of [116,136]){
 for(let y=131;y<=136;y++)put(x,y,z,stone(x,y,z));put(x,137,z,'stone_brick_stairs',stair(x===116?'east':'west'));
 put(x,133,z-1,'stone_brick_stairs',stair(z%2?'north':'south'));
}
// South entry piers and a partially surviving arch above a five-block opening.
for(const x of [122,130]){for(let y=133;y<=139;y++)put(x,y,81,stone(x,y,81));put(x,140,81,'chiseled_stone_bricks');}
for(const [x,y]of [[123,139],[124,140],[125,141],[126,141],[127,141],[128,140],[129,139]])put(x,y,81,'stone_bricks');
// Roof survives at the northern end and in two jagged strips; most of the nave is open sky.
for(let z=49;z<=78;z++)for(let x=116;x<=136;x++){
 const d=Math.abs(x-126),y=142-Math.floor(d/3);if(y>143||y<138)continue;
 if(z<=54||z>=73&&z<=76&&x<=120||z>=60&&z<=62&&x>=133)put(x,y,z,'dark_oak_stairs',stair(x<126?'east':'west'));
}
for(const z of [55,68,76])for(let x=118;x<=134;x++)if(z===55||x<122||x>131)put(x,138,z,'dark_oak_log',{axis:'x'});
// Off-axis broken bell tower. Hollow room, south doorway and open jagged belfry.
for(let z=70;z<=79;z++)for(let x=108;x<=115;x++){
 put(x,131,z,'stone_bricks');put(x,132,z,'stone_bricks');const edge=x===108||x===115||z===70||z===79;
 if(!edge)continue;const door=z===79&&x>=111&&x<=113;for(let y=133;y<=143;y++){
  if(door&&y<=135)continue;if(y>=140&&((x===108||x===115)&&z>=73&&z<=76||(z===70||z===79)&&x>=110&&x<=113))continue;
  if(y>=142&&(x+z)%3!==0)continue;put(x,y,z,stone(x,y,z));
 }
}
for(let x=109;x<=114;x++)put(x,139,74,'dark_oak_log',{axis:'x'});
// Decorative broken bell represented by blocks: no block entities or scripted behavior.
put(112,138,74,'chain',{axis:'y',waterlogged:'false'});put(112,137,74,'cut_copper');put(112,136,74,'cut_copper_slab',slab);
// Paired broken benches leave the central three-wide aisle and eastern wall breach open.
for(const z of [60,66,72,77])for(const side of [-1,1])for(let dx=4;dx<=6;dx++)if((dx+z+side)%5!==0)put(126+dx*side,133,z,'spruce_stairs',stair('north'));
for(let x=123;x<=129;x++)for(let z=49;z<=50;z++)put(x,133,z,'polished_andesite');put(126,134,49,'chiseled_stone_bricks');
// Six leaning/fragmented grave markers with grounded slabs and a broken low boundary.
for(const [i,[x,z]]of [[141,58],[145,58],[141,64],[145,64],[141,70],[145,70]].entries()){
 for(let zz=z;zz<=z+2;zz++){put(x,132,zz,'coarse_dirt');put(x,133,zz,'stone_brick_slab',slab);}put(x,133,z,'mossy_cobblestone');if(i%3)put(x,134,z,i%2?'chiseled_stone_bricks':'cobblestone_slab',i%2?undefined:slab);
}
for(let z=54;z<=75;z++)if(z%5!==0)put(148,133,z,'mossy_cobblestone');
for(const [x,z]of [[119,56],[133,65],[118,74],[131,79],[113,81],[137,76]]){put(x,133,z,'mossy_cobblestone');if(x%2)put(x,134,z,'cobblestone_slab',slab);}
// Sparse weeds only on supported empty ground; never fill the three-wide aisle.
for(let z=45;z<=88;z++)for(let x=106;x<=147;x++)if(Math.abs(x-126)>2&&(x*17+z*11)%23===0&&name(x,133,z)==='air'&&['grass_block','dirt','coarse_dirt'].includes(name(x,132,z)))put(x,133,z,'short_grass');
assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent edit');const result=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});if(!fs.existsSync(BACKUP))fs.cpSync(DIR,BACKUP,{recursive:true});
// All scope and access checks run before writing. Verify compares staged data to backup.
const checks=verify();fs.writeFileSync(FILE+'.pending',result.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent edit');fs.renameSync(FILE+'.pending',FILE);
const saved=load(path.join(ROOT,'maps')),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]),'Saved semantic mismatch');
const report={bounds:[105,128,42,148,143,92],floorY:132,feetY:133,nave:[117,53,135,81],door:[126,133,81],altar:[126,134,49],tower:[108,70,115,79],graves:6,sourceHash:hash(source),resultHash:hash(result.bytes),backup:path.relative(ROOT,BACKUP),changedChunks:result.changed,verification:{...checks,savedSemanticComparison:true,webGL:false,deployed:false}};
fs.writeFileSync(path.join(DIR,'chapel-report.json'),JSON.stringify(report,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;manifest.description='Forest basin · abandoned village and ruined ridge chapel';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify(report));
