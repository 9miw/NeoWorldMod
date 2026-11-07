const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'castle-fortress-report.json');if(fs.existsSync(rf))throw Error('Already remodelled');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const pre=createLibrary(path.join(root,'archive/map-edit-backups/before-governor-castle')).load(path.basename(map)),pb=Buffer.from(pre.blocks,'base64'),protectedCol=new Uint8Array(S*S),ground=new Int16Array(S*S).fill(-1),nat=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
for(let z=26;z<=149;z++)for(let x=99;x<=223;x++)for(let y=90;y<pre.high;y++){const n=pre.definitions[pb.readUInt16LE(ix(x,y,z)*2)].Name;if(n!=='minecraft:air')ground[z*S+x]=y;if(!nat.has(n))protectedCol[z*S+x]=1;}
function put(x,y,z,n,p){if(x<103||x>217||z<30||z>124||y<96||y>171)throw Error('Outside fortress scope '+[x,y,z]);if(protectedCol[z*S+x])throw Error('Existing city column '+[x,z]);const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
function box(x0,y0,z0,x1,y1,z1,n,p){for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=y0;y<=y1;y++)put(x,y,z,n,p);}
const stair=f=>({facing:f,half:'bottom',shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},inKeep=(x,z)=>x>=147&&x<=173&&z>=41&&z<=67,towers=[[125,47,150],[195,47,150],[125,92,142],[195,92,142]],inTower=(x,z)=>towers.some(([cx,cz])=>Math.hypot(x-cx,z-cz)<=8.5);
// Remove the three house-like pitched roof masses and their triangular gables.
for(const [x0,x1,z0,z1,lo]of [[117,143,41,93,139],[177,203,41,93,139],[141,179,57,95,141]])for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++){
 if(inKeep(x,z)||inTower(x,z))continue;for(let y=lo;y<=156;y++)put(x,y,z,'air');
}
function terrace(x0,z0,x1,z1,y){
 for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++){
  if(inKeep(x,z)||inTower(x,z))continue;put(x,y,z,'stone_bricks');const edge=x===x0||x===x1||z===z0||z===z1;
  if(edge){put(x,y+1,z,'stone_bricks');if((x+z)%4<2)put(x,y+2,z,'chiseled_stone_bricks');}
 }
}
terrace(118,42,142,92,139);terrace(178,42,202,92,139);terrace(142,58,178,94,141);
// Small blue roof volumes above the rear wings retain the heraldic roof color without a giant gable.
for(const cx of [130,190])for(let x=cx-7;x<=cx+7;x++)for(let z=59;z<=73;z++){
 const r=Math.max(Math.abs(x-cx),Math.abs(z-66));put(x,141+Math.floor((7-r)*.7),z,'blue_concrete');
}
// Strengthen and flare each tower base; narrow its broad windows to arrow slits.
for(const [cx,cz,top]of towers){
 for(let x=cx-9;x<=cx+9;x++)for(let z=cz-9;z<=cz+9;z++){
  const r=Math.hypot(x-cx,z-cz);if(r>8.5)continue;
  if(r>=6.7){for(let y=116;y<=119;y++)put(x,y,z,y===119?'chiseled_stone_bricks':'stone_bricks');if(r>7.2)put(x,120,z,'stone_brick_stairs',stair(Math.abs(x-cx)>Math.abs(z-cz)?x>cx?'west':'east':z>cz?'north':'south'));}
  for(let y=121;y<=top;y++){const n=m.definitions[data[ix(x,y,z)]].Name;if(n==='minecraft:light_blue_stained_glass'&&Math.abs(x-cx)>.3&&Math.abs(z-cz)>.3)put(x,y,z,'smooth_quartz');}
  if(r>7.2){put(x,top-2,z,'stone_brick_stairs',stair(Math.abs(x-cx)>Math.abs(z-cz)?x>cx?'west':'east':z>cz?'north':'south'));put(x,top-1,z,'chiseled_stone_bricks');put(x,top,z,'smooth_quartz_slab',slab);}
 }
}
// Projecting stone buttresses interrupt the long flat wing walls.
for(const x of [134,140,180,186])for(let y=117;y<=135;y++)for(let dx=0;dx<=1;dx++)for(let z=93;z<=94;z++){
 const depth=y<=123||z===93;if(depth)put(x+dx,y,z,y<=120?'stone_bricks':'smooth_quartz');
}
for(const [x0,face]of [[116,'east'],[203,'west']])for(const z0 of [60,72,84]){
 box(x0,116,z0,x0+1,129,z0+1,'stone_bricks');box(x0,130,z0,x0+1,133,z0+1,'smooth_quartz');for(let z=z0;z<=z0+1;z++)put(x0,134,z,'stone_brick_stairs',stair(face));
}
// Replace the keep's pyramid with a defensible crown and four slender corner turrets.
for(let x=147;x<=173;x++)for(let z=41;z<=67;z++)for(let y=154;y<=164;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:blue_concrete')put(x,y,z,'air');
box(148,156,42,172,156,66,'stone_bricks');
for(let x=148;x<=172;x++)for(let z=42;z<=66;z++)if(x===148||x===172||z===42||z===66){put(x,157,z,'stone_bricks');if((x+z)%4<2)put(x,158,z,'chiseled_stone_bricks');}
for(const [cx,cz]of [[150,44],[170,44],[150,64],[170,64]]){
 for(let y=154;y<=162;y++)for(let x=cx-2;x<=cx+2;x++)for(let z=cz-2;z<=cz+2;z++)if(Math.hypot(x-cx,z-cz)<=2.4)put(x,y,z,'calcite');
 for(let y=163;y<=168;y++)for(let x=cx-3;x<=cx+3;x++)for(let z=cz-3;z<=cz+3;z++)if(Math.hypot(x-cx,z-cz)<=(168-y)*.5+.6)put(x,y,z,'blue_concrete');
}
for(let y=157;y<=171;y++)put(160,y,54,y===171?'sea_lantern':'oak_fence',y===171?undefined:{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});
// The former rear gable crossed this stairwell; remove it and rebuild the clear upper flight.
box(151,141,50,155,154,61,'air');for(let i=0;i<12;i++)for(let x=151;x<=155;x++)put(x,141+i,61-i,'stone_brick_stairs',stair('north'));
box(158,153,46,162,158,49,'air');for(let i=0;i<4;i++)for(let x=158;x<=162;x++)put(x,153+i,49-i,'stone_brick_stairs',stair('north'));
// Deep, thick gatehouse with a tall arched passage and an upper guard hall.
for(let x=149;x<=171;x++)for(let z=83;z<=99;z++){
 put(x,116,z,'stone_bricks');for(let y=117;y<=148;y++){
  const edge=x<=150||x>=170||z<=84||z>=98;if(edge)put(x,y,z,y<=121||y===128||y===140||y===148?'stone_bricks':'calcite');
 }
 put(x,140,z,'smooth_stone');put(x,148,z,'stone_bricks');
 if(x===149||x===171||z===83||z===99){put(x,149,z,'stone_bricks');if((x+z)%4<2){put(x,150,z,'chiseled_stone_bricks');put(x,151,z,'stone_bricks');}}
}
box(151,141,85,169,147,97,'air');
for(let x=155;x<=165;x++)for(let z=83;z<=99;z++){
 const top=126+Math.floor(4*(1-Math.pow(Math.abs(x-160)/6,2)));for(let y=117;y<=top;y++)put(x,y,z,'air');
}
// Recessed arch surround, raised iron teeth, and a blue-gold crest.
for(const x of [153,154,166,167])box(x,117,99,x,130,99,'smooth_quartz');
for(let x=155;x<=165;x++){const y=127+Math.floor(4*(1-Math.pow(Math.abs(x-160)/6,2)));put(x,y,99,'smooth_quartz');if(x%2===0)put(x,129,98,'iron_bars',{north:'false',south:'false',east:'true',west:'true',waterlogged:'false'});}
for(let y=133;y<=138;y++)for(let x=158;x<=162;x++)if(y<137||Math.abs(x-160)<=138-y)put(x,y,99,x===160||y===135?'gold_block':'blue_wool');
for(const x of [152,168])for(let y=133;y<=138;y++)put(x,y,99,y===133||y===138?'smooth_quartz':'light_blue_stained_glass');
// Four continuous rises replace the old shallow steps to meet the extended gatehouse floor.
for(let x=153;x<=167;x++)for(let z=100;z<=103;z++){
 const y=216-z;for(let yy=112;yy<y;yy++)put(x,yy,z,'stone_bricks');for(let yy=y+1;yy<=117;yy++)put(x,yy,z,'air');put(x,y,z,'stone_brick_stairs',stair('north'));
}
// Roof walk from the keep to the guard hall; stairs continue to the gatehouse battlements.
box(157,141,66,163,141,82,'stone_bricks');box(157,141,65,163,145,65,'air');
for(let x=157;x<=163;x++){put(x,141,66,'stone_brick_stairs',stair('south'));box(x,141,83,x,145,84,'air');put(x,141,83,'stone_brick_stairs',stair('north'));}
box(152,141,88,154,150,95,'air');for(let i=0;i<8;i++)for(let x=152;x<=154;x++)put(x,141+i,88+i,'stone_brick_stairs',stair('south'));
// Thicken the curtain wall to a three-block defensive walk, preserving all earlier city columns.
const ring=[];for(let x=108;x<=212;x++)for(let z=34;z<=124;z++){
 const dx=Math.max(120-x,0,x-200),dz=Math.max(46-z,0,z-112),r=Math.hypot(dx,dz),edge=x===108||x===212||z===34||z===124||r>=11&&r<=12.2;
 if(edge&&r<=12.2&&ground[z*S+x]>=96&&!(x>=151&&x<=169&&z>=120))ring.push([x,z]);
}
const wallCells=new Set();for(const [cx,cz]of ring)for(let dx=-1;dx<=1;dx++)for(let dz=-1;dz<=1;dz++){
 const x=cx+dx,z=cz+dz,k=z*S+x;if(x<107||x>213||z<33||z>124||protectedCol[k]||ground[k]<96||x>=151&&x<=169&&z>=120)continue;wallCells.add(k);
 const floor=z>=102?112:116,deck=floor+8;for(let y=Math.min(floor,ground[k]);y<=deck;y++)put(x,y,z,'stone_bricks');
}
for(const k of wallCells){const x=k%S,z=Math.floor(k/S),deck=z>=102?120:124,edge=![k-1,k+1,k-S,k+S].every(j=>wallCells.has(j));if(edge){put(x,deck+1,z,'stone_bricks');if((x+z)%4<2)put(x,deck+2,z,'chiseled_stone_bricks');}}
// Courtyard stairs reach the outer wall walk without encroaching on the blacksmith.
for(let i=0;i<8;i++)for(let x=119;x<=121;x++){const z=119-i,y=113+i;for(let yy=112;yy<y;yy++)put(x,yy,z,'stone_bricks');for(let yy=y+1;yy<=122;yy++)put(x,yy,z,'air');put(x,y,z,'stone_brick_stairs',stair('north'));}
box(112,120,109,121,120,111,'stone_bricks');box(112,121,109,121,122,111,'air');
// Validate multi-level access through the new arch and both defensive walks.
const cache=new Map(),surface=(hx,hz)=>{const key=hx+','+hz;if(cache.has(key))return cache.get(key);const x=Math.floor(hx/2),z=Math.floor(hz/2),a=[];if(x<107||x>213||z<33||z>150)return a;
 for(let y=108;y<=157;y++){const d=m.definitions[data[ix(x,y,z)]],n=d.Name;if(n==='minecraft:air'||n.includes('fence')||n.includes('wall')||n.includes('lantern')||n.includes('carpet')||n.includes('iron_bars'))continue;let h=y+1;if(n.endsWith('_stairs')){const f=d.Properties.facing,hi=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=y+(hi?1:.5);}if(n.endsWith('_slab'))h=y+.5;
 let free=true;for(let yy=Math.ceil(h);yy<Math.ceil(h)+2;yy++){const nn=m.definitions[data[ix(x,yy,z)]].Name;if(nn!=='minecraft:air'&&!nn.endsWith('_carpet'))free=false;}if(free)a.push(Math.round(h*16));
 }cache.set(key,a);return a;};
const seen=new Set(),queue=[[320,298,1744]],key=(x,z,h)=>x+','+z+','+h;for(let q=0;q<queue.length;q++){
 const [x,z,h]=queue[q];if(seen.has(key(x,z,h))||!surface(x,z).includes(h))continue;seen.add(key(x,z,h));for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]])for(const nh of surface(nx,nz))if(Math.abs(nh-h)<=8&&!seen.has(key(nx,nz,nh)))queue.push([nx,nz,nh]);
}
const targets=[['court',160,116,113],['great hall',160,80,117],['left room',133,82,117],['right room',187,82,117],['gallery',140,60,129],['keep upper',163,48,153],['guard hall',160,90,141],['gate battlements',160,96,149],['keep battlements',160,45,157],['outer wall',115,110,121]];
for(const [name,x,z,h]of targets)if(!seen.has(key(x*2,z*2,h*16))){console.log({failed:name,surfaces:surface(x*2,z*2),keepReached:[...seen].filter(k=>{const [x,z,h]=k.split(',').map(Number);return x>=300&&x<=340&&z>=90&&z<=130&&h>=128*16;}).slice(-12)});throw Error('Fortress route unreachable '+name);}
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),r=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-castle-fortress/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',r.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),castle=manifest.castle;castle.style='fortified stone castle with gatehouse, flat defensive terraces and pointed turrets';castle.entrance=[160,117,99];castle.fortress={gatehouse:[149,116,83,171,151,99],wallWalkWidth:3,verifiedRoutes:targets,removedLargeGables:true,highestY:171};manifest.rooms=manifest.rooms.map(r=>r.id===castle.id?castle:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===castle.id?{...z,...castle}:z),null,2));fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:r.changed,fortress:castle.fortress,reachableSurfaces:seen.size,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:r.changed,reachableSurfaces:seen.size});
