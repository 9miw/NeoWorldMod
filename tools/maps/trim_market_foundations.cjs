const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-foundation-trim-report.json');if(fs.existsSync(rf))throw Error('Trim already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),market=manifest.market;
function put(x,y,z,facing){if(x<56||x>134||z<176||z>216||y<103||y>104)throw Error('Outside foundation trim');const d={Name:'minecraft:stone_brick_stairs',Properties:{facing,half:'bottom',shape:'straight',waterlogged:'false'}},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const summaries=[];
for(const shop of market.shops){
 const big=shop.id==='general',hw=big?12:8,hd=big?11:(shop.bounds[3]-shop.bounds[1])/2,cx=big?76:(shop.bounds[0]+shop.bounds[2])/2,cz=big?197:(shop.bounds[1]+shop.bounds[3])/2,c=big?Math.cos(Math.PI/6):1,s=big?.5:0;
 const local=(x,z)=>[c*(x-cx)-s*(z-cz),s*(x-cx)+c*(z-cz)],world=(u,v)=>[Math.round(cx+c*u+s*v),Math.round(cz-s*u+c*v)];
 let trim=0,apron=0;
 for(let x=Math.floor(cx-22);x<=Math.ceil(cx+22);x++)for(let z=Math.floor(cz-22);z<=Math.ceil(cz+22);z++){
  const [u,v]=local(x,z),au=Math.abs(u),av=Math.abs(v);let facing;
  if(au>hw+.45&&au<=hw+1.5&&av<=hd+1.2)facing=u>0?'west':'east';
  else if(av>hd+.45&&av<=hd+1.5&&au<=hw+1.2)facing=v>0?'north':'south';else continue;
  if(v>0&&Math.abs(u)<3.5)continue;
  const under=m.definitions[data[ix(x,103,z)]].Name,here=m.definitions[data[ix(x,104,z)]].Name;
  if(here!=='minecraft:air'||!['minecraft:stone_bricks','minecraft:grass_block','minecraft:smooth_stone','minecraft:dirt'].includes(under))continue;
  put(x,104,z,facing);trim++;
 }
 // The shallow apron occupies the paving layer; high faces meet the unchanged interior floor.
 for(let u=-1;u<=1;u++){
  const [x,z]=world(u,hd+1);const n=m.definitions[data[ix(x,103,z)]].Name;
  if(!['minecraft:stone_bricks','minecraft:smooth_stone'].includes(n))continue;
  if(m.definitions[data[ix(x,104,z)]].Name!=='minecraft:air')throw Error('Entrance apron obstructed');put(x,103,z,'north');apron++;
 }
 shop.foundationTrim={material:'stone_brick_stairs',trimBlocks:trim,apronBlocks:apron,interiorFloorY:103};summaries.push({id:shop.id,...shop.foundationTrim});
}
// Test walking at half-block resolution, including the stair treads and two-block headroom.
const surface=(hx,hz)=>{const x=Math.floor(hx/2),z=Math.floor(hz/2);if(x<55||x>143||z<170||z>244)return null;
 const d=m.definitions[data[ix(x,103,z)]];if(d.Name==='minecraft:air')return null;let h=104;
 if(d.Name.endsWith('_stairs')){const f=d.Properties.facing,high=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=high?104:103.5;}
 for(let y=Math.ceil(h);y<Math.ceil(h)+2;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')return null;return h;};
const seen=new Set(),queue=[[224,430]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(seen.has(key(x,z)))continue;const h=surface(x,z);if(h===null)continue;seen.add(key(x,z));for(const [nx,nz]of [[x+1,z],[x-1,z],[x,z+1],[x,z-1]]){const nh=surface(nx,nz);if(nh!==null&&Math.abs(nh-h)<=.5&&!seen.has(key(nx,nz)))queue.push([nx,nz]);}}
for(const [x,z]of [[76,197],[102,188],[123,189]])if(!seen.has(key(x*2,z*2)))throw Error('Shop inaccessible');
for(let z=212;z<=218;z++)for(let x=106;x<=143;x++)for(let y=103;y<=108;y++)if(data[ix(x,y,z)]!==old[ix(x,y,z)])throw Error('Street changed');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-foundation-trim/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
manifest.rooms=manifest.rooms.map(r=>r.id===market.id?market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===market.id?{...z,...market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({summaries,changedBlocks:count,changedChunks:result.changed,reachableHalfCells:seen.size,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,summaries});
