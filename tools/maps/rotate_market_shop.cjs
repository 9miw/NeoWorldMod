const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-shop-rotation-report.json');if(fs.existsSync(rf))throw Error('Already rotated');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);
const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Map();
function put(x,y,z,n,p){if(x<64||x>92||z<188||z>215||y<103||y>115)throw Error('Outside shop');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.set(ix(x,y,z),[x,y,z]);}
const c=Math.cos(Math.PI/6),s=.5,point=(u,v)=>[Math.round(78+c*u+s*v),Math.round(202-s*u+c*v)];
// Clear only the existing shop envelope; surrounding market furniture is outside it.
for(let x=69;x<=87;x++)for(let z=193;z<=211;z++)for(let y=103;y<=115;y++)put(x,y,z,y===103?'stone_bricks':'air');
for(let x=64;x<=92;x++)for(let z=188;z<=215;z++){
 const dx=x-78,dz=z-202,u=c*dx-s*dz,v=s*dx+c*dz;if(Math.abs(u)>9.5||Math.abs(v)>9.5)continue;
 for(let y=104;y<=115;y++){const n=m.definitions[data[ix(x,y,z)]].Name;if(n!=='minecraft:air')throw Error('Rotated footprint intersects '+[x,y,z,n]);}
 if(m.definitions[data[ix(x,103,z)]].Name==='minecraft:air')throw Error('Unsupported floor');put(x,103,z,'spruce_planks');
}
function line(u0,v0,u1,v1,y,n,p){const steps=Math.ceil(Math.hypot(u1-u0,v1-v0)*8);for(let t=0;t<=steps;t++){const [x,z]=point(u0+(u1-u0)*t/steps,v0+(v1-v0)*t/steps);put(x,y,z,n,p);}}
for(const u of [-9,9])for(const v of [-9,0,9]){const [x,z]=point(u,v);for(let y=104;y<=110;y++)put(x,y,z,'dark_oak_log',{axis:'y'});}
for(const u of [-9,9])line(u,-9,u,9,110,'dark_oak_log',{axis:'z'});
for(const v of [-9,9]){
 line(-9,v,9,v,110,'dark_oak_log',{axis:'x'});
 for(let u=-9;u<=9;u++)line(Math.max(-9,u-.5),v,Math.min(9,u+.5),v,111+Math.floor((9-Math.abs(u))/2),'spruce_planks');
}
line(0,-9,0,9,115,'dark_oak_log',{axis:'z'});
// Two-block headroom and level-floor connectivity from the market road to the entrance and interior.
const seen=new Set(),queue=[[112,215]],k=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<60||x>143||z<181||z>244||seen.has(k(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(103)==='minecraft:air'||n(104)!=='minecraft:air'||n(105)!=='minecraft:air')continue;seen.add(k(x,z));queue.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
for(const [x,z]of [[83,210],[78,202],[84,212],[88,224],[102,224]])if(!seen.has(k(x,z)))throw Error('Route blocked '+[x,z]);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-shop-rotation/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),shop=manifest.market.shops.find(s=>s.id==='general');Object.assign(shop,{bounds:[65,189,91,215],localSize:[19,19],center:[78,103,202],rotationDegrees:30,faces:'southeast toward market fountain',entrance:[83,104,210],corners:[point(-9,-9),point(9,-9),point(9,9),point(-9,9)]});manifest.rooms=manifest.rooms.map(r=>r.id===manifest.market.id?manifest.market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.market.id?{...z,...manifest.market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({shop,changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup),walkableCells:seen.size},null,2));console.log({changedBlocks:count,changedChunks:result.changed,corners:shop.corners,entrance:shop.entrance});
