const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),{createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'avenue-landscaping-report.json');if(fs.existsSync(rf))throw Error('Already landscaped');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),buf=Buffer.from(m.blocks,'base64'),old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
function put(x,y,z,n,p){if(x<136||x>184||z<169||z>276||y<96||y>115)throw Error('Outside avenue landscaping');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));}
const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);let shoulders=0;
for(let z=169;z<=272;z++)for(let x=136;x<=184;x++){
 const off=Math.abs(x-160);if(off<8)continue;let top=96,built=false;for(let y=96;y<m.high;y++){const n=m.definitions[old[ix(x,y,z)]].Name;if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built||top>103||m.definitions[old[ix(x,96,z)]].Name==='minecraft:air')continue;
 const target=Math.max(top,Math.round(top+(Math.max(top,103-Math.max(0,Math.ceil((off-15)/2)))-top)*Math.min(1,(z-169)/5,(272-z)/5)));if(target===top)continue;shoulders++;
 for(let y=top-2;y<=target;y++)put(x,y,z,y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
}
const asset=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(root,'maps/tree_library/structures/t199.nbt')))),trees=[];
for(const z of [174,246,264])for(const x of [146,174]){
 const rotation=(trees.length%4),blocks=[];
 for(const b of asset.blocks){let [lx,ly,lz]=b.pos;for(let r=0;r<rotation;r++)[lx,lz]=[8-lz,lx];const d=asset.palette[b.state],p=d.Properties?{...d.Properties}:undefined;if(p?.axis&&rotation%2&&p.axis!=='y')p.axis=p.axis==='x'?'z':'x';if(d.Name.endsWith('_leaves'))p.persistent='true';const wx=x-4+lx,wz=z-4+lz,wy=104+ly;
  if(m.definitions[data[ix(wx,wy,wz)]].Name!=='minecraft:air')throw Error('Tree collision '+[wx,wy,wz]);put(wx,wy,wz,d.Name.slice(10),p);blocks.push([wx,wy,wz]);
 }
 trees.push({asset:'t199',center:[x,104,z],rotation:rotation*90,blocks:blocks.length});
}
const flags=[];for(const z of [200,256,274])for(const x of [150,170]){
 // Avoid changing the original street or its existing furniture.
 const ground=m.definitions[data[ix(x,103,z)]].Name;if(!['minecraft:grass_block','minecraft:dirt'].includes(ground))throw Error('Flag base is not outside the pavement '+[x,z]);
 put(x,103,z,'polished_andesite');put(x,104,z,'chiseled_stone_bricks');
 for(let y=105;y<=113;y++){if(m.definitions[data[ix(x,y,z)]].Name!=='minecraft:air')throw Error('Flag mast collision');put(x,y,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});}
 const dir=x<160?1:-1;for(let dx=1;dx<=2;dx++)for(let y=109;y<=112;y++){
  if(m.definitions[data[ix(x+dir*dx,y,z)]].Name!=='minecraft:air')throw Error('Flag cloth collision');put(x+dir*dx,y,z,dx===1?'white_wool':'blue_wool');
 }
 put(x,114,z,'lantern',{hanging:'false',waterlogged:'false'});flags.push({base:[x,104,z],topY:114,colors:['white','blue']});
}
// All original paved columns and their walking clearance remain unchanged below Y107.
for(let z=169;z<=276;z++)for(let x=136;x<=184;x++){
 const n=m.definitions[old[ix(x,103,z)]].Name;if(!['minecraft:stone_bricks','minecraft:smooth_stone','minecraft:polished_andesite'].includes(n))continue;
 for(let y=103;y<=106;y++)if(data[ix(x,y,z)]!==old[ix(x,y,z)])throw Error('Existing pavement or walking clearance changed');
}
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');count++;}
const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-avenue-landscaping/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.streetsSquare.avenueLandscaping={mainPathWidth:13,treeRowsX:[146,174],newTrees:trees,retainedTreeRowsZ:[190,225],newFlags:flags,retainedFlagsZ:178,shoulderColumns:shoulders,totalAvenueTrees:10};manifest.rooms=manifest.rooms.map(r=>r.id===manifest.streetsSquare.id?manifest.streetsSquare:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.streetsSquare.id?{...z,...manifest.streetsSquare}:z),null,2));fs.writeFileSync(rf,JSON.stringify({trees,flags,shoulderColumns:shoulders,changedBlocks:count,changedChunks:result.changed,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:result.changed,trees:trees.length,flags:flags.length,shoulderColumns:shoulders});
