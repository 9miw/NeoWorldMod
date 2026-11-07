const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const http=require('node:http');
const os=require('node:os');
const {createLibrary,readNbt}=require('./map-library.cjs');
const {createViewerServer}=require('./server.cjs');
const {buildMap}=require('../create_ruined_castle.cjs');
const ROOT=path.resolve(__dirname,'../../..');
const source=path.join(ROOT,'archive/maps-before-single-world/ruined_castle_v1');
const files=['level.dat','map-manifest.json','region/r.0.0.mca'];
const hashes=()=>files.map(f=>crypto.createHash('sha256').update(fs.readFileSync(path.join(source,f))).digest('hex'));
const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());

test('saved map preview matches every generated voxel and preserves source files',()=>{
  const before=hashes(),library=createLibrary(path.join(ROOT,'archive/maps-before-single-world')),m=library.load('ruined_castle_v1'),expected=buildMap();
  assert.equal(m.stats.blocks,164169);assert.equal(m.stats.containers,37);assert.deepEqual(m.spawn,[75,65,127]);
  const bytes=Buffer.from(m.blocks,'base64'),mapping=m.definitions.map(key),expectedStates=expected.definitions.map(key);
  for(let y=m.low;y<m.high;y++)for(let z=0;z<m.size;z++)for(let x=0;x<m.size;x++){
    const actual=bytes.readUInt16LE(((y-m.low)*m.size*m.size+z*m.size+x)*2);
    const id=y>=expected.LOW&&y<expected.HIGH?expected.blocks[(y-expected.LOW)*expected.S*expected.S+z*expected.S+x]:0;
    if(mapping[actual]!==expectedStates[id])assert.fail(`Different block at ${x},${y},${z}`);
  }
  assert.deepEqual(hashes(),before);assert.throws(()=>library.load('../ruined_castle_v1'));assert.throws(()=>readNbt(Buffer.from([10,0,0,3])));
});

test('list, select and refresh saved maps; all mutation endpoints are disabled',async()=>{
  const parent=path.join(ROOT,'build/tmp');fs.mkdirSync(parent,{recursive:true});const root=fs.mkdtempSync(path.join(parent,'map-review-test-'));
  function copyMap(relative,name){const destination=path.join(root,relative);fs.mkdirSync(path.join(destination,'region'),{recursive:true});for(const f of files)fs.copyFileSync(path.join(source,f),path.join(destination,f));const manifest=JSON.parse(fs.readFileSync(path.join(destination,'map-manifest.json'),'utf8'));manifest.name=name;fs.writeFileSync(path.join(destination,'map-manifest.json'),JSON.stringify(manifest));}
  copyMap('castle_one','Castle One');const server=createViewerServer({mapsRoot:root});await new Promise(resolve=>server.listen(0,'0.0.0.0',resolve));const base=`http://127.0.0.1:${server.address().port}`;
  try{
    assert.equal((await (await fetch(base+'/api/maps')).json()).maps.length,1);
    copyMap('preview-exports/castle_two','Castle Two');
    const entries=(await (await fetch(base+'/api/maps')).json()).maps;assert.deepEqual(entries.map(m=>m.name),['Castle One','Castle Two']);assert.ok(entries.every(m=>!m.folder));
    for(const entry of entries){const response=await fetch(base+'/api/map?id='+encodeURIComponent(entry.id));assert.equal(response.status,200);const data=await response.json();assert.equal(data.name,entry.name);assert.equal(data.resources.version,'1.21.1');assert.equal(data.resources.states.length,data.definitions.length);assert.deepEqual(data.resources.missingTextures,[]);}
    const texture=await fetch(base+'/api/texture?id=minecraft:block/stone');assert.equal(texture.status,200);assert.equal(texture.headers.get('Content-Type'),'image/png');assert.equal(Buffer.from(await texture.arrayBuffer()).subarray(1,4).toString(),'PNG');
    assert.equal((await fetch(base+'/api/texture?id=minecraft:../../version')).status,404);
    assert.equal((await fetch(base+'/api/map?id=..%2Fcastle_one')).status,404);
    for(const route of ['/api/scene','/api/export','/api/layouts']){assert.equal((await fetch(base+route,{method:'POST',headers:{Origin:base,'Content-Type':'application/json'},body:'{}'})).status,405);assert.equal((await fetch(base+route)).status,404);}
    assert.equal((await fetch(base,{headers:{Origin:'https://example.com'}})).status,403);
    const hostStatus=await new Promise((resolve,reject)=>http.get(base,{headers:{Host:'untrusted.example'}},res=>{res.resume();resolve(res.statusCode);}).on('error',reject));assert.equal(hostStatus,403);
    const lan=Object.values(os.networkInterfaces()).flat().find(a=>a.family==='IPv4'&&!a.internal);if(lan)assert.equal((await fetch(`http://${lan.address}:${server.address().port}/api/maps`)).status,200);
    const html=await (await fetch(base)).text();assert.ok(html.includes('id="map-list"'));for(const id of ['add-part','apply-part','export','save-layout'])assert.ok(!html.includes(`id="${id}"`));
    assert.equal(fs.existsSync(path.join(root,'layouts')),false);
  }finally{await new Promise(resolve=>server.close(resolve));}
});

test('negative non-aligned origins crop chunks and preserve world coordinates',()=>{
  const parent=path.join(ROOT,'build/tmp');fs.mkdirSync(parent,{recursive:true});const root=fs.mkdtempSync(path.join(parent,'map-origin-test-')),folder=path.join(root,'negative');
  fs.mkdirSync(path.join(folder,'region'),{recursive:true});for(const f of files)fs.copyFileSync(path.join(source,f),path.join(folder,f));
  const manifest=JSON.parse(fs.readFileSync(path.join(folder,'map-manifest.json'),'utf8'));manifest.origin=[-7,-11];manifest.footprint=[64,64];fs.writeFileSync(path.join(folder,'map-manifest.json'),JSON.stringify(manifest));
  const m=createLibrary(root).load('negative'),expected=buildMap(),bytes=Buffer.from(m.blocks,'base64');
  assert.deepEqual(m.origin,[-7,-11]);assert.deepEqual(m.worldSpawn,[75,65,127]);assert.deepEqual(m.spawn,[82,65,138]);
  const states=m.definitions.map(key),original=expected.definitions.map(key);
  for(let y=m.low;y<m.high;y++)for(let z=0;z<64;z++)for(let x=0;x<64;x++){
    const wx=x-7,wz=z-11,actual=bytes.readUInt16LE(((y-m.low)*64*64+z*64+x)*2);
    const id=wx>=0&&wz>=0&&y>=expected.LOW&&y<expected.HIGH?expected.blocks[(y-expected.LOW)*150*150+wz*150+wx]:0;
    if(states[actual]!==original[id])assert.fail(`Offset mismatch at ${wx},${y},${wz}`);
  }
  const metadata=JSON.parse(fs.readFileSync(path.join(folder,'map-manifest.json'),'utf8'));metadata.origin=[-320,-320];fs.writeFileSync(path.join(folder,'map-manifest.json'),JSON.stringify(metadata));assert.throws(()=>createLibrary(root).load('negative'),/ไม่มีบล็อก/);
});
