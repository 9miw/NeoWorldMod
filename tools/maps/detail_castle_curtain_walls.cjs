const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'castle-wall-details-report.json');if(fs.existsSync(rf))throw Error('Wall detail already applied');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),planned=new Set();
const pre=createLibrary(path.join(root,'archive/map-edit-backups/before-governor-castle')).load(path.basename(map)),pb=Buffer.from(pre.blocks,'base64'),ground=new Int16Array(S*S).fill(-1),protectedCol=new Uint8Array(S*S),natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
for(let z=29;z<=128;z++)for(let x=103;x<=217;x++)for(let y=90;y<pre.high;y++){const n=pre.definitions[pb.readUInt16LE(ix(x,y,z)*2)].Name;if(n!=='minecraft:air')ground[z*S+x]=y;if(!natural.has(n))protectedCol[z*S+x]=1;}
function put(x,y,z,n,p){if(x<104||x>216||z<30||z>127||y<96||y>128)throw Error('Outside wall detail');if(protectedCol[z*S+x]||ground[z*S+x]<96)return false;const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);planned.add(ix(x,y,z));return true;}
const stairs=(f,half='bottom')=>({facing:f,half,shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},lantern={hanging:'false',waterlogged:'false'},posts=[],slits=[],banners=[];
// Each facade uses local coordinates: u runs along its wall, v projects outwards.
const faces=[
 {name:'front',floor:112,deck:120,range:[120,200],point:(u,v)=>[u,124+v],inward:'north',posts:[120,132,144,176,188,200],slots:[126,138,182,194],flags:[120,144,176,200]},
 {name:'left',floor:116,deck:124,range:[48,96],point:(u,v)=>[107-v,u],inward:'east',posts:[48,60,72,84,96],slots:[54,66,78,90],flags:[60,84]},
 {name:'right',floor:116,deck:124,range:[48,96],point:(u,v)=>[213+v,u],inward:'west',posts:[48,60,72,84,96],slots:[54,66,78,90],flags:[60,84]},
 {name:'rear',floor:116,deck:124,range:[124,196],point:(u,v)=>[u,33-v],inward:'south',posts:[124,136,148,160,172,184,196],slots:[130,142,154,166,178,190],flags:[]}
];
for(const f of faces){
 function at(u,y,v,n,p){const [x,z]=f.point(u,v);return put(x,y,z,n,p);}
 for(let u=f.range[0];u<=f.range[1];u++){
  if(f.name==='front'&&u>=149&&u<=171)continue;
  const [wx,wz]=f.point(u,0),wall=m.definitions[old[ix(wx,f.deck,wz)]].Name;if(wall!=='minecraft:stone_bricks')continue;
  // Lower plinth steps and a continuous raised band give the wall a heavy foot.
  at(u,f.floor,2,'stone_brick_stairs',stairs(f.inward));at(u,f.floor+1,1,'stone_brick_stairs',stairs(f.inward));
  at(u,f.floor+2,0,'polished_andesite');at(u,f.deck-2,0,'chiseled_stone_bricks');
  // Projecting brackets support the stone lip below the parapet.
  if(u%3===0){at(u,f.deck-1,1,'stone_brick_stairs',stairs(f.inward,'top'));at(u,f.deck,1,'smooth_stone_slab',slab);}
 }
 for(const u of f.posts){
  const [wx,wz]=f.point(u,0);if(m.definitions[old[ix(wx,f.deck,wz)]].Name!=='minecraft:stone_bricks')continue;
  let placed=false;for(let du=0;du<=1;du++)for(let v=0;v<=2;v++){
   const [x,z]=f.point(u+du,v);for(let y=Math.min(f.floor,Math.max(96,ground[z*S+x]));y<=f.deck+1;y++)placed=at(u+du,y,v,y===f.floor+2||y===f.deck-2?'chiseled_stone_bricks':v===2&&y>f.floor+3?'polished_andesite':'stone_bricks')||placed;
   at(u+du,f.deck+2,v,'polished_andesite');
  }
  if(placed){at(u,f.deck+3,1,'lantern',lantern);posts.push({face:f.name,at:[wx,f.floor,wz],topY:f.deck+3});}
 }
 for(const u of f.slots){
  const [wx,wz]=f.point(u,0);if(m.definitions[old[ix(wx,f.deck,wz)]].Name!=='minecraft:stone_bricks')continue;
  for(let du=-1;du<=1;du++)for(let y=f.floor+2;y<=f.floor+7;y++){
   if(du===0&&y>=f.floor+3&&y<=f.floor+6){at(u,y,0,'air');at(u,y,-1,'iron_bars',{north:f.inward==='east'||f.inward==='west'?'true':'false',south:f.inward==='east'||f.inward==='west'?'true':'false',east:f.inward==='north'||f.inward==='south'?'true':'false',west:f.inward==='north'||f.inward==='south'?'true':'false',waterlogged:'false'});at(u,y,-2,'air');}
   else at(u+du,y,0,y===f.floor+2||y===f.floor+7?'chiseled_stone_bricks':'smooth_quartz');
  }
  slits.push({face:f.name,at:[wx,f.floor+3,wz]});
 }
 for(const u of f.flags){
  const [wx,wz]=f.point(u,3);if(ground[wz*S+wx]<96||protectedCol[wz*S+wx])continue;
  for(let du=-1;du<=1;du++){at(u+du,f.deck,3,'dark_oak_log',{axis:f.name==='front'||f.name==='rear'?'x':'z'});for(let y=f.floor+3;y<=f.floor+7;y++)at(u+du,y,3,du===-1?'white_wool':'blue_wool');}
  banners.push({face:f.name,at:[wx,f.floor+3,wz],colors:['white','blue']});
 }
}
// Reuse the saved-world surface graph against the staged blocks, before writing.
const src=fs.readFileSync(path.join(root,'tools/maps/remodel_castle_fortress.cjs'),'utf8'),start=src.indexOf('const cache=new Map()'),end=src.indexOf('let count=0;',start);eval(src.slice(start,end)+'\nconsole.log({verifiedRoutes:targets.length,reachableSurfaces:seen.size});');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned edit');count++;}
const file=path.join(map,'region/r.0.0.mca'),r=patch(m,data,old,fs.readFileSync(file)),backup=path.join(root,'archive/map-edit-backups/before-castle-wall-detail/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(map,backup,{recursive:true});fs.writeFileSync(file+'.pending',r.bytes);fs.renameSync(file+'.pending',file);
const mf=path.join(map,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.castle.wallDetails={posts,slits,banners,slopedStoneBase:true,projectingCorbels:true,walkwayPreserved:true};manifest.rooms=manifest.rooms.map(r=>r.id===manifest.castle.id?manifest.castle:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(map,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.castle.id?{...z,...manifest.castle}:z),null,2));fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:r.changed,posts,slits,banners,backup:path.relative(root,backup)},null,2));console.log({changedBlocks:count,changedChunks:r.changed,posts:posts.length,slits:slits.length,banners:banners.length});
