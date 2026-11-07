const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),zlib=require('node:zlib'),path=require('node:path');
const {buildLibrary}=require('./create_tree_library.cjs'),{decode}=require('./nbt-edit.cjs'),{createLibrary}=require('./viewer/map-library.cjs');
test('all fourteen reusable trees match their world blocks and exclude ground and adjacent plots',{skip:JSON.parse(fs.readFileSync('maps/tree_library/catalog.json')).length!==14?'Legacy fourteen-tree library replaced by imported bundle':false},()=>{
 const t=buildLibrary(),saved=createLibrary(path.resolve('maps')).load('tree_library'),bytes=Buffer.from(saved.blocks,'base64');assert.equal(t.trees.length,14);
 const key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),map=saved.definitions.map(d=>t.definitions.findIndex(e=>key(d)===key(e)));assert.ok(map.every(i=>i>=0));
 for(const tree of t.trees){
  const n=decode(zlib.gunzipSync(fs.readFileSync('maps/tree_library/structures/'+tree.id+'.nbt'))).value,palette=n.palette.value.values,records=n.blocks.value.values;
  assert.deepEqual(n.size.value.values,tree.size);assert.equal(records.length,tree.blocks.length);assert.ok(tree.size.every(s=>s<=48));
  const seen=new Set();for(const b of records){const p=b.pos.value.values,k=p.join(',');assert.ok(!seen.has(k));seen.add(k);assert.ok(p.every((v,i)=>v>=0&&v<tree.size[i]));
   const wx=tree.copyBounds[0]+p[0],wy=tree.copyBounds[1]+p[1],wz=tree.copyBounds[2]+p[2];assert.ok(wy>=65);assert.ok(wx>tree.plotBounds[0]&&wx<tree.plotBounds[2]&&wz>tree.plotBounds[1]&&wz<tree.plotBounds[3]);
   const state=palette[b.state.value],d={Name:state.Name.value,...(state.Properties?{Properties:Object.fromEntries(Object.entries(state.Properties.value).map(([k,v])=>[k,v.value]))}:{})};assert.equal(key(d),key(t.definitions[t.get(wx,wy,wz)]));
   if(d.Name.endsWith('_leaves'))assert.equal(d.Properties.persistent,'true');
   const i=((wy-saved.low)*saved.size*saved.size+wz*saved.size+wx)*2;assert.equal(map[bytes.readUInt16LE(i)],t.get(wx,wy,wz));
  }
 }
 assert.equal(t.get(...t.spawn),0);assert.equal(t.get(t.spawn[0],t.spawn[1]+1,t.spawn[2]),0);
});
