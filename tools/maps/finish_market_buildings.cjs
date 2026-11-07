const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'market-buildings-detail-report.json');if(fs.existsSync(rf))throw Error('Details already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);
const data=old.slice(),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),market=manifest.market;
function put(x,y,z,n,p){if(x<55||x>134||z<169||z>218||y<96||y>126)throw Error('Outside buildings');for(const s of market.stalls){const [a,c,b,d]=s.bounds;if(x>=a&&x<=b&&z>=c&&z<=d)throw Error('Stall collision');}const def={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(def);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(def);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const stairs=facing=>({facing,half:'bottom',shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},lantern={hanging:'false',waterlogged:'false'};
const buildings=[];
for(const s of market.shops){
 const big=s.id==='general',hw=big?12:8,hd=big?11:(s.bounds[3]-s.bounds[1])/2,cx=big?76:(s.bounds[0]+s.bounds[2])/2,cz=big?197:(s.bounds[1]+s.bounds[3])/2,angle=big?Math.PI/6:0,c=Math.cos(angle),sn=Math.sin(angle);
 const local=(x,z)=>[c*(x-cx)-sn*(z-cz),sn*(x-cx)+c*(z-cz)],world=(u,v)=>[Math.round(cx+c*u+sn*v),Math.round(cz-sn*u+c*v)];
 function at(u,y,v,n,p){const [x,z]=world(u,v);put(x,y,z,n,p);}
 const radius=Math.ceil(Math.hypot(hw+3,hd+3)),bounds=[Math.floor(cx-radius),Math.floor(cz-radius),Math.ceil(cx+radius),Math.ceil(cz+radius)];
 // Remove the old open-frame roof and posts only inside this shop's local envelope.
 for(let x=bounds[0];x<=bounds[2];x++)for(let z=bounds[1];z<=bounds[3];z++){
  const [u,v]=local(x,z);if(Math.abs(u)>hw+.8||Math.abs(v)>hd+.8)continue;
  for(let y=104;y<=117;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(n==='minecraft:dark_oak_log'||n==='minecraft:spruce_planks')put(x,y,z,'air');}
 }
 for(let x=bounds[0];x<=bounds[2];x++)for(let z=bounds[1];z<=bounds[3];z++){
  const [u,v]=local(x,z),au=Math.abs(u),av=Math.abs(v);
  if(au<=hw+.5&&av<=hd+.5){
   const edge=au>=hw-1.5||av>=hd-1.5,corner=au>=hw-(big?1.6:.65)&&av>=hd-(big?1.6:.65),sidePost=au>=hw-(big?1.5:.6)&&Math.abs(v)<.8,frontPost=av>=hd-(big?1.5:.6)&&(Math.abs(u)<.7||Math.abs(Math.abs(u)-6)<.7);
   if(edge){
    for(let y=104;y<=110;y++){
     const recessed=au<=hw-.5&&av<=hd-.5;
     let n=(corner||sidePost||frontPost)?'dark_oak_log':y===104?'stone_bricks':recessed?'calcite':'air';
     if(y===110)n='dark_oak_log';
     // Windows sit inside recessed wall panels, with a full wooden surround.
     const front=av>=hd-1.5&&av<=hd-.5,side=au>=hw-1.5&&au<=hw-.5;
     let windowT=front?u:side?v:null;
     if(windowT!==null&&!corner&&!sidePost){const centers=front?(big?[-7,7]:[-4,4]):[-hd/2,hd/2];const delta=Math.min(...centers.map(t=>Math.abs(windowT-t)));
      if(delta<=2.1&&y>=105&&y<=109)n=delta<1.5&&y>=106&&y<=108?'light_blue_stained_glass':'spruce_planks';
     }
     // An open three-block arch keeps the diagonal entrance usable.
     if(v>hd-2&&Math.abs(u)<1.8&&y<=107)n='air';
     if(v>hd-2&&Math.abs(u)<2.8&&Math.abs(u)>=1.8&&y<=108)n='dark_oak_log';
     if(v>hd-2&&Math.abs(u)<2.8&&y===108)n='dark_oak_log';
     put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);
    }
   }
   // Closed gables with a contrasting central timber and orange infill.
   if(av>=hd-1.5&&av<=hd-.5)for(let y=111;y<112+Math.floor((hw+1-au)*.65);y++)put(x,y,z,Math.abs(u)<.65?'dark_oak_log':y%2?'calcite':'spruce_planks',Math.abs(u)<.65?{axis:'y'}:undefined);
  }
  // Overhanging pitched roof: warm sandstone, copper-colored infill and dark eaves.
  if(au<=hw+1.5&&av<=hd+1.5){
   const y=112+Math.floor(Math.max(0,hw+1-au)*.65),rim=av>=hd+.5||au>=hw+.5;
   put(x,y,z,rim?'dark_oak_planks':Math.floor(au*2)%3===0?'red_sandstone':'orange_terracotta');
   if(au>.8&&!rim)put(x,y+1,z,'red_sandstone_stairs',stairs(u<0?'east':'west'));
   if(au<.8)put(x,y+1,z,'red_sandstone_slab',slab);
  }
 }
 // Stone plinths thicken and articulate the main posts rather than flat wall stripes.
 for(const u of [-hw,hw])for(const v of [-hd,0,hd]){
  if(big){for(const du of [-.5,.5])for(const dv of [-.5,.5]){at(u+du,104,v+dv,'stone_bricks');at(u+du,105,v+dv,'chiseled_stone_bricks');}}
  else {at(u,104,v,'stone_bricks');at(u,105,v,'chiseled_stone_bricks');}
  at(u,110,v,'dark_oak_log',{axis:'y'});
 }
 // Proud sills, lintels, shutters, flower troughs, and two entrance lights.
 for(const u of big?[-7,7]:[-4,4]){
  for(let du=-2;du<=2;du++){at(u+du,105,hd,'spruce_slab',slab);at(u+du,109,hd,'dark_oak_slab',slab);}
  for(const side of [-2,2])for(let y=106;y<=108;y++)at(u+side,y,hd,'spruce_planks');
  if(s.id==='food-materials')for(let du=-1;du<=1;du++){at(u+du,104,hd+1,'moss_block');at(u+du,105,hd+1,du?'poppy':'dandelion');}
 }
 for(const u of [-3,3]){at(u,104,hd+1,'chiseled_stone_bricks');at(u,105,hd+1,'lantern',lantern);}
 // Small canopy and a hanging emblem above the entrance, with open headroom below.
 for(let u=-3;u<=3;u++)for(let v=hd;v<=hd+2;v++)at(u,109,v,'spruce_slab',slab);
 at(0,111,hd+1,'dark_oak_log',{axis:'y'});at(0,110,hd+1,s.id==='general'?'gold_block':s.id==='food-materials'?'hay_block':'iron_block',s.id==='food-materials'?{axis:'y'}:undefined);
 // Usable interior: rear service counter, shelves, benches and ceiling lamps.
 for(let u=-hw+3;u<=hw-3;u++){at(u,104,-hd+3,'spruce_planks');if(u%3===0)at(u,105,-hd+3,'smooth_stone_slab',slab);}
 for(const u of [-hw+2,hw-2])for(let v=-hd+2;v<=-hd+6;v++)for(let y=104;y<=106;y++)at(u,y,v,'bookshelf');
 for(let v=0;v<=3;v++)at(-hw+3,104,v,'spruce_stairs',stairs('west'));
 for(const u of [-4,4]){at(u,109,0,'dark_oak_planks');at(u,108,0,'lantern',{hanging:'true',waterlogged:'false'});}
 for(const [u,v,n]of [[hw-3,hd+1,'hay_block'],[hw-4,hd+2,'spruce_wood'],[-hw+3,hd+1,'spruce_wood']])at(u,104,v,n,n==='hay_block'||n==='spruce_wood'?{axis:'y'}:undefined);
 // One masonry chimney on each small shop, offset from entrances.
 if(!big){for(let y=112;y<=121;y++)at(hw-3,y,-hd+3,'bricks');at(hw-3,122,-hd+3,'brick_slab',slab);}
 buildings.push({id:s.id,center:[cx,103,cz],localSize:[hw*2+1,hd*2+1],roofTopY:big?121:122,entrance:world(0,hd-1),inside:world(0,0)});
 s.stage='finished timber and stone shop; framed windows; pitched roof; interior';s.decoration={postWidth:big?2:1,wallInset:1,roofOverhang:1,windowFrames:true,entranceCanopy:true};
}
// Validate the main street and every public stall against accidental construction overlap.
for(let z=212;z<=218;z++)for(let x=106;x<=143;x++)for(let y=104;y<=108;y++)if(data[ix(x,y,z)]!==old[ix(x,y,z)])throw Error('Street changed');
// Level-floor BFS: actual access through arches into each furnished interior.
const seen=new Set(),queue=[[112,215]],key=(x,z)=>x+','+z;
for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(x<55||x>143||z<170||z>253||seen.has(key(x,z)))continue;const n=y=>m.definitions[data[ix(x,y,z)]].Name;if(n(103)==='minecraft:air'||n(104)!=='minecraft:air'||n(105)!=='minecraft:air')continue;seen.add(key(x,z));queue.push([x+1,z],[x-1,z],[x,z+1],[x,z-1]);}
for(const s of buildings)for(const [x,z]of [s.inside,s.entrance])if(!seen.has(key(x,z)))throw Error('Shop inaccessible '+s.id+' '+[x,z]);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-market-buildings-detail/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
market.buildingDetails=buildings;market.stage='three detailed shops; six stalls and fountain; wider market landscaping pending';manifest.rooms=manifest.rooms.map(r=>r.id===market.id?market:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===market.id?{...z,...market}:z),null,2));fs.writeFileSync(rf,JSON.stringify({buildings,changedBlocks:count,changedChunks:result.changed,walkableCells:seen.size,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,walkableCells:seen.size,buildings});
