// Scoped saved-world patch; never rebuild from the original generator.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib'),assert=require('node:assert/strict');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs');
const {patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),file=path.join(DIR,'region/r.0.0.mca');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const before=fs.readFileSync(file),sourceHash=hash(before),m=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01');
const S=m.size,bytes=Buffer.from(m.blocks,'base64'),data=new Uint16Array(bytes.length/2);
for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
const original=data.slice(),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
const at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
function id(name,props){const d={Name:name.includes(':')?name:'minecraft:'+name,...(props?{Properties:props}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
function get(x,y,z){return x<0||z<0||x>=S||z>=S||y<m.low||y>=m.high?0:data[at(x,y,z)];}
function name(x,y,z){return m.definitions[get(x,y,z)].Name.replace('minecraft:','');}
const changes=new Map();
function put(x,y,z,n,p,reason){assert(x>=0&&z>=0&&x<S&&z<S&&y>=m.low&&y<m.high,'Out of bounds');const i=at(x,y,z),v=id(n,p);if(data[i]!==v){data[i]=v;changes.set(i,reason);}}
const natural=new Set(['air','grass_block','dirt','stone','andesite','cobblestone','mossy_cobblestone','deepslate']);
const plants=new Set(['air','short_grass','tall_grass','fern','large_fern','dandelion','poppy']);
function ground(x,z){for(let y=115;y>=87;y--)if(name(x,y,z)==='grass_block')return y;return null;}
const backup=path.join(ROOT,'archive/map-edit-backups/before-leveling-refinement/leveling_island_01');
assert(!fs.existsSync(backup),'Already applied: backup exists');
// Preserve all ladder coordinates; provide the attachment face for west-facing ladders.
for(const y of [116,120]){assert.equal(name(76,y,115),'ladder');assert.equal(name(77,y,115),'air');put(77,y,115,'stone_bricks',undefined,'ladder');}
// Irregular suspended rock teeth attach to existing land, without changing the surface footprint.
const rockPeaks=[[51,207,13,15,22],[76,243,16,13,19],[111,267,12,12,19],[184,256,16,14,21],[225,223,15,17,18],[259,177,12,18,20],[256,116,13,16,21],[212,78,14,14,18],[95,79,14,14,20],[48,143,13,18,21]];
let rockColumns=0;
for(const [cx,cz,rx,rz,tip] of rockPeaks)for(let z=cz-rz;z<=cz+rz;z++)for(let x=cx-rx;x<=cx+rx;x++){
  const r=Math.hypot((x-cx)/rx,(z-cz)/rz);if(r>1)continue;
  let bottom=null;for(let y=20;y<=100;y++)if(natural.has(name(x,y,z))&&name(x,y,z)!=='air'){bottom=y;break;}
  if(bottom===null)continue;const target=Math.round(tip+(bottom-tip)*r*r);if(target>=bottom)continue;
  for(let y=target;y<bottom;y++)if(name(x,y,z)==='air')put(x,y,z,y<target+3?'deepslate':(x+z+y)%11===0?'andesite':'stone',undefined,'rock');rockColumns++;
}
// Low asymmetrical mounds on empty natural columns only; architecture and routes stay protected.
let moundColumns=0;
for(const [cx,cz,rx,rz,height] of [[115,174,18,24,5],[179,165,17,22,5],[100,216,12,17,3]])for(let z=cz-rz;z<=cz+rz;z++)for(let x=cx-rx;x<=cx+rx;x++){
  const r=Math.hypot((x-cx)/rx,(z-cz)/rz),h=Math.round(height*Math.max(0,1-r*r)**2);if(!h)continue;
  const y=ground(x,z);if(y===null)continue;let safe=true;
  for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)for(let yy=y-1;yy<=y+h+4;yy++)if(!natural.has(name(x+dx,yy,z+dz))&&!plants.has(name(x+dx,yy,z+dz)))safe=false;
  if(!safe)continue;put(x,y,z,'dirt',undefined,'mound');for(let yy=y+1;yy<=y+h;yy++)put(x,yy,z,yy===y+h?'grass_block':'dirt',yy===y+h?{snowy:'false'}:undefined,'mound');moundColumns++;
}
// Restore only paving belonging to the former straight/weakly curved avenue, then use a wider bend.
const paving=new Set(['gravel','coarse_dirt','cobblestone','mossy_cobblestone','cracked_stone_bricks','dirt_path']);
for(let z=143;z<=207;z++)for(let x=136;x<=154;x++)for(let y=90;y<=97;y++)if(paving.has(name(x,y,z))&&plants.has(name(x,y+1,z))&&['dirt','stone'].includes(name(x,y-1,z)))put(x,y,z,'grass_block',{snowy:'false'},'road');
const road=[];
for(let z=139;z<=210;z++){
  const t=(z-139)/71,cx=Math.round(150-15*Math.sin(Math.PI*t));road.push([cx,z]);
  for(let dx=-2;dx<=2;dx++){const x=cx+dx,y=ground(x,z);if(y===null)continue;let safe=true;for(let yy=y+1;yy<=y+3;yy++)if(!plants.has(name(x,yy,z)))safe=false;if(!safe)continue;for(let yy=y+1;yy<=y+3;yy++)if(name(x,yy,z)!=='air')put(x,yy,z,'air',undefined,'road');put(x,y,z,Math.abs(dx)===2?'coarse_dirt':(x+z)%4===0?'mossy_cobblestone':'gravel',undefined,'road');}
}
// Trees use the lowest log cluster as the actual anchor, not the structure bounding-box center.
const trees=[],skipped=[];
function tree(asset,tx,tz){
  const t=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures',asset+'.nbt'))));
  const logs=t.blocks.filter(b=>/_(log|wood)$/.test(t.palette[b.state].Name)),by=Math.min(...logs.map(b=>b.pos[1])),roots=logs.filter(b=>b.pos[1]===by);
  const ax=Math.round(roots.reduce((s,b)=>s+b.pos[0],0)/roots.length),az=Math.round(roots.reduce((s,b)=>s+b.pos[2],0)/roots.length),gy=ground(tx,tz);if(gy===null){skipped.push([tx,tz,'no ground']);return;}
  const ox=tx-ax,oz=tz-az,oy=gy+1-by,placements=[];
  for(const b of roots){const x=ox+b.pos[0],z=oz+b.pos[2],g=ground(x,z);if(g===null||Math.abs(g-gy)>2){skipped.push([tx,tz,'root slope']);return;}}
  for(const b of t.blocks){const x=ox+b.pos[0],y=oy+b.pos[1],z=oz+b.pos[2],p=t.palette[b.state];if(x<188||x>256||z<116||z>205||y>=m.high||road.some(([rx,rz])=>Math.abs(rx-x)<4&&Math.abs(rz-z)<4)){skipped.push([tx,tz,'bounds']);return;}
    const n=name(x,y,z);if(!plants.has(n)&&!n.endsWith('_leaves')&&!(y<=gy&&natural.has(n))){skipped.push([tx,tz,'collision']);return;}placements.push([x,y,z,p]);}
  for(const b of roots){const x=ox+b.pos[0],z=oz+b.pos[2],g=ground(x,z);for(let y=g+1;y<=gy;y++)put(x,y,z,'oak_log',{axis:'y'},'tree');}
  for(const [x,y,z,p] of placements){if(name(x,y,z).endsWith('_leaves')&&p.Name.endsWith('_leaves'))continue;const props={...p.Properties};if(p.Name.endsWith('_leaves'))Object.assign(props,{persistent:'true',distance:'7',waterlogged:'false'});put(x,y,z,p.Name,Object.keys(props).length?props:undefined,'tree');}
  trees.push({asset,anchor:[tx,gy+1,tz],origin:[ox,oy,oz],bounds:t.size});
}
let ti=0;for(let z=124;z<=196;z+=9)for(let x=197+(Math.floor(z/9)%2)*4;x<=248;x+=10){if(x>=229&&z>=138&&z<=163)continue;tree(['t003','t001','t011'][ti++%3],x,z);}
// Replace perforated roof only, preserving inhabited floors, furniture, courtyard and east passage.
for(let z=59;z<=92;z++)for(let x=129;x<=170;x++)for(let y=120;y<=m.high-1;y++)if(name(x,y,z)!=='air')put(x,y,z,'air',undefined,'roof');
const stair=(facing)=>({facing,half:'bottom',shape:'straight',waterlogged:'false'});
for(let z=59;z<=92;z++)for(let x=129;x<=170;x++){
  const y=120+Math.floor(Math.min(x-129,170-x)*0.65),collapse=x>=132&&x<=143&&z>=65&&z<=78;
  if(collapse){if(z%4===1)put(x,y,z,'dark_oak_log',{axis:'x'},'roof');continue;}
  put(x,y,z,'dark_oak_stairs',stair(x<150?'east':'west'),'roof');
  if(z===60||z===91)for(let yy=120;yy<y;yy++)put(x,yy,z,(x+yy)%5===0?'cracked_stone_bricks':'stone_bricks',undefined,'roof');
}
// Two cross-gables and projecting stone bays give the front a broken height hierarchy.
for(const [cx,apex] of [[138,136],[161,133]])for(let z=85;z<=95;z++)for(let x=cx-5;x<=cx+5;x++){
  const y=apex-Math.abs(x-cx);put(x,y,z,'dark_oak_stairs',stair(x<cx?'east':'west'),'roof');
  if(z===94)for(let yy=120;yy<y;yy++)put(x,yy,z,Math.abs(x-cx)<=1&&yy>=123&&yy<=126?'dark_oak_fence':'stone_bricks',Math.abs(x-cx)<=1&&yy>=123&&yy<=126?{north:'false',south:'false',east:'true',west:'true',waterlogged:'false'}:undefined,'roof');
}
for(const x of [133,143,156,166])for(let y=109;y<=121;y++)if(name(x,y,92)==='air')put(x,y,92,y%4===0?'chiseled_stone_bricks':'stone_bricks',undefined,'roof');
// Verify staged scope, then make a recoverable backup and atomic region replacement.
const counts={};let net=0;for(const [i,r] of changes)if(data[i]!==original[i]){net++;counts[r]=(counts[r]||0)+1;}
assert(trees.length>=8,'Insufficient collision-free trees');
assert.equal(hash(fs.readFileSync(file)),sourceHash,'Concurrent map change; reload required');
fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(DIR,backup,{recursive:true});
const output=patch(m,data,original,before);assert.equal(hash(fs.readFileSync(file)),sourceHash,'Concurrent map change before write');fs.writeFileSync(file+'.pending',output.bytes);fs.renameSync(file+'.pending',file);
const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');let differences=0;
for(let i=0;i<data.length;i++){const k=key(saved.definitions[sb.readUInt16LE(i*2)]);assert.equal(k,key(m.definitions[data[i]]),'Saved semantic mismatch at '+i);if(data[i]!==original[i])differences++;}
assert.equal(differences,net);
const report={sourceHash,resultHash:hash(output.bytes),backup:path.relative(ROOT,backup),netChangedBlocks:net,changedChunks:output.changed,counts,rockColumns,moundColumns,trees,skippedTrees:skipped,road,verification:{savedSemanticComparison:true,unplannedVoxelChanges:0,inGame:false}};
fs.writeFileSync(path.join(DIR,'refinement-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({...report,trees:trees.length,skippedTrees:skipped.length,road:undefined},null,2));
