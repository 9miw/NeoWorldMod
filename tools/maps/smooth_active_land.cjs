const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {createLibrary,paletteIndex}=require('./viewer/map-library.cjs'),{patchRegion}=require('./nbt-edit.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
const protectedZone=(x,z)=>x>=134&&x<=186&&z>=281&&z<=333;
function smoothModel(m){
  const S=m.size;
  const bytes=Buffer.from(m.blocks,'base64'),data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
  const original=data.slice(),top=new Float64Array(S*S).fill(-1),bottom=new Float64Array(S*S).fill(-1),protectedMask=new Uint8Array(S*S);
  const at=(x,y,z)=>(y-m.low)*S*S+z*S+x,get=(x,y,z)=>y<m.low||y>=m.high?0:data[at(x,y,z)],ids=Object.fromEntries(m.definitions.map((d,i)=>[d.Name,i]));
  for(let z=0;z<S;z++)for(let x=0;x<S;x++){
    const k=z*S+x;protectedMask[k]=protectedZone(x,z)?1:0;
    for(let y=m.low;y<m.high;y++){const id=get(x,y,z);if(!id)continue;if(!natural.has(m.definitions[id].Name))protectedMask[k]=1;if(bottom[k]<0)bottom[k]=y;if(m.definitions[id].Name==='minecraft:grass_block')top[k]=y;}
  }
  function filter(field,passes){let current=field.slice();for(let pass=0;pass<passes;pass++){
    const next=current.slice();for(let z=1;z<S-1;z++)for(let x=1;x<S-1;x++){
      const k=z*S+x;if(top[k]<0||protectedMask[k])continue;let sum=0,weight=0;
      for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++){const j=(z+dz)*S+x+dx;if(x+dx<0||z+dz<0||x+dx>=S||z+dz>=S||top[j]<0)continue;const w=(3-Math.abs(dx))*(3-Math.abs(dz));sum+=current[j]*w;weight+=w;}
      const d=x<134?134-x:x>186?x-186:0,e=z<281?281-z:z>333?z-333:0,blend=Math.min(1,Math.hypot(d,e)/12);
      next[k]=current[k]+(sum/weight-current[k])*blend;
    }current=next;}return current;}
  const nextTop=filter(top,5),nextBottom=filter(bottom,3);let changedBlocks=0,changedColumns=0,beforeJumps=0,afterJumps=0;
  for(let z=0;z<S;z++)for(let x=0;x<S;x++){
    const k=z*S+x;if(top[k]<0||protectedMask[k])continue;
    const hi=Math.round(nextTop[k]),lo=Math.max(m.low,Math.min(hi-4,Math.round(nextBottom[k]))),oldHi=top[k],oldLo=bottom[k];
    for(let y=Math.min(lo,oldLo);y<=Math.max(hi,oldHi);y++){
      let id=get(x,y,z);
      if(y<lo||y>hi)id=0;else if(y===hi)id=ids['minecraft:grass_block'];else if(y>hi-3)id=ids['minecraft:dirt'];else if(!id||m.definitions[id].Name==='minecraft:grass_block'||m.definitions[id].Name==='minecraft:dirt')id=ids['minecraft:stone'];
      if(id!==get(x,y,z)){data[at(x,y,z)]=id;changedBlocks++;}
    }
    if(hi!==oldHi||lo!==oldLo)changedColumns++;
  }
  for(let z=0;z<S-1;z++)for(let x=0;x<S-1;x++)for(const j of [z*S+x+1,(z+1)*S+x]){const k=z*S+x;if(top[k]<0||top[j]<0||protectedMask[k]||protectedMask[j])continue;if(Math.abs(top[k]-top[j])>1)beforeJumps++;if(Math.abs(Math.round(nextTop[k])-Math.round(nextTop[j]))>1)afterJumps++;}
  return {data,original,protectedMask,stats:{changedBlocks,changedColumns,beforeJumps,afterJumps}};
}
function patch(m,data,original,region){
  const S=m.size,key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),lookup=new Map(m.definitions.map((d,i)=>[key(d),i]));
  const n=(type,value)=>({type,value}),state=d=>({Name:n(8,d.Name),...(d.Properties?{Properties:n(10,Object.fromEntries(Object.entries(d.Properties).map(([k,v])=>[k,n(8,v)])))}:{})});
  return patchRegion(region,tree=>{
    const c=tree.value,cx=c.xPos.value,cz=c.zPos.value;let dirty=false;
    for(const section of c.sections.value.values){
      const sy=section.Y.value,states=section.block_states.value,palette=states.palette.value.values,words=states.data?.value||[],values=[],paletteIds=[];let sectionDirty=false;
      for(const p of palette){const d={Name:p.Name.value,...(p.Properties?{Properties:Object.fromEntries(Object.entries(p.Properties.value).map(([k,v])=>[k,v.value]))}:{})};paletteIds.push(lookup.get(key(d)));}
      for(let i=0;i<4096;i++){
        const x=cx*16+(i&15),z=cz*16+((i>>4)&15),y=sy*16+(i>>8);let id=paletteIds[paletteIndex(words,palette.length,i)];
        if(x<S&&z<S&&y>=m.low&&y<m.high){const offset=(y-m.low)*S*S+z*S+x;if(data[offset]!==original[offset]){id=data[offset];sectionDirty=true;}}
        if(id===undefined)throw Error('Unknown existing palette');values.push(id);
      }
      if(!sectionDirty)continue;dirty=true;
      const ids=[...new Set(values)],index=new Map(ids.map((v,i)=>[v,i])),bits=Math.max(4,Math.ceil(Math.log2(ids.length))),per=Math.floor(64/bits),packed=Array(Math.ceil(4096/per)).fill(0n);
      values.forEach((v,i)=>{packed[Math.floor(i/per)]|=BigInt(index.get(v))<<BigInt(i%per*bits);});
      section.block_states=n(10,{palette:n(9,{type:10,values:ids.map(i=>state(m.definitions[i]))}),...(ids.length>1?{data:n(12,packed)}:{})});
    }
    if(dirty){delete c.Heightmaps;c.isLightOn=n(1,0);for(const section of c.sections.value.values){delete section.SkyLight;delete section.BlockLight;}}
    return dirty;
  });
}
function run(){
  const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),result=smoothModel(m),regionFile=path.join(MAP,'region/r.0.0.mca'),before=fs.readFileSync(regionFile),changed=patch(m,result.data,result.original,before);
  if(!result.stats.changedBlocks)return {changedBlocks:0};
  if(result.stats.afterJumps>result.stats.beforeJumps)throw Error('Slope quality regressed');
  const backup=path.join(ROOT,'archive/map-edit-backups/before-terrain-smooth/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Smoothing already has a backup; review before rerunning');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});
  const pending=regionFile+'.pending';fs.writeFileSync(pending,changed.bytes);fs.renameSync(pending,regionFile);
  const profile={...result.stats,changedChunks:changed.changed,sourceRegionSha256:crypto.createHash('sha256').update(before).digest('hex'),backup:path.relative(ROOT,backup)};
  const manifestFile=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(manifestFile,'utf8'));manifest.terrain.lastSmoothing=profile;manifest.description='พื้นเกาะปรับลาดนุ่มขึ้น · คงจุดเกิดและของตกแต่งเดิม';fs.writeFileSync(manifestFile,JSON.stringify(manifest,null,2));
  fs.writeFileSync(path.join(MAP,'smoothing-report.json'),JSON.stringify(profile,null,2));return profile;
}
module.exports={smoothModel,patch,run,protectedZone};
if(require.main===module)console.log(JSON.stringify(run()));
