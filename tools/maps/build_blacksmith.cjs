const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'blacksmith-report.json');if(fs.existsSync(rf))throw Error('Smith already built');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']),tops=new Int16Array(S*S).fill(-1),built=new Uint8Array(S*S);
for(let z=108;z<=181;z++)for(let x=44;x<=153;x++){for(let y=90;y<m.high;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(n!=='minecraft:air')tops[z*S+x]=y;if(!natural.has(n))built[z*S+x]=1;}}
function put(x,y,z,n,p){if(x<46||x>153||z<110||z>180||y<90||y>130)throw Error('Outside smith scope');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const stair=f=>({facing:f,half:'bottom',shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},lantern={hanging:'false',waterlogged:'false'};
const plate=[];for(let z=133;z<=166;z++)for(let x=62;x<=115;x++){
 const d=Math.hypot(Math.max(67-x,0,x-110),Math.max(138-z,0,z-161));if(d<=5)plate.push([x,z]);
}
for(let x=109;x<=115;x++)for(let z=167;z<=178;z++)plate.push([x,z]);
for(let x=116;x<=153;x++)for(let z=159;z<=161;z++)plate.push([x,z]);
const surface=new Map();for(const [x,z]of plate){let h=107;if(x>=109&&x<=115&&z>=167)h=z>=177?103:z>=174?104:z>=171?105:z>=168?106:107;surface.set(z*S+x,h);}
// Blend the new working terrace into existing natural land, preserving city structures and cliffs.
for(let z=111;z<=180;z++)for(let x=46;x<=153;x++){
 const k=z*S+x;if(built[k]||tops[k]<96)continue;let target=tops[k],on=surface.has(k);if(on)target=surface.get(k);else{
  let distance=19,near=target;for(const [px,pz]of plate){const d=Math.hypot(px-x,pz-z);if(d<distance){distance=d;near=surface.get(pz*S+px);}}
  if(distance>=18)continue;let edge=false;for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(tops[(z+dz)*S+x+dx]<96)edge=true;if(edge)continue;
  const t=distance/18,e=t*t*(3-2*t);target=Math.round(near+(tops[k]-near)*e);
 }
 for(let y=Math.min(target,tops[k])-2;y<=Math.max(target,tops[k]);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
for(const [k,h]of surface){const x=k%S,z=Math.floor(k/S);if(built[k])throw Error('Terrace overlaps existing structure');put(x,h,z,(x+z)%9===0?'andesite':'stone_bricks');}
// Seven-wide northbound stair approach: one rise every three rows with landings.
for(const [z,h]of [[176,104],[173,105],[170,106],[167,107]])for(let x=109;x<=115;x++)put(x,h,z,'stone_brick_stairs',stair('north'));
function house(x0,z0,x1,z1,wing){
 const cx=(x0+x1)/2,hw=(x1-x0)/2;
 for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++){
  put(x,107,z,(x+z)%8===0?'polished_andesite':'stone_bricks');
  if(x!==x0&&x!==x1&&z!==z0&&z!==z1)continue;
  const post=(x===x0||x===x1)&&(z===z0||z===z1||z%6===0)||(z===z0||z===z1)&&x%6===0;
  for(let y=108;y<=115;y++){
   let n=y<=109?'stone_bricks':post||y===115?'dark_oak_log':'calcite';
   const window=(z===z0||z===z1)&&Math.abs(x-cx)>4&&Math.abs(x-cx)<9;
   if(window&&y>=110&&y<=113)n=y===110||y===113?'spruce_planks':'light_blue_stained_glass';
   // Main front arch and side door in the shorter L wing.
   if(!wing&&z===z1&&x>=84&&x<=90&&y<=111)n='air';
   if(wing&&x===x1&&z>=154&&z<=158&&y<=111)n='air';
   put(x,y,z,n,n==='dark_oak_log'?{axis:'y'}:undefined);
  }
 }
 for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++){
  if(z!==z0&&z!==z1)continue;const top=116+Math.floor((hw-Math.abs(x-cx))*.6);
  for(let y=116;y<top;y++)put(x,y,z,x===cx?'dark_oak_log':'spruce_planks',x===cx?{axis:'y'}:undefined);
 }
 for(let x=x0-1;x<=x1+1;x++)for(let z=z0-1;z<=z1+1;z++){
  const y=116+Math.floor(Math.max(0,hw+1-Math.abs(x-cx))*.6),edge=z===z0-1||z===z1+1;
  put(x,y,z,edge?'dark_oak_planks':'spruce_planks');if(!edge&&x!==cx)put(x,y+1,z,'spruce_stairs',stair(x<cx?'east':'west'));if(x===cx)put(x,y+1,z,'dark_oak_slab',slab);
 }
 for(const x of [x0,x1])for(const z of [z0,z1]){put(x,108,z,'chiseled_stone_bricks');put(x,109,z,'chiseled_stone_bricks');}
 for(let x=x0;x<=x1;x++)if(wing||x<83||x>91)put(x,108,z1+1,'stone_brick_stairs',stair('north'));
}
house(66,137,96,149,false);house(66,150,78,161,true);
// Link the L wing and the main workroom through the shared north partition.
for(let x=70;x<=74;x++)for(let y=108;y<=111;y++){put(x,y,149,'air');put(x,y,150,'air');}
// Covered outside work bay, intentionally open to the south and east.
for(const x of [80,94])for(const z of [151,160]){for(let y=108;y<=114;y++)put(x,y,z,y===108?'chiseled_stone_bricks':'dark_oak_log',y===108?undefined:{axis:'y'});}
for(let x=79;x<=95;x++)for(let z=150;z<=161;z++)put(x,115-Math.floor((z-150)/5),z,'spruce_slab',slab);
for(let x=80;x<=94;x++)put(x,112,160,'dark_oak_log',{axis:'x'});
// Enclosed luminous forge bed and chimney; solid grating keeps the fire display off the walking route.
for(let x=85;x<=91;x++)for(let z=138;z<=143;z++){
 const rim=x===85||x===91||z===138||z===143;
 put(x,108,z,rim?'deepslate_bricks':(x+z)%2?'magma_block':'shroomlight');
 if(rim)put(x,109,z,'deepslate_bricks');else put(x,109,z,'iron_bars',{north:'true',south:'true',east:'true',west:'true',waterlogged:'false'});
 if(x===85||x===91)for(let y=110;y<=113;y++)put(x,y,z,'deepslate_bricks');
 put(x,114,z,'deepslate_bricks');
}
for(let y=108;y<=128;y++)for(let x=68;x<=72;x++)for(let z=139;z<=143;z++){
 const outer=x===68||x===72||z===139||z===143;put(x,y,z,outer?'stone_bricks':'air');
}
for(let x=67;x<=73;x++)for(let z=138;z<=144;z++)if(x===67||x===73||z===138||z===144)put(x,129,z,'stone_brick_slab',slab);
// Real vanilla workstations; storage piles are decorative and need no inventory block entities.
for(const [x,z]of [[84,154],[90,155],[82,144]])put(x,108,z,'anvil',{facing:'north'});
for(const [x,z,n]of [[92,145,'smithing_table'],[80,140,'crafting_table'],[69,156,'smithing_table']])put(x,108,z,n);
for(let x=80;x<=83;x++)put(x,108,139,'spruce_planks');
for(let z=152;z<=159;z++)for(let x=68;x<=70;x++)put(x,108,z,(x+z)%3?'raw_iron_block':'coal_block');
for(const [x,z]of [[99,151],[101,151],[99,153],[68,164]])put(x,108,z,'spruce_wood',{axis:'y'});
for(let x=98;x<=101;x++)put(x,108,157,'coal_block');
for(let x=98;x<=100;x++)for(let z=161;z<=163;z++){put(x,107,z,'stone_bricks');put(x,108,z,x===99&&z===162?'water':'polished_andesite',x===99&&z===162?{level:'0'}:undefined);}
for(const [x,z]of [[81,151],[93,151],[104,163],[64,164]]){put(x,108,z,'chiseled_stone_bricks');put(x,109,z,'lantern',lantern);}
for(const [x,z]of [[80,143],[91,146],[72,154]]){put(x,113,z,'dark_oak_planks');put(x,112,z,'lantern',{hanging:'true',waterlogged:'false'});}
// Half-step route validation from the existing market entrance to the new room and work bay.
const surf=(hx,hz)=>{const x=Math.floor(hx/2),z=Math.floor(hz/2);if(x<58||x>155||z<130||z>210)return null;
 for(let y=102;y<=108;y++){const d=m.definitions[data[ix(x,y,z)]],n=d.Name;if(n==='minecraft:air'||n.includes('water')||n.includes('lantern')||n.includes('iron_bars')||n.includes('leaves'))continue;
 let h=y+1;if(n==='minecraft:dirt_path')h=y+.9375;if(n.endsWith('_stairs')){const f=d.Properties.facing,hi=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=y+(hi?1:.5);}if(n.endsWith('_slab'))h=y+.5;
 let free=true;for(let yy=Math.ceil(h);yy<Math.ceil(h)+2;yy++)if(m.definitions[data[ix(x,yy,z)]].Name!=='minecraft:air')free=false;if(free)return h;
 }return null;};
const seen=new Set(),queue=[[224,402]],key=(x,z)=>x+','+z;for(let q=0;q<queue.length;q++){const [x,z]=queue[q];if(seen.has(key(x,z)))continue;const h=surf(x,z);if(h===null)continue;seen.add(key(x,z));for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]]){const nh=surf(nx,nz);if(nh!==null&&Math.abs(nh-h)<=.5&&!seen.has(key(nx,nz)))queue.push([nx,nz]);}}
for(const [x,z]of [[112,178],[112,165],[87,157],[87,146],[74,155],[152,160]])if(!seen.has(key(x*2,z*2)))throw Error('Unreachable smith target '+[x,z]);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-blacksmith/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const smith={id:'05-blacksmith',name:'Blacksmith',bounds:[62,133,115,178],buildingBounds:[66,137,96,161],buildingSize:[31,25],shape:'L',floorY:107,entrance:[87,108,149],workBay:[79,150,95,161],chimneyBounds:[67,108,138,73,129,144],marketApproach:[112,104,178],mainStreetConnection:[153,108,160],stations:[{type:'anvil',at:[84,108,154]},{type:'smithing_table',at:[92,108,145]},{type:'crafting_table',at:[80,108,140]}],stage:'built; vanilla workstations; no NPC or mod service integration'};
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blacksmith=smith;manifest.rooms=(manifest.rooms||[]).filter(r=>r.id!==smith.id);manifest.rooms.push(smith);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf)),zi=zones.findIndex(z=>z.id===smith.id);if(zi>=0)zones[zi]={...zones[zi],...smith};else zones.push(smith);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({smith,changedBlocks:count,changedChunks:result.changed,reachableHalfCells:seen.size,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,smith});
