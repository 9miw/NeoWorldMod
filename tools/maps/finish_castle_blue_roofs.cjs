// Texture QA correction: Vanilla has no blue-concrete stair; use stepped blue blocks.
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(dir,'castle-completion-report.json');
const report=JSON.parse(fs.readFileSync(rf));if(report.blueRoofCorrection)throw Error('Roof correction already applied');
const file=path.join(dir,'region/r.0.0.mca'),source=fs.readFileSync(file),hash=b=>crypto.createHash('sha256').update(b).digest('hex'),sourceHash=hash(source);
const lib=createLibrary(path.dirname(dir)),m=lib.load(path.basename(dir)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);
for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),at=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
let blue=m.definitions.findIndex(d=>d.Name==='minecraft:blue_concrete');if(blue<0)throw Error('Blue roof material missing');
const changes=[];for(const t of report.towers){const [cx,,cz]=t.center;
 for(let x=cx-8;x<=cx+8;x++)for(let z=cz-8;z<=cz+8;z++)for(let y=t.deckY+2;y<t.tipY;y++){
  const i=at(x,y,z);if(m.definitions[data[i]].Name==='minecraft:prismarine_brick_stairs'){data[i]=blue;changes.push([x,y,z]);}
 }
}if(!changes.length)throw Error('No green roof stairs found');
const backup=path.join(root,'archive/map-edit-backups/before-castle-blue-roofs/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');
fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(dir,backup,{recursive:true});const result=patch(m,data,old,source);
if(hash(fs.readFileSync(file))!==sourceHash)throw Error('Concurrent map change');fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const k=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),saved=lib.load(path.basename(dir)),sb=Buffer.from(saved.blocks,'base64'),expected=m.definitions.map(k),actual=saved.definitions.map(k);
for(let i=0;i<data.length;i++)if(actual[sb.readUInt16LE(i*2)]!==expected[data[i]])throw Error('Saved mismatch '+i);
const previewFile=path.join(root,'docs/maps/zone07-blueprint/castle-complete.json'),preview=JSON.parse(fs.readFileSync(previewFile)),positions=new Set(changes.map(p=>p.join(',')));
for(const block of preview.blocks)if(positions.has(block.position.join(',')))block.state={Name:'minecraft:blue_concrete'};
const correction={changedBlocks:changes.length,changedChunks:result.changed,backup:path.relative(root,backup),reason:'blue concrete instead of green Vanilla prismarine roof stairs',savedVoxelComparison:true};
preview.blueRoofCorrection=correction;report.blueRoofCorrection=correction;fs.writeFileSync(previewFile,JSON.stringify(preview));fs.writeFileSync(rf,JSON.stringify(report,null,2));
for(const name of ['map-manifest.json','zones.json']){const f=path.join(dir,name),v=JSON.parse(fs.readFileSync(f));if(Array.isArray(v)){for(const z of v)if(z.id==='07-castle')z.completion.blueRoofCorrection=correction;}else{v.castle.completion.blueRoofCorrection=correction;for(const r of v.rooms)if(r.id==='07-castle')r.completion.blueRoofCorrection=correction;}fs.writeFileSync(f,JSON.stringify(v,null,2));}
console.log(correction);
