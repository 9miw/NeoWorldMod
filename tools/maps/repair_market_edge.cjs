const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-edge-repair-report.json');if(fs.existsSync(rf))throw Error('Repair already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function id(d){const k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
function put(x,y,z,n,p){if(x<43||x>147||z<162||z>267||y<90||y>110)throw Error('Outside repair');data[ix(x,y,z)]=id({Name:'minecraft:'+n,...(p?{Properties:p}:{})});planned.add(ix(x,y,z));}
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),market=manifest.market,stall=market.stalls.find(s=>s.bounds[0]===66&&s.bounds[1]===245);if(!stall)throw Error('Expected edge stall missing');
// Move the complete red stall onto the existing paved interior, clear the old envelope.
for(let x=66;x<=74;x++)for(let z=245;z<=251;z++)for(let y=103;y<=108;y++){
 const dx=x+15,dz=z-8;if(y>103&&m.definitions[old[ix(dx,y,dz)]].Name!=='minecraft:air')throw Error('Destination occupied');
 data[ix(dx,y,dz)]=old[ix(x,y,z)];planned.add(ix(dx,y,dz));put(x,y,z,y===103?'stone_bricks':'air');
}
// Restore the former protruding platform to its natural pre-extension profile.
const prev=createLibrary(path.join(root,'archive/map-edit-backups/before-market-stall-setback')).load(path.basename(map)),pb=Buffer.from(prev.blocks,'base64');
for(let x=65;x<=78;x++)for(let z=245;z<=253;z++)for(let y=96;y<=103;y++){data[ix(x,y,z)]=id(prev.definitions[pb.readUInt16LE(ix(x,y,z)*2)]);planned.add(ix(x,y,z));}
stall.bounds=[81,237,89,243];
for(let x=81;x<=89;x++)for(let z=237;z<=243;z++)for(let y=103;y<=108;y++)if(data[ix(x,y,z)]!==old[ix(x-15,y,z+8)])throw Error('Stall translation mismatch');
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']),height=new Float64Array(S*S).fill(-1),built=new Uint8Array(S*S),fixed=new Uint8Array(S*S),paved=[];
for(let z=157;z<=272;z++)for(let x=38;x<=152;x++){
 const k=z*S+x;for(let y=90;y<m.high;y++){const n=m.definitions[data[ix(x,y,z)]].Name;if(n!=='minecraft:air')height[k]=y;if(!natural.has(n))built[k]=1;}
 if(m.definitions[data[ix(x,103,z)]].Name==='minecraft:stone_bricks')paved.push([x,z]);
}
for(let z=162;z<=267;z++)for(let x=43;x<=147;x++){
 const k=z*S+x;fixed[k]=built[k]||height[k]<96||x<49||x>141||z<168||z>261?1:0;
 for(let dz=-3;dz<=3&&!fixed[k];dz++)for(let dx=-3;dx<=3;dx++)if(height[(z+dz)*S+x+dx]<96){fixed[k]=1;break;}
}
let current=height.slice();for(let pass=0;pass<8;pass++){
 const next=current.slice();for(let z=162;z<=267;z++)for(let x=43;x<=147;x++){
  const k=z*S+x;if(fixed[k])continue;let sum=0,w=0;for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++){const j=(z+dz)*S+x+dx;if(height[j]<96||built[j])continue;const weight=(3-Math.abs(dx))*(3-Math.abs(dz));sum+=current[j]*weight;w+=weight;}if(w)next[k]=current[k]+.65*(sum/w-current[k]);
 }current=next;
}
const rounded=current.map(Math.round);
for(let z=162;z<=267;z++)for(let x=43;x<=147;x++){
 const k=z*S+x;if(fixed[k])continue;let d=15;for(const [px,pz]of paved){if(Math.abs(px-x)>14||Math.abs(pz-z)>14)continue;d=Math.min(d,Math.hypot(x-px,z-pz));}
 // Wide grass shoulders meet market paving; retain a gradual descent beyond it.
 if(d<14&&height[k]<103){const t=d/14,e=t*t*(3-2*t);rounded[k]=Math.max(rounded[k],Math.round(103-(103-height[k])*e));}
}
for(let pass=0;pass<100;pass++){let changed=0;for(let z=163;z<267;z++)for(let x=44;x<147;x++){
 const k=z*S+x;for(const j of [k+1,k+S]){if(built[k]||built[j]||height[k]<96||height[j]<96)continue;const hi=rounded[k]>rounded[j]?k:j,lo=hi===k?j:k;if(rounded[hi]-rounded[lo]<=1)continue;if(!fixed[hi]){rounded[hi]=rounded[lo]+1;changed++;}else if(!fixed[lo]){rounded[lo]=rounded[hi]-1;changed++;}}
 }if(!changed)break;}
const columns=[];for(let z=162;z<=267;z++)for(let x=43;x<=147;x++){
 const k=z*S+x;if(fixed[k]||height[k]===rounded[k])continue;const before=height[k],after=rounded[k];columns.push({x,z,before,after});
 for(let y=Math.min(before,after)-2;y<=Math.max(before,after);y++)put(x,y,z,y>after?'air':y===after?'grass_block':y>=after-2?'dirt':'stone',y===after?{snowy:'false'}:undefined);
}
let beforeJumps=0,afterJumps=0,maxBefore=0,maxAfter=0;const changedCols=new Set(columns.map(c=>c.z*S+c.x));
for(let z=161;z<=267;z++)for(let x=42;x<=147;x++){const k=z*S+x;for(const j of [k+1,k+S]){if(built[k]||built[j]||height[k]<96||height[j]<96||!changedCols.has(k)&&!changedCols.has(j))continue;const a=Math.abs(height[k]-height[j]),b=Math.abs(rounded[k]-rounded[j]);if(a>1)beforeJumps++;if(b>1)afterJumps++;maxBefore=Math.max(maxBefore,a);maxAfter=Math.max(maxAfter,b);}}
if(afterJumps>beforeJumps)throw Error('Natural slopes regressed');
// Every stall interior remains reachable on the level market floor.
const seen=new Set(),queue=[[112,215]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<60||x>143||z<175||z>244||seen.has(key(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(103)==='minecraft:air'||n(104)!=='minecraft:air'||n(105)!=='minecraft:air')continue;seen.add(key(x,z));queue.push([x-1,z],[x+1,z],[x,z-1],[x,z+1]);}
for(const s of market.stalls){const [a,c,b,d]=s.bounds;if(d>244)throw Error('Stall outside plaza');if(!seen.has(key((a+b)/2,(c+d)/2)))throw Error('Stall inaccessible');}
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-edge-repair/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
market.stallPlots=market.stalls.map(s=>s.bounds);market.bounds[3]=244;market.landscapeSmoothing={bounds:[43,162,147,267],columns:columns.length,beforeJumps,afterJumps,maxBefore,maxAfter};manifest.rooms=manifest.rooms.map(r=>r.id===market.id?market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===market.id?{...z,...market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({stall,changedBlocks:count,changedChunks:result.changed,smoothing:market.landscapeSmoothing,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,smoothing:market.landscapeSmoothing});
