// Plant approved reusable assets into the saved forest-basin world, never regenerate terrain.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca'),broadPass=process.argv.includes('--broadleaf-pass'),BACKUP=path.join(ROOT,'archive/map-edit-backups',broadPass?'before-forest-basin-broadleaf':'before-forest-basin-trees','leveling_island_01');
const previous=broadPass?JSON.parse(fs.readFileSync(path.join(DIR,'forest-placement-report.json'))):null;
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'),'Use --apply');assert(!fs.existsSync(BACKUP),'Already planted');
const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),S=m.size,old=Buffer.from(m.blocks,'base64');
const oldHigh=m.high;m.high=192;const data=new Uint16Array(S*S*(m.high-m.low));for(let i=0;i<old.length/2;i++)data[i]=old.readUInt16LE(i*2);const original=data.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
const at=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>y<m.low||y>=m.high?0:data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name;
function id(d){const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const ground=new Int16Array(S*S).fill(-1);for(let z=0;z<S;z++)for(let x=0;x<S;x++)for(let y=oldHigh-1;y>=m.low;y--)if(name(x,y,z)==='minecraft:grass_block'){ground[z*S+x]=y;break;}
const protectedMask=new Uint8Array(S*S),box=(x,z,b,p=3)=>x>=b[0]-p&&x<=b[2]+p&&z>=b[1]-p&&z<=b[3]+p;
// Keep combat clearings, chapel reserve, cave frontage, house envelopes and the main sight corridor clear.
const clearings=[[104,220,14],[193,254,13],[246,151,11]];
for(let z=0;z<S;z++)for(let x=0;x<S;x++){
 const t=Math.max(0,Math.min(1,(320-z)/249)),sightX=193+(126-193)*t;
 if(z>=312||layout.buildingReserves.some(p=>box(x,z,p.bounds))||box(x,z,layout.zones[3].bounds,5)||box(x,z,layout.zones[4].bounds,5)||clearings.some(([cx,cz,r])=>Math.hypot(x-cx,z-cz)<=r)||z>=71&&z<=320&&Math.abs(x-sightX)<=9)protectedMask[z*S+x]=1;
}
for(const n of layout.walkNodes)for(let dz=-5;dz<=5;dz++)for(let dx=-5;dx<=5;dx++)if(n.x+dx>=0&&n.z+dz>=0&&n.x+dx<S&&n.z+dz<S)protectedMask[(n.z+dz)*S+n.x+dx]=1;
const assets=['t018','t024','t073','t003','t001','t106'].map(asset=>{const t=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures',asset+'.nbt'))));const logs=t.blocks.filter(b=>/_(log|wood)$/.test(t.palette[b.state].Name)),minY=Math.min(...logs.map(b=>b.pos[1])),roots=logs.filter(b=>b.pos[1]===minY),ax=Math.round(roots.reduce((s,b)=>s+b.pos[0],0)/roots.length),az=Math.round(roots.reduce((s,b)=>s+b.pos[2],0)/roots.length);return {asset,t,roots,ax,az,minY};});
const occupied=new Uint8Array(S*S),placed=[],skips={},changes=new Set();
function skip(r){skips[r]=(skips[r]||0)+1;return false;}
function place(a,x,z){const gy=ground[z*S+x];if(gy<0||protectedMask[z*S+x])return skip('anchor protected or no grass');const {t,ax,az,minY,roots}=a,ox=x-ax,oz=z-az,oy=gy+1-minY,voxels=[];
 for(const b of roots){const gx=ox+b.pos[0],gz=oz+b.pos[2],g=ground[gz*S+gx];if(g<0||Math.abs(g-gy)>2)return skip('root slope');}
 const footprint=new Set();for(const b of t.blocks){const [px,py,pz]=b.pos,bx=ox+px,by=oy+py,bz=oz+pz,p=t.palette[b.state],isLog=/_(log|wood)$/.test(p.Name);if(bx<3||bz<3||bx>=S-3||bz>=312||by>=m.high||by<m.low)return skip('bounds');const k=bz*S+bx;if(protectedMask[k]||occupied[k])return skip('protected canopy or tree spacing');if(ground[k]<0)return skip('canopy outside island');const n=name(bx,by,bz);if(n!=='minecraft:air'&&!(isLog&&by>=gy-1&&by<=gy+1&&['minecraft:grass_block','minecraft:dirt','minecraft:stone'].includes(n)))return skip('voxel collision');footprint.add(k);const props={...p.Properties};if(p.Name.endsWith('_leaves'))Object.assign(props,{persistent:'true',distance:'7',waterlogged:'false'});voxels.push([bx,by,bz,id({Name:p.Name,...(Object.keys(props).length?{Properties:props}:{})})]);}
 // Fill at most two missing root-support blocks with matching trunk wood, never add dirt platforms.
 for(const b of roots){const bx=ox+b.pos[0],bz=oz+b.pos[2],g=ground[bz*S+bx],p=t.palette[b.state];for(let y=g+1;y<gy+1;y++){assert(name(bx,y,bz)==='minecraft:air');voxels.push([bx,y,bz,id({Name:p.Name,Properties:{...p.Properties,axis:'y'}})]);}}
 for(const [bx,by,bz,v] of voxels){const i=at(bx,by,bz);data[i]=v;changes.add(i);}
 for(const k of footprint){const bx=k%S,bz=Math.floor(k/S);for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)if(bx+dx>=0&&bz+dz>=0&&bx+dx<S&&bz+dz<S)occupied[(bz+dz)*S+bx+dx]=1;}
 placed.push({asset:a.asset,origin:[ox,oy,oz],anchor:[x,gy+1,z],size:t.size,rootSupports:roots.map(b=>[ox+b.pos[0],ground[(oz+b.pos[2])*S+ox+b.pos[0]],oz+b.pos[2]])});return true;
}
// Dense eastern shoulder first, then scattered clusters on the western/northern slopes.
const candidates=[];for(let z=106;z<=225;z+=11)for(let x=225+(Math.floor(z/11)%2)*5;x<=314;x+=12)candidates.push([x,z,'east']);
for(let z=96;z<=297;z+=13)for(let x=65+(Math.floor(z/13)%2)*6;x<=285;x+=14)if(Math.sin(x/24)+Math.cos(z/29)>.05)candidates.push([x,z,'outer']);
if(broadPass){for(let z=0;z<S;z++)for(let x=0;x<S;x++){let tree=false;for(let y=Math.max(0,ground[z*S+x]+1);y<oldHigh;y++){const n=name(x,y,z);if(/_(leaves|log|wood)$/.test(n)){tree=true;break;}}if(tree)for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)if(x+dx>=0&&z+dz>=0&&x+dx<S&&z+dz<S)occupied[(z+dz)*S+x+dx]=1;}candidates.length=0;for(let z=98;z<=301;z+=5)for(let x=65+(Math.floor(z/5)%2)*2;x<=312;x+=6)candidates.push([x,z,'broad']);}
let index=0;for(const [x,z,area]of candidates){const large=index++%13===0,preferred=broadPass?index%3:large?5:area==='east'?[0,3,1,3,2][index%5]:[0,1,2,4][index%4];if(!place(assets[preferred],x,z)&&preferred!==4&&!broadPass)place(assets[4],x,z);}
assert(placed.length>=(broadPass?5:25),'Not enough collision-free tree placements');
let overwritten=0;for(const i of changes){const k=i%(S*S);assert(!protectedMask[k]);if(original[i]!==0)overwritten++;}
assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent world change');const result=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',result.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent change before commit');fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<saved.size*saved.size*(saved.high-saved.low);i++){const k=key(saved.definitions[sb.readUInt16LE(i*2)]);assert.equal(k,key(m.definitions[data[i]]),'Saved semantic mismatch '+i);}for(let i=sb.length/2;i<data.length;i++)assert.equal(data[i],0);
const combined=[...(previous?.placements||[]),...placed];const report={trees:combined.length,placements:combined,latestPassTrees:placed.length,previousPass:previous?{trees:previous.trees,resultHash:previous.resultHash,backup:previous.backup}:undefined,skips,changedBlocks:changes.size,overwrittenRootTerrainBlocks:overwritten,protectedVoxelChanges:0,changedChunks:result.changed,sourceHash:hash(source),resultHash:hash(result.bytes),backup:path.relative(ROOT,BACKUP),clearings,sightCorridor:{south:[193,320],north:[126,71],halfWidth:9},verification:{savedSemanticComparison:true,rootSlopeMax:2,treeBuffer:3,persistentLeaves:true,inGame:false}};
fs.writeFileSync(path.join(DIR,'forest-placement-report.json'),JSON.stringify(report,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;manifest.description='Terrain with grouped reusable tree assets · buildings pending';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({...report,placements:undefined},null,2));
