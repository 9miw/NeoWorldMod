// Approved six-building village concept, applied only inside existing reserves.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-forest-village/leveling_island_01');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'),'Use --apply');assert(!fs.existsSync(BACKUP),'Village already created; patch saved structures instead');
const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),bytes=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(bytes.length/2);
for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);const original=data.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const get=(x,y,z)=>data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name;
const changed=new Set(),trees=new Set();for(let i=0;i<original.length;i++)if(/_(leaves|log|wood)$/.test(m.definitions[original[i]].Name))trees.add(i);
let current;function put(x,y,z,n,p){assert(y>=m.low&&y<m.high);assert(x>=current.x1-2&&x<=current.x2+2&&z>=current.z1-2&&z<=current.z2+2,'Outside building scope');const i=at(x,y,z),v=id(n,p);assert(!trees.has(i),'Existing tree collision');if(data[i]!==v){data[i]=v;changed.add(i);}}
function fill(x1,y1,z1,x2,y2,z2,n,p){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,n,p);}
const stairs=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},log={axis:'y'};
const specs=[{id:'01',name:'โรงเตี๊ยมร้าง',cx:169,cz:264,w:17,d:13,face:'east',stories:2},{id:'02',name:'บ้านช่างไม้',cx:210,cz:271,w:15,d:11,face:'west'},{id:'03',name:'ร้านเสบียงร้าง',cx:230,cz:244,w:15,d:11,face:'north'},{id:'04',name:'บ้านพักร้าง',cx:189,cz:228,w:13,d:11,face:'east'},{id:'05',name:'บ้านพักตะวันตก',cx:155,cz:242,w:13,d:11,face:'east',wideDoor:true},{id:'06',name:'โกดังร้าง',cx:222,cz:214,w:19,d:11,face:'west',wideDoor:true}];
const route=new Set(layout.walkNodes.map(n=>n.x+','+n.z)),records=[];
for(let index=0;index<specs.length;index++){
 const s=specs[index],reserve=layout.buildingReserves[index],base=reserve.center[1],x1=s.cx-(s.w-1)/2,x2=s.cx+(s.w-1)/2,z1=s.cz-(s.d-1)/2,z2=s.cz+(s.d-1)/2,wall=s.stories===2?10:5,eave=base+wall+1;
 current={x1,x2,z1,z2};assert(x1>=reserve.bounds[0]&&x2<=reserve.bounds[2]&&z1>=reserve.bounds[1]&&z2<=reserve.bounds[3]);
 // Solid, terrain-supported stone plinth and a usable floor, never a solid filled building.
 for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){
  let ground=base;while(ground>m.low&&name(x,ground,z)==='minecraft:air')ground--;for(let y=ground+1;y<base;y++)put(x,y,z,(x+z+y)%7===0?'mossy_cobblestone':'cobblestone');put(x,base,z,x===x1||x===x2||z===z1||z===z2?'stone_bricks':'spruce_planks');fill(x,base+1,z,x,base+wall,z,'air');
 }
 // Framed wall bays, with projecting posts at corners and intermediate bays.
 for(let y=base+1;y<=base+wall;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)if(x===x1||x===x2||z===z1||z===z2){const post=(x===x1||x===x2)&&((z-z1)%4===0||z===z2)||(z===z1||z===z2)&&((x-x1)%4===0||x===x2);put(x,y,z,post?'spruce_log':y<=base+2?((x+z)%4===0?'mossy_stone_bricks':'stone_bricks'):'oak_planks',post?log:undefined);}
 for(const y of s.stories===2?[base+5,base+10]:[base+5]){for(let x=x1;x<=x2;x++){put(x,y,z1,'spruce_log',{axis:'x'});put(x,y,z2,'spruce_log',{axis:'x'});}for(let z=z1;z<=z2;z++){put(x1,y,z,'spruce_log',{axis:'z'});put(x2,y,z,'spruce_log',{axis:'z'});}}
 for(const [x,z]of [[x1,z1],[x2,z1],[x1,z2],[x2,z2]]){put(x,base,z,'chiseled_stone_bricks');for(const [dx,dz,f]of [[-1,0,'east'],[1,0,'west'],[0,-1,'south'],[0,1,'north']])if(x+dx<x1||x+dx>x2||z+dz<z1||z+dz>z2)put(x+dx,base,z+dz,'stone_brick_stairs',stairs(f));}
 const windows=[];function window(x,z,axis,y){for(let t=-1;t<=1;t++){
  const xx=x+(axis==='x'?t:0),zz=z+(axis==='z'?t:0);put(xx,y-1,zz,'spruce_slab',slab);put(xx,y+2,zz,'spruce_log',{axis:axis});for(let yy=y;yy<=y+1;yy++)put(xx,yy,zz,Math.abs(t)===1?'spruce_log':'glass_pane',Math.abs(t)===1?log:{east:axis==='x'?'true':'false',west:axis==='x'?'true':'false',north:axis==='z'?'true':'false',south:axis==='z'?'true':'false',waterlogged:'false'});
 }windows.push([x,y,z]);}
 for(const y of s.stories===2?[base+3,base+8]:[base+3]){for(let x=x1+3;x<x2-1;x+=5){window(x,z1,'x',y);window(x,z2,'x',y);}for(let z=z1+3;z<z2-1;z+=5){window(x1,z,'z',y);window(x2,z,'z',y);}}
 if(s.stories===2)fill(x1+1,base+6,z1+1,x2-1,base+6,z2-1,'spruce_planks');
 // Deliberate roof collapse in a single rear corner, with surviving exposed rafters.
 for(let z=z1-1;z<=z2+1;z++)for(let x=x1-1;x<=x2+1;x++){
  const roofY=eave+Math.min(z-(z1-1),z2+1-z),collapsed=x<x1+Math.ceil(s.w*.4)&&z<z1+Math.ceil(s.d*.46);
  if(collapsed){if((x-x1)%4===0)put(x,roofY,z,'spruce_log',{axis:'z'});continue;}
  put(x,roofY,z,'spruce_stairs',stairs(z<=s.cz?'south':'north'));if(x===x1||x===x2)for(let y=eave;y<roofY;y++)put(x,y,z,(z===s.cz||y===eave)?'spruce_log':'oak_planks',z===s.cz||y===eave?log:undefined);
 }
 // Partially broken masonry, not random holes across every facade.
 const breach=s.id==='06'?[x2,base+1,z1+2,x2,base+4,z1+6]:[x1,base+3,z1+1,x1,base+wall,z1+3];fill(...breach,'air');
 // Decorative furnishings use blocks with no block entities; no fake chests/furnaces are exported.
 const furniture=[];function furnish(x,y,z,n,p){if(route.has(x+','+z))return;put(x,y,z,n,p);furniture.push([x,y,z,n]);}
 for(let z=z1+2;z<=z2-2;z+=3){furnish(x2-2,base+1,z,s.id==='03'?'bookshelf':'spruce_planks');furnish(x1+2,base+1,z,s.id==='06'?'hay_block':'spruce_slab',s.id==='06'?{axis:'y'}:slab);}
 if(s.id==='02'){for(let x=x1+3;x<=x2-2;x+=3)furnish(x,base+1,z2-2,'oak_log',{axis:'x'});furnish(x2-2,base+1,z1+2,'crafting_table');}
 if(s.id==='03'){for(let x=x1+2;x<=x2-2;x++)furnish(x,base+1,z2-2,'bookshelf');furnish(x2-2,base+1,s.cz,'composter',{level:'0'});}
 if(s.id==='01'){
  for(let z=z1+2;z<=z1+5;z++)furnish(x2-3,base+1,z,'spruce_planks');
  // Two-wide northbound staircase to the upper floor; clear the entire stairwell first.
  const sx=x1+2;fill(sx,base+1,z2-6,sx+1,base+10,z2-1,'air');for(let i=0;i<6;i++)for(let x=sx;x<=sx+1;x++){const z=z2-1-i,y=base+1+i;fill(x,base+1,z,x,y-1,z,'spruce_planks');put(x,y,z,'spruce_stairs',stairs('north'));}
  fill(sx,base+6,z2-7,sx+1,base+6,z2-7,'spruce_planks');
  for(let z=z1+2;z<=z2-2;z+=4){put(x2-2,base+7,z,'white_wool');put(x2-3,base+7,z,'spruce_slab',slab);}
 }
 const dx=s.face==='east'?x2:s.face==='west'?x1:s.cx,dz=s.face==='north'?z1:s.cz,width=s.wideDoor?2:1;
 // Open the primary door and every existing route crossing of the building envelope.
 const portals=[];function open(x,z,axis,r){for(let t=-r;t<=r;t++){const xx=x+(axis==='x'?t:0),zz=z+(axis==='z'?t:0);fill(xx,base+1,zz,xx,base+3,zz,'air');}portals.push({x,z,axis,r,feet:base+1});}
 open(dx,dz,s.face==='north'?'x':'z',width);
 for(const n of layout.walkNodes)if((n.x===x1||n.x===x2)&&n.z>z1+1&&n.z<z2-1)open(n.x,n.z,'z',1);else if((n.z===z1||n.z===z2)&&n.x>x1+1&&n.x<x2-1)open(n.x,n.z,'x',1);
 // Keep all reserved walk-center cells clear under the roof and retain original route floors.
 for(const n of layout.walkNodes)if(n.x>=x1&&n.x<=x2&&n.z>=z1&&n.z<=z2){for(let y=base+1;y<=base+3;y++)put(n.x,y,n.z,'air');const oldFloor=original[at(n.x,base,n.z)];if(oldFloor!==0)put(n.x,base,n.z,m.definitions[oldFloor].Name.slice(10),m.definitions[oldFloor].Properties);}
 records.push({...s,floorY:base,feetY:base+1,bounds:[x1,z1,x2,z2],eaveY:eave,roofPeakY:eave+(s.d+1)/2,portals,windows,furniture,upperFloorY:s.stories===2?base+6:undefined});
}
// Validate staged interiors and protect every tree voxel and all changes outside the six envelopes.
let net=0;for(let i=0;i<data.length;i++)if(data[i]!==original[i]){assert(changed.has(i));net++;}for(const i of trees)assert.equal(data[i],original[i],'Tree changed');
for(const r of records){assert.equal(name(r.cx,r.feetY,r.cz),'minecraft:air');for(const p of r.portals)for(let y=r.feetY;y<r.feetY+3;y++)assert.equal(name(p.x,y,p.z),'minecraft:air');}
assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent map change');const out=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',out.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source));fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]));
const report={buildings:records,netChangedBlocks:net,changedChunks:out.changed,treeVoxelChanges:0,backup:path.relative(ROOT,BACKUP),sourceHash:hash(source),resultHash:hash(out.bytes),verification:{savedSemanticComparison:true,doorCentersClear:true,inGame:false}};
fs.writeFileSync(path.join(DIR,'village-buildings.json'),JSON.stringify(report,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;manifest.description='Forest basin · six ruined buildings, usable interiors · gameplay pending';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({buildings:records.length,netChangedBlocks:net,changedChunks:out.changed,treeVoxelChanges:0,backup:report.backup}));
