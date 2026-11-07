// Local, deterministic decoration of the existing saved city. Never generates a world.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const STAGE=path.join(ROOT,'tmp/island-landscaping'),REPORT=path.join(MAP,'island-landscaping-report.json');
const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function run(){
 if(fs.existsSync(REPORT))throw Error('Landscaping already applied; inspect the report before further edits');
 const library=createLibrary(path.dirname(MAP)),m=library.load(path.basename(MAP));
 if(m.size!==384||m.origin.some(v=>v!==0))throw Error('Expected the original full city coordinates');
 const S=m.size,bytes=Buffer.from(m.blocks,'base64'),old=new Uint16Array(bytes.length/2);
 for(let i=0;i<old.length;i++)old[i]=bytes.readUInt16LE(i*2);
 const data=old.slice(),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,inside=(x,z)=>x>=24&&x<=303&&z>=24&&z<=343;
 const at=(x,y,z,source=data)=>x<0||x>=S||z<0||z>=S||y<m.low||y>=m.high?0:source[ix(x,y,z)];
 const name=(x,y,z,source=data)=>m.definitions[at(x,y,z,source)].Name;
 const natural=new Set(['air','grass_block','dirt','stone','andesite','cobblestone','mossy_cobblestone'].map(n=>'minecraft:'+n));
 const top=new Int16Array(S*S).fill(-1),protectedMask=new Uint8Array(S*S),hard=new Uint8Array(S*S);
 const zones=JSON.parse(fs.readFileSync(path.join(MAP,'zones.json'))).filter(z=>!['01-island','03-streets-square'].includes(z.id));
 for(let z=0;z<S;z++)for(let x=0;x<S;x++){
  const k=z*S+x;
  for(let y=m.low;y<m.high;y++){
   const n=name(x,y,z,old);if(n==='minecraft:grass_block')top[k]=y;
   if(!natural.has(n))hard[k]=1;
  }
  if(zones.some(a=>x>=a.bounds[0]-2&&x<=a.bounds[2]+2&&z>=a.bounds[1]-2&&z<=a.bounds[3]+2))hard[k]=1;
 }
 // Protect architectural details, existing vegetation, roads and their immediate clearances.
 for(let z=0;z<S;z++)for(let x=0;x<S;x++)if(hard[z*S+x])
  for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)if(x+dx>=0&&x+dx<S&&z+dz>=0&&z+dz<S)protectedMask[(z+dz)*S+x+dx]=1;
 const eligible=(x,z)=>inside(x,z)&&top[z*S+x]>=0&&!protectedMask[z*S+x];
 const ids=new Map(m.definitions.map((d,i)=>[key(d),i])),planned=new Set(),counts={},trees=[],boulders=[],shrubs=[];
 function id(d){const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
 function put(x,y,z,d,kind){
  if(!eligible(x,z)||y<m.low||y>=m.high)throw Error('Outside safe decoration area '+[x,y,z]);
  const i=ix(x,y,z),next=id(d);if(data[i]===next)return;
  if(!natural.has(m.definitions[old[i]].Name))throw Error('Attempt to replace existing detail');
  data[i]=next;planned.add(i);counts[kind]=(counts[kind]||0)+1;
 }
 let seed=2401026;const rand=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
 const hash=(x,z)=>{let n=Math.imul(x+501,374761393)^Math.imul(z+707,668265263);n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967296;};
 const sector=(x,z)=>z<150?(x<160?'north-west':'north-east'):z<250?(x<160?'west':'east'):(x<160?'south-west':'south-east');
 function rotate(d,turns){d=structuredClone(d);const p=d.Properties;if(!p)return d;
  const dirs=['north','east','south','west'];
  if(p.axis&&turns%2&&p.axis!=='y')p.axis=p.axis==='x'?'z':'x';
  if(p.facing&&dirs.includes(p.facing))p.facing=dirs[(dirs.indexOf(p.facing)+turns)%4];
  const connections={};for(const dir of dirs)if(dir in p){connections[dirs[(dirs.indexOf(dir)+turns)%4]]=p[dir];delete p[dir];}Object.assign(p,connections);
  return d;
 }
 const assets=['t018','t020','t037','t039','t055','t210'].map(asset=>{
  const a=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures',asset+'.nbt'))));
  if(a.palette.some(d=>/leaves$/.test(d.Name)&&d.Properties?.persistent!=='true'))throw Error('Nonpersistent asset leaves');
  return {asset,...a};
 });
 function tree(a,x,z,turns){
  const rotated=a.blocks.map(b=>{let [px,py,pz]=b.pos,w=a.size[0],depth=a.size[2];for(let t=0;t<turns;t++){[px,pz]=[depth-1-pz,px];[w,depth]=[depth,w];}return {p:[px,py,pz],d:rotate(a.palette[b.state],turns)};});
  const roots=rotated.filter(b=>b.p[1]===0),cx=Math.round(roots.reduce((n,b)=>n+b.p[0],0)/roots.length),cz=Math.round(roots.reduce((n,b)=>n+b.p[2],0)/roots.length),ox=x-cx,oz=z-cz;
  const ground=roots.map(b=>top[(oz+b.p[2])*S+ox+b.p[0]]);
  if(ground.some(y=>y===undefined||y<0)||Math.max(...ground)-Math.min(...ground)>2)return false;
  const oy=Math.min(...ground)+1,voxels=rotated.map(b=>({x:ox+b.p[0],y:oy+b.p[1],z:oz+b.p[2],d:b.d}));
  for(const v of voxels){
   if(!eligible(v.x,v.z)||v.y>=m.high)return false;
   const n=name(v.x,v.y,v.z),wood=/_wood$|_log$/.test(v.d.Name);
   if(n!=='minecraft:air'&&!(wood&&['minecraft:grass_block','minecraft:dirt'].includes(n)&&v.y>=top[v.z*S+v.x]-2))return false;
  }
  for(const r of roots){const gx=ox+r.p[0],gz=oz+r.p[2];if(!['minecraft:grass_block','minecraft:dirt'].includes(name(gx,oy-1,gz)))return false;}
  for(const v of voxels)put(v.x,v.y,v.z,v.d,'treeBlocks');
  trees.push({asset:a.asset,trunkCenter:[x,oy,z],origin:[ox,oy,oz],rotation:turns*90,size:turns%2?[a.size[2],a.size[1],a.size[0]]:a.size,blocks:voxels.length,sector:sector(x,z),bounds:[Math.min(...voxels.map(v=>v.x)),oy,Math.min(...voxels.map(v=>v.z)),Math.max(...voxels.map(v=>v.x)),oy+a.size[1]-1,Math.max(...voxels.map(v=>v.z))]});
  return true;
 }
 const candidates=[];for(let z=30;z<=337;z+=4)for(let x=30;x<=297;x+=4)if(eligible(x,z))candidates.push({x,z,order:rand(),sector:sector(x,z)});
 candidates.sort((a,b)=>a.order-b.order);
 // Balanced coverage of the six geographic sectors, with open spaces between crowns.
 const sectors=['north-west','north-east','west','east','south-west','south-east'];
 for(let pass=0;pass<6&&trees.length<32;pass++)for(const region of sectors){
  if(trees.length>=32)break;
  for(const c of candidates){
   if(c.sector!==region||trees.some(t=>Math.hypot(t.trunkCenter[0]-c.x,t.trunkCenter[2]-c.z)<17))continue;
   let success=false;for(let j=0;j<assets.length;j++)if(tree(assets[(trees.length+j)%assets.length],c.x,c.z,Math.floor(rand()*4))){success=true;break;}
   if(success)break;
  }
 }
 if(trees.length<25)throw Error('Insufficient safe tree positions: '+trees.length);
 const stoneNames=['stone','andesite','cobblestone','mossy_cobblestone'];
 for(const c of candidates){
  if(boulders.length>=58)break;
  if(!eligible(c.x,c.z)||trees.some(t=>Math.hypot(t.trunkCenter[0]-c.x,t.trunkCenter[2]-c.z)<7)||boulders.some(b=>Math.hypot(b.center[0]-c.x,b.center[2]-c.z)<12))continue;
  const r=boulders.length%4===0?3:2,h=r===3?3:2,base=top[c.z*S+c.x],vox=[];let valid=true;
  rock: for(let dz=-r;dz<=r;dz++)for(let dx=-r;dx<=r;dx++){
   const x=c.x+dx,z=c.z+dz,q=(dx*dx/(r*r)+dz*dz/((r-.3)*(r-.3)));if(q>1)continue;
   if(!eligible(x,z)||Math.abs(top[z*S+x]-base)>2){valid=false;break rock;}
   const height=Math.max(1,Math.round(h*Math.sqrt(1-q)));
   for(let dy=1;dy<=height;dy++){const y=top[z*S+x]+dy;if(name(x,y,z)!=='minecraft:air'){valid=false;break rock;}vox.push([x,y,z]);}
  }
  if(!valid||vox.length<8||vox.some(([x,y,z])=>!eligible(x,z)||name(x,y,z)!=='minecraft:air'))continue;
  // Every occupied column starts on the existing terrain, including on uneven ground.
  for(const [x,y,z]of vox)put(x,y,z,{Name:'minecraft:'+stoneNames[Math.floor(rand()*stoneNames.length)]},'boulderBlocks');
  boulders.push({center:[c.x,base+1,c.z],radius:r,blocks:vox.length,sector:c.sector});
 }
 for(const c of candidates){
  if(shrubs.length>=100)break;
  if(hash(c.x,c.z)>.45||shrubs.some(b=>Math.hypot(b.center[0]-c.x,b.center[2]-c.z)<8))continue;
  const vox=[];for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)if(Math.abs(dx)+Math.abs(dz)<=1){
   const x=c.x+dx,z=c.z+dz,y=top[z*S+x]+1;
   if(eligible(x,z)&&name(x,y-1,z)==='minecraft:grass_block'&&name(x,y,z)==='minecraft:air')vox.push([x,y,z]);
  }
  if(vox.length!==5)continue;
  for(const [x,y,z]of vox)put(x,y,z,{Name:'minecraft:oak_leaves',Properties:{persistent:'true',distance:'7',waterlogged:'false'}},'shrubBlocks');
  shrubs.push({center:[c.x,top[c.z*S+c.x]+1,c.z],blocks:5,sector:c.sector});
 }
 // Organic surface patches; preserve terrain heights and cliff silhouette exactly.
 let eligibleColumns=0;const vegetationBySector={};
 for(let z=24;z<=343;z++)for(let x=24;x<=303;x++){
  if(!eligible(x,z))continue;eligibleColumns++;const y=top[z*S+x];
  if(name(x,y,z)!=='minecraft:grass_block'||name(x,y+1,z)!=='minecraft:air')continue;
  const patchiness=(Math.sin(x*.19+Math.sin(z*.13)*2)+Math.cos(z*.17-x*.07)+2)/4,noise=hash(x,z);
  const nearRock=boulders.some(b=>Math.hypot(b.center[0]-x,b.center[2]-z)<b.radius+2);
  const nearRoot=trees.some(t=>Math.hypot(t.trunkCenter[0]-x,t.trunkCenter[2]-z)<3);
  if((nearRock||nearRoot||patchiness>.78)&&noise<.4){put(x,y,z,{Name:'minecraft:'+(nearRock&&noise<.09?'moss_block':'coarse_dirt')},'surfaceBlocks');continue;}
  if(noise<.12+patchiness*.20){
   const flower=patchiness>.64&&hash(z+100,x+400)<.13;
   const n=flower?(hash(z,x)<.55?'oxeye_daisy':'dandelion'):(hash(z,x)>.68?'fern':'short_grass');
   put(x,y+1,z,{Name:'minecraft:'+n},flower?'flowers':'grassAndFerns');const s=sector(x,z);vegetationBySector[s]=(vegetationBySector[s]||0)+1;
  }
 }
 // Restrained exposed-cliff material variation, no added out-of-footprint voxels.
 for(let z=24;z<=343;z++)for(let x=24;x<=303;x++)if(eligible(x,z)){
  const surface=top[z*S+x];for(let y=Math.max(m.low,surface-18);y<surface-3;y++){
   if(name(x,y,z)!=='minecraft:stone'||hash(x+y*17,z)>.09)continue;
   if([[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>name(x+dx,y,z+dz,old)==='minecraft:air'))put(x,y,z,{Name:'minecraft:'+(hash(z,y+x)<.22?'mossy_cobblestone':'andesite')},'cliffBlocks');
  }
 }
 const regionFile=path.join(MAP,'region/r.0.0.mca'),before=fs.readFileSync(regionFile),sourceHash=crypto.createHash('sha256').update(before).digest('hex');
 let changed=0,protectedChanges=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){changed++;if(!planned.has(i))throw Error('Unplanned change');const k=i%(S*S);if(protectedMask[k])protectedChanges++;}
 if(protectedChanges)throw Error('Protected area changed');
 const info={status:'staged',sourceHash,seed:2401026,scope:'Vacant natural land across zone 01; existing structures, roads, plazas and vegetation protected',islandBounds:[24,24,303,343],trees,boulders,shrubs,counts,eligibleColumns,vegetationBySector,changedBlocks:changed,protectedChanges:0,outsidePlannedChanges:0,terrainFootprintChanged:false,terrainHeightChanged:false,giantT365Placed:false};
 fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'plan.json'),JSON.stringify(info,null,2));
 if(process.argv.includes('--apply')){
  const backup=path.join(ROOT,'archive/map-edit-backups/before-island-landscaping/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');
  fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});
  const result=patch(m,data,old,before);
  if(crypto.createHash('sha256').update(fs.readFileSync(regionFile)).digest('hex')!==sourceHash)throw Error('World changed since staging');
  fs.writeFileSync(regionFile+'.pending',result.bytes);fs.renameSync(regionFile+'.pending',regionFile);
  const saved=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),sb=Buffer.from(saved.blocks,'base64');
  if(saved.low!==m.low||saved.high!==m.high||saved.size!==m.size)throw Error('Saved dimensions changed');
  for(let i=0;i<data.length;i++)if(key(saved.definitions[sb.readUInt16LE(i*2)])!==key(m.definitions[data[i]]))throw Error('Saved block mismatch '+i);
  info.status='applied';info.backup=path.relative(ROOT,backup);info.changedChunks=result.changed;info.savedBlockComparison='All voxels match planned result using Name and sorted Properties';
  fs.writeFileSync(REPORT,JSON.stringify(info,null,2));
 }
 console.log(JSON.stringify({status:info.status,trees:trees.length,assets:trees.reduce((o,t)=>(o[t.asset]=(o[t.asset]||0)+1,o),{}),boulders:boulders.length,shrubs:shrubs.length,counts,changedBlocks:changed,protectedChanges,coverage:trees.reduce((o,t)=>(o[t.sector]=(o[t.sector]||0)+1,o),{}),report:process.argv.includes('--apply')?REPORT:path.join(STAGE,'plan.json')},null,2));
}
if(require.main===module)run();
