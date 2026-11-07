const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/starter_sky_land_spawn_v1');
function verify(){
 const m=createLibrary(path.join(root,'maps')).load('starter_sky_land_spawn_v1'),b=Buffer.from(m.blocks,'base64'),S=m.size;
 const def=(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)],name=(x,y,z)=>def(x,y,z).Name.slice(10);
 assert.equal(name(249,113,136),'sandstone','Missing new training floor');
 const r=JSON.parse(fs.readFileSync(path.join(dir,'guild-campus-report.json')));
 assert.deepEqual(r.dimensions.main,[25,29]);assert.deepEqual(r.dimensions.innLocal,[23,29]);assert.deepEqual(r.dimensions.trainingClear,[25,21]);
 for(let z=126;z<=146;z++)for(let x=237;x<=261;x++)assert.equal(name(x,113,z),'sandstone','Training floor dimensions');
 for(const p of r.protectedNPCs){const [x,y,z]=p.cell;assert.notEqual(name(x,y-1,z),'air','NPC support '+p.name);for(let dy=0;dy<3;dy++)assert.equal(name(x,y+dy,z),'air','NPC headroom '+p.name);}
 assert.equal(name(244,110,195),r.originalWaypointFloor,'Waypoint floor changed');
 const solid=d=>!['air','short_grass','fern','vine','chain','lantern','torch'].includes(d.Name.slice(10))&&!d.Name.endsWith('_carpet');
 function surface(wx,wz,expected){const x=Math.floor(wx),z=Math.floor(wz),fx=wx-x,fz=wz-z;for(let y=Math.floor(expected);y>=Math.floor(expected)-3;y--){const d=def(x,y,z),p=d.Properties||{};if(!solid(d))continue;let top=y+1;if(d.Name.endsWith('_slab')&&p.type==='bottom')top=y+.5;if(d.Name.endsWith('_stairs')){assert.equal(p.half,'bottom');const high=p.facing==='north'?fz<.5:p.facing==='south'?fz>=.5:p.facing==='west'?fx<.5:fx>=.5;top=y+(high?1:.5);}for(let h=Math.ceil(top);h<top+2;h++)assert.equal(name(x,h,z),'air','Route headroom '+[x,h,z]);return top;}throw Error('Missing route ground '+[x,z]);}
 let samples=0;for(const route of r.routes){let last;for(let j=1;j<route.points.length;j++){const a=route.points[j-1],c=route.points[j],n=Math.max(1,Math.ceil(Math.hypot(a[0]-c[0],a[2]-c[2])*8));for(let i=0;i<=n;i++){const t=i/n,wx=a[0]+.5+(c[0]-a[0])*t,wz=a[2]+.5+(c[2]-a[2])*t,h=surface(wx,wz,Math.max(a[1],c[1])+1);if(last!==undefined)assert(Math.abs(h-last)<=.50001,route.name+' discontinuity '+[wx,wz,last,h]);last=h;samples++;}}}
 function flood(start,y,box){const ok=(x,z)=>x>=box[0]&&x<=box[2]&&z>=box[1]&&z<=box[3]&&name(x,y,z)==='air'&&name(x,y+1,z)==='air'&&name(x,y-1,z)!=='air',q=[start],seen=new Set([start.join(',')]);assert(ok(...start),'BFS start');for(let i=0;i<q.length;i++){const [x,z]=q[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const xx=x+dx,zz=z+dz,k=xx+','+zz;if(!seen.has(k)&&ok(xx,zz)){q.push([xx,zz]);seen.add(k);}}}return seen;}
 const guestReach=flood([279,191],118,[263,162,303,204]);
 for(const room of r.innRooms){assert(room.clearArea>=24);assert.equal(name(...room.access),'air');assert.notEqual(name(room.access[0],117,room.access[2]),'air','Guest room lower ceiling/support');assert(guestReach.has(room.access[0]+','+room.access[2]),'Guest door disconnected from stair');}
 const upperReach=flood([238,182],119,[233,172,255,198]);for(const [x,z]of [[235,183],[235,194],[252,182],[252,193]])assert(upperReach.has(x+','+z),'Mezzanine room disconnected');
 assert(r.innRooms.length>=4);assert(r.savedSemanticComparison);assert.equal(r.outsideScopeChanges,0);
 const before=createLibrary(path.dirname(path.join(root,r.backup))).load('starter_sky_land_spawn_v1'),bb=Buffer.from(before.blocks,'base64'),keys=m.definitions.map(d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort())),bk=before.definitions.map(d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()));let net=0;
 for(let i=0;i<b.length/2;i++){if(keys[b.readUInt16LE(i*2)]===bk[bb.readUInt16LE(i*2)])continue;net++;const x=i%S,z=Math.floor(i/S)%S,y=Math.floor(i/(S*S))+m.low;assert(x>=203&&x<=313&&z>=102&&z<=246&&y>=24&&y<=155,'Outside approved campus/tree extension envelope');assert(!(x<=220&&z<=124),'Castle changed');assert(!(x<=208&&z>=150),'Main street changed');assert(!(x>=277&&z>=214),'Portal area changed');}
 assert.equal(net,r.netChangedBlocks);return {netChangedBlocks:net,routeSurfaceSamples:samples,protectedNPCs:r.protectedNPCs.length,guestRooms:r.innRooms.length,outsideScopeChanges:0,verification:'Saved blocks and point/headroom sampling only; full hitbox and runtime behavior need in-game testing.'};
}
module.exports={verify};if(require.main===module)console.log(JSON.stringify(verify(),null,2));
