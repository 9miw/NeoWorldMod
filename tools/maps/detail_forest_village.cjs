// Scoped abandoned-village dressing; keeps combat clearings, tree assets and route surfaces intact.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-village-dressing/leveling_island_01');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'));assert(!fs.existsSync(BACKUP),'Already dressed; patch current saved map instead');
const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),houses=JSON.parse(fs.readFileSync(path.join(DIR,'village-buildings.json'))).buildings,forest=JSON.parse(fs.readFileSync(path.join(DIR,'forest-placement-report.json'))),old=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(old.length/2);
for(let i=0;i<data.length;i++)data[i]=old.readUInt16LE(i*2);const original=data.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const get=(x,y,z)=>data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name.slice(10),air=id('air');
const ground=new Int16Array(S*S).fill(-1);for(let z=200;z<=299;z++)for(let x=133;x<=253;x++)for(let y=110;y>=78;y--)if(['grass_block','dirt','coarse_dirt','podzol'].includes(name(x,y,z))&&get(x,y+1,z)===air){ground[z*S+x]=y;break;}
const routes=new Uint8Array(S*S);for(const n of layout.walkNodes)for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)routes[(n.z+dz)*S+n.x+dx]=1;
const clearings=forest.clearings,box=(x,z,b,p=0)=>x>=b[0]-p&&x<=b[2]+p&&z>=b[1]-p&&z<=b[3]+p;
const treeColumns=new Uint8Array(S*S),treeVoxels=new Set();
// Tree footprint derives from the pre-building saved world, not from building timber posts.
const tm=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-forest-village')).load('leveling_island_01'),tb=Buffer.from(tm.blocks,'base64');for(let i=0;i<tb.length/2;i++)if(/_(leaves|log|wood)$/.test(tm.definitions[tb.readUInt16LE(i*2)].Name)){treeVoxels.add(i);const k=i%(S*S),x=k%S,z=Math.floor(k/S);for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)if(x+dx>=0&&z+dz>=0&&x+dx<S&&z+dz<S)treeColumns[(z+dz)*S+x+dx]=1;}
const protectedColumn=(x,z)=>x<137||x>250||z<202||z>294||routes[z*S+x]||clearings.some(([cx,cz,r])=>Math.hypot(x-cx,z-cz)<=r)||houses.some(h=>Math.hypot(x-h.door[0],z-h.door[2])<=4);
const safe=(x,z,allowWall=false)=>!protectedColumn(x,z)&&!treeColumns[z*S+x]&&(allowWall||!houses.some(h=>box(x,z,h.worldBounds,2)));
const changes=new Map(),features=[];function put(x,y,z,n,p,kind){assert(!protectedColumn(x,z),'Protected route/combat/door column');assert(x>=137&&x<=250&&z>=202&&z<=294&&y>=m.low&&y<m.high);const i=at(x,y,z);assert(!treeVoxels.has(i));const v=id(n,p);if(data[i]!==v){data[i]=v;changes.set(i,kind);}}
const fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'},slab={type:'bottom',waterlogged:'false'};
// Select an offset well site with its entire footprint outside the combat clearing.
let well;for(const [cx,cz]of [[211,252],[213,254],[213,251],[209,252],[211,249]]){let ok=true,lo=999,hi=-1;for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++){const x=cx+dx,z=cz+dz,g=ground[z*S+x];if(!safe(x,z)||g<0)ok=false;lo=Math.min(lo,g);hi=Math.max(hi,g);}if(ok&&hi-lo<=2){well={x:cx,z:cz,floorY:lo};break;}}
assert(well,'No safe well footprint; do not invade combat/road space');
for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++){
 const x=well.x+dx,z=well.z+dz,r=Math.max(Math.abs(dx),Math.abs(dz)),g=ground[z*S+x];
 for(let y=well.floorY-5;y<=g+1;y++){if(r===2)put(x,y,z,(x+z+y)%3===0?'mossy_cobblestone':'cobblestone',undefined,'well');else put(x,y,z,y===well.floorY-5?'gravel':'air',undefined,'well');}
 if(r===2)put(x,well.floorY+1,z,'cobblestone_slab',slab,'well');
}
for(const dx of [-2,2])for(let y=well.floorY+2;y<=well.floorY+4;y++)put(well.x+dx,y,well.z,'oak_fence',fence,'well');
for(let x=well.x-2;x<=well.x+2;x++)put(x,well.floorY+5,well.z,'oak_log',{axis:'x'},'well');
features.push({kind:'dry-well',...well,depth:5,closedRim:true});
function objectFits(x1,z1,x2,z2){let lo=999,hi=-1;for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){if(!safe(x,z)||ground[z*S+x]<0)return null;lo=Math.min(lo,ground[z*S+x]);hi=Math.max(hi,ground[z*S+x]);for(let y=ground[z*S+x]+1;y<=ground[z*S+x]+4;y++)if(get(x,y,z)!==air)return null;}return hi-lo<=1?hi:null;}
// Broken wagon: grounded wheels/axles, damaged bed, short broken shafts; no entities or containers.
let wagon;for(const [cx,cz]of [[182,280],[184,282],[179,280],[185,285],[176,282]]){const y=objectFits(cx-3,cz-2,cx+3,cz+2);if(y===null)continue;wagon={x:cx,z:cz,floorY:y};break;}assert(wagon,'No safe wagon site');
for(const dx of [-2,2])for(const dz of [-1,1]){const x=wagon.x+dx,z=wagon.z+dz,g=ground[z*S+x];for(let y=g+1;y<=wagon.floorY+1;y++)put(x,y,z,'dark_oak_log',{axis:'z'},'wagon');}
for(let x=wagon.x-2;x<=wagon.x+2;x++){put(x,wagon.floorY+1,wagon.z,'oak_log',{axis:'x'},'wagon');for(let dz=-1;dz<=1;dz++)if(!(x===wagon.x+2&&dz===1))put(x,wagon.floorY+2,wagon.z+dz,'spruce_slab',slab,'wagon');}
for(let x=wagon.x-2;x<=wagon.x;x++)put(x,wagon.floorY+3,wagon.z-1,'spruce_trapdoor',{facing:'north',half:'bottom',open:'true',powered:'false',waterlogged:'false'},'wagon');
features.push({kind:'broken-wagon',...wagon});
// Short wall fragments, fence gaps and timber piles, each skipped independently on conflicts.
let walls=0,piles=0,fences=0;
for(const [cx,cz]of [[166,283],[232,281],[243,263],[175,215],[241,222],[172,282]]){
 const y=objectFits(cx-3,cz-1,cx+3,cz+1);if(y===null)continue;
 for(let dx=-3;dx<=3;dx++)if(dx!==1){const x=cx+dx,g=ground[cz*S+x],height=dx<0?2:1;for(let yy=g+1;yy<=y+height;yy++)put(x,yy,cz,(x+yy)%3===0?'mossy_stone_bricks':'cracked_stone_bricks',undefined,'broken-wall');}walls++;features.push({kind:'broken-wall',x:cx,z:cz});
}
for(const [cx,cz]of [[230,283],[178,288],[243,253],[174,214]]){const y=objectFits(cx-2,cz-1,cx+2,cz+1);if(y===null)continue;for(let dx=-2;dx<=2;dx++)put(cx+dx,y+1,cz,'oak_log',{axis:'x'},'timber');put(cx,y+2,cz,'oak_log',{axis:'x'},'timber');piles++;features.push({kind:'timber-pile',x:cx,z:cz});}
for(const [cx,cz]of [[173,289],[236,279],[243,258],[175,211]])for(let dx=-3;dx<=3;dx++){if(dx===0||dx===2)continue;const x=cx+dx,g=ground[cz*S+x];if(!safe(x,cz)||g<0||get(x,g+1,cz)!==air)continue;put(x,g+1,cz,'oak_fence',fence,'broken-fence');fences++;}
// Two modest hanging lamps with a real beam above each lantern.
let lamps=0;for(const [x,z]of [[187,279],[216,258],[180,286],[240,260]]){const y=objectFits(x,z,x+1,z);if(y===null)continue;for(let yy=ground[z*S+x]+1;yy<=y+1;yy++)put(x,yy,z,'mossy_cobblestone',undefined,'lamp');for(let yy=y+2;yy<=y+3;yy++)put(x,yy,z,'oak_fence',fence,'lamp');for(let xx=x;xx<=x+1;xx++)put(xx,y+4,z,'oak_slab',slab,'lamp');put(x+1,y+3,z,'lantern',{hanging:'true',waterlogged:'false'},'lamp');features.push({kind:'old-lamp',x,z});if(++lamps===2)break;}
// Supported ivy only on remaining solid wall faces; preserve wide openings and all tree blocks.
const wallNames=new Set(['stone_bricks','mossy_stone_bricks','cracked_stone_bricks','cobblestone','spruce_planks','oak_planks','spruce_log']);let vines=0,plants=0,groundPatches=0;
for(const h of houses)for(let z=h.worldBounds[1]-1;z<=h.worldBounds[3]+1;z++)for(let x=h.worldBounds[0]-1;x<=h.worldBounds[2]+1;x++){
 if(!safe(x,z,true)||(x*7+z*11)%9!==0)continue;for(let y=h.floorY+1;y<=h.floorY+4;y++)if(get(x,y,z)===air){const props={north:'false',south:'false',east:'false',west:'false',up:'false'};for(const [dx,dz,d]of [[0,-1,'north'],[0,1,'south'],[1,0,'east'],[-1,0,'west']])if(wallNames.has(name(x+dx,y,z+dz)))props[d]='true';if(Object.values(props).includes('true')){put(x,y,z,'vine',props,'ivy');vines++;}}}
