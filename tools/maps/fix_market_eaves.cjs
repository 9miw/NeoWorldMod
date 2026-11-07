// Extend roofs of the CURRENT shops only. Expanded building dimensions remain a separate proposal.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{verifyEaves}=require('./verify_market_eaves.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),RF=path.join(DIR,'market-eaves-report.json'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-market-eaves-20261031/starter_sky_land_spawn_v1'),STAGE=path.join(ROOT,'archive/map-edit-staging/market-eaves-20261031');
assert(!fs.existsSync(RF),'Eaves already applied: inspect current map instead of replaying');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),semantic=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),source=fs.readFileSync(FILE);
assert.equal(hash(source),'a9429fbed131a8b8c3ba0695398a3c79eb37bbf45482c03b892d19a3ef16e62d','Baseline changed: inspect before editing');
const m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),b=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(b.length/2);for(let i=0;i<data.length;i++)data[i]=b.readUInt16LE(i*2);const original=data.slice(),originalDefs=m.definitions.slice(),ids=new Map(m.definitions.map((d,i)=>[semantic(d),i])),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x;
const edits=new Map(),parts=[],owners=new Map(),stair=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'};
function put(part,x,y,z,n,p){assert(x>=55&&x<=134&&z>=177&&z<=218&&y>=109&&y<=124,'Out of roof scope');const def={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=semantic(def),i=ix(x,y,z),old=originalDefs[original[i]],owner=owners.get(i);
 if(owner&&owner!==part)assert.equal(semantic(m.definitions[data[i]]),k,'Intersecting proposed roofs '+[x,y,z]);
 // Existing construction, plants and block entities are not erased to make room for an eave.
 const obsoleteFoodTrim=x===111&&y===112&&z>=180&&z<=196&&old.Name==='minecraft:dark_oak_planks';
 if(old.Name!=='minecraft:air'&&!obsoleteFoodTrim){assert.equal(semantic(old),k,'Roof collision '+part+' '+[x,y,z]+' '+old.Name);return;}
 if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(def);}data[i]=ids.get(k);owners.set(i,part);edits.set(i,{part,position:[x,y,z],state:def});
}
function begin(id,wall,roof,note){parts.push({id,wallEnvelope:wall,roofEnvelope:roof,note,addedBlocks:0,conflicts:0});return id;}
const C=Math.cos(Math.PI/6),SN=.5,local=(x,z)=>[C*(x-76)-SN*(z-197),SN*(x-76)+C*(z-197)];
const main=begin('merchant-main',{localU:[-12,12],localV:[-11,7]},{localU:[-13,13],localV:[-12,8]},'One block beyond wall axes; rotated raster, independent of floor mask.');
for(let z=178;z<=218;z++)for(let x=55;x<=96;x++){
 const [u,v]=local(x,z),au=Math.abs(u);if(au>13.5||v< -12.5||v>8.5)continue;
 // Interior roof voxels are already present; add only the missing outer perimeter.
 if(au<=12.5&&v>=-11.5)continue;const y=115+Math.floor((12-au)*.6);
 put(main,x,y,z,'orange_terracotta');put(main,x,y+1,z,au<.9?'red_sandstone_slab':'red_sandstone_stairs',au<.9?slab:stair(u<0?'east':'west'));
}
const wing=begin('merchant-left-wing',{localU:[-12,-5],localV:[8,11]},{localU:[-13,-4],localV:[7,12]},'End and side eaves; shared seam with main roof uses the existing higher roof.');
for(let z=178;z<=218;z++)for(let x=55;x<=96;x++){
 const [u,v]=local(x,z);if(u< -13.5||u> -3.5||v<=8.5||v>12.5)continue;
 if(u>=-12.5&&u<=-4.5&&v<=11.5)continue;
 const y=115+Math.floor((4-Math.abs(u+8.5))*.75);
 put(wing,x,y,z,'orange_terracotta');put(wing,x,y+1,z,'red_sandstone_stairs',stair(u< -8.5?'east':'west'));
}
const food=begin('food',{worldX:[94,110],worldZ:[181,195]},{worldX:[93,111],worldZ:[180,196]},'Keep existing west/north/south eaves; complete missing eastern edge.');
// Historical roof trim remained above the protected ground route; replace only this exact roof line.
for(let z=180;z<=196;z++)put(food,111,112,z,'air');
for(let z=180;z<=196;z++){const y=109+Math.floor(Math.max(0,7-Math.abs(z-188))*.75);put(food,111,y,z,'orange_terracotta');put(food,111,y+1,z,z===188?'red_sandstone_slab':'red_sandstone_stairs',z===188?slab:stair(z<188?'south':'north'));}
const herbs=begin('herbs-main',{worldX:[115,131],worldZ:[181,197]},{worldX:[114,132],worldZ:[180,198]},'Eastern part over the lower glass bay is handled separately, never covered by the main roof.');
for(let z=180;z<=198;z++){
 let y=111+Math.floor(Math.max(0,10-Math.abs(114-121))*.7);put(herbs,114,y,z,'orange_terracotta');put(herbs,114,y+1,z,'red_sandstone_stairs',stair('east'));
 if(z>=183&&z<=195)continue;y=111+Math.floor((10-Math.abs(132-121))*.7);put(herbs,132,y,z,'orange_terracotta');put(herbs,132,y+1,z,'red_sandstone_stairs',stair('west'));
}
const glass=begin('herbs-glass-bay',{worldX:[128,131],worldZ:[183,195]},{worldX:[128,132],worldZ:[182,196]},'Attached wall at west shares roof joint; exposed east and gable ends get overhang.');
for(let z=183;z<=195;z++)put(glass,132,110,z,'glass');
// End returns sit below the existing high gable, maintaining the greenhouse roof transition.
for(const z of [182,196])for(let x=128;x<=131;x++){const y=110+Math.floor(Math.max(0,131-x)*.5);if(x===131&&originalDefs[original[ix(x,y,z)]].Name==='minecraft:spruce_planks')continue;put(glass,x,y,z,'glass');}
for(const e of edits.values())parts.find(p=>p.id===e.part).addedBlocks++;
const report={sourceHash:hash(source),backup:path.relative(ROOT,BACKUP),netChangedBlocks:edits.size,parts,edits:[...edits.values()],bodyDimensionsChanged:false,buildingsExpanded:false,serverModified:false,verification:{webGL:false,inGame:false,deployed:false}};
const staged={...m,blocks:Buffer.from(data.buffer).toString('base64')},baseline={...m,definitions:originalDefs,blocks:b.toString('base64')};report.validation=verifyEaves(staged,baseline,report);
const result=patch(m,data,original,source);report.resultHash=hash(result.bytes);report.changedChunks=result.changed;
fs.mkdirSync(STAGE,{recursive:true});fs.writeFileSync(path.join(STAGE,'dry-run-report.json'),JSON.stringify(report,null,2));fs.writeFileSync(path.join(STAGE,'planned-eaves.mca'),result.bytes);
if(!process.argv.includes('--apply')){console.log(JSON.stringify({mode:'dry-run',netChangedBlocks:report.netChangedBlocks,changedChunks:result.changed,parts:parts.map(p=>({id:p.id,addedBlocks:p.addedBlocks})),validation:report.validation}));process.exit(0);}
assert(!fs.existsSync(BACKUP),'Backup exists: inspect before applying');assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent region edit');fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent region edit after backup');fs.writeFileSync(FILE+'.pending',result.bytes);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1');report.validation=verifyEaves(saved,baseline,report);fs.writeFileSync(RF,JSON.stringify(report,null,2));
const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.market.eaves={report:'market-eaves-report.json',parts:parts.map(({id,wallEnvelope,roofEnvelope,note})=>({id,wallEnvelope,roofEnvelope,note}))};manifest.rooms=manifest.rooms.map(r=>r.id==='04-market'?manifest.market:r);manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id==='04-market'?manifest.market:z),null,2));console.log(JSON.stringify({mode:'applied',resultHash:report.resultHash,netChangedBlocks:report.netChangedBlocks,changedChunks:result.changed,validation:report.validation}));

