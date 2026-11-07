const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const {decode,encode}=require('./nbt-edit.cjs'),{createLibrary,paletteIndex,readNbt}=require('./viewer/map-library.cjs');
const ROOT=path.resolve(__dirname,'../..'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
const plain=p=>({Name:p.Name.value,...(p.Properties?{Properties:Object.fromEntries(Object.entries(p.Properties.value).map(([k,v])=>[k,v.value]))}:{})});
function chunks(buf){const out=new Map();for(let slot=0;slot<1024;slot++){const l=buf.readUInt32BE(slot*4);if(!l)continue;const o=(l>>>8)*4096,n=buf.readUInt32BE(o);assert.equal(buf[o+4],2);out.set(slot,decode(zlib.inflateSync(buf.subarray(o+5,o+4+n))));}return out;}
function state(c,section,index){const p=section.block_states.value,pa=p.palette.value.values;return plain(pa[paletteIndex(p.data?.value||[],pa.length,index)]);}
const allowed=(x,y,z)=>x>=203&&x<=313&&z>=102&&z<=246&&y>=24&&y<=155&&!(x<=220&&z<=124)&&!(x<=208&&z>=150)&&!(x>=277&&z>=214)||x>=55&&x<=134&&z>=177&&z<=251&&y>=103&&y<=124&&!(x>=90&&x<=100&&z>=219&&z<=229)||x>=46&&x<=141&&z>=175&&z<=220&&y>=99&&y<=128&&!(x>=90&&x<=100&&z>=219)||x>=41&&x<=43&&y>=99&&y<=101&&z>=193&&z<=195||[153,167].includes(x)&&z===325&&[97,98].includes(y);
function models(){const a=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-guild-campus')).load('starter_sky_land_spawn_v1'),b=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1');return {a,b,ab:Buffer.from(a.blocks,'base64'),bb:Buffer.from(b.blocks,'base64')};}
function verify(input,output,entityBytes,postLoad=false){
 const {a,b,ab,bb}=models(),before=chunks(input),after=chunks(output);assert.equal(before.size,after.size);const ak=a.definitions.map(key),bk=b.definitions.map(key),S=b.size;
 let expected=0,outside=0,changed=0;const lookup=(x,y,z)=>{const c=after.get((x>>4)+32*(z>>4))?.value,s=c?.sections.value.values.find(s=>s.Y.value===(y>>4));return s?state(c,s,((y&15)<<8)|((z&15)<<4)|(x&15)):{Name:'minecraft:air'};};
 assert.equal(lookup(244,107,224).Name,'minecraft:purple_terracotta','Server is missing new compass floor');
 for(const [slot,t]of before){const c=t.value,d=after.get(slot).value,sections=new Map(d.sections.value.values.map(s=>[s.Y.value,s]));for(const s of c.sections.value.values){const s2=sections.get(s.Y.value);assert(s2,'Missing section');const p1=s.block_states.value,p2=s2.block_states.value,ks1=p1.palette.value.values.map(plain).map(key),ks2=p2.palette.value.values.map(plain).map(key);for(let i=0;i<4096;i++){
  const x=c.xPos.value*16+(i&15),z=c.zPos.value*16+((i>>4)&15),y=s.Y.value*16+(i>>8),v1=ks1[paletteIndex(p1.data?.value||[],ks1.length,i)],v2=ks2[paletteIndex(p2.data?.value||[],ks2.length,i)];let target;
  if(x>=0&&x<S&&z>=0&&z<S&&y>=b.low&&y<b.high){const j=((y-b.low)*S*S+z*S+x)*2,baseKey=ak[ab.readUInt16LE(j)],wantKey=bk[bb.readUInt16LE(j)];if(baseKey!==wantKey){assert(allowed(x,y,z),'Unapproved local delta');target=wantKey;expected++;}}
  if(target){if(postLoad)assert.equal(v2.split('[')[0],target.split('[')[0],'Post-load target type mismatch '+[x,y,z]);else assert.equal(v2,target,'Target mismatch '+[x,y,z]);}else if(v1!==v2){outside++;}if(v1!==v2)changed++;
 }} }
 assert.equal(expected,154066,'Local deployment scope changed');if(!postLoad)assert.equal(outside,0);
 const allBE=cs=>[...cs.values()].flatMap(t=>t.value.block_entities?.value.values||[]),beKey=e=>[e.x.value,e.y.value,e.z.value].join(','),finger=e=>encode({name:'',value:e}).toString('base64');
 const originalBE=allBE(before),finalBE=allBE(after),finalMap=new Map(finalBE.map(e=>[beKey(e),e]));assert.equal(finalMap.size,finalBE.length,'Duplicate block entities');
 for(const e of originalBE){const f=finalMap.get(beKey(e));assert(f,'Server block entity removed '+beKey(e));if(!postLoad)assert.equal(finger(f),finger(e),'Server block entity contents changed '+beKey(e));else assert.equal(f.id.value,e.id.value);}
 const local=chunks(fs.readFileSync(path.join(ROOT,'maps/starter_sky_land_spawn_v1/region/r.0.0.mca'))),localBE=allBE(local);for(const e of localBE){const f=finalMap.get(beKey(e));assert(f,'Missing new block entity '+beKey(e));assert.equal(f.id.value,e.id.value);if(!postLoad)assert.equal(finger(f),finger(e),'New entity content mismatch '+beKey(e));}
 assert.equal(finalBE.length,originalBE.length+localBE.length,'Unexpected entity list change');
 let npcs=0,points=0;const pointTypes={},pass=d=>['minecraft:air','minecraft:short_grass','minecraft:allium','minecraft:white_tulip','minecraft:fern','minecraft:chain','minecraft:lantern','neoworld:navigation_marker'].includes(d.Name)||d.Name.endsWith('_carpet');
 if(entityBytes)for(const t of chunks(entityBytes).values()){const entities=t.value.Entities?.value.values||[];for(const e of entities){if(e.id.value!=='customnpcs:customnpc')continue;npcs++;const p=e.Pos.value.values,home=e.StartPosNew?.value,patrol=e.MovingPathNew?.value.values||[],list=[{kind:'actual',p},...(home? [{kind:'home',p:home}]:[]),...patrol.map(q=>({kind:'patrol',p:q.Array.value}))];for(const q of list){const [x,y,z]=q.p.map(Math.floor);if(!allowed(x,y,z)&&!allowed(x,y-1,z))continue;points++;pointTypes[q.kind]=(pointTypes[q.kind]||0)+1;let floor;
   for(let yy=Math.ceil(q.p[1]);yy>=Math.floor(q.p[1])-2;yy--){const d=lookup(x,yy,z);if(pass(d))continue;let h=yy+1;if(d.Name.endsWith('_slab')&&d.Properties?.type==='bottom')h=yy+.5;if(d.Name==='minecraft:dirt_path')h=yy+.9375;if(q.kind==='actual'&&Math.abs(h-q.p[1])>.25)continue;floor=h;break;}
   assert(floor!==undefined,'NPC support missing '+e.Name.value+' '+q.kind+' '+q.p);for(let yy=Math.ceil(floor);yy<floor+2;yy++)assert(pass(lookup(x,yy,z)),'NPC clearance '+e.Name.value+' '+q.kind+' '+[x,yy,z]);
 }} }
 return {approvedDelta:expected,changedBlocks:changed,outsideDeltaChanges:postLoad?null:outside,...(postLoad?{runtimeChangesOutsideDelta:outside}:{}),originalBlockEntities:originalBE.length,newBlockEntities:localBE.length,finalBlockEntities:finalBE.length,npcs,protectedSamples:points,pointTypes,postLoad};
}
module.exports={verify,chunks,plain,state,allowed,models,key};
if(require.main===module){const [input,output,entities,mode]=process.argv.slice(2);console.log(JSON.stringify(verify(fs.readFileSync(input),fs.readFileSync(output),fs.readFileSync(entities),mode==='post'),null,2));}