for(let z=206;z<=290;z++)for(let x=140;x<=247;x++){
 if(!safe(x,z,true)||houses.some(h=>box(x,z,h.worldBounds))||treeColumns[z*S+x])continue;const g=ground[z*S+x];if(g<0||get(x,g+1,z)!==air||!['grass_block','dirt','coarse_dirt','podzol'].includes(name(x,g,z)))continue;
 const n=(Math.imul(x,73856093)^Math.imul(z,19349663))>>>0;if(n%7===0){put(x,g+1,z,n%5===0?'fern':'short_grass',undefined,'weeds');plants++;}else if(n%41===0){put(x,g,z,n%2?'coarse_dirt':'podzol',undefined,'ground-patch');groundPatches++;}
}
assert(vines>5&&plants>20);let net=0;const counts={};for(const [i,r]of changes)if(data[i]!==original[i]){net++;counts[r]=(counts[r]||0)+1;const k=i%(S*S);assert(!protectedColumn(k%S,Math.floor(k/S)));}for(const i of treeVoxels)assert.equal(data[i],original[i]);
assert.equal(hash(fs.readFileSync(FILE)),hash(source));const out=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',out.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source));fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]));
const report={features,counts,wallFragments:walls,timberPiles:piles,fenceBlocks:fences,lamps,vines,plants,groundPatches,netChangedBlocks:net,changedChunks:out.changed,sourceHash:hash(source),resultHash:hash(out.bytes),backup:path.relative(ROOT,BACKUP),verification:{protectedColumnChanges:0,treeVoxelChanges:0,savedSemanticComparison:true,inGame:false}};
fs.writeFileSync(path.join(DIR,'village-dressing-report.json'),JSON.stringify(report,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;manifest.description='Overgrown village ruins · dry well, wagon and supported ivy';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({...report,features:features.length},null,2));
