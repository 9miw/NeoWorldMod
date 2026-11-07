const fs=require('node:fs');
const path=require('node:path');
const zlib=require('node:zlib');
const {colours}=require('../create_ruined_castle.cjs');

// Read saved maps without generating, exporting or changing any files.
function readNbt(bytes){
  let offset=0,nodes=0;
  function take(n){if(n<0||offset+n>bytes.length)throw Error('Incomplete NBT');const start=offset;offset+=n;return start;}
  const byte=()=>bytes.readUInt8(take(1)),int=()=>bytes.readInt32BE(take(4));
  const str=()=>{const n=bytes.readUInt16BE(take(2));const start=take(n);return bytes.toString('utf8',start,start+n);};
  function length(){const n=int();if(n<0||n>4000000)throw Error('NBT array too large');return n;}
  function value(type,depth=0){
    if(depth>64||++nodes>4000000)throw Error('NBT too complex');
    switch(type){
      case 1:return bytes.readInt8(take(1));case 2:return bytes.readInt16BE(take(2));case 3:return int();
      case 4:return bytes.readBigInt64BE(take(8));case 5:return bytes.readFloatBE(take(4));case 6:return bytes.readDoubleBE(take(8));
      case 7:{const n=length(),start=take(n);return bytes.subarray(start,start+n);}
      case 8:return str();case 9:{const t=byte(),n=length();return Array.from({length:n},()=>value(t,depth+1));}
      case 10:{const result=Object.create(null);let t;while((t=byte())!==0){const name=str();result[name]=value(t,depth+1);}return result;}
      case 11:{const n=length();return Array.from({length:n},int);}
      case 12:{const n=length();return Array.from({length:n},()=>bytes.readBigInt64BE(take(8)));}
      default:throw Error('Unsupported NBT type '+type);
    }
  }
  if(byte()!==10)throw Error('NBT root must be a compound');str();return value(10);
}
function chunkData(root){
  const chunk=root.Level||root,version=root.DataVersion||chunk.DataVersion||3955;
  return {...chunk,block_entities:chunk.block_entities||chunk.TileEntities||[],sections:(chunk.sections||chunk.Sections||[]).map(section=>({...section,block_states:section.block_states||(section.Palette?{palette:section.Palette,data:section.BlockStates}:null),dense:version<2529}))};
}
function paletteIndex(data,paletteLength,i,dense=false){
  if(paletteLength===1)return 0;const bits=Math.max(4,Math.ceil(Math.log2(paletteLength))),mask=(1n<<BigInt(bits))-1n;
  if(!dense){const per=Math.floor(64/bits);return Number((BigInt.asUintN(64,data[Math.floor(i/per)])>>BigInt((i%per)*bits))&mask);}
  const offset=i*bits,word=Math.floor(offset/64),shift=offset%64;let n=BigInt.asUintN(64,data[word])>>BigInt(shift);if(shift+bits>64)n|=BigInt.asUintN(64,data[word+1])<<BigInt(64-shift);return Number(n&mask);
}
function createLibrary(root){
  const mapsRoot=path.resolve(root);
  const indexes=new Map();
  function worldIndex(folder){
    const region=path.join(folder,'region');if(fs.lstatSync(region).isSymbolicLink())throw Error('External region links are not supported');
    const files=fs.readdirSync(region).filter(n=>/^r\.(-?\d+)\.(-?\d+)\.mca$/.test(n)),signature=files.map(n=>{const p=path.join(region,n),s=fs.lstatSync(p);return n+':'+s.size+':'+s.mtimeMs;}).join('|');
    if(indexes.get(folder)?.signature===signature)return indexes.get(folder).info;
    const chunks=[];let minX=Infinity,minZ=Infinity,maxX=-Infinity,maxZ=-Infinity;
    for(const name of files){
      const [,rx,rz]=/^r\.(-?\d+)\.(-?\d+)\.mca$/.exec(name),p=path.join(region,name);if(fs.lstatSync(p).isSymbolicLink())continue;
      const fd=fs.openSync(p,'r'),header=Buffer.alloc(4096);let length;try{length=fs.readSync(fd,header,0,4096,0);}finally{fs.closeSync(fd);}if(length!==4096)continue;
      for(let i=0;i<1024;i++){
        const location=header.readUInt32BE(i*4);if((location>>>8)<2||!(location&255))continue;
        const x=Number(rx)*512+(i%32)*16,z=Number(rz)*512+Math.floor(i/32)*16;if(Math.abs(x)>30000000||Math.abs(z)>30000000)continue;
        chunks.push([x,z]);minX=Math.min(minX,x);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x+15);maxZ=Math.max(maxZ,z+15);
      }
    }
    if(!chunks.length)throw Error('No saved chunks');
    const width=maxX-minX+1,depth=maxZ-minZ+1,grid=128,coverage=new Uint8Array(grid*grid);
    for(const [x,z]of chunks){const x0=Math.floor((x-minX)/width*grid),z0=Math.floor((z-minZ)/depth*grid),x1=Math.min(grid-1,Math.floor((x+15-minX)/width*grid)),z1=Math.min(grid-1,Math.floor((z+15-minZ)/depth*grid));for(let gz=z0;gz<=z1;gz++)for(let gx=x0;gx<=x1;gx++)coverage[gz*grid+gx]=1;}
    const info={bounds:[minX,minZ,maxX,maxZ],footprint:[width,depth],chunkCount:chunks.length,coverage:Buffer.from(coverage).toString('base64'),grid};indexes.set(folder,{signature,info});return info;
  }
  function list(){
    const entries=[];
    for(const [parent,prefix]of [[mapsRoot,''],[path.join(mapsRoot,'preview-exports'),'preview-exports/']]){
      if(!fs.existsSync(parent)||fs.lstatSync(parent).isSymbolicLink())continue;
      for(const entry of fs.readdirSync(parent,{withFileTypes:true})){
        if(!entry.isDirectory()||entry.isSymbolicLink())continue;
        const folder=path.join(parent,entry.name),manifestFile=path.join(folder,'map-manifest.json');
        if(!fs.existsSync(manifestFile)||fs.lstatSync(manifestFile).isSymbolicLink())continue;
        try{
          if(fs.statSync(manifestFile).size>1024*1024)continue;
          const manifest=JSON.parse(fs.readFileSync(manifestFile,'utf8'));
          if(!Array.isArray(manifest.footprint)||!fs.existsSync(path.join(folder,'level.dat'))||!fs.existsSync(path.join(folder,'region')))continue;
          const world=manifest.fullWorld?worldIndex(folder):null;
          entries.push({id:prefix+entry.name,name:manifest.name||entry.name,description:manifest.description||'แมพที่สร้างไว้ · '+(manifest.format||'Minecraft Java'),footprint:world?.footprint||manifest.footprint,spawn:manifest.spawn,fullWorld:!!manifest.fullWorld,worldBounds:world?.bounds,updated:fs.statSync(manifestFile).mtime.toISOString(),folder});
        }catch{ /* Ignore unfinished map manifests. */ }
      }
    }
    return entries.sort((a,b)=>a.id.localeCompare(b.id));
  }
  function info(id){
    const entry=list().find(e=>e.id===id);if(!entry)throw Object.assign(Error('ไม่พบแมพนี้ในรายการ'),{status:404});
    if(entry.fullWorld)return {id,fullWorld:true,spawn:entry.spawn,...worldIndex(entry.folder)};
    const manifest=JSON.parse(fs.readFileSync(path.join(entry.folder,'map-manifest.json'),'utf8')),origin=manifest.origin||[0,0];return {id,fullWorld:false,spawn:entry.spawn,footprint:entry.footprint,bounds:[origin[0],origin[1],origin[0]+entry.footprint[0]-1,origin[1]+entry.footprint[1]-1]};
  }
  function load(id,options={}){
    const entry=list().find(e=>e.id===id);if(!entry)throw Object.assign(Error('ไม่พบแมพนี้ในรายการ'),{status:404});
    const file=relative=>{const p=path.join(entry.folder,relative);if(fs.lstatSync(p).isSymbolicLink())throw Error('ไม่อ่านไฟล์ลิงก์ภายนอก');return p;};
    if(fs.lstatSync(path.join(entry.folder,'region')).isSymbolicLink())throw Error('ไม่อ่านโฟลเดอร์ลิงก์ภายนอก');
    const level=readNbt(zlib.gunzipSync(fs.readFileSync(file('level.dat')),{maxOutputLength:16*1024*1024})).Data;
    const manifest=JSON.parse(fs.readFileSync(file('map-manifest.json'),'utf8'));
    const world=entry.fullWorld?worldIndex(entry.folder):null;
    if(!entry.fullWorld&&(options.center||options.size))throw Object.assign(Error('This map has fixed preview bounds'),{status:400});
    let size=entry.fullWorld?(options.size||384):null;
    if(size&&![320,384,512].includes(size))throw Object.assign(Error('Invalid window size'),{status:400});
    const center=options.center||[level.SpawnX,level.SpawnZ];
    if(entry.fullWorld&&(!Array.isArray(center)||center.length!==2||!center.every(Number.isFinite)||center[0]<world.bounds[0]||center[0]>world.bounds[2]||center[1]<world.bounds[1]||center[1]>world.bounds[3]))throw Object.assign(Error('จุดที่เลือกอยู่นอกขอบเขตแมพ'),{status:400});
    const [width,depth]=entry.fullWorld?[size,size]:manifest.footprint;
    if(!Number.isInteger(width)||!Number.isInteger(depth)||width<1||depth<1||width>512||depth>512)throw Error('พรีวิวรองรับหน้าต่างขนาดไม่เกิน 512 × 512');
    const origin=entry.fullWorld?center.map(v=>Math.floor((v-size/2)/16)*16):(manifest.origin||[0,0]);
    if(!Array.isArray(origin)||origin.length!==2||!origin.every(n=>Number.isInteger(n)&&Math.abs(n)<=30000000))throw Error('Invalid preview origin');
    const [originX,originZ]=origin;
    const S=Math.max(width,depth),definitions=[{Name:'minecraft:air'}],lookup=new Map([['minecraft:air{}',0]]),sections=[];
    let low=320,high=-64,containers=0;
    const regions=new Map();
    for(let cz=Math.floor(originZ/16);cz<Math.ceil((originZ+depth)/16);cz++)for(let cx=Math.floor(originX/16);cx<Math.ceil((originX+width)/16);cx++){
      const name=`region/r.${cx>>5}.${cz>>5}.mca`;
      if(!regions.has(name))regions.set(name,fs.existsSync(path.join(entry.folder,name))?fs.readFileSync(file(name)):null);
      if(!regions.get(name))continue;
      const bytes=regions.get(name),slot=((cx&31)+(cz&31)*32)*4;
      if(bytes.length<8192)throw Error('Incomplete region header');
      const location=bytes.readUInt32BE(slot),start=(location>>>8)*4096,sectors=location&255;
      if(!location)continue;
      if(start<8192||start+sectors*4096>bytes.length)throw Error('Invalid chunk location');
      const length=bytes.readUInt32BE(start),compression=bytes[start+4];
      if(length<1||length+4>sectors*4096)throw Error('Invalid chunk size');
      const compressed=bytes.subarray(start+5,start+4+length),limit={maxOutputLength:16*1024*1024};
      const raw=compression===2?zlib.inflateSync(compressed,limit):compression===1?zlib.gunzipSync(compressed,limit):compression===3?compressed:null;
      if(!raw)throw Error('Unsupported chunk compression');
      const chunk=chunkData(readNbt(raw));if(chunk.xPos!==cx||chunk.zPos!==cz)throw Error('Wrong chunk coordinates');
      containers+=(chunk.block_entities||[]).filter(e=>e.x>=originX&&e.x<originX+width&&e.z>=originZ&&e.z<originZ+depth).length;
      for(const section of chunk.sections||[]){
        const states=section.block_states;if(!states?.palette?.length)continue;
        if(states.palette.every(d=>['minecraft:air','minecraft:cave_air','minecraft:void_air'].includes(d.Name)))continue;
        const y=section.Y*16;if(y< -64||y>=320)throw Error('Invalid section height');
        const palette=states.palette.map(d=>{
          if(['minecraft:air','minecraft:cave_air','minecraft:void_air'].includes(d.Name))return 0;
          const definition={Name:d.Name,...(d.Properties?{Properties:{...d.Properties}}:{})},key=definition.Name+JSON.stringify(definition.Properties||{});
          if(!lookup.has(key)){if(definitions.length>=65535)throw Error('Too many block states');lookup.set(key,definitions.length);definitions.push(definition);}return lookup.get(key);
        });
        sections.push({cx,cz,y,palette,data:states.data,dense:section.dense});low=Math.min(low,y);high=Math.max(high,y+16);
      }
    }
    if(!sections.length){if(!entry.fullWorld)throw Error('แมพไม่มีบล็อกสำหรับแสดง');low=Number.isInteger(manifest.floorY)?manifest.floorY:0;high=low+1;}
    if(S*S*(high-low)>48000000){if(entry.fullWorld&&size>320)return load(id,{...options,center,size:320});throw Error('แมพมีข้อมูลมากเกินขีดจำกัดพรีวิว');}
    const blocks=new Uint16Array(S*S*(high-low));let count=0;
    for(const section of sections){
      const {cx,cz,y,palette,data,dense}=section,bits=Math.max(4,Math.ceil(Math.log2(palette.length))),per=Math.floor(64/bits);
      if(palette.length>1&&(!data||data.length<(dense?Math.ceil(4096*bits/64):Math.ceil(4096/per))))throw Error('Incomplete block palette data');
      for(let i=0;i<4096;i++){
        const index=paletteIndex(data,palette.length,i,dense);
        if(index>=palette.length)throw Error('Invalid palette index');
        const x=cx*16+(i&15)-originX,z=cz*16+((i>>4)&15)-originZ,ty=y+(i>>8),id=palette[index];
        if(x<0||z<0||x>=width||z>=depth||!id)continue;blocks[(ty-low)*S*S+z*S+x]=id;count++;
      }
    }
    const rooms=(manifest.rooms||[]).filter(r=>Array.isArray(r.center)&&r.center.length===3&&r.center[0]>=originX&&r.center[0]<originX+width&&r.center[2]>=originZ&&r.center[2]<originZ+depth).map(r=>({name:r.name,x:r.center[0]-originX,z:r.center[2]-originZ,y:r.center[1]+13,labelExact:true}));
    const worldSpawn=[level.SpawnX,level.SpawnY,level.SpawnZ],spawn=[level.SpawnX-originX,level.SpawnY,level.SpawnZ-originZ];
    return {id:entry.id,name:entry.name,size:S,footprint:[width,depth],fullWorld:entry.fullWorld,worldBounds:world?.bounds,worldFootprint:world?.footprint,origin,worldSpawn,low,high,floor:Math.max(low,Math.min(high-1,Number.isInteger(manifest.floorY)?manifest.floorY:low)),definitions,blocks:Buffer.from(blocks.buffer).toString('base64'),encoding:'uint16-le',rooms,spawn,colours:{...colours,grass_block:'#64844b',oak_log:'#785737',oak_leaves:'#478048'},stats:{blocks:count,containers},updated:entry.updated};
  }
  return {list:()=>list().map(({folder,...entry})=>entry),info,load};
}
module.exports={createLibrary,readNbt,chunkData,paletteIndex};
