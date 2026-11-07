const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function verifyEaves(m,baseline,report){
 const b=Buffer.from(m.blocks,'base64'),S=m.size,at=(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)];
 for(const [x,y,z,block]of [[62,115,199,'red_sandstone_stairs'],[111,115,188,'red_sandstone_slab'],[114,114,189,'red_sandstone_stairs'],[132,110,189,'glass']])assert.equal(at(x,y,z).Name,'minecraft:'+block,'Missing overhang '+[x,y,z]);
 if(!report)return {samples:4};
 const before=Buffer.from(baseline.blocks,'base64'),ak=m.definitions.map(key),bk=baseline.definitions.map(key),allowed=new Set(report.edits.map(e=>e.position.join(',')));let net=0;
 for(let i=0;i<b.length/2;i++){if(ak[b.readUInt16LE(i*2)]===bk[before.readUInt16LE(i*2)])continue;net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(y>=109&&y<=124,'Body/floor edited');assert(allowed.has([x,y,z].join(',')),'Outside roof patch');const out=at(x,y,z).Name,old=baseline.definitions[before.readUInt16LE(i*2)].Name;assert(['minecraft:red_sandstone_stairs','minecraft:red_sandstone_slab','minecraft:orange_terracotta','minecraft:glass'].includes(out)||out==='minecraft:air'&&x===111&&y===112&&old==='minecraft:dark_oak_planks','Unexpected roof material/removal');}
 assert.equal(net,report.netChangedBlocks);
 const {verifyModels}=require('./verify_market_redesign.cjs'),old=JSON.parse(fs.readFileSync(path.join(DIR,'market-redesign-report.json')));
 const access=verifyModels(m,baseline,{...old,netChangedBlocks:net});
 for(const part of report.parts){assert(part.addedBlocks>0,'Missing roof part '+part.id);assert.equal(part.conflicts,0);}
 return {netChangedBlocks:net,roofParts:report.parts.length,protectedPoints:access.protectedPoints,outsideRoofChanges:0};
}
function run(){const m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),rf=path.join(DIR,'market-eaves-report.json');if(!fs.existsSync(rf))return verifyEaves(m);const r=JSON.parse(fs.readFileSync(rf)),base=createLibrary(path.dirname(path.join(ROOT,r.backup))).load('starter_sky_land_spawn_v1');return verifyEaves(m,base,r);}
module.exports={verifyEaves,run};if(require.main===module)console.log(JSON.stringify(run(),null,2));
