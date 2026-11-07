const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function verifyRepairs(m,baseline,report){
 const b=Buffer.from(m.blocks,'base64'),S=m.size,at=(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)],n=(x,y,z)=>at(x,y,z).Name.slice(10);
 for(const x of [153,167]){assert.equal(n(x,97,325),'chiseled_stone_bricks','Missing spawn lantern plinth');assert.equal(n(x,98,325),'stone_brick_wall','Missing spawn lantern post');assert.equal(n(x,99,325),'lantern');assert.equal(at(x,99,325).Properties.hanging,'false');}
 const C=Math.cos(Math.PI/9),SN=Math.sin(Math.PI/9);let roofColumns=0;
 for(let z=160;z<=207;z++)for(let x=261;x<=305;x++){const u=Math.round((x-282)*C+(z-183)*SN),v=Math.round(-(x-282)*SN+(z-183)*C);if(Math.abs(u)>12||Math.abs(v)>15||Math.abs(u)<=11&&Math.abs(v)<=14)continue;roofColumns++;const y=124+Math.floor((11-Math.abs(u))*.65);assert.equal(n(x,y,z),'purple_concrete','Missing inn eave '+[x,y,z]);if(Math.abs(u)===12||Math.abs(v)===15)assert.equal(n(x,y+1,z),'stone_brick_slab','Missing inn eave trim');}
 assert.equal(roofColumns,108);
 let railPairs=0;for(const x of [240,248])for(let z=179;z<198;z++)if(n(x,119,z).endsWith('_fence')&&n(x,119,z+1).endsWith('_fence')){assert.equal(at(x,119,z).Properties.south,'true','Broken gallery rail south');assert.equal(at(x,119,z+1).Properties.north,'true','Broken gallery rail north');railPairs++;}assert.equal(railPairs,31);
 if(!report)return {roofColumns,railPairs};
 const bb=Buffer.from(baseline.blocks,'base64'),ak=m.definitions.map(key),bk=baseline.definitions.map(key),allowed=new Set(report.edits.map(e=>e.position.join(',')));let net=0;
 for(let i=0;i<b.length/2;i++){if(ak[b.readUInt16LE(i*2)]===bk[bb.readUInt16LE(i*2)])continue;net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(allowed.has([x,y,z].join(',')),'Change outside repair mask');const d=at(x,y,z),before=baseline.definitions[bb.readUInt16LE(i*2)];if(y===119){assert([240,248].includes(x)&&z>=179&&z<=198,'Rail scope');assert.equal(d.Name,before.Name,'Rail block replaced');assert(d.Name.endsWith('_fence'));}else{assert.equal(before.Name,'minecraft:air','Existing architecture overwritten');assert(x===153&&z===325||x===167&&z===325||y>=123&&x>=261&&x<=305&&z>=160&&z<=207,'Roof/post scope');}}
 assert.equal(net,report.netChangedBlocks);
 // Recheck the recorded standing pads, guest-room doors, stair route cells and main mezzanine access.
 const g=JSON.parse(fs.readFileSync(path.join(DIR,'guild-campus-report.json')));
 for(const p of g.protectedNPCs){const [x,y,z]=p.cell;assert.notEqual(n(x,y-1,z),'air');for(let dy=0;dy<=2;dy++)assert.equal(n(x,y+dy,z),'air','NPC headroom');}
 function flood(start,y,box){const pass=(x,z)=>x>=box[0]&&x<=box[2]&&z>=box[1]&&z<=box[3]&&n(x,y,z)==='air'&&n(x,y+1,z)==='air'&&n(x,y-1,z)!=='air',q=[start],seen=new Set();for(let i=0;i<q.length;i++){const [x,z]=q[i],k=x+','+z;if(seen.has(k)||!pass(x,z))continue;seen.add(k);q.push([x+1,z],[x-1,z],[x,z+1],[x,z-1]);}return seen;}
 const guests=flood([279,191],118,[263,162,303,204]);for(const room of g.innRooms)assert(guests.has(room.access[0]+','+room.access[2]),'Guest room disconnected');
 const upper=flood([238,182],119,[233,172,255,198]);for(const [x,z]of [[235,183],[235,194],[252,182],[252,193]])assert(upper.has(x+','+z),'Mezzanine disconnected');
 for(const r of g.routes)for(const [x,y,z]of r.points){assert.notEqual(n(x,y,z),'air','Route support removed');for(let h=y+1;h<=y+2;h++)assert.equal(n(x,h,z),'air','Route blocked');}
 return {netChangedBlocks:net,innEaveColumns:roofColumns,continuousRailPairs:railPairs,protectedNPCs:g.protectedNPCs.length,guestRooms:g.innRooms.length,outsideRepairChanges:0};
}
function run(){const m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),rf=path.join(DIR,'island-audit-repairs-report.json');if(!fs.existsSync(rf))return verifyRepairs(m);const r=JSON.parse(fs.readFileSync(rf)),before=createLibrary(path.dirname(path.join(ROOT,r.backup))).load('starter_sky_land_spawn_v1');return verifyRepairs(m,before,r);}
module.exports={verifyRepairs,run};if(require.main===module)console.log(JSON.stringify(run(),null,2));
