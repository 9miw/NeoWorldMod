const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {decode,encode,patchRegion}=require('./nbt-edit.cjs'),{createLibrary}=require('./viewer/map-library.cjs'),{smoothModel,patch,protectedZone}=require('./smooth_active_land.cjs');
const tag=(type,value)=>({type,value});
test('saved working map matches the terrain edit and retains original level metadata',{skip:!fs.existsSync('archive/map-edit-backups/before-terrain-smooth/starter_sky_land_spawn_v1')},()=>{
 const backup=path.resolve('archive/map-edit-backups/before-terrain-smooth'),before=createLibrary(backup).load('starter_sky_land_spawn_v1'),expected=smoothModel(before),after=createLibrary(path.resolve('maps')).load('starter_sky_land_spawn_v1'),bytes=Buffer.from(after.blocks,'base64');
 const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),mapping=after.definitions.map(d=>before.definitions.findIndex(e=>key(d)===key(e)));assert.ok(mapping.every(i=>i>=0));
 assert.equal(after.low,before.low);assert.equal(after.high,before.high);
 for(let i=0;i<expected.data.length;i++)if(mapping[bytes.readUInt16LE(i*2)]!==expected.data[i])assert.fail('Saved block mismatch '+i);
 assert.deepEqual(fs.readFileSync('maps/starter_sky_land_spawn_v1/level.dat'),fs.readFileSync(path.join(backup,'starter_sky_land_spawn_v1/level.dat')));
 assert.ok(createLibrary(path.resolve('maps')).list().every(m=>['starter_sky_land_spawn_v1','tree_library'].includes(m.id)));
});
test('typed NBT preserves unknown fields, numeric types and array values',()=>{
 const root={name:'Root',type:10,value:{byte:tag(1,-1),short:tag(2,123),int:tag(3,-123456),long:tag(4,-9223372036854775808n),float:tag(5,1.25),double:tag(6,2.5),bytes:tag(7,Buffer.from([0,255,128])),text:tag(8,'ไทย'),list:tag(9,{type:10,values:[{custom:tag(8,'preserve me')}]}),ints:tag(11,[-2,0,2147483647]),longs:tag(12,[1n,-1n])}};
 assert.deepEqual(encode(decode(encode(root))),encode(root));
});
test('smoothing removes two-block slope jumps, preserves spawn and existing unknown chunk data',()=>{
 const backup='archive/map-edit-backups/before-terrain-smooth',root=fs.existsSync(backup)?backup:'maps',m=createLibrary(path.resolve(root)).load('starter_sky_land_spawn_v1'),r=smoothModel(m),S=m.size;
 assert.ok(r.stats.changedBlocks>0);assert.ok(r.stats.afterJumps<r.stats.beforeJumps);assert.equal(r.stats.afterJumps,0);
 for(let z=281;z<=333;z++)for(let x=134;x<=186;x++)for(let y=m.low;y<m.high;y++){assert.ok(protectedZone(x,z));const i=(y-m.low)*S*S+z*S+x;assert.equal(r.data[i],r.original[i]);}
 const bytes=fs.readFileSync(path.resolve(root,'starter_sky_land_spawn_v1/region/r.0.0.mca'));
 const seeded=patchRegion(bytes,(tree,slot)=>{if(slot!==330)return false;tree.value.UnknownFutureData=tag(10,{secret:tag(8,'must survive'),version:tag(2,72)});return true;}).bytes;
 const out=patch(m,r.data,r.original,seeded);assert.ok(out.changed>0);
 const loc=out.bytes.readUInt32BE(330*4),start=(loc>>>8)*4096,len=out.bytes.readUInt32BE(start),tree=decode(zlib.inflateSync(out.bytes.subarray(start+5,start+4+len)));
 assert.equal(tree.value.UnknownFutureData.value.secret.value,'must survive');assert.equal(tree.value.UnknownFutureData.value.version.type,2);
 const oldLoc=seeded.readUInt32BE(0),newLoc=out.bytes.readUInt32BE(0);assert.deepEqual(out.bytes.subarray((newLoc>>>8)*4096,((newLoc>>>8)+(newLoc&255))*4096),seeded.subarray((oldLoc>>>8)*4096,((oldLoc>>>8)+(oldLoc&255))*4096));
});
