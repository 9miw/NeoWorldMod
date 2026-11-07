// Import only the scanned individual schematics; never copy source-world commands.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {readNbt}=require('./viewer/map-library.cjs');
const {encode,decode}=require('./nbt-edit.cjs');
const {structure}=require('./create_tree_library.cjs');
const tag=(type,value)=>({type,value}),I=v=>tag(3,v),B=v=>tag(1,v),L=v=>tag(4,BigInt(v)),T=v=>tag(8,v),C=v=>tag(10,v),A=(type,values)=>tag(9,{type,values});
const woods=['oak','spruce','birch','jungle','acacia','dark_oak'];
const dyes=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black'];
function convert(id,d){
 let name,properties;
 if(id===17||id===162){const wood=woods[(id===162?4:0)+(d&(id===162?1:3))];name=wood+((d&12)===12?'_wood':'_log');properties={axis:(d&12)===4?'x':(d&12)===8?'z':'y'};}
 else if(id===18||id===161){name=woods[(id===161?4:0)+(d&(id===161?1:3))]+'_leaves';properties={persistent:'true',distance:'7',waterlogged:'false'};}
 else if([35,95,159,160].includes(id)){name=dyes[d&15]+({35:'_wool',95:'_stained_glass',159:'_terracotta',160:'_stained_glass_pane'}[id]);if(id===160)properties={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'};}
 else if(id===5)name=woods[d&7]+'_planks';
 else if(id===125||id===126){name=woods[d&7]+'_slab';properties={type:id===125?'double':d&8?'top':'bottom',waterlogged:'false'};}
 else if([85,113,188,190,191].includes(id)){name=({85:'oak',113:'nether_brick',188:'spruce',190:'jungle',191:'dark_oak'}[id])+'_fence';properties={north:'false',south:'false',east:'false',west:'false',...(id===113?{}:{waterlogged:'false'})};}
 else if(id===164){name='dark_oak_stairs';properties={facing:['east','west','south','north'][d&3],half:d&4?'top':'bottom',shape:'straight',waterlogged:'false'};}
 else if(id===179)name=['red_sandstone','chiseled_red_sandstone','cut_red_sandstone'][d];
 else if(id===182){name='red_sandstone_slab';properties={type:d&8?'top':'bottom',waterlogged:'false'};}
 else if(id===127){name='cocoa';properties={facing:['south','west','north','east'][d&3],age:String(d>>2)};}
 else if(id===86){name='carved_pumpkin';properties={facing:['south','west','north','east'][d&3]};}
 else name=({4:'cobblestone',30:'cobweb',40:'red_mushroom',89:'glowstone',103:'melon'}[id]);
 if(!name||name.includes('undefined'))throw Error('Unmapped legacy block '+id+':'+d);
 return {name,...(properties?{properties}:{})};
}
function pack(values,bits){const per=Math.floor(64/bits),out=Array(Math.ceil(values.length/per)).fill(0n);values.forEach((v,i)=>{out[Math.floor(i/per)]|=BigInt(v)<<BigInt(i%per*bits);});return tag(12,out);}
function generate(){
 const root=path.resolve('downloads/exsilit-tree-repository'),dest=path.resolve('maps/tree_library');
 const catalog=JSON.parse(fs.readFileSync(path.join(root,'tree-shape-catalog.json'))).trees;
 const stage=fs.mkdtempSync(path.resolve('downloads/tree-import-stage-'));fs.mkdirSync(path.join(stage,'structures'));fs.mkdirSync(path.join(stage,'region'));
 const definitions=[{Name:'minecraft:air'}],ids=new Map(),chunks=new Map();
 function put(x,y,z,name,properties){const key=JSON.stringify([name,properties]);let id=ids.get(key);if(id===undefined){id=definitions.length;ids.set(key,id);definitions.push({Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});}const cx=x>>4,cz=z>>4,k=cx+','+cz;let c=chunks.get(k);if(!c)chunks.set(k,c={cx,cz,sections:new Map()});let sy=y>>4,s=c.sections.get(sy);if(!s)c.sections.set(sy,s=new Uint16Array(4096));s[(y&15)*256+(z&15)*16+(x&15)]=id;}
 const sorted=[...catalog].sort((a,b)=>a.size[1]-b.size[1]||a.id.localeCompare(b.id));let x=12,z=24,rowDepth=0;const trees=[];
 const font={t:['010','010','111','010','011'],0:['111','101','101','101','111'],1:['010','110','010','010','111'],2:['111','001','111','100','111'],3:['111','001','111','001','111'],4:['101','101','111','001','001'],5:['111','100','111','001','111'],6:['111','100','111','101','111'],7:['111','001','010','010','010'],8:['111','101','111','101','111'],9:['111','101','111','001','111']};
 for(const a of sorted){const [w,h,d]=a.size,pw=Math.max(23,w+8),pd=d+17;if(x+pw>1012){x=12;z+=rowDepth+6;rowDepth=0;}rowDepth=Math.max(rowDepth,pd);const ox=x+Math.floor((pw-w)/2),oz=z+4;
  for(let zz=z;zz<z+pd;zz++)for(let xx=x;xx<x+pw;xx++)put(xx,64,zz,xx===x||xx===x+pw-1||zz===z||zz===z+pd-1?'quartz_block':'grass_block',xx===x||xx===x+pw-1||zz===z||zz===z+pd-1?undefined:{snowy:'false'});
  const n=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(root,'contents',a.file)))),records=[];
  for(let i=0;i<n.Blocks.length;i++){const id=n.Blocks[i]+256*((n.AddBlocks?.[i>>1]||0)>>(i%2?4:0)&15);if(!id)continue;const bx=i%n.Width-a.occupiedBounds.min[0],by=Math.floor(i/(n.Width*n.Length))-a.occupiedBounds.min[1],bz=Math.floor(i/n.Width)%n.Length-a.occupiedBounds.min[2],state=convert(id,n.Data[i]);records.push({x:bx,y:by,z:bz,...state});}
  // Resolve fence/pane connections from actual neighboring blocks, also in reusable assets.
  const lookup=new Map(records.map(b=>[[b.x,b.y,b.z].join(','),b]));
  for(const b of records)if(b.name.endsWith('_fence')||b.name.endsWith('_pane'))for(const [side,dx,dz]of [['north',0,-1],['south',0,1],['east',1,0],['west',-1,0]]){const other=lookup.get([b.x+dx,b.y,b.z+dz].join(','));b.properties[side]=String(!!other&&(other.name===b.name||other.name.endsWith('_log')||other.name.endsWith('_wood')||other.name.endsWith('_planks')));}
  for(const b of records)put(ox+b.x,65+b.y,oz+b.z,b.name,b.properties);
  [...a.id].forEach((letter,i)=>font[letter].forEach((row,dz)=>[...row].forEach((v,dx)=>{if(v==='1')put(x+4+i*4+dx,64,z+pd-7+dz,'black_concrete');})));
  const tree={id:a.id,name:a.id+' · '+a.shape.category,size:a.size,min:[0,0,0],blocks:records};fs.writeFileSync(path.join(stage,'structures',a.id+'.nbt'),structure(tree));
  trees.push({id:a.id,name:tree.name,size:a.size,base:[ox,65,oz],anchor:[0,0,0],plotBounds:[x,z,x+pw-1,z+pd-1],copyBounds:[ox,65,oz,ox+w-1,64+h,oz+d-1],blockCount:records.length,structure:'structures/'+a.id+'.nbt',source:a.file});x+=pw+6;
 }
 const width=1024,depth=Math.ceil((z+rowDepth+12)/16)*16;
 for(let zz=0;zz<depth;zz++)for(let xx=0;xx<width;xx++){const c=chunks.get((xx>>4)+','+(zz>>4)),s=c?.sections.get(4),idx=(zz&15)*16+(xx&15);if(!s?.[idx])put(xx,64,zz,'smooth_stone');}
 const regions=new Map();
 for(const c of chunks.values()){
  const sections=[];for(let sy=-4;sy<20;sy++){let data=c.sections.get(sy),palette=[],index=new Map(),values=[];for(let i=0;i<4096;i++){let id=data?.[i]||0;if(!index.has(id)){index.set(id,palette.length);let d=definitions[id];palette.push({Name:T(d.Name),...(d.Properties?{Properties:C(Object.fromEntries(Object.entries(d.Properties).map(([k,v])=>[k,T(v)])))}:{})});}values.push(index.get(id));}const states={palette:A(10,palette)};if(palette.length>1)states.data=pack(values,Math.max(4,Math.ceil(Math.log2(palette.length))));sections.push({Y:B(sy),block_states:C(states),biomes:C({palette:A(8,['minecraft:the_void'])})});}
  const heights=[];for(let zz=0;zz<16;zz++)for(let xx=0;xx<16;xx++){let y=319;while(y>=-64&&!(c.sections.get(y>>4)?.[(y&15)*256+zz*16+xx]))y--;heights.push(y+65);}
  const n={DataVersion:I(3955),xPos:I(c.cx),zPos:I(c.cz),yPos:I(-4),Status:T('minecraft:full'),LastUpdate:L(0),InhabitedTime:L(0),isLightOn:B(0),sections:A(10,sections),block_entities:A(10,[]),block_ticks:A(10,[]),fluid_ticks:A(10,[]),PostProcessing:A(9,Array.from({length:24},()=>({type:2,values:[]}))),structures:C({starts:C({}),References:C({})}),Heightmaps:C({WORLD_SURFACE:pack(heights,9)})};
  const key=(c.cx>>5)+','+(c.cz>>5);if(!regions.has(key))regions.set(key,[]);regions.get(key).push({slot:(c.cx&31)+(c.cz&31)*32,bytes:zlib.deflateSync(encode({value:n}))});
 }
 for(const [key,list]of regions){let sector=2,header=Buffer.alloc(8192),bodies=[];for(const c of list){const body=Buffer.alloc(Math.ceil((c.bytes.length+5)/4096)*4096);if(body.length/4096>255)throw Error('Chunk exceeds Anvil limit');body.writeUInt32BE(c.bytes.length+1);body[4]=2;c.bytes.copy(body,5);header.writeUInt32BE((sector<<8)|body.length/4096,c.slot*4);header.writeUInt32BE(Math.floor(Date.now()/1000),4096+c.slot*4);sector+=body.length/4096;bodies.push(body);}fs.writeFileSync(path.join(stage,'region','r.'+key.replace(',','.')+'.mca'),Buffer.concat([header,...bodies]));}
 const level=decode(zlib.gunzipSync(fs.readFileSync(path.join(dest,'level.dat')))),l=level.value.Data.value;Object.assign(l,{SpawnX:I(8),SpawnY:I(65),SpawnZ:I(16),BorderCenterX:tag(6,width/2),BorderCenterZ:tag(6,depth/2),BorderSize:tag(6,Math.max(width,depth)+32),BorderSizeLerpTarget:tag(6,Math.max(width,depth)+32)});fs.writeFileSync(path.join(stage,'level.dat'),zlib.gzipSync(encode(level)));
 const manifest={format:'Minecraft Java 1.21.1',dataVersion:3955,name:'NeoWorld · Tree Library',description:'exsilit · 370 trees · เรียงเล็กไปใหญ่ · รหัส t001–t370',footprint:[width,depth],fullWorld:true,floorY:64,spawn:[8,65,16],chunks:chunks.size,rooms:trees.map(t=>({name:t.id,center:t.base,bounds:t.plotBounds})),trees};
 fs.writeFileSync(path.join(stage,'map-manifest.json'),JSON.stringify(manifest,null,2));fs.writeFileSync(path.join(stage,'catalog.json'),JSON.stringify(trees,null,2));fs.writeFileSync(path.join(stage,'zones.json'),JSON.stringify(trees.map(t=>({id:t.id,bounds:t.plotBounds,center:t.base})),null,2));
 fs.writeFileSync(path.join(stage,'MAP.md'),'# คลังต้นไม้ NeoWorld\n\nใช้ maps/tree_library เดิม แทนต้นไม้เดิม 14 แบบด้วยชุด exsilit 370 แบบ ตามที่ผู้ใช้ยืนยัน 2026-10-23\n\nพื้นที่ '+width+' × '+depth+' บล็อก พื้น Y64 เกิด 8 65 16 แถวเริ่ม Z24 ไปทาง +Z เรียงตามความสูงจากเล็กไปใหญ่ ป้ายรหัสอยู่บนพื้นด้าน +Z ของแต่ละแปลง\n\nแปลงเผื่อรอบต้นอย่างน้อย 4 บล็อก มีทางเดินเชื่อม ใบไม้ persistent=true ป้องกันสลาย ไม่มี command block หรือคำสั่งจากโลกต้นฉบับ\n\nแปลง legacy ID/metadata เป็น block state 1.21.1; ท่อนไม้ bark ใช้ wood บล็อกสีและของตกแต่งคงชนิดเดิม ต้นที่ไม่มีใบไม่ได้เติมใบใหม่ ไฟล์ structures/t001.nbt–t370.nbt มีเฉพาะบล็อกต้น ไม่รวมพื้นและป้าย จุดวางเป็นมุมต่ำสุดของ copyBounds ไม่ใช่กึ่งกลางลำต้น\n\nแหล่งที่มา: https://www.planetminecraft.com/project/tree-bundle-370-custom-trees-download/ โดย exsilit\n\nพิกัดแปลง ขอบเขตคัดลอก ขนาด และไฟล์แยกต้นอยู่ใน catalog.json; รายละเอียดวัสดุและรูปทรงอยู่ downloads/exsilit-tree-repository/TREE-CATALOG.md\n\nยังไม่ทดสอบในเกมจริง\n');
 // Keep the map folder identity; archive its previous contents before replacing generated data.
 const backup=path.resolve('archive/tree-library-before-exsilit-'+Date.now());fs.mkdirSync(backup,{recursive:true});fs.cpSync(dest,backup,{recursive:true});
 for(const name of ['region','structures','level.dat','map-manifest.json','catalog.json','zones.json','MAP.md','README.md','top-down.png','concept.png']){const target=path.resolve(dest,name);if(path.dirname(target)!==dest)throw Error('Unsafe target');if(fs.existsSync(target))fs.rmSync(target,{recursive:true});}
 fs.cpSync(stage,dest,{recursive:true});console.log(JSON.stringify({trees:trees.length,footprint:[width,depth],chunks:chunks.size,regions:regions.size,backup,stage}));
}
module.exports={convert,generate};if(require.main===module)generate();
