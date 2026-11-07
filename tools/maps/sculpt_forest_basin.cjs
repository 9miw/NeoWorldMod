// Stage-two local terrain patch. Does not invoke the original world generator.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/leveling_island_01'),FILE=path.join(DIR,'region/r.0.0.mca');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),stateKey=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
function stage(){
 const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),layout=JSON.parse(fs.readFileSync(path.join(DIR,'layout.json'))),S=m.size,bytes=Buffer.from(m.blocks,'base64'),data=new Uint16Array(bytes.length/2);
 for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);const original=data.slice(),ids=new Map(m.definitions.map((d,i)=>[stateKey(d),i])),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
 const id=(n,p)=>{const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=stateKey(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);};
 const air=id('air'),grass=id('grass_block',{snowy:'false'}),dirt=id('dirt'),road=id('coarse_dirt'),stone=id('stone'),deep=id('deepslate'),andesite=id('andesite');
 const get=(x,y,z)=>y<m.low||y>=m.high?air:data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name;
 const reasons=new Map(),put=(x,y,z,v,r)=>{assert(x>=0&&z>=0&&x<S&&z<S&&y>=m.low&&y<m.high,'Outside loaded window');const i=at(x,y,z);if(data[i]!==v){data[i]=v;reasons.set(i,r);}};
 const top=new Int16Array(S*S).fill(-1),bottom=new Int16Array(S*S).fill(-1);
 for(let z=0;z<S;z++)for(let x=0;x<S;x++)for(let y=m.low;y<m.high;y++)if(get(x,y,z)!==air){if(bottom[z*S+x]<0)bottom[z*S+x]=y;top[z*S+x]=y;}
 const oldTop=top.slice(),oldBottom=bottom.slice();
 function surface(x,z,h,material,r){const k=z*S+x,old=top[k];if(old<0)return;assert(h>=8&&h<m.high-3);for(let y=Math.min(h,old)-3;y<=Math.max(h,old);y++)put(x,y,z,y>h?air:y===h?material:y>h-4?dirt:stone,r);top[k]=h;}
 // Flatten the high ridge crown, then blend its descending south shoulder into existing land.
 for(let z=28;z<=127;z++)for(let x=91;x<=172;x++){
  const k=z*S+x;if(top[k]<0)continue;const d=Math.max(Math.abs(x-130)/41,Math.abs(z-74)/53),blend=Math.min(1,Math.max(0,(1-d)/.3));if(!blend)continue;
  const target=132-Math.max(0,z-84)*.28-Math.max(0,Math.abs(x-128)-15)*.14;
  surface(x,z,Math.round(top[k]*(1-blend)+target*blend),grass,'chapel-ridge');
 }
 // Chapel usable core, with a broad transition ring; no walls or buildings yet.
 for(let z=42;z<=100;z++)for(let x=101;x<=152;x++){
  const k=z*S+x;if(top[k]<0)continue;const r=Math.max(Math.abs(x-126)/25,Math.abs(z-71)/29),blend=Math.min(1,Math.max(0,(1-r)/.42));if(blend)surface(x,z,Math.round(top[k]*(1-blend)+132*blend),grass,'chapel-terrace');
 }
 // A low village basin with gentle shoulders, retaining the six building reserve locations.
 for(let z=207;z<=294;z++)for(let x=137;x<=250;x++){
  const k=z*S+x;if(top[k]<0)continue;const r=Math.hypot((x-193)/57,(z-254)/48),blend=Math.max(0,1-r*r)*.65;if(!blend)continue;
  surface(x,z,Math.round(top[k]*(1-blend)+(86+.025*(254-z))*blend),grass,'village-basin');
 }
 for(const p of layout.buildingReserves){const [cx,,cz]=p.center,[x1,z1,x2,z2]=p.bounds,level=top[cz*S+cx];
  for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++){const r=Math.max(Math.abs(x-cx)/((x2-x1)/2),Math.abs(z-cz)/((z2-z1)/2)),blend=Math.min(1,Math.max(0,(1-r)/.4));if(top[z*S+x]>=0)surface(x,z,Math.round(top[z*S+x]*(1-blend)+level*blend),grass,'building-reserve');}p.center[1]=level;
 }
 // Attached rock spurs beneath existing perimeter columns; no footprint extension or floating stones.
 let spurColumns=0;const spurs=[[67,257,18,23],[54,222,16,18],[89,182,17,24],[91,107,15,21],[112,39,18,16],[270,88,20,18],[305,159,21,23],[318,205,19,22],[283,274,21,23],[227,313,18,15],[149,300,18,17]];
 for(let s=0;s<spurs.length;s++){const [cx,cz,rx,rz]=spurs[s];for(let z=cz-rz;z<=cz+rz;z++)for(let x=cx-rx;x<=cx+rx;x++){
  const k=z*S+x,r=Math.hypot((x-cx)/rx,(z-cz)/rz);if(r>1||oldTop[k]<0||z>=319)continue;
  const tooth=5*Math.sin(x*.63+z*.29)+3*Math.cos(z*.73),tip=16+(s%4)*5;
  const lo=Math.max(9,Math.round(tip+(oldBottom[k]-tip)*r*r+tooth*(1-r)));if(lo>=oldBottom[k])continue;
  for(let y=lo;y<oldBottom[k];y++)if(get(x,y,z)===air)put(x,y,z,y<lo+3?deep:(x*11+z*7+y)%17===0?andesite:stone,'rock-spur');bottom[k]=lo;spurColumns++;
 }}
 // Rasterize center routes as cardinal cells, so every ramp has a definite travel direction.
 const raster=points=>{const result=[];for(let j=1;j<points.length;j++){const [ax,az]=points[j-1],[bx,bz]=points[j],n=Math.max(Math.abs(bx-ax),Math.abs(bz-az));for(let i=0;i<=n;i++){const t=i/n,x=Math.round(ax+(bx-ax)*t),z=Math.round(az+(bz-az)*t);if(result.length){const a=result.at(-1);if(a[0]!==x&&a[1]!==z)result.push([x,a[1]]);}if(!result.length||result.at(-1)[0]!==x||result.at(-1)[1]!==z)result.push([x,z]);}}return result;};
 // Join the three routes, arrival landing and every house pad to the same path network.
 const plans=[...layout.paths,[[193,337],[193,319],[193,305],[196,300]]];
 for(const p of layout.buildingReserves){const [cx,,cz]=p.center;let best=null,dist=Infinity;for(const points of layout.paths)for(const q of raster(points)){const d=Math.hypot(q[0]-cx,q[1]-cz);if(d<dist){dist=d;best=q;}}plans.push([[cx,cz],best]);}
 const lines=plans.map(raster),nodes=new Map(),kof=(x,z)=>z*S+x;
 for(const line of lines)for(const [x,z] of line){const k=kof(x,z);assert(top[k]>=0,'Route outside land');if(!nodes.has(k))nodes.set(k,{x,z,h:top[k]+1,neighbors:new Set()});}
 for(const line of lines)for(let i=1;i<line.length;i++){const a=nodes.get(kof(...line[i-1])),b=nodes.get(kof(...line[i]));a.neighbors.add(kof(b.x,b.z));b.neighbors.add(kof(a.x,a.z));}
 // Cap to half-block gradients by lowering high samples, leaving the bridge at its existing height.
 for(let pass=0;pass<500;pass++){let changed=false;for(const n of nodes.values())for(const k of n.neighbors){const b=nodes.get(k);if(n.h>b.h+.5){n.h=b.h+.5;changed=true;}}if(!changed)break;assert(pass<499,'Ramp profile did not converge');}
 const candidates=new Map();for(const n of nodes.values())for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(dx*dx+dz*dz<=5){const x=n.x+dx,z=n.z+dz,k=kof(x,z);if(top[k]<0||z>=319)continue;const d=dx*dx+dz*dz;if(!candidates.has(k)||candidates.get(k).d>d)candidates.set(k,{x,z,h:n.h,d});}
 // Center nodes always take their graph height, even where adjacent path shoulders overlap.
 for(const n of nodes.values())if(n.z<319)candidates.set(kof(n.x,n.z),{...n,d:0});
 const slab=id('stone_brick_slab',{type:'bottom',waterlogged:'false'}),stairIds={};for(const facing of ['north','south','east','west'])stairIds[facing]=id('stone_brick_stairs',{facing,half:'bottom',shape:'straight',waterlogged:'false'});
 for(const c of candidates.values()){const full=Number.isInteger(c.h),floor=Math.floor(c.h)-(full?1:0),k=kof(c.x,c.z),old=top[k];for(let y=Math.min(floor,old)-3;y<=Math.max(floor,old)+3;y++)put(c.x,y,c.z,y>floor?air:y===floor?(full?road:slab):dirt,'walkway');top[k]=floor;}
 const ramps=[];
 for(const n of nodes.values()){
  if(Number.isInteger(n.h)||n.z>=319||n.neighbors.size!==2)continue;const [a,b]=[...n.neighbors].map(k=>nodes.get(k));if(a.x+b.x!==2*n.x||a.z+b.z!==2*n.z||Math.abs(a.h-b.h)!==1)continue;
  const hi=a.h>b.h?a:b,lo=a.h>b.h?b:a;if(!Number.isInteger(hi.h)||!Number.isInteger(lo.h))continue;const facing=hi.x>n.x?'east':hi.x<n.x?'west':hi.z>n.z?'south':'north',y=Math.floor(n.h);
  // Only replace straight half-step nodes; turns retain slabs with the same graph heights.
  for(const c of candidates.values())if((facing==='north'||facing==='south'?c.z===n.z&&Math.abs(c.x-n.x)<=1:c.x===n.x&&Math.abs(c.z-n.z)<=1)&&c.h===n.h)put(c.x,y,c.z,stairIds[facing],'stair');ramps.push({x:n.x,y,z:n.z,facing});
 }
 // Metadata stores precise graph surfaces, not the top of a slab's block cell.
 layout.walkRoutes=lines;layout.walkNodes=[...nodes.values()].map(n=>({x:n.x,z:n.z,feet:n.h}));layout.stairRamps=ramps;
 layout.zones[3].center[1]=133;for(const zone of layout.zones){if(zone!==layout.zones[0]&&zone!==layout.zones[3])zone.center[1]=top[kof(zone.center[0],zone.center[2])]+1;}
 const counts={};let net=0,protectedChanges=0;for(let i=0;i<data.length;i++)if(data[i]!==original[i]){net++;const r=reasons.get(i);assert(r,'Unplanned change');counts[r]=(counts[r]||0)+1;const z=Math.floor((i%(S*S))/S);if(z>=319)protectedChanges++;}assert.equal(protectedChanges,0,'Entry/bridge modified');
 for(const n of nodes.values())for(const k of n.neighbors)assert(Math.abs(n.h-nodes.get(k).h)<=.5);
 return {m,data,original,source,sourceHash:hash(source),layout,report:{netChangedBlocks:net,counts,protectedEntryChanges:0,spurColumns,stairRampCenters:ramps.length,walkNodes:nodes.size,verification:{stagedHalfStepGraph:true,inGame:false}}};
}
function apply(){assert(process.argv.includes('--apply'),'Use --apply');const backup=path.join(ROOT,'archive/map-edit-backups/before-forest-basin-stage2/leveling_island_01');assert(!fs.existsSync(backup),'Stage 2 already applied');const s=stage(),out=patch(s.m,s.data,s.original,s.source);assert.equal(hash(fs.readFileSync(FILE)),s.sourceHash,'Concurrent change');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(DIR,backup,{recursive:true});fs.writeFileSync(FILE+'.pending',out.bytes);assert.equal(hash(fs.readFileSync(FILE)),s.sourceHash,'Concurrent change before commit');fs.renameSync(FILE+'.pending',FILE);
 const saved=createLibrary(path.join(ROOT,'maps')).load('leveling_island_01'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<s.data.length;i++)assert.equal(stateKey(saved.definitions[sb.readUInt16LE(i*2)]),stateKey(s.m.definitions[s.data[i]]),'Saved state mismatch at '+i);
 fs.writeFileSync(path.join(DIR,'layout.json'),JSON.stringify(s.layout,null,2));fs.writeFileSync(path.join(DIR,'zones.json'),JSON.stringify(s.layout.zones,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.rooms=s.layout.zones;manifest.blockStates=s.m.definitions;manifest.description='Terrain stage 2 · ridge terrace, village basin and half-step routes';fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const report={...s.report,changedChunks:out.changed,sourceHash:s.sourceHash,resultHash:hash(out.bytes),backup:path.relative(ROOT,backup),verification:{...s.report.verification,savedSemanticComparison:true}};fs.writeFileSync(path.join(DIR,'terrain-stage2-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));}
module.exports={stage};if(require.main===module)apply();
