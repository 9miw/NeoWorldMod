const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const semantic=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function view(m){const b=Buffer.from(m.blocks,'base64'),S=m.size;return {m,b,S,at:(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)]};}
function verifyModels(model,baseline,report){
 const v=view(model),n=(x,y,z)=>v.at(x,y,z).Name.slice(10);
 assert.equal(n(76,110,197),'spruce_planks','Merchant upper floor missing');
 assert.equal(n(102,115,188),'red_sandstone_slab','Food transverse roof ridge missing');
 assert.equal(n(131,107,189),'glass','Herb glass bay missing');
 assert.equal(n(103,118,184),'brick_slab','Food chimney missing');
 const q=[[112,215]],seen=new Set(),pass=(x,z)=>x>=55&&x<=134&&z>=177&&z<=251&&n(x,103,z)!=='air'&&n(x,104,z)==='air'&&n(x,105,z)==='air';
 for(let i=0;i<q.length;i++){const [x,z]=q[i],k=x+','+z;if(seen.has(k)||!pass(x,z))continue;seen.add(k);q.push([x+1,z],[x-1,z],[x,z+1],[x,z-1]);}
 for(const [x,z]of [[76,194],[102,187],[123,187],[102,193],[123,195],[112,181],[113,222],[67,201]])assert(seen.has(x+','+z),'Disconnected service/entrance '+[x,z]);
 for(let z=181;z<=215;z++)for(let x=111;x<=113;x++)for(let y=104;y<=105;y++)assert.equal(n(x,y,z),'air','Smith route blocked');
 if(!report)return {reachable:seen.size};
 for(const p of report.newBlockEntities||[])if(p.kind==='sign')assert.notEqual(n(p.x,p.y,p.z-1),'air','Unsupported shop sign');
 let floorCells=0;for(let z=178;z<=218;z++)for(let x=56;x<=96;x++){const u=Math.cos(Math.PI/6)*(x-76)-.5*(z-197),w=.5*(x-76)+Math.cos(Math.PI/6)*(z-197);if(Math.abs(u)>12.5||Math.abs(w)>11.5)continue;assert.notEqual(n(x,103,z),'air','Lost merchant floor');floorCells++;}assert.equal(floorCells,577);
 for(const [r,count]of [[[94,181,110,195],255],[[115,181,131,197],289]]){let cells=0;for(let z=r[1];z<=r[3];z++)for(let x=r[0];x<=r[2];x++){assert.notEqual(n(x,103,z),'air','Lost small shop floor');cells++;}assert.equal(cells,count);}
 const uq=[[71,200]],us=new Set();for(let i=0;i<uq.length;i++){const [x,z]=uq[i],k=x+','+z;if(us.has(k)||x<56||x>96||z<178||z>218||n(x,110,z)==='air'||n(x,111,z)!=='air'||n(x,112,z)!=='air')continue;us.add(k);uq.push([x+1,z],[x-1,z],[x,z+1],[x,z-1]);}for(const [x,z]of [[76,197],[80,202]])assert(us.has(x+','+z),'Upper room/balcony disconnected '+[x,z]);
 for(const p of report.protectedPoints){const [x,feet,z]=p.cell;assert.notEqual(n(x,103,z),'air','NPC support');for(let y=104;y<=106;y++)assert.equal(n(x,y,z),'air','Protected headroom '+p.name);assert(seen.has(x+','+z),'NPC disconnected '+p.name);}
 // Independently sample both half-steps of the returned stair, not just full-block BFS.
 for(const p of report.upperRoute){const d=v.at(...p.cell),props=d.Properties||{};assert.equal(d.Name,'minecraft:'+p.block);if(p.facing)assert.equal(props.facing,p.facing);for(let y=p.cell[1]+1;y<=p.cell[1]+3;y++)assert.equal(n(p.cell[0],y,p.cell[2]),'air','Stair headroom');}
 const bv=view(baseline),keys=model.definitions.map(semantic),bk=baseline.definitions.map(semantic);assert.equal(v.b.length,bv.b.length);let net=0;
 for(let i=0;i<v.b.length/2;i++){if(keys[v.b.readUInt16LE(i*2)]===bk[bv.b.readUInt16LE(i*2)])continue;net++;const x=i%v.S,z=Math.floor(i/v.S)%v.S,y=Math.floor(i/(v.S*v.S))+model.low;assert(x>=55&&x<=134&&z>=177&&z<=251&&y>=103&&y<=124,'Out of market bounds');assert(!(x>=90&&x<=100&&z>=219&&z<=229),'Fountain changed');if(x>=111&&x<=113&&z<=215&&y>=104&&y<=106)assert(x===111&&y===104&&z<=196&&bv.at(x,y,z).Name==='minecraft:stone_brick_stairs'&&n(x,y,z)==='air','Unexpected smith-route edit');}
 assert.equal(net,report.netChangedBlocks);return {netChangedBlocks:net,reachable:seen.size,protectedPoints:report.protectedPoints.length,outsideBoundsChanges:0};
}
function run(){if(fs.existsSync(path.join(DIR,'market-eaves-report.json')))return require('./verify_market_eaves.cjs').run();const lib=createLibrary(path.join(ROOT,'maps')),m=lib.load('starter_sky_land_spawn_v1'),rf=path.join(DIR,'market-redesign-report.json');if(!fs.existsSync(rf))return verifyModels(m);const r=JSON.parse(fs.readFileSync(rf)),base=createLibrary(path.dirname(path.join(ROOT,r.backup))).load('starter_sky_land_spawn_v1');return verifyModels(m,base,r);}
module.exports={verifyModels};if(require.main===module)console.log(JSON.stringify(run(),null,2));

