const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function verifyCourt(m,before,report){
 const S=m.size,b=Buffer.from(m.blocks,'base64'),old=before&&Buffer.from(before.blocks,'base64');
 const at=(x,y,z)=>((y-m.low)*S*S+z*S+x)*2,def=(x,y,z)=>m.definitions[b.readUInt16LE(at(x,y,z))],name=(x,y,z)=>def(x,y,z).Name.slice(10);
 assert.equal(name(244,107,224),'purple_terracotta','Missing new compass center');
 const pass=d=>['air','short_grass','allium','white_tulip','blue_orchid','fern'].includes(d.Name.slice(10));
 let lane=0;for(let z=207;z<=241;z++)for(let x=240;x<=248;x++){assert(!pass(def(x,107,z)),'Main lane support');for(let y=108;y<=110;y++)assert(pass(def(x,y,z)),'Nine-wide lane blocked '+[x,y,z]);lane++;}
 for(const [x,z]of [[244,217],[244,231],[237,224],[251,224]])assert.equal(name(x,107,z),'yellow_terracotta','Compass cardinal '+[x,z]);
 for(const [dx,dz]of [[4,4],[-4,4],[4,-4],[-4,-4]])assert.equal(name(244+dx,107,224+dz),'deepslate_tiles','Missing diagonal compass point');
 for(const [dx,dz]of [[3,2],[-3,2],[3,-2],[-3,-2],[1,3]])assert.equal(name(244+dx,107,224+dz),'deepslate_tiles','Compass arrow shoulder too thin');
 for(const [dx,dz]of [[5,4],[-5,4],[5,-4],[-5,-4]])assert.equal(name(244+dx,107,224+dz),'smooth_stone','Compass points must separate from rim');
 for(const [x,z]of [[257,233],[256,233],[258,233]])assert.notEqual(name(x,107,z),'air','Memorial support');
 if(report)for(const e of report.monument.voxels){const [x,y,z]=e.position;assert.equal(key(def(x+13,y,z+9)),key(e.state),'Monument state changed '+e.position);}
 // Ground routes stay flat; sample the entire clear width, not only their centerlines.
 let routeCells=0;const corridors=[[225,213,239,217],[249,217,263,220],[236,215,238,237],[250,215,252,237]];
 for(const [x1,z1,x2,z2]of corridors)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){assert(!pass(def(x,107,z)),'Route ground');for(let y=108;y<=110;y++)assert(pass(def(x,y,z)),'Route obstructed '+[x,y,z]);routeCells++;}
 const accessible=(x,z)=>x>=225&&x<=263&&z>=207&&z<=241&&!pass(def(x,107,z))&&pass(def(x,108,z))&&pass(def(x,109,z)),queue=[[244,224]],seen=new Set(['244,224']);
 for(let i=0;i<queue.length;i++){const [x,z]=queue[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const xx=x+dx,zz=z+dz,k=xx+','+zz;if(!seen.has(k)&&accessible(xx,zz)){seen.add(k);queue.push([xx,zz]);}}}
 for(const p of [[244,207],[225,215],[263,218],[231,229],[258,214],[253,233],[244,239]])assert(seen.has(p.join(',')),'Disconnected destination '+p);
 for(let x=256;x<=259;x++)for(let z=213;z<=216;z++){assert(pass(def(x,108,z))&&pass(def(x,109,z)),'Cafe clear entrance');}
 for(const [x1,z1,x2,z2]of [[226,225,233,233],[254,208,261,216]]){
  for(const [x,z]of [[x1+1,z1+1],[x2-1,z1+1],[x1+1,z2-1],[x2-1,z2-1]])for(let y=108;y<=111;y++)assert.equal(name(x,y,z),'dark_oak_log','Pergola post');
  for(let z=z1;z<=z2;z+=2)for(let x=x1;x<=x2;x++)assert.equal(name(x,112,z),'spruce_slab','Roof envelope/beam');
 }
 let lights=0,flowers=0;for(let z=207;z<=241;z++)for(let x=225;x<=263;x++)for(let y=108;y<=119;y++){
  const n=name(x,y,z);if(n==='lantern'){lights++;const hanging=def(x,y,z).Properties?.hanging==='true';assert(name(x,y+(hanging?1:-1),z)!=='air','Unsupported lantern '+[x,y,z]);}
  if(['allium','white_tulip','blue_orchid'].includes(n)){flowers++;assert(['grass_block','dirt'].includes(name(x,y-1,z)),'Unsupported flower');}
 }
 assert(lights>=12,'Lighting missing');assert(flowers>=24,'Planting missing');
 let net=0;if(before){const keys=m.definitions.map(key),bk=before.definitions.map(key);for(let i=0;i<b.length;i+=2){if(keys[b.readUInt16LE(i)]===bk[old.readUInt16LE(i)])continue;net++;const q=i/2,x=q%S,z=Math.floor(q/S)%S,y=Math.floor(q/(S*S))+m.low;assert(x>=225&&x<=263&&z>=207&&z<=241&&y>=100&&y<=119,'Outside approved scope '+[x,y,z]);}
  for(let z=199;z<=206;z++)for(let x=239;x<=249;x++)for(let y=100;y<=119;y++)assert.equal(key(def(x,y,z)),key(before.definitions[old.readUInt16LE(at(x,y,z))]),'Original stair changed');
  if(report){assert.equal(net,report.netChangedBlocks);for(const e of report.shrubs){assert.equal(key(def(...e.to)),key(e.state),'Shrub lost');}}
 }
 return {netChangedBlocks:net,nineWideLaneCells:lane,clearRouteCells:routeCells,reachableGroundCells:seen.size,lanterns:lights,flowers,outsideScopeChanges:0};
}
module.exports={verifyCourt};
if(require.main===module){const m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),rf=path.join(DIR,'guild-forecourt-report.json');let report,before;if(fs.existsSync(rf)){report=JSON.parse(fs.readFileSync(rf));before=createLibrary(path.dirname(path.join(ROOT,report.backup))).load('starter_sky_land_spawn_v1');}console.log(JSON.stringify(verifyCourt(m,before,report),null,2));}
