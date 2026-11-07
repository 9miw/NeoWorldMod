const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1');
const report=path.join(map,'market-road-fix-report.json');if(fs.existsSync(report))throw Error('Already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
const prev=createLibrary(path.join(root,'archive/map-edit-backups/before-market-expansion')).load(path.basename(map)),pb=Buffer.from(prev.blocks,'base64'),key=d=>JSON.stringify(d),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
function id(d){const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const air=id({Name:'minecraft:air'}),mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),market=manifest.market,stall=market.stalls.find(s=>s.bounds[0]===116&&s.bounds[1]===215);if(!stall)throw Error('Expected stall missing');
for(let z=215;z<=221;z++)for(let x=116;x<=124;x++)for(let y=103;y<=108;y++){
 const state=old[ix(x,y,z)],dx=x-17,dz=z+20;
 if(y>103&&m.definitions[old[ix(dx,y,dz)]].Name!=='minecraft:air')throw Error('Destination occupied');
 data[ix(dx,y,dz)]=state;
 data[ix(x,y,z)]=y===103?id(prev.definitions[pb.readUInt16LE(ix(x,y,z)*2)]):air;
}
for(let z=212;z<=218;z++)for(let x=106;x<=143;x++)for(let y=104;y<=108;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')throw Error('Road blocked '+[x,y,z]);
stall.bounds=[99,235,107,241];market.stallPlots=market.stalls.map(s=>s.bounds);manifest.rooms=manifest.rooms.map(r=>r.id===market.id?market:r);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-road-fix/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===market.id?{...z,...market}:z),null,2));
fs.writeFileSync(report,JSON.stringify({from:[116,215,124,221],to:stall.bounds,changedBlocks:count,changedChunks:result.changed,road:[106,212,143,218],backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,road:'clear 7 blocks wide',stalls:market.stalls.length});
