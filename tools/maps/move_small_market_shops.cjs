const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'small-market-shops-move-report.json');if(fs.existsSync(rf))throw Error('Move already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const air=id('air'),paving=id('stone_bricks'),mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),shops=manifest.market.shops.filter(s=>s.id!=='general'),source=new Set(),dest=new Set();
for(const s of shops){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=115;y++){source.add(ix(x,y,z));dest.add(ix(x,y,z-10));}}
for(const i of dest)if(!source.has(i)&&m.definitions[old[i]].Name!=='minecraft:air'){
 const y=Math.floor(i/(m.size*m.size))+m.low;if(y>103)throw Error('Destination occupied');
 const n=m.definitions[old[i]].Name;if(!['minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:smooth_stone','minecraft:stone_bricks'].includes(n))throw Error('Protected floor at destination');
}
for(const s of shops){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=115;y++)data[ix(x,y,z)]=y===103?paving:air;}
for(const s of shops){const [x0,z0,x1,z1]=s.bounds;for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++){
 for(let y=96;y<103;y++)if(m.definitions[data[ix(x,y,z-10)]].Name==='minecraft:air')data[ix(x,y,z-10)]=id(y>=101?'dirt':'stone');
 for(let y=103;y<=115;y++)data[ix(x,y,z-10)]=old[ix(x,y,z)];
}s.bounds=[x0,z0-10,x1,z1-10];s.entrance[2]-=10;}
// Seven-block paths connect the translated front doors to the retained market paving.
for(const s of shops){const [cx,,ez]=s.entrance;for(let z=ez+1;z<=208;z++)for(let x=cx-3;x<=cx+3;x++){
 for(let y=96;y<103;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:air')data[ix(x,y,z)]=id(y>=101?'dirt':'stone');data[ix(x,103,z)]=paving;
 for(let y=104;y<=105;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')throw Error('Entrance path blocked');
}}
// Route to the future smith now uses the gap between the two translated shops.
for(let x=111;x<=114;x++)for(let z=181;z<=208;z++){
 for(let y=96;y<103;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:air')data[ix(x,y,z)]=id(y>=101?'dirt':'stone');data[ix(x,103,z)]=paving;
 for(let y=104;y<=105;y++)if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')throw Error('Smith path blocked');
}
manifest.market.smithRoute=[112,104,181];
for(const s of shops){const [x0,,x1,z1]=s.bounds;let z0=s.bounds[1];for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=103;y<=115;y++)if(data[ix(x,y,z)]!==old[ix(x,y,z+10)])throw Error('Translation mismatch');}
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){const x=i%m.size,z=Math.floor(i/m.size)%m.size,y=Math.floor(i/(m.size*m.size))+m.low;if(x<94||x>131||z<181||z>208||y<96||y>115)throw Error('Outside move');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-small-market-shops-move/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
manifest.rooms=manifest.rooms.map(r=>r.id===manifest.market.id?manifest.market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.market.id?{...z,...manifest.market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({shops,offset:[0,0,-10],changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,shops});
