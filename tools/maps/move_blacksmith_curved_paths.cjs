const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'blacksmith-relocation-report.json');if(fs.existsSync(rf))throw Error('Already moved');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function stateId(d){const k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
function put(x,y,z,n,p){if(x<44||x>153||z<94||z>180||y<90||y>130)throw Error('Outside relocation');data[ix(x,y,z)]=stateId({Name:'minecraft:'+n,...(p?{Properties:p}:{})});planned.add(ix(x,y,z));}
const pre=createLibrary(path.join(root,'archive/map-edit-backups/before-blacksmith')).load(path.basename(map)),pb=Buffer.from(pre.blocks,'base64'),preIds=pre.definitions.map(stateId);
// Revert only the previous smith edit, including its straight roads and supporting terrain.
for(let z=110;z<=180;z++)for(let x=46;x<=153;x++)for(let y=90;y<=130;y++){const i=ix(x,y,z),prior=preIds[pb.readUInt16LE(i*2)];if(data[i]!==prior){data[i]=prior;planned.add(i);}}
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']),top=new Int16Array(S*S).fill(-1),built=new Uint8Array(S*S);
for(let z=91;z<=184;z++)for(let x=41;x<=156;x++)for(let y=90;y<m.high;y++){const n=m.definitions[data[ix(x,y,z)]].Name;if(n!=='minecraft:air')top[z*S+x]=y;if(!natural.has(n))built[z*S+x]=1;}
const terrace=new Map();for(let z=132;z<=166;z++)for(let x=61;x<=116;x++){
 const n=m.definitions[old[ix(x,107,z)]].Name;if(n==='minecraft:stone_bricks'||n==='minecraft:andesite'||n==='minecraft:polished_andesite')terrace.set((z-15)*S+x,107);
}
const cubic=(p,t)=>{const u=1-t;return [0,1].map(j=>u*u*u*p[0][j]+3*u*u*t*p[1][j]+3*u*t*t*p[2][j]+t*t*t*p[3][j]);};
const marketCurve=[[112,178],[128,168],[116,157],[102,149]],roadCurve=[[114,139],[131,136],[137,149],[153,150]],marketRows=new Map(),roadRows=new Map();
for(let i=0;i<=1000;i++){const a=cubic(marketCurve,i/1000),b=cubic(roadCurve,i/1000);marketRows.set(Math.round(a[1]),Math.round(a[0]));roadRows.set(Math.round(b[0]),Math.round(b[1]));}
const paths=new Map();for(const [z,cx]of marketRows){const h=z>=173?103:z>=167?104:z>=161?105:z>=155?106:107;for(let x=cx-3;x<=cx+3;x++)paths.set(z*S+x,{h,stairs:[172,166,160,154].includes(z)?'north':null});}
for(const [x,cz]of roadRows){const h=x>=147?108:107;for(let z=cz-2;z<=cz+2;z++)paths.set(z*S+x,{h,stairs:x===147?'east':null});}
// Smooth a wide natural shoulder around the shifted terrace and winding roads.
const support=new Map([...terrace,...[...paths].map(([k,v])=>[k,v.h])]);
for(let z=95;z<=180;z++)for(let x=44;x<=153;x++){
 const k=z*S+x;if(built[k]||top[k]<96)continue;let target=top[k];if(support.has(k))target=support.get(k);else{
  let dist=19,near=target;for(const [j,h]of support){const d=Math.hypot(x-j%S,z-Math.floor(j/S));if(d<dist){dist=d;near=h;}}
  if(dist>=18)continue;let edge=false;for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(top[(z+dz)*S+x+dx]<96)edge=true;if(edge)continue;
  const t=dist/18,e=t*t*(3-2*t);target=Math.round(near+(top[k]-near)*e);
 }
 for(let y=Math.min(top[k],target)-2;y<=Math.max(top[k],target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
// Translate the complete saved building, furnishings and court exactly, including clear interior air.
for(const [k]of terrace){const x=k%S,z=Math.floor(k/S);if(built[k])throw Error('Shifted court collision');for(let y=107;y<=129;y++){data[ix(x,y,z)]=old[ix(x,y,z+15)];planned.add(ix(x,y,z));}}
for(const [k,p]of paths){if(terrace.has(k))continue;const x=k%S,z=Math.floor(k/S);if(built[k])throw Error('Path overlaps existing architecture');put(x,p.h,z,p.stairs?'stone_brick_stairs':(x+z)%5===0?'coarse_dirt':'dirt_path',p.stairs?{facing:p.stairs,half:'bottom',shape:'straight',waterlogged:'false'}:undefined);}
// Preserve every voxel of the main building and L wing after its 15-block translation.
for(let x=65;x<=97;x++)for(let z=121;z<=147;z++)for(let y=107;y<=129;y++){
 if(!terrace.has(z*S+x))continue;if(data[ix(x,y,z)]!==old[ix(x,y,z+15)])throw Error('Building translation mismatch');
}
const surface=(hx,hz)=>{const x=Math.floor(hx/2),z=Math.floor(hz/2);if(x<58||x>155||z<115||z>210)return null;for(let y=102;y<=109;y++){
 const d=m.definitions[data[ix(x,y,z)]],n=d.Name;if(n==='minecraft:air'||n.includes('water')||n.includes('lantern')||n.includes('iron_bars'))continue;let h=y+1;if(n==='minecraft:dirt_path')h=y+.9375;if(n.endsWith('_stairs')){const f=d.Properties.facing,hi=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=y+(hi?1:.5);}if(n.endsWith('_slab'))h=y+.5;
 let free=true;for(let yy=Math.ceil(h);yy<Math.ceil(h)+2;yy++)if(m.definitions[data[ix(x,yy,z)]].Name!=='minecraft:air')free=false;if(free)return h;
 }return null;};
const seen=new Set(),queue=[[224,402]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(seen.has(key(x,z)))continue;const h=surface(x,z);if(h===null)continue;seen.add(key(x,z));for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]]){const nh=surface(nx,nz);if(nh!==null&&Math.abs(nh-h)<=.5&&!seen.has(key(nx,nz)))queue.push([nx,nz]);}}
for(const [x,z]of [[112,178],[102,149],[87,142],[87,131],[74,140],[153,150]])if(!seen.has(key(x*2,z*2)))throw Error('New path unreachable '+[x,z]);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-blacksmith-relocation/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),smith=manifest.blacksmith;smith.bounds=[62,118,115,163];smith.buildingBounds=[66,122,96,146];smith.entrance[2]-=15;smith.workBay=[79,135,95,146];smith.chimneyBounds=[67,108,123,73,129,129];for(const s of smith.stations)s.at[2]-=15;smith.marketApproach=[112,104,178];smith.mainStreetConnection=[153,109,150];smith.paths={marketCurve,roadCurve,widths:[7,5],marketStairRows:[172,166,160,154],mainStreetStairX:147};manifest.rooms=manifest.rooms.map(r=>r.id===smith.id?smith:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===smith.id?{...z,...smith}:z),null,2));fs.writeFileSync(rf,JSON.stringify({offset:[0,0,-15],smith,changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,smith});
