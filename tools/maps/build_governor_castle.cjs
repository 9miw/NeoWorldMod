const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'governor-castle-report.json');if(fs.existsSync(rf))throw Error('Castle already built');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64');m.high=192;const S=m.size,old=new Uint16Array(S*S*(m.high-m.low));for(let i=0;i<buf.length/2;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function put(x,y,z,n,p){if(x<99||x>223||z<26||z>149||y<90||y>171)throw Error('Outside castle scope '+[x,y,z]);const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const stairs=(f,h='bottom')=>({facing:f,half:h,shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},lantern={hanging:'false',waterlogged:'false'};
function box(x0,y0,z0,x1,y1,z1,n,p){for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)for(let y=y0;y<=y1;y++)put(x,y,z,n,p);}
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']),top=new Int16Array(S*S).fill(-1),built=new Uint8Array(S*S);
for(let z=26;z<=149;z++)for(let x=99;x<=223;x++)for(let y=90;y<144;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(n!=='minecraft:air')top[z*S+x]=y;if(!natural.has(n))built[z*S+x]=1;}
const support=new Map();for(let z=38;z<=101;z++)for(let x=112;x<=208;x++)if(Math.hypot(Math.max(120-x,0,x-200),Math.max(46-z,0,z-93))<=8&&top[z*S+x]>=96)support.set(z*S+x,116);
for(let z=102;z<=124;z++)for(let x=116;x<=204;x++)if(Math.hypot(Math.max(122-x,0,x-198),Math.max(108-z,0,z-118))<=6)support.set(z*S+x,112);
function approach(z){if(z>=147)return 108;if(z>=143)return 109;if(z>=139)return 110;if(z>=135)return 111;if(z>=104)return 112;if(z>=102)return 113;if(z>=100)return 114;if(z>=98)return 115;return 116;}
for(let z=95;z<=149;z++)for(let x=153;x<=167;x++)support.set(z*S+x,approach(z));
// Shape only natural terrain, with a soft 14-block bank around the terraces.
for(let z=27;z<=149;z++)for(let x=100;x<=222;x++){
 const k=z*S+x;if(built[k]||top[k]<96)continue;let target=top[k];if(support.has(k))target=support.get(k);else{
  let d=15,h=target;for(const [j,y]of support){const dz=z-Math.floor(j/S),dx=x-j%S;if(Math.abs(dx)>14||Math.abs(dz)>14)continue;const r=Math.hypot(dx,dz);if(r<d){d=r;h=y;}}
  if(d>=14)continue;const t=d/14,e=t*t*(3-2*t);target=Math.round(h+(top[k]-h)*e);
 }
 for(let y=Math.min(target,top[k])-2;y<=Math.max(target,top[k]);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
for(const [k,h]of support){const x=k%S,z=Math.floor(k/S);if(built[k]){if(x===153&&z>=147)continue;throw Error('Castle terrace overlaps existing structure '+[x,z]);}put(x,h,z,Math.abs(x-160)<=2?'smooth_stone':(x+z)%11===0?'andesite':'stone_bricks');}
for(const [z,h]of [[146,109],[142,110],[138,111],[134,112],[103,113],[101,114],[99,115],[97,116]])for(let x=153;x<=167;x++)put(x,h,z,'stone_brick_stairs',stairs('north'));
function shell(x0,z0,x1,z1,wallTop){
 for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++){
  put(x,116,z,(x+z)%9===0?'polished_andesite':'smooth_stone');if(x!==x0&&x!==x1&&z!==z0&&z!==z1)continue;
  const pier=(x===x0||x===x1)&&(z%7===0||z===z0||z===z1)||(z===z0||z===z1)&&(x%7===0||x===x0||x===x1);
  for(let y=117;y<=wallTop;y++){
   let n=y<=119?'stone_bricks':pier?'smooth_quartz':y===128||y===wallTop?'chiseled_stone_bricks':'calcite';
   const t=x===x0||x===x1?z:x,window=t%7>=2&&t%7<=4;
   if(!pier&&window&&((y>=122&&y<=126)||(y>=132&&y<=wallTop-2)))n=y===122||y===126||y===132||y===wallTop-2?'smooth_quartz':'light_blue_stained_glass';put(x,y,z,n);
  }
 }
}
shell(118,42,142,92,138);shell(178,42,202,92,138);shell(137,42,183,57,138);shell(142,58,178,94,140);
// Side floors and galleries retain a tall central nave.
box(119,128,43,141,128,91,'spruce_planks');box(179,128,43,201,128,91,'spruce_planks');
box(138,128,43,182,128,60,'spruce_planks');box(143,128,61,148,128,92,'spruce_planks');box(172,128,61,177,128,92,'spruce_planks');
for(const x of [149,171])for(let z=62;z<=91;z++)put(x,129,z,'stone_brick_wall',{up:'true',north:'low',south:'low',east:'none',west:'none',waterlogged:'false'});
function roof(x0,z0,x1,z1,eave){const cx=(x0+x1)/2,hw=(x1-x0)/2;for(let x=x0-1;x<=x1+1;x++)for(let z=z0-1;z<=z1+1;z++){
 const h=eave+Math.floor(Math.max(0,hw+1-Math.abs(x-cx))*.7),rim=z===z0-1||z===z1+1;put(x,h,z,rim?'smooth_quartz':'blue_concrete');if(x!==cx&&!rim)put(x,h+1,z,'prismarine_brick_stairs',stairs(x<cx?'east':'west'));if(x===cx)put(x,h+1,z,'dark_prismarine_slab',slab);
}}
roof(118,42,142,92,139);roof(178,42,202,92,139);roof(142,58,178,94,141);
// Four round towers create the broad castle silhouette and accessible lower rooms.
const towers=[];for(const [cx,cz,wallTop,tip]of [[125,47,150,165],[195,47,150,165],[125,92,142,157],[195,92,142,157]]){
 for(let x=cx-7;x<=cx+7;x++)for(let z=cz-7;z<=cz+7;z++){
  const r=Math.hypot(x-cx,z-cz);if(r>7.2)continue;put(x,116,z,'stone_bricks');
  for(let y=117;y<=wallTop;y++){
   const edge=r>=5.7;let n=edge?(y<=119||y===128||y===wallTop?'stone_bricks':'calcite'):'air';
   if(edge&&(Math.abs(x-cx)<=1||Math.abs(z-cz)<=1)&&y%10>=3&&y%10<=7&&y>120)n='light_blue_stained_glass';put(x,y,z,n);
  }
  if(r<5.7)put(x,128,z,'spruce_planks');
 }
 for(let y=wallTop+1;y<=tip;y++){const r=(tip-y)*7/(tip-wallTop);for(let x=cx-8;x<=cx+8;x++)for(let z=cz-8;z<=cz+8;z++)if(Math.hypot(x-cx,z-cz)<=r+.6)put(x,y,z,y===wallTop+1?'smooth_quartz':'blue_concrete');}
 put(cx,tip+1,cz,'lightning_rod',{facing:'up',waterlogged:'false',powered:'false'});towers.push({center:[cx,116,cz],radius:7,tipY:tip+1});
}
// Highest central keep, with three stair flights connecting reception, galleries and upper hall.
shell(149,43,171,65,153);box(150,128,44,170,128,64,'spruce_planks');box(150,140,44,170,140,64,'spruce_planks');box(150,152,44,170,152,64,'spruce_planks');
for(let x=147;x<=173;x++)for(let z=41;z<=67;z++){const r=Math.max(Math.abs(x-160),Math.abs(z-54));if(r<=13)put(x,154+Math.floor((13-r)*.8),z,'blue_concrete');}
for(let y=165;y<=171;y++)put(160,y,54,y===171?'sea_lantern':'oak_fence',y===171?undefined:{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});
for(let z=55;z<=58;z++)for(let y=167;y<=170;y++)put(160,y,z,z%2?'white_wool':'blue_wool');
function cutDoor(x0,z0,x1,z1,y0,y1){box(x0,y0,z0,x1,y1,z1,'air');}
// Inter-room passages at both floor levels.
for(const y of [117,129]){cutDoor(140,56,144,60,y,y+4);cutDoor(176,56,180,60,y,y+4);cutDoor(152,64,168,66,y,y+4);}
cutDoor(156,93,164,95,117,124);
for(const [cx,cz]of [[125,47],[195,47],[125,92],[195,92]])for(const y of [117,129])cutDoor(cx-2,cz-7,cx+2,cz-5,y,y+3);
for(const x0 of [130,184]){
 cutDoor(x0,64,x0+4,75,117,130);for(let i=0;i<12;i++)for(let x=x0;x<=x0+4;x++)put(x,117+i,75-i,'stone_brick_stairs',stairs('north'));
 box(x0,128,62,x0+4,128,63,'spruce_planks');
}
cutDoor(151,50,155,61,117,154);cutDoor(165,50,169,61,129,142);
for(let i=0;i<12;i++)for(let x=151;x<=155;x++){put(x,117+i,61-i,'stone_brick_stairs',stairs('north'));put(x,141+i,61-i,'stone_brick_stairs',stairs('north'));}
for(let i=0;i<12;i++)for(let x=165;x<=169;x++)put(x,129+i,50+i,'stone_brick_stairs',stairs('south'));
box(151,128,48,169,128,49,'spruce_planks');box(151,140,62,169,140,63,'spruce_planks');box(151,152,48,169,152,49,'spruce_planks');
// Reception, throne dais, meeting table and service desks leave a seven-wide public aisle.
box(153,117,68,167,117,72,'polished_andesite');put(160,118,70,'spruce_stairs',stairs('north'));box(159,119,69,161,122,69,'blue_wool');
for(let z=76;z<=90;z++)for(let x=158;x<=162;x++)put(x,117,z,'blue_carpet');
for(const [x0,z0]of [[122,63],[183,63]]){box(x0,117,z0,x0+8,117,z0+2,'spruce_planks');for(const x of [x0+1,x0+7])put(x,118,z0+1,'lantern',lantern);}
box(154,129,76,166,129,79,'dark_oak_planks');for(let x=154;x<=166;x+=3)put(x,129,81,'spruce_stairs',stairs('south'));
for(const [x,z,y]of [[145,65,117],[175,65,117],[145,87,117],[175,87,117],[151,46,153],[169,46,153]]){put(x,y,z,'chiseled_stone_bricks');put(x,y+1,z,'lantern',lantern);}
// Broad front portal, balcony and blue-white heraldry.
for(const x of [153,167])box(x,117,94,x+1,131,95,'smooth_quartz');box(153,130,94,168,131,95,'smooth_quartz');
box(153,128,96,167,128,98,'stone_bricks');for(let x=153;x<=167;x++)put(x,129,98,'stone_brick_wall',{up:'true',north:'none',south:'none',east:'low',west:'low',waterlogged:'false'});
for(const x of [139,181])for(let y=123;y<=134;y++)put(x,y,95,y===123||x===139?'blue_wool':'white_wool');
// Garden beds, benches, lights and a central monument on the arrival court.
for(const [x0,z0]of [[126,108],[185,108],[126,118],[185,118]])for(let x=x0;x<x0+7;x++)for(let z=z0;z<z0+3;z++){
 const edge=x===x0||x===x0+6||z===z0||z===z0+2;put(x,113,z,edge?'stone_brick_slab':'moss_block',edge?slab:undefined);if(!edge)put(x,114,z,x%2?'azure_bluet':'white_tulip');
}
for(const x0 of [137,178])for(let x=x0;x<x0+5;x++)put(x,113,119,'spruce_stairs',stairs('south'));
for(const [x,z]of [[149,126],[171,126],[149,109],[171,109],[119,104],[201,104]]){put(x,113,z,'chiseled_stone_bricks');for(let y=114;y<=117;y++)put(x,y,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,118,z,'lantern',lantern);}
// Rounded perimeter walls with a walkable top and a gap for the central stairway.
for(let x=108;x<=212;x++)for(let z=34;z<=124;z++){
 const dx=Math.max(120-x,0,x-200),dz=Math.max(46-z,0,z-112),r=Math.hypot(dx,dz),edge=(x===108||x===212||z===34||z===124||r>=11&&r<=12.2);
 if(!edge||r>12.2||top[z*S+x]<96||x>=151&&x<=169&&z>=120)continue;
 const floor=z>=102?112:116;for(let y=floor;y<=floor+4;y++)put(x,y,z,'stone_bricks');if((x+z)%3!==0)put(x,floor+5,z,'chiseled_stone_bricks');
}
// Reachable-surface graph includes both floors and stair half-treads.
const cache=new Map(),surfaces=(hx,hz)=>{const k=hx+','+hz;if(cache.has(k))return cache.get(k);const x=Math.floor(hx/2),z=Math.floor(hz/2),a=[];if(x<108||x>212||z<34||z>150)return a;
 for(let y=108;y<=153;y++){const d=m.definitions[data[ix(x,y,z)]],n=d.Name;if(n==='minecraft:air'||n.includes('fence')||n.includes('wall')||n.includes('lantern')||n.includes('carpet')||n.includes('leaves'))continue;let h=y+1;if(n.endsWith('_stairs')){const f=d.Properties.facing,hi=f==='north'?hz%2===0:f==='south'?hz%2===1:f==='east'?hx%2===1:hx%2===0;h=y+(hi?1:.5);}if(n.endsWith('_slab'))h=y+.5;
 let free=true;for(let yy=Math.ceil(h);yy<Math.ceil(h)+2;yy++){const nn=m.definitions[data[ix(x,yy,z)]].Name;if(nn!=='minecraft:air'&&!nn.endsWith('_carpet'))free=false;}if(free)a.push(Math.round(h*16));
 }cache.set(k,a);return a;};
const seen=new Set(),queue=[[320,298,109*16]],key=(x,z,h)=>x+','+z+','+h;for(let q=0;q<queue.length;q++){
 const [x,z,h]=queue[q],k=key(x,z,h);if(seen.has(k)||!surfaces(x,z).includes(h))continue;seen.add(k);for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]])for(const nh of surfaces(nx,nz))if(Math.abs(nh-h)<=8&&!seen.has(key(nx,nz,nh)))queue.push([nx,nz,nh]);
}
const targets=[{name:'court',x:160,z:116,h:113},{name:'great hall',x:160,z:85,h:117},{name:'left service',x:133,z:82,h:117},{name:'right service',x:187,z:82,h:117},{name:'upper gallery',x:140,z:60,h:129},{name:'keep upper hall',x:160,z:48,h:153}];
for(const t of targets)if(!seen.has(key(t.x*2,t.z*2,t.h*16)))throw Error('Castle route unreachable: '+t.name);
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-governor-castle/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const castle={id:'07-castle',name:'Governor Castle',bounds:[103,30,217,124],buildingBounds:[118,40,202,99],buildingSize:[85,60],floorY:116,courtFloorY:112,highestY:171,heightAboveFloor:55,entrance:[160,117,94],towers,rooms:[{name:'great hall',bounds:[142,58,178,94]},{name:'left services',bounds:[118,42,142,92]},{name:'right services',bounds:[178,42,202,92]},{name:'keep',bounds:[149,43,171,65],floors:[116,128,140,152]}],verifiedRoutes:targets,stage:'castle architecture and usable rooms; no NPC integration'};
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.castle=castle;manifest.rooms=(manifest.rooms||[]).filter(r=>r.id!==castle.id);manifest.rooms.push(castle);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf)),zi=zones.findIndex(z=>z.id===castle.id);if(zi>=0)zones[zi]={...zones[zi],...castle};else zones.push(castle);fs.writeFileSync(zf,JSON.stringify(zones,null,2));fs.writeFileSync(rf,JSON.stringify({castle,changedBlocks:count,changedChunks:result.changed,reachableSurfaces:seen.size,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,reachableSurfaces:seen.size,castle});
