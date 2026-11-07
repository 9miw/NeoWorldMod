const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createLibrary}=require('./map-library.cjs');
const {createViewerServer}=require('./server.cjs');

test('window rebasing preserves world camera coordinates and triggers near an edge',async()=>{
  const {rebasePoint,needsWindow,overviewPoint}=await import('./world-navigation.mjs');
  const old=[-224,-208],next=[-128,-112],point=[220,41,210],rebased=rebasePoint(point,old,next);
  assert.equal(rebased[0]+next[0],point[0]+old[0]);assert.equal(rebased[2]+next[1],point[2]+old[1]);assert.equal(rebased[1],41);
  assert.equal(needsWindow({fullWorld:true,size:384},[192,40,192],true),false);assert.equal(needsWindow({fullWorld:true,size:384},[240,40,192],true),true);assert.equal(needsWindow({fullWorld:false,size:384},[400,40,192],true),false);
  assert.deepEqual(overviewPoint([-100,-200,300,600],.5,.5),[100,200]);assert.deepEqual(overviewPoint([-100,-200,300,600],-1,2),[-100,600]);
});

test('full-world index and bounded windows share saved data, reject invalid requests',async()=>{
  const root=path.resolve(__dirname,'../../..'),parent=path.join(root,'build/tmp');fs.mkdirSync(parent,{recursive:true});const temp=fs.mkdtempSync(path.join(parent,'world-navigation-test-')),folder=path.join(temp,'full');fs.mkdirSync(path.join(folder,'region'),{recursive:true});
  for(const file of ['level.dat','map-manifest.json','region/r.0.0.mca'])fs.copyFileSync(path.join(root,'archive/maps-before-single-world/ruined_castle_v1',file),path.join(folder,file));
  const manifest=JSON.parse(fs.readFileSync(path.join(folder,'map-manifest.json'),'utf8'));manifest.fullWorld=true;fs.writeFileSync(path.join(folder,'map-manifest.json'),JSON.stringify(manifest));
  const library=createLibrary(temp),info=library.info('full');assert.deepEqual(info.bounds,[0,0,159,159]);assert.deepEqual(info.footprint,[160,160]);assert.equal(info.chunkCount,100);assert.equal(Buffer.from(info.coverage,'base64').length,128*128);
  const first=library.load('full',{size:320,center:[75,127]}),second=library.load('full',{size:320,center:[159,159]});assert.equal(first.stats.blocks,164169);assert.equal(second.stats.blocks,164169);assert.notDeepEqual(first.origin,second.origin);assert.deepEqual(first.worldSpawn,second.worldSpawn);assert.deepEqual(second.worldBounds,info.bounds);
  assert.throws(()=>library.load('full',{center:[5000,5000]}),e=>e.status===400);assert.throws(()=>library.load('full',{size:900}),e=>e.status===400);
  const server=createViewerServer({mapsRoot:temp});await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
  try{
    assert.deepEqual((await (await fetch(base+'/api/map-info?id=full')).json()).bounds,info.bounds);
    const response=await fetch(base+'/api/map?id=full&x=159&z=159&size=320');assert.equal(response.status,200);assert.deepEqual((await response.json()).origin,second.origin);
    for(const query of ['x=nan&z=0','x=0','x=0&z=5000','size=0','size=9999'])assert.equal((await fetch(base+'/api/map?id=full&'+query)).status,400);
  }finally{await new Promise(resolve=>server.close(resolve));}
});

test('older Level/Sections schemas and packed values across word boundaries are readable',()=>{
  const {chunkData,paletteIndex}=require('./map-library.cjs');
  const legacy=chunkData({DataVersion:2586,Level:{xPos:34,zPos:-16,Sections:[{Y:0,Palette:[{Name:'minecraft:stone'}],BlockStates:[]}],TileEntities:[{x:544,y:1,z:-256}]}});
  assert.equal(legacy.xPos,34);assert.equal(legacy.sections[0].block_states.palette[0].Name,'minecraft:stone');assert.equal(legacy.block_entities.length,1);assert.equal(legacy.sections[0].dense,false);
  const paletteLength=17,bits=5,values=Array.from({length:4096},(_,i)=>i%17),words=new Array(Math.ceil(4096*bits/64)).fill(0n);
  for(let i=0;i<values.length;i++){const offset=i*bits,index=Math.floor(offset/64),shift=offset%64;words[index]|=BigInt(values[i])<<BigInt(shift);if(shift+bits>64)words[index+1]|=BigInt(values[i])>>BigInt(64-shift);}
  for(let i=0;i<values.length;i++)assert.equal(paletteIndex(words,paletteLength,i,true),values[i]);
  assert.equal(chunkData({DataVersion:2200,Level:{Sections:[{Y:0,Palette:[{Name:'minecraft:stone'}]}]}}).sections[0].dense,true);
});

test('returning to a loaded area reuses its data but uncovered areas need another window',async()=>{
 const {cachedWindow}=await import('./world-navigation.mjs');
 const a={data:{id:'world',fullWorld:true,size:384,origin:[-192,-192]}},b={data:{id:'world',fullWorld:true,size:384,origin:[0,-192]}};
 assert.equal(cachedWindow([a,b],'world',[192,0],true),b);
 assert.equal(cachedWindow([a,b],'world',[0,0],true),a);
 assert.equal(cachedWindow([a,b],'world',[800,0],true),undefined);
 assert.equal(cachedWindow([a,b],'other',[0,0],true),undefined);
});
