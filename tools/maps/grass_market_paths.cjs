const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-grass-paths-report.json');if(fs.existsSync(rf))throw Error('Already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),eligible=new Set();
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const grass=id('grass_block',{snowy:'false'}),dirt=id('dirt_path'),coarse=id('coarse_dirt');
const routes=[
 {name:'main approach',width:5,points:[[133,215],[116,215],[106,212],[95,215]]},
 {name:'large shop',width:3,points:[[95,215],[88,214],[83,210],[82,207]]},
 {name:'food shop',width:3,points:[[106,212],[102,205],[102,196]]},
 {name:'supplies shop',width:3,points:[[116,215],[123,208],[123,198]]},
 {name:'blue west stall',width:3,points:[[87,224],[80,224],[70,226]]},
 {name:'orange west stall',width:3,points:[[80,224],[80,231],[70,237]]},
 {name:'red inner stall',width:3,points:[[92,232],[90,235],[85,240]]},
 {name:'blue inner stall',width:3,points:[[99,231],[103,235],[103,238]]},
 {name:'orange east stall',width:3,points:[[103,224],[112,225],[120,229]]},
 {name:'red east stall',width:3,points:[[112,225],[115,235],[120,240]]},
 {name:'future smith connection',width:3,points:[[112,215],[112,201],[112,181]]}
];
// Convert only the paved ground layer. Water basin, timber floors and all stair blocks stay intact.
for(let z=179;z<=244;z++)for(let x=59;x<=132;x++){
 const n=m.definitions[old[ix(x,103,z)]].Name;if(!['minecraft:stone_bricks','minecraft:smooth_stone'].includes(n))continue;
 if(Math.hypot(x-95,z-224)<=5.5)continue;
 // Retain stone under architectural supports and furniture.
 const above=m.definitions[old[ix(x,104,z)]].Name;if(!['minecraft:air','minecraft:water'].includes(above))continue;
 data[ix(x,103,z)]=grass;eligible.add(z*m.size+x);
}
function distance(x,z,a,b){const dx=b[0]-a[0],dz=b[1]-a[1],t=Math.max(0,Math.min(1,((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz)));return Math.hypot(x-a[0]-dx*t,z-a[1]-dz*t);}
let pathCells=0,grassCells=0;
for(const k of eligible){const x=k%m.size,z=Math.floor(k/m.size);let signed=Math.abs(Math.hypot(x-95,z-224)-8)-1.5;
 for(const route of routes)for(let j=1;j<route.points.length;j++)signed=Math.min(signed,distance(x,z,route.points[j-1],route.points[j])-(route.width-1)/2);
 if(signed<=.35){const noise=((x*73+z*31)%11+11)%11;data[ix(x,103,z)]=signed>-.5||noise===0?coarse:dirt;pathCells++;}else grassCells++;
}
// Verify actual walking heights, including lowered dirt paths and entrance stair halves.
const surface=(hx,hz)=>{const x=Math.floor(hx/2),z=Math.floor(hz/2);if(x<55||x>143||z<170||z>244)return null;const d=m.definitions[data[ix(x,103,z)]];if(d.Name==='minecraft:air')return null;let h=d.Name==='minecraft:dirt_path'?103.9375:104;
 if(d.Name.endsWith('_stairs')){const f=d.Properties.facing,high=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=high?104:103.5;}
 for(let y=Math.ceil(h);y<Math.ceil(h)+2;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')return null;return h;};
const seen=new Set(),queue=[[270,430]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(seen.has(key(x,z)))continue;const h=surface(x,z);if(h===null)continue;seen.add(key(x,z));for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]]){const nh=surface(nx,nz);if(nh!==null&&Math.abs(nh-h)<=.5&&!seen.has(key(nx,nz)))queue.push([nx,nz]);}}
for(const [x,z]of [[76,197],[102,188],[123,189],[70,226],[70,237],[85,240],[103,238],[120,229],[120,240],[87,224],[103,224]])if(!seen.has(key(x*2,z*2)))throw Error('Inaccessible '+[x,z]);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-grass-paths/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.market.ground={type:'grass with dirt paths and coarse dirt edges',floorY:103,grassCells,pathCells,routes,fountainRingRadius:8};manifest.rooms=manifest.rooms.map(r=>r.id===manifest.market.id?manifest.market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.market.id?{...z,...manifest.market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:result.changed,grassCells,pathCells,routes,backup:path.relative(root,backup),reachableHalfCells:seen.size},null,2));console.log({changedBlocks:count,changedChunks:result.changed,grassCells,pathCells});
