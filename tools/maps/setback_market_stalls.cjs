const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-stall-setback-report.json');if(fs.existsSync(rf))throw Error('Already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
function id(n){const d={Name:'minecraft:'+n},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const air=id('air'),paving=id('stone_bricks'),mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),stalls=manifest.market.stalls.filter(s=>s.bounds[0]===66);if(stalls.length!==3)throw Error('Expected three left stalls');
const sources=new Set();for(const s of stalls){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=108;y++)sources.add(ix(x,y,z));}
for(const s of stalls){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=104;y<=108;y++)if(!sources.has(ix(x,y,z+8))&&m.definitions[old[ix(x,y,z+8)]].Name!=='minecraft:air')throw Error('Occupied destination');}
for(const s of stalls){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=108;y++)data[ix(x,y,z)]=y===103?paving:air;}
// Extend a narrow supported paved strip on existing land, keeping the island outline.
for(let x=65;x<=78;x++)for(let z=241;z<=253;z++){
 if(m.definitions[old[ix(x,90,z)]].Name==='minecraft:air')throw Error('Beyond island');
 for(let y=96;y<103;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:air')data[ix(x,y,z)]=id(y>=101?'dirt':'stone');
 data[ix(x,103,z)]=paving;
}
for(const s of stalls){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=108;y++)data[ix(x,y,z+8)]=old[ix(x,y,z)];s.bounds=[x0,z0+8,x1,z1+8];}
for(const s of stalls){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=108;y++)if(data[ix(x,y,z)]!==old[ix(x,y,z-8)])throw Error('Translation mismatch');}
// Walkable interior and a continuous approach on the east of the translated stalls.
for(let z=215;z<=253;z++)for(let x=75;x<=78;x++)for(let y=104;y<=105;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')throw Error('Approach blocked');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){const x=i%m.size,z=Math.floor(i/m.size)%m.size,y=Math.floor(i/(m.size*m.size))+m.low;if(x<65||x>78||z<215||z>253||y<96||y>108)throw Error('Outside setback');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-stall-setback/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
manifest.market.stallPlots=manifest.market.stalls.map(s=>s.bounds);manifest.market.bounds[3]=253;manifest.rooms=manifest.rooms.map(r=>r.id===manifest.market.id?manifest.market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.market.id?{...z,...manifest.market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({stalls,offset:[0,0,8],changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,stalls});
