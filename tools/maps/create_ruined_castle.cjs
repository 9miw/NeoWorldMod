// Deterministic, dependency-free Minecraft Java 1.21.1 map authoring. Never overwrites output.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const COLOURS = {air:'#18232b',stone:'#77797b',bedrock:'#454545',stone_bricks:'#8c8d85',mossy_stone_bricks:'#6b7b56',cracked_stone_bricks:'#777b75',polished_deepslate:'#41434b',chiseled_stone_bricks:'#777969',cobbled_deepslate:'#454751',dark_oak_planks:'#654b30',dark_oak_slab:'#6c5138',bookshelf:'#8b6439',gold_block:'#f2cb4a',chest:'#bd863f',barrel:'#a67d49',furnace:'#555b60',anvil:'#4b5056',iron_bars:'#9da4a6',red_carpet:'#a43d39',blue_carpet:'#345c9b',yellow_terracotta:'#ddaf55',orange_stained_glass:'#f69031',blue_stained_glass:'#4180a6',purple_stained_glass:'#8860a1',lantern:'#ffcf76',shroomlight:'#f4ac52',polished_andesite:'#a4a79f',polished_diorite:'#ced0c5',mossy_cobblestone:'#6e805b'};
function buildMap(options = {}) {
const S = 150, LOW = 58, HIGH = 96, FLOOR = 64;
const blocks = new Uint16Array(S * S * (HIGH - LOW));
const definitions = [{ Name: 'minecraft:air' }], ids = new Map([['air', 0]]);
const tiles = new Map();
function block(name, properties) {
  const key = name + (properties ? JSON.stringify(properties) : '');
  if (!ids.has(key)) { ids.set(key, definitions.length); definitions.push({ Name: 'minecraft:' + name, ...(properties ? { Properties: properties } : {}) }); }
  return ids.get(key);
}
function at(x,y,z) { return (y - LOW) * S * S + z * S + x; }
function get(x,y,z) { return x>=0 && z>=0 && x<S && z<S && y>=LOW && y<HIGH ? blocks[at(x,y,z)] : 0; }
function put(x,y,z,name,properties) {
  if (x<0 || z<0 || x>=S || z>=S || y<LOW || y>=HIGH) return;
  blocks[at(x,y,z)] = block(name, properties);
  tiles.delete(`${x},${y},${z}`);
  const entity = {chest:'chest', barrel:'barrel', furnace:'furnace'}[name];
  if (entity) tiles.set(`${x},${y},${z}`, {id:'minecraft:'+entity,x,y,z});
}
function fill(x1,y1,z1,x2,y2,z2,name,props) { for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,name,props); }
function hash(x,z,salt=0) { let n=Math.imul(x+salt*17,374761393)^Math.imul(z+salt*31,668265263);n=Math.imul(n^(n>>>13),1274126177);return (n^(n>>>16))>>>0; }
function stone(x,y,z) { const h=hash(x,z,y)%100;put(x,y,z,h<18?'mossy_stone_bricks':h<32?'cracked_stone_bricks':'stone_bricks'); }
function pillar(x,z,h=9) { fill(x-1,65,z-1,x+1,65+h,z+1,'polished_deepslate');fill(x-2,65+h,z-2,x+2,65+h,z+2,'chiseled_stone_bricks'); }
function lamp(x,z,y=65) { put(x,y,z,'chiseled_stone_bricks');put(x,y+1,z,'lantern',{hanging:'false',waterlogged:'false'}); }
function arch(cx,cz,axis,width=9,height=8) {
  const a=Math.floor(width/2);
  for(const d of [-a,a])for(let y=65;y<=65+height;y++)put(cx+(axis==='x'?d:0),y,cz+(axis==='z'?d:0),'polished_deepslate');
  for(let d=-a;d<=a;d++)put(cx+(axis==='x'?d:0),65+height,cz+(axis==='z'?d:0),'stone_bricks');
}
// Structural platform: its entire footprint, including walls and entrance, is exactly 150 x 150.
fill(0,59,0,149,59,149,'bedrock');fill(0,60,0,149,62,149,'stone');fill(0,63,0,149,63,149,'cobbled_deepslate');
for(let z=0;z<S;z++)for(let x=0;x<S;x++)stone(x,64,z);
for(let y=65;y<=75;y++)for(let t=0;t<S;t++)for(let d=0;d<2;d++) {
  stone(t,y,d);stone(t,y,149-d);stone(d,y,t);stone(149-d,y,t);
}
for(let t=2;t<148;t+=4) {
  fill(t,76,0,t+1,77,1,'stone_bricks');fill(t,76,148,t+1,77,149,'stone_bricks');
  fill(0,76,t,1,77,t+1,'stone_bricks');fill(148,76,t,149,77,t+1,'stone_bricks');
}
fill(70,65,147,80,74,149,'air');arch(75,148,'x',13,11);
// Floor mosaic and open-roof room shells. Door clearances remain seven blocks wide.
const rooms = [
  {name:'Treasure 1',x:5,z:5,kind:'treasure'}, {name:'Room 3 - chapel',x:55,z:5,kind:'chapel'}, {name:'Treasure 2 - library',x:105,z:5,kind:'library'},
  {name:'Room 2 - prison',x:5,z:55,kind:'prison'}, {name:'Boss arena',x:55,z:55,kind:'boss'}, {name:'Room 4 - forge',x:105,z:55,kind:'forge'},
  {name:'Room 1 - barracks',x:5,z:105,kind:'barracks'}, {name:'Spawn',x:55,z:105,kind:'spawn'}, {name:'Room 5 - knights hall',x:105,z:105,kind:'knights'}
];
for(const r of rooms) {
  for(let z=r.z+1;z<r.z+39;z++)for(let x=r.x+1;x<r.x+39;x++) {
    const edge=Math.min(x-r.x,z-r.z,r.x+39-x,r.z+39-z);
    put(x,64,z,edge===1?'polished_deepslate':hash(x,z)%13===0?'mossy_stone_bricks':r.kind==='knights'?'polished_andesite':'stone_bricks');
  }
  for(let y=65;y<=71;y++)for(let t=0;t<40;t++)for(const [x,z] of [[r.x+t,r.z],[r.x+t,r.z+39],[r.x,r.z+t],[r.x+39,r.z+t]]) {
    if(y===71 && hash(x,z)%7===0)continue;stone(x,y,z);
  }
  const dirs=r.kind==='boss'?['south']:['north','south','east','west'];
  for(const dir of dirs) {
    const horizontal=dir==='north'||dir==='south';
    const cx=horizontal?r.x+20:dir==='east'?r.x+39:r.x;
    const cz=horizontal?(dir==='south'?r.z+39:r.z):r.z+20;
    fill(horizontal?cx-3:cx,65,horizontal?cz:cz-3,horizontal?cx+3:cx,69,horizontal?cz:cz+3,'air');
    arch(cx,cz,horizontal?'x':'z',9,7);
  }
  if(options.decorations !== false) for(const [dx,dz] of [[4,4],[35,4],[4,35],[35,35],[4,20],[35,20],[20,4],[20,35]]) lamp(r.x+dx,r.z+dz);
}
if(options.decorations !== false) {
// Treasury: empty decorative chests only; no generated rewards or loot tables.
for(const [x,z] of [[11,12],[17,12],[30,12],[37,12],[11,35],[37,35],[12,25],[36,25]]) {
  put(x,65,z,'chest',{facing:'south',type:'single',waterlogged:'false'});
  if(hash(x,z)%2)fill(x+1,65,z+1,x+2,66,z+2,'gold_block');
}
fill(23,65,21,27,65,29,'red_carpet');fill(22,65,17,28,65,18,'polished_blackstone_bricks');
// Chapel: axial nave, benches, columns and stained glass.
for(const x of [62,87])for(const z of [14,25,36])pillar(x,z,10);
fill(72,65,10,78,66,12,'polished_diorite');put(75,67,11,'lantern',{hanging:'false',waterlogged:'false'});
fill(73,65,15,77,65,39,'red_carpet');
for(const x of [63,82])for(const z of [18,28,36])fill(x,65,z,x+4,65,z,'dark_oak_planks');
for(let y=68;y<=77;y++)for(let x=71;x<=79;x++)put(x,y,5,((x+y)%3)?'blue_stained_glass':'purple_stained_glass');
// Ancient library.
for(const x of [110,139])for(let z=10;z<=39;z++)if(Math.abs(z-25)>4)fill(x,65,z,x,70,z,'bookshelf');
for(const z of [11,17,33,39])for(const x of [114,130])fill(x,65,z,x+6,68,z,'bookshelf');
fill(121,65,23,129,65,27,'dark_oak_planks');put(125,66,25,'lantern',{hanging:'false',waterlogged:'false'});
for(const [x,z] of [[115,23],[136,23]])put(x,65,z,'chest',{facing:'south',type:'single',waterlogged:'false'});
// Prison cells: keep a wide north-south aisle through the room.
for(const x of [10,30])for(const z of [60,80]) {
  fill(x,65,z,x+9,68,z,'iron_bars');fill(x,65,z+10,x+9,68,z+10,'iron_bars');
  fill(x,65,z,x,68,z+10,'iron_bars');fill(x+9,65,z,x+9,68,z+10,'iron_bars');
  fill(x+3,65,z,x+5,67,z,'air');fill(x+2,65,z+7,x+6,65,z+8,'dark_oak_slab',{type:'bottom',waterlogged:'false'});
}
// Open boss floor: ring and heraldry are decorative floor blocks, not a combat mechanism.
for(let z=58;z<=91;z++)for(let x=58;x<=91;x++) {
  const d=Math.hypot(x-74.5,z-74.5);
  if((d>11&&d<13)||(d>3&&d<5))put(x,64,z,'polished_deepslate');
  if(Math.abs(x-75)<1&&Math.abs(z-75)<1)put(x,64,z,'chiseled_stone_bricks');
}
for(const [x,z] of [[61,61],[88,61],[61,88],[88,88]])pillar(x,z,8);
arch(75,94,'x',13,10);fill(69,65,94,81,69,94,'air');
fill(70,73,94,80,74,94,'iron_bars');
// Forge: glowing glass channels imitate lava without flowing liquids or damage mechanics.
for(const z of [61,67,83,89])for(const x of [110,113,137,140])put(x,65,z,'furnace',{facing:x<125?'east':'west',lit:'false'});
for(const [x,z] of [[113,74],[135,74],[118,86],[132,63]])put(x,65,z,'anvil',{facing:'north'});
for(const x of [117,133])for(let z=62;z<=88;z++){put(x,63,z,'shroomlight');put(x,64,z,'orange_stained_glass');}
for(const x of [110,139])for(const z of [72,78])put(x,65,z,'barrel',{facing:'up',open:'false'});
// Barracks: stacked storage, benches and scattered broken masonry away from door axes.
for(const x of [11,36])for(const z of [112,119,132,139]) {
  put(x,65,z,'barrel',{facing:'up',open:'false'});fill(x+1,65,z,x+3,65,z,'dark_oak_planks');
}
for(let x=9;x<41;x++)for(let z=109;z<141;z++)if(Math.abs(x-25)>6&&Math.abs(z-125)>6&&hash(x,z,7)%43===0)put(x,65,z,'mossy_cobblestone');
// Knights hall.
for(const x of [112,137])for(const z of [113,125,137])pillar(x,z,10);
fill(122,65,110,128,65,140,'blue_carpet');
fill(117,65,110,120,66,112,'dark_oak_planks');fill(130,65,110,133,66,112,'dark_oak_planks');
// Spawn plaza: keep the exact spawn and its approach free of obstacles.
for(let z=112;z<=140;z++)for(let x=62;x<=87;x++)if(hash(x,z,22)%31===0)put(x,64,z,'mossy_stone_bricks');
fill(71,64,117,79,64,133,'polished_andesite');
for(const [x,z] of [[63,114],[86,114],[63,136],[86,136]])lamp(x,z);
// Gold floor arrows show a coherent route around the eight perimeter rooms, then north to the boss.
function arrow(cx,cz,dx,dz){for(let n=-2;n<=2;n++)put(cx+dx*n,64,cz+dz*n,'yellow_terracotta');for(const side of [-1,1])for(let n=0;n<3;n++)put(cx+dx*(2-n)-dz*side*n,64,cz+dz*(2-n)+dx*side*n,'yellow_terracotta');}
for(const a of [[50,125,-1,0],[25,100,0,-1],[25,50,0,-1],[50,25,1,0],[100,25,1,0],[125,50,0,1],[125,100,0,1],[100,125,-1,0],[75,100,0,-1]])arrow(...a);
}
// Corner towers, battlements and sparse ivy.
for(const [cx,cz] of [[6,6],[143,6],[6,143],[143,143]]) {
  for(let y=65;y<=84;y++)for(let dx=-5;dx<=5;dx++)for(let dz=-5;dz<=5;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))===5)stone(cx+dx,y,cz+dz);
  fill(cx-4,81,cz-4,cx+4,81,cz+4,'dark_oak_planks');
  for(let t=-5;t<=5;t+=3)for(const [dx,dz] of [[t,-5],[t,5],[-5,t],[5,t]])fill(cx+dx,85,cz+dz,cx+dx,86,cz+dz,'stone_bricks');
}
if(options.decorations !== false) for(let t=12;t<138;t+=11){lamp(3,t,70);lamp(146,t,70);lamp(t,3,70);if(Math.abs(t-75)>10)lamp(t,146,70);}
// Connect bars deterministically rather than depending on later neighbor updates.
for(let y=65;y<80;y++)for(let z=0;z<S;z++)for(let x=0;x<S;x++)if(definitions[get(x,y,z)].Name==='minecraft:iron_bars') {
  const p={waterlogged:'false'};for(const [name,dx,dz] of [['north',0,-1],['south',0,1],['east',1,0],['west',-1,0]])p[name]=get(x+dx,y,z+dz)?'true':'false';put(x,y,z,'iron_bars',p);
}
// Check player-footprint routes on the actual placed blocks (includes carpet steps).
function walkHeight(x,z){for(let y=66;y>=64;y--){const n=definitions[get(x,y,z)].Name;if(n==='minecraft:air'||n.endsWith('_carpet'))continue;const above=definitions[get(x,y+1,z)].Name;if((above==='minecraft:air'||above.endsWith('_carpet'))&&!get(x,y+2,z))return y+1;}return null;}
const visited=new Set(), queue=[[75,127]];visited.add('75,127');
for(let i=0;i<queue.length;i++){const [x,z]=queue[i], y=walkHeight(x,z);for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz,k=`${nx},${nz}`;if(nx<2||nz<2||nx>=148||nz>=148||visited.has(k))continue;const h=walkHeight(nx,nz);if(h!==null&&Math.abs(h-y)<=1){visited.add(k);queue.push([nx,nz]);}}}
for(const r of rooms){let reachable=false;for(let dx=-4;dx<=4;dx++)for(let dz=-4;dz<=4;dz++)if(visited.has(`${r.x+20+dx},${r.z+20+dz}`))reachable=true;if(!reachable)throw Error('Disconnected room '+r.name);}
if(get(75,65,127)||get(75,66,127)||!get(75,64,127))throw Error('Unsafe spawn');
return { S, LOW, HIGH, FLOOR, definitions, blocks, tiles: [...tiles.values()], rooms, reachableFloorCells: visited.size, decorations: options.decorations !== false };
}
function exportMap(model, destination) {
const out = path.resolve(destination);
if(fs.existsSync(out)) throw Error('Output already exists: ' + out);
const { S, LOW, HIGH, FLOOR, definitions, blocks, rooms } = model;
const spawn=model.spawn||[75,65,127];
const tiles = new Map(model.tiles.map(e => [e.x+','+e.y+','+e.z,e]));
const visited = { size: model.reachableFloorCells };
function get(x,y,z){ return x>=0&&z>=0&&x<S&&z<S&&y>=LOW&&y<HIGH ? blocks[(y-LOW)*S*S+z*S+x] : 0; }
// NBT encoding; every generated state is named, never a registry-dependent numeric ID.
const B=v=>[1,v], I=v=>[3,v], L=v=>[4,BigInt(v)], F=v=>[5,v], D=v=>[6,v], T=v=>[8,v], A=(type,values)=>[9,{type,values}], C=v=>[10,v], LA=v=>[12,v];
function str(s){const b=Buffer.from(s,'utf8'),n=Buffer.alloc(2);n.writeUInt16BE(b.length);return Buffer.concat([n,b]);}
function payload(type,value){let b;switch(type){case 1:return Buffer.from([value&255]);case 3:b=Buffer.alloc(4);b.writeInt32BE(value);return b;case 4:b=Buffer.alloc(8);b.writeBigInt64BE(BigInt.asIntN(64,value));return b;case 5:b=Buffer.alloc(4);b.writeFloatBE(value);return b;case 6:b=Buffer.alloc(8);b.writeDoubleBE(value);return b;case 8:return str(value);case 9:b=Buffer.alloc(5);b[0]=value.type;b.writeInt32BE(value.values.length,1);return Buffer.concat([b,...value.values.map(v=>payload(value.type,v))]);case 10:return Buffer.concat([...Object.entries(value).map(([k,[t,v]])=>Buffer.concat([Buffer.from([t]),str(k),payload(t,v)])),Buffer.from([0])]);case 12:b=Buffer.alloc(4+value.length*8);b.writeInt32BE(value.length);value.forEach((v,i)=>b.writeBigUInt64BE(BigInt.asUintN(64,BigInt(v)),4+i*8));return b;default:throw Error('NBT type '+type);}}
function nbt(compound){return Buffer.concat([Buffer.from([10,0,0]),payload(10,compound)]);}
function packed(values,bits){const n=Math.floor(64/bits),data=new Array(Math.ceil(values.length/n)).fill(0n);values.forEach((v,i)=>{data[Math.floor(i/n)]|=BigInt(v)<<BigInt((i%n)*bits);});return data;}
function paletteEntry(d){return {Name:T(d.Name),...(d.Properties?{Properties:C(Object.fromEntries(Object.entries(d.Properties).map(([k,v])=>[k,T(v)])))}:{})};}
const chunks=[];
for(let cz=0;cz<Math.ceil(S/16);cz++)for(let cx=0;cx<Math.ceil(S/16);cx++){
  const sections=[];
  for(let sy=-4;sy<20;sy++){
    const palette=[],lookup=new Map(),values=[];
    for(let y=0;y<16;y++)for(let z=0;z<16;z++)for(let x=0;x<16;x++){
      const id=get(cx*16+x,sy*16+y,cz*16+z);if(!lookup.has(id)){lookup.set(id,palette.length);palette.push(paletteEntry(definitions[id]));}values.push(lookup.get(id));
    }
    const states={palette:A(10,palette)};if(palette.length>1)states.data=LA(packed(values,Math.max(4,Math.ceil(Math.log2(palette.length)))));
    sections.push({Y:B(sy),block_states:C(states),biomes:C({palette:A(8,['minecraft:the_void'])})});
  }
  const heights=[];for(let z=0;z<16;z++)for(let x=0;x<16;x++){let y=HIGH-1;while(y>=LOW&&!get(cx*16+x,y,cz*16+z))y--;heights.push(y<LOW?0:y+65);}
  const entities=[...tiles.values()].filter(e=>(e.x>>4)===cx&&(e.z>>4)===cz).map(e=>({id:T(e.id),x:I(e.x),y:I(e.y),z:I(e.z),Items:A(10,[]),...(e.id==='minecraft:furnace'?{BurnTime:[2,0],CookTime:[2,0],CookTimeTotal:[2,200]}:{})}));
  // Short tags are added explicitly below to keep container NBT vanilla-compatible.
  const data={DataVersion:I(3955),xPos:I(cx),zPos:I(cz),yPos:I(-4),Status:T('minecraft:full'),LastUpdate:L(0),InhabitedTime:L(0),isLightOn:B(0),sections:A(10,sections),block_entities:A(10,entities),block_ticks:A(10,[]),fluid_ticks:A(10,[]),PostProcessing:A(9,Array.from({length:24},()=>({type:2,values:[]}))),structures:C({starts:C({}),References:C({})}),Heightmaps:C(Object.fromEntries(['WORLD_SURFACE','OCEAN_FLOOR','MOTION_BLOCKING','MOTION_BLOCKING_NO_LEAVES'].map(k=>[k,LA(packed(heights,9))])))};
  chunks.push({x:cx,z:cz,data});
}
// Extend the encoder for short payloads used by containers/post-processing.
const encodePayload=payload;
payload=function(type,value){if(type===2){const b=Buffer.alloc(2);b.writeInt16BE(value);return b;}return encodePayload(type,value);};
fs.mkdirSync(path.join(out,'region'),{recursive:true});
const header=Buffer.alloc(8192),bodies=[];let sector=2;
for(const c of chunks){const compressed=zlib.deflateSync(nbt(c.data)),body=Buffer.alloc(Math.ceil((compressed.length+5)/4096)*4096);body.writeUInt32BE(compressed.length+1);body[4]=2;compressed.copy(body,5);const slot=c.x+c.z*32;header.writeUInt32BE((sector<<8)|(body.length/4096),slot*4);header.writeUInt32BE(Math.floor(Date.now()/1000),4096+slot*4);sector+=body.length/4096;bodies.push(body);}
fs.writeFileSync(path.join(out,'region/r.0.0.mca'),Buffer.concat([header,...bodies]));
const stem={type:T('minecraft:overworld'),generator:C({type:T('minecraft:flat'),settings:C({biome:T('minecraft:the_void'),layers:A(10,[]),lakes:B(0),features:B(0),structure_overrides:A(8,[])})})};
const level={DataVersion:I(3955),Version:C({Id:I(3955),Name:T('1.21.1'),Series:T('main'),Snapshot:B(0)}),LevelName:T(model.name||(model.composed?'NeoWorld - Map Studio':'NeoWorld - Ruined Castle v1')),GameType:I(1),Difficulty:B(0),hardcore:B(0),allowCommands:B(1),initialized:B(1),SpawnX:I(spawn[0]),SpawnY:I(spawn[1]),SpawnZ:I(spawn[2]),SpawnAngle:F(180),Time:L(0),DayTime:L(6000),LastPlayed:L(Date.now()),clearWeatherTime:I(0),rainTime:I(0),raining:B(0),thunderTime:I(0),thundering:B(0),GameRules:C({doMobSpawning:T('false'),doDaylightCycle:T('false'),doWeatherCycle:T('false'),doFireTick:T('false')}),DataPacks:C({Enabled:A(8,['vanilla']),Disabled:A(8,[])}),enabled_features:A(8,['minecraft:vanilla']),WorldGenSettings:C({seed:L(0),generate_features:B(0),bonus_chest:B(0),dimensions:C({'minecraft:overworld':C(stem)})}),BorderCenterX:D(S/2),BorderCenterZ:D(S/2),BorderSize:D(S),BorderSizeLerpTarget:D(S),BorderSizeLerpTime:L(0),BorderSafeZone:D(5),BorderDamagePerBlock:D(0.2),BorderWarningBlocks:I(5),BorderWarningTime:I(15)};
fs.writeFileSync(path.join(out,'level.dat'),zlib.gzipSync(nbt({Data:C(level)})));
// Render a deterministic top-down preview from the actual highest placed blocks, not from concept art.
const colours={...COLOURS,...model.colours};
const scale=S>150?4:8,W=S*scale,pixels=Buffer.alloc((W*4+1)*W);
for(let z=0;z<S;z++)for(let x=0;x<S;x++){let y=HIGH-1;while(y>=LOW&&!get(x,y,z))y--;const name=definitions[get(x,y,z)].Name.slice(10);const hex=colours[name]||'#8f8d83';const rgb=[1,3,5].map(p=>parseInt(hex.slice(p,p+2),16));const shade=y>71?0.75:y>65?0.9:1;for(let pz=0;pz<scale;pz++)for(let px=0;px<scale;px++){const o=(z*scale+pz)*(W*4+1)+1+(x*scale+px)*4;const edge=px===0||pz===0?0.9:1;for(let k=0;k<3;k++)pixels[o+k]=Math.round(rgb[k]*shade*edge);pixels[o+3]=255;}}
function crc(buffer){let c=0xffffffff;for(const b of buffer){c^=b;for(let j=0;j<8;j++)c=(c>>>1)^(c&1?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function pngChunk(type,data){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([n,t,data,c]);}
const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(W);ihdr.writeUInt32BE(W,4);ihdr[8]=8;ihdr[9]=6;
fs.writeFileSync(path.join(out,'top-down.png'),Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),pngChunk('IHDR',ihdr),pngChunk('IDAT',zlib.deflateSync(pixels)),pngChunk('IEND',Buffer.alloc(0))]));
const manifest={format:'Minecraft Java 1.21.1',dataVersion:3955,footprint:[S,S],chunks:chunks.length,spawn,floorY:FLOOR,rooms:rooms.map(r=>({name:r.name,bounds:r.bounds||[r.x,r.z,r.x+39,r.z+39],center:r.center||[r.x+20,65,r.z+20]})),reachableFloorCells:visited.size,blockStates:definitions,blockEntities:tiles.size,gameplay:'No mobs, loot tables, command blocks or scripted gates. Forge channels are glass, not lava.'};
fs.writeFileSync(path.join(out,'map-manifest.json'),JSON.stringify(manifest,null,2));
fs.writeFileSync(path.join(out,'README.md'),model.composed ? '# Map Studio composition\n\nMinecraft Java 1.21.1. Size: '+S+' x '+S+'. Spawn: '+spawn.join(' ')+'.\n\nSee composition.json for placement settings. Preview-only visibility does not remove blocks. No mobs or rewards. Copy this folder to world-imports and use /neoworld world admin import.\n' : '# Ruined Castle v1\n\nMinecraft Java 1.21.1, 150 x 150 blocks, 100 chunks. Spawn: `75 65 127`.\n\nCopy this folder to `world-imports/ruined_castle_v1`, then use:\n\n```text\n/neoworld world import check ruined_castle_v1\n/neoworld world import add ruined_castle_v1 neoworld:ruined_castle_v1\n/neoworld world tp @s neoworld:ruined_castle_v1 75 65 127\n```\n\nFive combat rooms, two empty treasure rooms, central boss arena and safe spawn plaza. Architecture-only: no mobs, rewards, functional locked gates or scripts. Roofs are open. Channels in the forge use orange glass over shroomlight. Actual block preview: top-down.png. Source generator: tools/maps/create_ruined_castle.cjs.\n\nThe standalone save disables natural mob spawning and uses a void generator. The importer does not transfer gamerules or borders: configure those on your server if needed. Do not reset this imported map expecting it to restore the castle: no template is stored.\n'.replaceAll('/neoworld world ', '/neoworld world admin ').replaceAll('ruined_castle_v1', path.basename(out)));
return {output:out,chunks:chunks.length,spawn:manifest.spawn,rooms:rooms.length,blockEntities:tiles.size,reachableFloorCells:visited.size};
}
module.exports = { buildMap, exportMap, colours: COLOURS };
if(require.main === module) console.log(JSON.stringify(exportMap(buildMap(), process.argv[2] || 'maps/ruined_castle_v1')));
