const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),report=path.join(map,'hall-rear-tree-report.json');
if(fs.existsSync(report))throw Error('Tree already placed');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);
for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);
const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
const a=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(root,'maps/tree_library/structures/t106.nbt')))),origin=[234,103,134],planned=new Map();
for(const v of a.blocks){const [x,y,z]=v.pos.map((n,i)=>n+origin[i]),i=ix(x,y,z),d=a.palette[v.state];
 if(x<234||x>253||z<134||z>152||y<103||y>138)throw Error('Outside approved bounds');
 if(m.definitions[old[i]].Name!=='minecraft:air')throw Error('Collision '+[x,y,z]);
 if(v.pos[1]===0&&m.definitions[old[ix(x,y-1,z)]].Name!=='minecraft:grass_block')throw Error('Unsupported root '+[x,y,z]);
 if(!ids.has(key(d))){ids.set(key(d),m.definitions.length);m.definitions.push(d);}data[i]=ids.get(key(d));planned.set(i,key(d));
}
let changed=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');changed++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-hall-rear-tree/starter_sky_land_spawn_v1');
if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});
fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const saved=createLibrary(path.dirname(map)).load(path.basename(map)),sb=Buffer.from(saved.blocks,'base64');
for(let i=0;i<old.length;i++){const actual=key(saved.definitions[sb.readUInt16LE(i*2)]),expected=planned.get(i)||key(m.definitions[old[i]]);if(actual!==expected)throw Error('Saved mismatch '+i);}
const info={asset:'t106',origin,size:a.size,trunkCenter:[244,103,143],bounds:[234,103,134,253,138,152],changedBlocks:changed,changedChunks:result.changed,verifiedAssetBlocks:planned.size,outsideChanges:0,backup:path.relative(root,backup)};
fs.writeFileSync(report,JSON.stringify(info,null,2));console.log(info);
