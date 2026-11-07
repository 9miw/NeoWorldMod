// Local redesign of six approved houses: irregular footprints, diagonal walls and major collapse.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-diagonal-village-ruins/leveling_island_01');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'));assert(!fs.existsSync(BACKUP),'Already redesigned; patch current ruins instead');
const source=fs.readFileSync(FILE),previous=JSON.parse(fs.readFileSync(path.join(DIR,'village-buildings.json')));assert.equal(hash(source),previous.resultHash,'Village changed since recorded build; review manual edits before replacing');
const lib=createLibrary(path.join(ROOT,'maps')),m=lib.load('leveling_island_01'),baseline=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-forest-village')).load('leveling_island_01'),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),S=m.size,b=Buffer.from(m.blocks,'base64'),bb=Buffer.from(baseline.blocks,'base64'),data=new Uint16Array(b.length/2),baseData=new Uint16Array(b.length/2),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
function id(d){const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const block=(n,p)=>id({Name:'minecraft:'+n,...(p?{Properties:p}:{})}),air=block('air'),stone=block('stone_bricks'),moss=block('mossy_stone_bricks'),wood=block('spruce_planks'),log=block('spruce_log',{axis:'y'});
const original=new Uint16Array(data.length),changed=new Set();let restored=0;
for(let i=0;i<data.length;i++){original[i]=b.readUInt16LE(i*2);baseData[i]=i<bb.length/2?id(baseline.definitions[bb.readUInt16LE(i*2)]):air;data[i]=original[i];if(data[i]!==baseData[i]){data[i]=baseData[i];changed.add(i);restored++;}}
assert.equal(restored,previous.netChangedBlocks,'Unexpected edits outside former building patch');
const at=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name;
const roadMask=new Uint8Array(S*S);for(const n of layout.walkNodes)for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)roadMask[(n.z+dz)*S+n.x+dx]=1;
const protectedTree=i=>/_(leaves|log|wood)$/.test(m.definitions[baseData[i]].Name);let current;
function put(x,y,z,v){assert(x>=current[0]&&x<=current[2]&&z>=current[1]&&z<=current[3],'Outside reserve');assert(y>=m.low&&y<m.high);const i=at(x,y,z);assert(!protectedTree(i),'Tree collision');if(data[i]!==v){data[i]=v;changed.add(i);}}
const specs=[
 {id:'01',name:'โรงเตี๊ยมตัว L ปีกถล่ม',cx:169,cz:264,w:17,d:13,angle:0,face:'east',stories:2,wall:10},
 {id:'02',name:'โรงช่างไม้แนวยาวและเพิงยุบ',cx:210,cz:271,w:19,d:9,angle:20,face:'west',wall:5},
 {id:'03',name:'ร้านเสบียงหน้าร้านถล่ม',cx:230,cz:244,w:15,d:11,angle:-15,face:'north',wall:5},
 {id:'04',name:'บ้านเล็กจั่วสูงหลังคาหายเกือบหมด',cx:189,cz:228,w:11,d:9,angle:0,face:'east',wall:5},
 {id:'05',name:'บ้านเยื้องและส่วนต่อเติมพัง',cx:155,cz:242,w:13,d:11,angle:25,face:'east',wall:5},
 {id:'06',name:'โกดังยาวหลังคายุบกลาง',cx:222,cz:214,w:21,d:11,angle:-20,face:'west',wall:7}
];
const records=[];
for(let j=0;j<specs.length;j++){
 const s=specs[j],reserve=layout.buildingReserves[j],base=reserve.center[1],r=s.angle*Math.PI/180,c=Math.cos(r),sn=Math.sin(r),hw=(s.w-1)/2,hd=(s.d-1)/2;current=reserve.bounds;
 const local=(x,z)=>{const dx=x-s.cx,dz=z-s.cz;return [dx*c+dz*sn,-dx*sn+dz*c];},world=(u,v)=>[Math.round(s.cx+u*c-v*sn),Math.round(s.cz+u*sn+v*c)];
 const inside=(u,v)=>Math.abs(u)<=hw+.45&&Math.abs(v)<=hd+.45&&!(s.id==='01'&&u>2&&v< -1)&&!(s.id==='05'&&u>1&&v>2.5);
 const cells=new Map();for(let z=current[1];z<=current[3];z++)for(let x=current[0];x<=current[2];x++){const [u,v]=local(x,z);if(inside(u,v))cells.set(x+','+z,{x,z,u,v});}
 const boundary=p=>[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!cells.has((p.x+dx)+','+(p.z+dz)));
 const hasRoof=(u,v)=>s.id==='01'?u< -3&&v<2:s.id==='02'?u< -3&&v<1:s.id==='03'?v>1&&u>0:s.id==='04'?u>2&&v< -1:s.id==='05'?u< -1&&v<0:Math.abs(u)>7;
 const eave=base+s.wall+1,peak=eave+hd+1,postCells=[];let roofTotal=0,roofRemain=0;
 for(const p of cells.values()){
  const {x,z,u,v}=p,edge=boundary(p),post=edge&&(Math.abs(u-Math.round(u/4)*4)<.7||Math.abs(v-Math.round(v/4)*4)<.65),nearRoute=roadMask[z*S+x];
  let gy=base;while(gy>m.low&&name(x,gy,z)==='minecraft:air')gy--;for(let y=gy+1;y<base;y++)put(x,y,z,(x+z+y)%4===0?moss:stone);put(x,base,z,edge?moss:wood);
  // Work only in the building column; clear low hillside intrusion, not neighboring land.
  for(let y=base+1;y<=Math.min(m.high-1,peak);y++)if(get(x,y,z)!==air)put(x,y,z,air);
  let remaining=s.wall;
  if(s.id==='01'&&u>0&&v>0)remaining=2;
  if(s.id==='02'&&v>0)remaining=u>0?1:3;
  if(s.id==='03'&&v<0)remaining=1;
  if(s.id==='04')remaining=u<0?2:Math.abs(v)>hd-1?3:5;
  if(s.id==='05'&&u>0)remaining=1;
  if(s.id==='06'&&(u>0||v>1))remaining=1;
  if(edge){const height=post?s.wall:remaining;for(let dy=1;dy<=height;dy++){if(nearRoute&&dy<=3)continue;const n=post?log:dy<=2?moss:wood;put(x,base+dy,z,n);}if(post)postCells.push([x,z]);}
  // Inn upper wing survives to the west; its eastern floor has collapsed completely.
  if(s.stories===2&&u< -1&&!nearRoute)put(x,base+6,z,wood);
  const roofY=Math.round(eave+Math.max(0,hd+1-Math.abs(v)));roofTotal++;
  if(hasRoof(u,v)){put(x,roofY,z,block('spruce_stairs',{facing:v<0?'south':'north',half:'bottom',shape:'straight',waterlogged:'false'}));roofRemain++;}
  else if(Math.abs(u-Math.round(u/5)*5)<.45&&edge)put(x,roofY,z,block('spruce_log',{axis:'z'}));
  // Partial rafters remain attached at the intact wall ends, rather than floating full roofs.
  if(edge&&post)for(let dy=s.wall+1;dy<=roofY-base;dy++)if(hasRoof(u,v)||dy<=s.wall+2)put(x,base+dy,z,log);
 }
 // Window surrounds survive on intact bays; ruined facades remain visibly open.
 const windows=[];for(const p of cells.values())if(boundary(p)&&!roadMask[p.z*S+p.x]&&hasRoof(p.u,p.v)&&Math.abs(p.u-Math.round(p.u/5)*5)<.6&&Math.abs(p.v)>hd-1){
  put(p.x,base+3,p.z,block('glass_pane',{north:'false',south:'false',east:'true',west:'true',waterlogged:'false'}));put(p.x,base+4,p.z,air);put(p.x,base+2,p.z,block('spruce_slab',{type:'bottom',waterlogged:'false'}));windows.push([p.x,base+3,p.z]);}
 // Wide diagonal doorway follows the wall's actual orientation and is not a rotated texture.
 const [du,dv]=s.face==='east'?[hw,0]:s.face==='west'?[-hw,0]:[0,-hd],door=world(du,dv);
 for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++){const x=door[0]+dx,z=door[1]+dz;if(x<current[0]||z<current[1]||x>current[2]||z>current[3])continue;for(let y=base+1;y<=base+3;y++)if(!protectedTree(at(x,y,z)))put(x,y,z,air);}
 // Selective props, debris and weeds stay outside the original walk corridor.
 const debris=[];for(const p of cells.values()){
  if(roadMask[p.z*S+p.x]||Math.abs(p.u)<2&&Math.abs(p.v)<2)continue;
  if(!hasRoof(p.u,p.v)&&((p.x*17+p.z*13+j)%19===0)){const v=(p.x+p.z)%3===0?block('spruce_slab',{type:'bottom',waterlogged:'false'}):moss;if(get(p.x,base+1,p.z)===air){put(p.x,base+1,p.z,v);debris.push([p.x,base+1,p.z]);}}
  if(!boundary(p)&&p.u>hw-2&&p.v<0&&get(p.x,base+1,p.z)===air){if(s.id==='03')put(p.x,base+1,p.z,block('bookshelf'));else if(s.id==='02')put(p.x,base+1,p.z,block('oak_log',{axis:'x'}));else if(s.id==='06')put(p.x,base+1,p.z,block('hay_block',{axis:'y'}));}
 }
 // The two-storey inn keeps a 2-wide functional stair into its surviving west wing.
 let upperStair; if(s.id==='01'){
  const sx=s.cx-hw+2,sz=s.cz+hd-1;for(let z=sz-6;z<=sz;z++)for(let x=sx;x<=sx+1;x++)for(let y=base+1;y<=base+10;y++)put(x,y,z,air);
  for(let i=0;i<6;i++)for(let x=sx;x<=sx+1;x++){for(let y=base+1;y<base+1+i;y++)put(x,y,sz-i,wood);put(x,base+1+i,sz-i,block('spruce_stairs',{facing:'north',half:'bottom',shape:'straight',waterlogged:'false'}));}for(let x=sx;x<=sx+1;x++)put(x,base+6,sz-6,wood);upperStair={x:sx,z:sz,floorY:base+6};
 }
 // Restore route surfaces exactly, and keep 3-block headroom even through a collapsed wall.
 for(const n of layout.walkNodes)if(n.x>=current[0]&&n.x<=current[2]&&n.z>=current[1]&&n.z<=current[3])for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){
  const x=n.x+dx,z=n.z+dz;if(x<current[0]||x>current[2]||z<current[1]||z>current[3])continue;const foot=Math.floor(n.feet);for(let y=Math.max(m.low,foot-1);y<=Math.min(m.high-1,foot+3);y++){if(y<=foot&&baseData[at(x,y,z)]!==air)put(x,y,z,baseData[at(x,y,z)]);else if(y>=Math.ceil(n.feet))put(x,y,z,air);}}
 // Mossy rubble and small persistent leaf clumps on ruin edges, without changing old tree voxels.
 for(const p of cells.values())if(boundary(p)&&!roadMask[p.z*S+p.x]&&(p.x*7+p.z*3)%29===0&&get(p.x,base+1,p.z)===air){put(p.x,base+1,p.z,block('oak_leaves',{persistent:'true',distance:'7',waterlogged:'false'}));}
 records.push({...s,floorY:base,feetY:base+1,worldBounds:[Math.min(...[...cells.values()].map(p=>p.x)),Math.min(...[...cells.values()].map(p=>p.z)),Math.max(...[...cells.values()].map(p=>p.x)),Math.max(...[...cells.values()].map(p=>p.z))],reserve:current,door:[door[0],base+1,door[1]],roofCells:roofTotal,remainingRoofCells:roofRemain,missingRoofPercent:Math.round(100*(1-roofRemain/roofTotal)),debris,windows,upperStair});
}
let net=0;for(let i=0;i<data.length;i++)if(data[i]!==original[i]){assert(changed.has(i));assert(!protectedTree(i));net++;}
assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent change');const out=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',out.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source));fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]));
const report={revision:'diagonal-heavy-ruins',buildings:records,netChangedBlocks:net,changedChunks:out.changed,oldBuildingCellsRestored:restored,treeVoxelChanges:0,sourceHash:hash(source),resultHash:hash(out.bytes),backup:path.relative(ROOT,BACKUP),verification:{savedSemanticComparison:true,inGame:false}};
fs.writeFileSync(path.join(DIR,'village-buildings.json'),JSON.stringify(report,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;manifest.description='Six distinct diagonal ruins · heavy structural collapse';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({netChangedBlocks:net,changedChunks:out.changed,buildings:records.map(r=>({id:r.id,angle:r.angle,shape:r.name,missingRoofPercent:r.missingRoofPercent}))},null,2));
