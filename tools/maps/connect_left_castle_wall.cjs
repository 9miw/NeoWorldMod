const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'castle-left-wall-connection-report.json');if(fs.existsSync(rf))throw Error('Corner already connected');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const pre=createLibrary(path.join(root,'archive/map-edit-backups/before-governor-castle')).load(path.basename(map)),pb=Buffer.from(pre.blocks,'base64'),protectedCol=new Set(),nat=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
for(let z=112;z<=124;z++)for(let x=107;x<=128;x++)for(let y=96;y<pre.high;y++)if(!nat.has(pre.definitions[pb.readUInt16LE(ix(x,y,z)*2)].Name))protectedCol.add(z*S+x);
function put(x,y,z,n,p){if(x<107||x>128||z<112||z>124||y<107||y>123)throw Error('Outside corner repair');if(protectedCol.has(z*S+x))throw Error('Existing blacksmith column');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const points=[[109,114],[110,115],[112,116],[114,117],[116,118],[118,120],[121,122],[124,123],[126,123]],line=new Set(),cells=new Set();
for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],steps=Math.ceil(Math.hypot(a[0]-b[0],a[1]-b[1])*8);for(let t=0;t<=steps;t++){
 const x=Math.round(a[0]+(b[0]-a[0])*t/steps),z=Math.round(a[1]+(b[1]-a[1])*t/steps);line.add(z*S+x);
 for(let dx=-1;dx<=1;dx++)for(let dz=-1;dz<=1;dz++){const xx=x+dx,zz=z+dz;if(zz<112||zz>124||xx<107||xx>128||protectedCol.has(zz*S+xx))continue;cells.add(zz*S+xx);}
}}
for(const k of line)if(protectedCol.has(k))throw Error('Center line crosses earlier zone');
const existingDeck=k=>m.definitions[old[ix(k%S,120,Math.floor(k/S))]].Name==='minecraft:stone_bricks',walk=new Set(cells);
for(let z=112;z<=124;z++)for(let x=107;x<=149;x++)if(existingDeck(z*S+x))walk.add(z*S+x);
for(const k of cells){const x=k%S,z=Math.floor(k/S),underpass=x>=119&&x<=121&&z>=112&&z<=119;
 if(!existingDeck(k)){
  if(!underpass)for(let y=112;y<120;y++)put(x,y,z,y===114||y===118?'chiseled_stone_bricks':'stone_bricks');
  put(x,120,z,'stone_bricks');
 }
 const edge=![k-1,k+1,k-S,k+S].every(j=>walk.has(j));
 if(!edge||line.has(k)){put(x,121,z,'air');put(x,122,z,'air');}
 else {put(x,121,z,'stone_bricks');put(x,122,z,(x+z)%4<2?'chiseled_stone_bricks':'air');}
}
// The walkway must connect both wall segments, independently of the staircase access check.
const reachable=new Set(),q=[[109,114]],key=(x,z)=>x+','+z;for(let i=0;i<q.length;i++){const [x,z]=q[i];if(x<107||x>149||z<109||z>124||reachable.has(key(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(120)!=='minecraft:stone_bricks'||n(121)!=='minecraft:air'||n(122)!=='minecraft:air')continue;reachable.add(key(x,z));q.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
if(!reachable.has(key(142,123)))throw Error('Left and front wall still disconnected');
const src=fs.readFileSync(path.join(root,'tools/maps/remodel_castle_fortress.cjs'),'utf8'),start=src.indexOf('const cache=new Map()'),end=src.indexOf('let count=0;',start);eval(src.slice(start,end)+'\nconsole.log({verifiedRoutes:targets.length});');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),r=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-castle-left-wall-fix/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',r.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.castle.leftWallConnection={centerline:points,deckY:120,minimumWidth:3,stairUnderpass:true};manifest.rooms=manifest.rooms.map(r=>r.id===manifest.castle.id?manifest.castle:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.castle.id?{...z,...manifest.castle}:z),null,2));fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:r.changed,points,deckY:120,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:r.changed,connectedDeckCells:reachable.size});
