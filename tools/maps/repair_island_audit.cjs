// Three approved repairs only: spawn lantern supports, inn eaves, main guild gallery fence states.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{verifyRepairs}=require('./verify_island_audit_repairs.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),RF=path.join(DIR,'island-audit-repairs-report.json'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-island-audit-repairs-20261031/starter_sky_land_spawn_v1'),STAGE=path.join(ROOT,'archive/map-edit-staging/island-audit-repairs-20261031');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),source=fs.readFileSync(FILE);
assert(!fs.existsSync(RF),'Repairs already applied; inspect instead of replaying');assert.equal(hash(source),'c3bc2abeb8970fc50bf735a413e4fc36af1febd91fba295ac13293c11d283071','Concurrent map edit: inspect current source');
const m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),bytes=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
const original=data.slice(),defs=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>defs[original[ix(x,y,z)]],edits=[];
const C=Math.cos(Math.PI/9),SN=Math.sin(Math.PI/9),local=(x,z)=>[Math.round((x-282)*C+(z-183)*SN),Math.round(-(x-282)*SN+(z-183)*C)];
function put(part,x,y,z,n,p){const old=get(x,y,z),d={Name:n.startsWith('minecraft:')?n:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(k===key(old))return;
 if(part==='gallery-rails'){assert(y===119&&[240,248].includes(x)&&z>=179&&z<=198&&old.Name.endsWith('_fence')&&d.Name===old.Name,'Outside rail mask');}
 else{assert.equal(old.Name,'minecraft:air','Collision at '+[x,y,z]);if(part==='spawn-lamps')assert([153,167].includes(x)&&z===325&&[97,98].includes(y),'Outside lamp mask');else{const [u,v]=local(x,z);assert(Math.abs(u)<=12&&Math.abs(v)<=15&&(Math.abs(u)>11||Math.abs(v)>14)&&y>=123&&y<=132,'Outside inn roof ring');}}
 if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);edits.push({part,position:[x,y,z],before:old,after:d});
}
for(const x of [153,167]){assert.equal(get(x,96,325).Name,'minecraft:stone_bricks');assert.equal(get(x,99,325).Name,'minecraft:lantern');put('spawn-lamps',x,97,325,'chiseled_stone_bricks');put('spawn-lamps',x,98,325,'stone_brick_wall',{north:'none',south:'none',east:'none',west:'none',up:'true',waterlogged:'false'});}
let roofColumns=0;for(let z=160;z<=207;z++)for(let x=261;x<=305;x++){
 const [u,v]=local(x,z);if(Math.abs(u)>12||Math.abs(v)>15||Math.abs(u)<=11&&Math.abs(v)<=14)continue;roofColumns++;const y=124+Math.floor((11-Math.abs(u))*.65);put('inn-eaves',x,y,z,'purple_concrete');if(Math.abs(u)===12||Math.abs(v)===15)put('inn-eaves',x,y+1,z,'stone_brick_slab',{type:'bottom',waterlogged:'false'});
}
assert.equal(roofColumns,108);
// Touch only the explicitly identified gallery rail runs, not the tables' separate fence legs.
for(const x of [240,248])for(let z=179;z<=198;z++){
 const old=get(x,119,z);if(!old.Name.endsWith('_fence'))continue;const p={...old.Properties};for(const [side,dx,dz]of [['north',0,-1],['south',0,1],['east',1,0],['west',-1,0]]){
  const neighbor=get(x+dx,119,z+dz).Name;p[side]=String(neighbor.endsWith('_fence')||neighbor.endsWith('_log')||neighbor.endsWith('_planks')||neighbor==='minecraft:stone_bricks');
 }put('gallery-rails',x,119,z,old.Name,p);
}
const report={sourceHash:hash(source),backup:path.relative(ROOT,BACKUP),netChangedBlocks:edits.length,edits,parts:Object.fromEntries(['spawn-lamps','inn-eaves','gallery-rails'].map(part=>[part,edits.filter(e=>e.part===part).length])),innRoof:{wallLocalSize:[23,29],roofLocalSize:[25,31],rotationDegrees:20,addedColumns:roofColumns},verification:{webGL:false,inGame:false,deployed:false}};
const staged={...m,blocks:Buffer.from(data.buffer).toString('base64')},baseline={...m,definitions:defs,blocks:bytes.toString('base64')};report.validation=verifyRepairs(staged,baseline,report);
const result=patch(m,data,original,source);report.resultHash=hash(result.bytes);report.changedChunks=result.changed;fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'dry-run-report.json'),JSON.stringify(report,null,2));fs.writeFileSync(path.join(STAGE,'planned-repairs.mca'),result.bytes);
if(!process.argv.includes('--apply')){console.log(JSON.stringify({mode:'dry-run',netChangedBlocks:edits.length,parts:report.parts,changedChunks:result.changed,validation:report.validation}));process.exit(0);}
assert(!fs.existsSync(BACKUP),'Backup exists: inspect previous attempt');assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Source changed before backup');fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Source changed before commit');fs.writeFileSync(FILE+'.pending',result.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1');report.validation=verifyRepairs(saved,baseline,report);fs.writeFileSync(RF,JSON.stringify(report,null,2));
const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.guildCampus.auditRepairs={report:'island-audit-repairs-report.json',innRoof:report.innRoof,galleryRailsConnected:true};manifest.spawnPlatform.lanternSupports={points:[[153,97,325],[167,97,325]],report:'island-audit-repairs-report.json'};manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({mode:'applied',netChangedBlocks:edits.length,parts:report.parts,changedChunks:result.changed,resultHash:report.resultHash,validation:report.validation}));
