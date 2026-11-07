// Approved Zone 08 redesign. Saved-world patch only; no server or NPC entity mutation.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary,readNbt}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs'),{patchRegion}=require('./nbt-edit.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-zone08-garden-redesign/starter_sky_land_spawn_v1');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
assert(process.argv.includes('--apply'),'Use --apply');assert(!fs.existsSync(BACKUP),'Already redesigned; patch current garden instead');
const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),baseline=createLibrary(path.join(ROOT,'archive/map-edit-backups/before-zone08-portal')).load('starter_sky_land_spawn_v1'),raw=Buffer.from(m.blocks,'base64'),bb=Buffer.from(baseline.blocks,'base64'),S=m.size,data=new Uint16Array(raw.length/2),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
for(let i=0;i<data.length;i++)data[i]=raw.readUInt16LE(i*2);const original=data.slice(),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
function id(n,p){const d=typeof n==='object'?n:{Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const air=id('air'),get=(x,y,z)=>data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name;
const npc=(x,y,z)=>x>=336&&x<=339&&z>=218&&z<=221&&y>=108&&y<=112;
const treeProtected=i=>{const n=m.definitions[original[i]].Name;return /_(log|wood|leaves)$/.test(n)&&i%(S*S)%S<320;};
const changed=new Map();function put(x,y,z,n,p,reason='detail'){
 assert(x>=277&&x<=375&&z>=186&&z<=246&&y>=40&&y<=138,'Outside approved Zone 08 envelope '+[x,y,z]);assert(y>=m.low&&y<m.high);
 const i=at(x,y,z),v=typeof n==='number'?n:id(n,p);if(npc(x,y,z))return;assert(!treeProtected(i)||v===original[i],'Protected city tree');if(data[i]!==v){data[i]=v;changed.set(i,reason);}
}
const fill=(x1,y1,z1,x2,y2,z2,n,p,r)=>{for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,n,p,r);};
const stair=(facing,half='bottom')=>({facing,half,shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},wall={up:'true',north:'none',south:'none',east:'none',west:'none',waterlogged:'false'},fence={north:'false',south:'false',east:'false',west:'false',waterlogged:'false'};
// Restore only the old portal-area delta; never replay the entire archived map.
for(let z=196;z<=240;z++)for(let x=277;x<=362;x++)for(let y=40;y<=135;y++){
 const i=at(x,y,z);if(treeProtected(i)||npc(x,y,z))continue;const bi=(y-baseline.low)*S*S+z*S+x,d=y>=baseline.low&&y<baseline.high?baseline.definitions[bb.readUInt16LE(bi*2)]:{Name:'minecraft:air'};if(key(m.definitions[get(x,y,z)])!==key(d))put(x,y,z,id(d),undefined,'remove-old-zone08');
}
// Teardrop footprint: western neck, broad garden shoulders, rounded eastern rear.
const land=new Uint8Array(S*S),ground=new Int16Array(S*S).fill(-1);
function halfWidth(x){return x<=349?3+24*Math.sin((x-320)/29*Math.PI/2)**.8:27*Math.sqrt(Math.max(0,1-((x-349)/25)**2));}
for(let x=320;x<=374;x++)for(let z=190;z<=244;z++){
 const w=halfWidth(x);if(Math.abs(z-217)>w)continue;const k=z*S+x;land[k]=1;ground[k]=108;
 const radial=Math.abs(z-217)/Math.max(1,w),along=Math.abs(x-349)/29,shape=Math.max(radial,along*.85),tooth=(Math.sin(x*.47+z*.31)+Math.cos(z*.59))*2;
 const lo=Math.max(44,Math.round(103-(1-shape)**.7*59+tooth));for(let y=lo;y<=108;y++)put(x,y,z,y===108?'grass_block':y>=106?'dirt':y<lo+3?'deepslate':(x+z+y)%19===0?'andesite':(x*3+z+y)%31===0?'tuff':'stone',y===108?{snowy:'false'}:undefined,'island');
}
// Arrival plaza, side loop and patterned ring. Keep Hannah's existing standing cell unchanged.
for(let x=320;x<=371;x++)for(let z=191;z<=243;z++)if(land[z*S+x]){
 const d=Math.hypot(x-337,z-216),loop=Math.hypot((x-349)/20,(z-217)/22);if(d<=13||loop>=.68&&loop<=.83||Math.abs(z-216)<=2&&x<=350){const n=d>=11.5&&d<=13?'stone_bricks':d>=8.7&&d<=9.8?'chiseled_stone_bricks':(x+z)%7===0?'polished_andesite':'smooth_stone';put(x,108,z,n,undefined,'plaza');}
}
// Retaining parapet follows the actual footprint, leaving only the 5-wide west entrance open.
let parapet=0;for(let x=320;x<=374;x++)for(let z=190;z<=244;z++)if(land[z*S+x]&&[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!land[(z+dz)*S+x+dx])){
 if(x<=322&&Math.abs(z-216)<=3)continue;put(x,108,z,'stone_bricks',undefined,'parapet');put(x,109,z,'stone_brick_wall',wall,'parapet');if((x+z)%11===0){put(x,109,z,'chiseled_stone_bricks',undefined,'parapet');put(x,110,z,'stone_brick_slab',slab,'parapet');}parapet++;
}
const centerZ=x=>x<=278?218:x<=291?Math.round(218+(x-278)*6/13):x<=305?Math.round(224+2*Math.sin((x-292)/13*Math.PI/2)):Math.round(226-10*((x-305)/13)**1.25);
const feet=x=>x===277?108:x===278?108.5:x<=291?109:Math.round((109+Math.max(0,1-Math.abs(x-304)/12))*2)/2;
const bridge=[];
for(let x=277;x<=319;x++){
 const cz=centerZ(x),h=feet(x),y=Number.isInteger(h)?h-1:Math.floor(h),half=!Number.isInteger(h),width=x<279?1:2;bridge.push({x,z:cz,feet:h,halfWidth:width});
 for(let dz=-width;dz<=width;dz++){
  const z=cz+dz;for(let yy=y+1;yy<=113;yy++)if(!treeProtected(at(x,yy,z)))put(x,yy,z,air,undefined,'bridge-clearance');put(x,y,z,half?(x<292?'stone_brick_slab':'spruce_slab'):(x<292?'smooth_stone':'dark_oak_planks'),half?slab:undefined,'bridge');
  if(x<=291){for(let yy=y-1;yy>=94;yy--){if(get(x,yy,z)!==air)break;put(x,yy,z,'stone_bricks',undefined,'promenade-support');}}
  else put(x,y-1,z,'spruce_log',{axis:'z'},'bridge-support');
 }
 if(x>=279){for(const dz of [-3,3]){
  const z=cz+dz;put(x,y,z,'stone_bricks',undefined,'bridge-rail');if(!(dz===3&&x>=302&&x<=306)){put(x,y+1,z,'stone_brick_wall',wall,'bridge-rail');}
  if(x>=292&&x<=318){const t=(x-292)/26,lo=Math.round(y-4-17*Math.abs(t-.5)**1.7);for(let yy=lo;yy<y;yy++)put(x,yy,z,yy===lo?'stone_brick_stairs':'stone_bricks',yy===lo?stair(x<305?'east':'west','top'):undefined,'arch-rib');}
 }}
}
// Viewing bay joins only where bridge feet are 110; rails do not cut across the entrance.
for(let x=301;x<=307;x++)for(let z=229;z<=234;z++){put(x,109,z,'smooth_stone',undefined,'viewing-bay');put(x,108,z,'stone_brick_stairs',stair('north','top'),'viewing-bay');if(z===234||x===301||x===307)put(x,110,z,'stone_brick_wall',wall,'viewing-bay');}
for(let x=303;x<=305;x++)put(x,110,233,'stone_brick_stairs',stair('north'),'viewing-bench');
// Detailed piers and properly supported hanging lights outside the clear deck.
for(const x of [282,290,296,312,318])for(const side of [-1,1]){
 const cz=centerZ(x),y=Math.floor(feet(x)),z=cz+side*4;fill(x,y-1,z,x+1,y,z,'stone_bricks',undefined,'pier');fill(x,y+1,z,x,y+3,z,'chiseled_stone_bricks',undefined,'pier');put(x,y+4,z,'stone_brick_slab',slab,'pier');put(x,y+2,z-side,'oak_fence',fence,'pier');put(x,y+3,z-side,'spruce_log',{axis:'z'},'pier');put(x,y+2,z-side,'lantern',{hanging:'true',waterlogged:'false'},'light');
}
// Portal terrace and four half-step approaches, connected to the level garden loop.
fill(347,108,207,357,110,225,'stone_bricks',undefined,'portal-pedestal');
for(let z=207;z<=225;z++)for(let i=0;i<3;i++){const x=344+i,h=109.5+i*.5,y=Math.floor(h)-(Number.isInteger(h)?1:0);put(x,y,z,Number.isInteger(h)?'smooth_stone':'stone_brick_slab',Number.isInteger(h)?undefined:slab,'portal-steps');}
for(let x=347;x<=357;x++)for(const side of [-1,1])for(let i=1;i<=3;i++){const z=(side<0?207:225)+side*i,h=111-i*.5,y=Math.floor(h)-(Number.isInteger(h)?1:0);put(x,y,z,Number.isInteger(h)?'smooth_stone':'stone_brick_slab',Number.isInteger(h)?undefined:slab,'portal-side-steps');}
for(let z=207;z<=225;z++)for(let i=1;i<=3;i++){const x=357+i,h=111-i*.5,y=Math.floor(h)-(Number.isInteger(h)?1:0);put(x,y,z,Number.isInteger(h)?'smooth_stone':'stone_brick_slab',Number.isInteger(h)?undefined:slab,'portal-rear-steps');}
// Double stone arch, recessed turquoise aperture, structurally connected arch shoulders.
for(let dy=1;dy<=17;dy++){
 const outer=dy<=10?7:18-dy,inner=outer-2;
 for(let dz=-outer;dz<=outer;dz++)for(let x=350;x<=353;x++){
  const opening=Math.abs(dz)<=inner&&dy<=15;put(x,110+dy,216+dz,opening?(x===350?'cyan_stained_glass':x===351?'light_blue_stained_glass':'sea_lantern'):(Math.abs(dz)===outer?'chiseled_stone_bricks':'stone_bricks'),undefined,'portal-arch');
 }
 for(const side of [-1,1]){const z=216+side*outer;put(349,110+dy,z,dy%3===0?'polished_andesite':'stone_brick_stairs',dy%3===0?undefined:stair(side<0?'south':'north',dy<=10?'bottom':'top'),'portal-trim');}
}
for(const z of [208,210,222,224]){fill(348,110,z,349,121,z,'stone_bricks',undefined,'portal-piers');put(348,112,z,'chiseled_stone_bricks',undefined,'portal-piers');put(348,119,z,'lantern',{hanging:'false',waterlogged:'false'},'portal-piers');put(348,122,z,'stone_brick_slab',slab,'portal-piers');}
put(351,128,216,'amethyst_block',undefined,'portal-crown');put(351,129,216,'amethyst_cluster',{facing:'up',waterlogged:'false'},'portal-crown');
// Benches at side gardens, outside the through route and NPC standing reserve.
for(const z of [200,233])for(let x=337;x<=340;x++){put(x,109,z,'stone_brick_stairs',stair(z<216?'south':'north'),'bench');put(x,109,z+(z<216?-1:1),'stone_brick_slab',slab,'bench');}
let flowers=0;for(let x=327;x<=370;x++)for(let z=192;z<=242;z++)if(land[z*S+x]&&name(x,108,z)==='minecraft:grass_block'&&get(x,109,z)===air&&Math.abs(z-216)>9){
 if((x*13+z*7)%17===0){put(x,109,z,['allium','cornflower','oxeye_daisy'][(x+z)%3],undefined,'flowers');flowers++;}else if((x*3+z)%43===0)put(x,109,z,'flowering_azalea_leaves',{persistent:'true',distance:'7',waterlogged:'false'},'shrub');
}
const trees=[];for(const [asset,tx,tz]of [['t018',362,201],['t024',362,233]]){
 const t=readNbt(zlib.gunzipSync(fs.readFileSync(path.join(ROOT,'maps/tree_library/structures',asset+'.nbt')))),logs=t.blocks.filter(b=>/_(log|wood)$/.test(t.palette[b.state].Name)),low=Math.min(...logs.map(b=>b.pos[1])),roots=logs.filter(b=>b.pos[1]===low),ax=Math.round(roots.reduce((s,b)=>s+b.pos[0],0)/roots.length),az=Math.round(roots.reduce((s,b)=>s+b.pos[2],0)/roots.length),ox=tx-ax,oz=tz-az,oy=109-low;
 for(const r of roots)assert(land[(oz+r.pos[2])*S+ox+r.pos[0]],'Unsupported tree root');
 for(const b of t.blocks){const x=ox+b.pos[0],y=oy+b.pos[1],z=oz+b.pos[2],p=t.palette[b.state],props={...p.Properties};assert(get(x,y,z)===air||['minecraft:allium','minecraft:cornflower','minecraft:oxeye_daisy','minecraft:flowering_azalea_leaves'].includes(name(x,y,z)),'Tree collides with architecture');if(p.Name.endsWith('_leaves'))Object.assign(props,{persistent:'true',distance:'7',waterlogged:'false'});put(x,y,z,id({Name:p.Name,...(Object.keys(props).length?{Properties:props}:{})}),undefined,'tree');}trees.push({asset,anchor:[tx,109,tz],origin:[ox,oy,oz],size:t.size});
}
// Keep the full 5-wide bridge deck and main plaza-to-portal approach unobstructed.
for(const p of bridge){const y=Math.floor(p.feet);for(let dz=-p.halfWidth;dz<=p.halfWidth;dz++)for(let yy=Math.ceil(p.feet);yy<=y+3;yy++)assert(get(p.x,yy,p.z+dz)===air,'Bridge obstruction '+[p.x,yy,p.z+dz]);}
for(let x=320;x<=343;x++)for(let dz=-2;dz<=2;dz++)for(let y=109;y<=111;y++)assert(get(x,y,216+dz)===air,'Plaza route obstructed');
for(let z=218;z<=221;z++)for(let x=336;x<=339;x++)for(let y=108;y<=112;y++)assert.equal(get(x,y,z),original[at(x,y,z)],'NPC reserve changed');
let net=0;const counts={};for(const [i,r]of changed)if(data[i]!==original[i]){net++;counts[r]=(counts[r]||0)+1;}
assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent city update');const out=patch(m,data,original,source);
// Remove only obsolete block entities inside changed cells (e.g. the old lectern).
const cleaned=patchRegion(out.bytes,tree=>{const c=tree.value,list=c.block_entities?.value?.values;if(!list)return false;const keep=list.filter(e=>{const x=e.x.value,y=e.y.value,z=e.z.value;if(x<277||x>375||z<186||z>246||y<m.low||y>=m.high)return true;return !changed.has(at(x,y,z))||name(x,y,z)===e.id.value;});if(keep.length===list.length)return false;c.block_entities.value.values=keep;return true;});
fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',cleaned.bytes);assert.equal(hash(fs.readFileSync(FILE)),hash(source),'Concurrent change before commit');fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),savedBytes=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[savedBytes.readUInt16LE(i*2)]),key(m.definitions[data[i]]),'Saved semantic mismatch');
const zone={id:'08-dimensional-station',name:'Portal Garden Island',bounds:[277,186,375,246],center:[344,108,216],islandBounds:[320,190,374,244],bridgeBounds:[277,214,319,235],portalCenter:[351,110,216],floorY:108,portalFloorY:110,status:'active',details:{clearBridgeWidth:5,spanLength:26,portalDimensions:[15,17],geometry:'curved_stone_timber_bridge_and_terraced_garden',gameplayConnected:false}};
const zf=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));const zi=zones.findIndex(z=>String(z.id).startsWith('08'));assert(zi>=0);zones[zi]=zone;fs.writeFileSync(zf,JSON.stringify(zones,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.rooms=manifest.rooms.map(r=>String(r.id).startsWith('08')?zone:r);if(Array.isArray(manifest.zones))manifest.zones=manifest.zones.map(z=>String(z.id).startsWith('08')?zone:z);manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
const report={zone,bridge,trees,flowers,parapetColumns:parapet,netChangedBlocks:net,counts,changedChunks:out.changed,sourceHash:hash(source),resultHash:hash(cleaned.bytes),backup:path.relative(ROOT,BACKUP),verification:{savedSemanticComparison:true,npcStandingReserveUnchanged:true,protectedCityTreeChanges:0,inGame:false,deployed:false}};fs.writeFileSync(path.join(DIR,'portal-garden-report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({...report,bridge:undefined,trees:trees.length},null,2));
