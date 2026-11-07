// Stage by default. --apply writes the verified, locally scoped patch to the existing city.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/starter_sky_land_spawn_v1'),out=path.join(root,'docs/maps/zone07-blueprint'),report=path.join(dir,'front-defense-report.json');
if(fs.existsSync(report))throw Error('Defense already applied; inspect before editing');
const lib=createLibrary(path.dirname(dir)),m=lib.load(path.basename(dir)),buf=Buffer.from(m.blocks,'base64'),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
const old=new Uint16Array(buf.length/2);for(let i=0;i<old.length;i++)old[i]=buf.readUInt16LE(i*2);const data=old.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i])),planned=new Map();
const pre=createLibrary(path.join(root,'archive/map-edit-backups/before-governor-castle')).load(path.basename(dir)),pb=Buffer.from(pre.blocks,'base64'),protectedCols=new Set(),natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone','minecraft:mossy_cobblestone']);
const previous=(x,y,z)=>y>=pre.high?{Name:'minecraft:air'}:pre.definitions[pb.readUInt16LE(((y-pre.low)*S*S+z*S+x)*2)];
for(let z=40;z<=128;z++)for(let x=103;x<=217;x++)for(let y=90;y<pre.high;y++)if(!natural.has(previous(x,y,z).Name)){protectedCols.add(z*S+x);break;}
function put(x,y,z,d){if(x<103||x>217||z<40||z>128||y<90||y>135)throw Error('Outside defense scope '+[x,y,z]);if(x>=115&&x<=205&&z<=101)throw Error('Protected palace envelope '+[x,y,z]);if(protectedCols.has(z*S+x))throw Error('Earlier city structure '+[x,z]);if(typeof d==='string')d={Name:'minecraft:'+d};const k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}const i=ix(x,y,z);data[i]=ids.get(k);planned.set(i,[x,y,z]);}
const get=(x,y,z)=>m.definitions[data[ix(x,y,z)]],air={Name:'minecraft:air'},stair=f=>({Name:'minecraft:stone_brick_stairs',Properties:{facing:f,half:'bottom',shape:'straight',waterlogged:'false'}}),slab={Name:'minecraft:smooth_quartz_slab',Properties:{type:'bottom',waterlogged:'false'}};
// Remove only earlier castle fortifications in the selected front/side corridors.
for(let z=40;z<=128;z++)for(let x=104;x<=216;x++){
 const side=x<=114||x>=206,front=z>=120&&(x<=152||x>=168),leftJoint=x<=128&&z>=112,rightJoint=x>=192&&z>=112;
 if(!(side||front||leftJoint||rightJoint))continue;
 for(let y=113;y<=135;y++){const d=get(x,y,z);if(!natural.has(d.Name)&&key(d)!==key(previous(x,y,z)))put(x,y,z,air);}
}
const model=JSON.parse(fs.readFileSync(path.join(out,'bastions.json')));if(!model.blocks?.length)throw Error('Detailed bastion plan required');
function inBastion(x,z){return [125,195].some(cx=>Math.hypot(x-cx,z-112)<=9.1);}
for(let z=103;z<=125;z++)for(let x=116;x<=204;x++)if(inBastion(x,z)||x>=148&&x<=172&&z>=108)for(let y=108;y<=135;y++)put(x,y,z,air);
for(const b of model.blocks)put(...b.position,b.state);
// Doorways from the existing courtyard into the lower tower rooms.
for(const cx of [125,195])for(let x=cx-1;x<=cx+1;x++)for(let z=103;z<=107;z++)for(let y=113;y<=116;y++)put(x,y,z,air);
const cells=new Map(),walk=new Set(),sections=[];
function line(points,type){const centers=[];for(let j=0;j<points.length-1;j++){const [ax,az]=points[j],[bx,bz]=points[j+1],n=Math.max(Math.abs(bx-ax),Math.abs(bz-az));for(let t=0;t<=n;t++)centers.push([Math.round(ax+(bx-ax)*t/n),Math.round(az+(bz-az)*t/n)]);}
 for(const [cx,cz]of centers){for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(dx*dx+dz*dz<=6.3&&cz+dz>=40){const x=cx+dx,z=cz+dz,k=z*S+x;cells.set(k,{x,z,type});}for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++)if(cz+dz>=40)walk.add((cz+dz)*S+cx+dx);}
 sections.push({type,centerline:points});}
line([[132,112],[137,114],[142,117],[148,120],[150,120]],'frontWest');
line([[188,112],[183,114],[178,117],[172,120],[170,120]],'frontEast');
line([[119,106],[113,104],[109,100],[108,87],[108,63],[110,50],[112,40]],'sideWest');
line([[201,106],[207,104],[211,100],[212,87],[212,63],[210,50],[208,40]],'sideEast');
function deck(x,z,type){return type.startsWith('front')?128+Math.max(0,Math.min(4,(type==='frontWest'?x:320-x)-142)):124+Math.max(0,Math.min(4,z-83));}
// Five-block structure, three-block usable walk and protected parapets outside it.
for(const [k,c]of cells){const {x,z,type}=c,h=deck(x,z,type);let ground=-1;for(let y=107;y>=90;y--)if(get(x,y,z).Name!=='minecraft:air'){ground=y;break;}if(ground<0)throw Error('No land support '+[x,z]);
 for(let y=ground+1;y<=h;y++)put(x,y,z,y===h?'smooth_stone':'stone_bricks');
 for(let y=h+1;y<=Math.min(135,h+3);y++)put(x,y,z,air);
 if(walk.has(k)){
  const rise=type==='frontWest'?x>=143&&x<=146:type==='frontEast'?x>=174&&x<=177:z>=84&&z<=87;
  if(rise)put(x,h,z,stair(type==='frontWest'?'east':type==='frontEast'?'west':'south'));
 }else{
  put(x,h+1,z,'stone_bricks');if((x+z)%4<2)put(x,h+2,z,'chiseled_stone_bricks');else put(x,h+1,z,slab);
  if((x+z)%7===0)put(x,h-1,z,{Name:'minecraft:stone_brick_stairs',Properties:{facing:x<160?'east':'west',half:'top',shape:'straight',waterlogged:'false'}});
 }
}
// Carve only local wall ports through tower crowns and the gatehouse parapets.
for(const [k,c]of cells)if(walk.has(k)){const {x,z,type}=c,h=deck(x,z,type);for(let y=h+1;y<=Math.min(135,h+3);y++)put(x,y,z,air);}
// Make the tower rear entrance floors continuous with the original court.
for(const cx of [125,195])for(let z=103;z<=107;z++)for(let x=cx-1;x<=cx+1;x++){put(x,112,z,'stone_bricks');for(let y=113;y<=116;y++)put(x,y,z,air);}
// Exterior piers, slit recesses, heraldry and lamps remain outside the usable walk.
const lamp={Name:'minecraft:lantern',Properties:{hanging:'false',waterlogged:'false'}};
function sideEdge(z,left){const xs=[...cells.values()].filter(c=>c.z===z&&c.type===(left?'sideWest':'sideEast')).map(c=>c.x);return left?Math.min(...xs):Math.max(...xs);}
for(const left of [true,false]){
 const type=left?'sideWest':'sideEast',dir=left?-1:1;
 for(const z of [48,60,72,84,96]){const edge=sideEdge(z,left),x=edge+dir,h=deck(x,z,type);for(let dz=0;dz<=1;dz++){
  for(let y=112;y<=h+1;y++)put(x,y,z+dz,y===h+1?'chiseled_stone_bricks':'stone_bricks');put(x,h+2,z+dz,slab);
  put(x+dir,112,z+dz,stair(left?'east':'west'));
 }put(x,h+3,z,lamp);}
 for(const z of [54,66,78,90]){const edge=sideEdge(z,left),h=deck(edge,z,type);for(let y=h-6;y<=h-3;y++){
  put(edge,y,z,air);put(edge-dir,y,z,{Name:'minecraft:iron_bars',Properties:{north:'true',south:'true',east:'false',west:'false',waterlogged:'false'}});
 }for(let y=h-7;y<=h-2;y++)put(edge+dir,y,z+3,y===h-4?'white_wool':'blue_wool');}
}
for(const left of [true,false])for(const xx of [137,141,145]){const x=left?xx:320-xx,type=left?'frontWest':'frontEast',row=[...cells.values()].filter(c=>c.x===x&&c.type===type),z=Math.max(...row.map(c=>c.z))+1,h=deck(x,z,type);
 for(let y=112;y<=h+1;y++)put(x,y,z,y===h+1?'chiseled_stone_bricks':'stone_bricks');put(x,h+2,z,slab);put(x,h+3,z,lamp);put(x,112,z+1,stair('north'));
 if(xx===141)for(let y=h-6;y<=h-2;y++)put(x+1,y,z,y===h-4?'white_wool':'blue_wool');
}
for(const [,c]of cells)if(c.z===40){put(c.x,125,40,'stone_bricks');put(c.x,126,40,slab);}
// Half-block route audit across existing castle and all newly connected wall segments.
const pass=n=>n==='minecraft:air'||n.endsWith('_carpet')||n.includes('tulip')||n.includes('azure_bluet')||n.includes('torch'),shapeCache=new Map();
function shape(id){if(shapeCache.has(id))return shapeCache.get(id);const d=m.definitions[id],n=d.Name,p=d.Properties||{};let a;if(pass(n))a=[];else if(n.endsWith('_stairs')){const top=p.half==='top',lo=top?0:.5,hi=top?.5:1;a=[top?[0,.5,0,1,1,1]:[0,0,0,1,.5,1],p.facing==='north'?[0,lo,0,1,hi,.5]:p.facing==='south'?[0,lo,.5,1,hi,1]:p.facing==='east'?[.5,lo,0,1,hi,1]:[0,lo,0,.5,hi,1]];}else if(n.endsWith('_slab'))a=p.type==='top'?[[0,.5,0,1,1,1]]:p.type==='double'?[[0,0,0,1,1,1]]:[[0,0,0,1,.5,1]];else a=[[0,0,0,1,1,1]];shapeCache.set(id,a);return a;}
const solid=(x,y,z)=>shape(data[ix(Math.floor(x),Math.floor(y),Math.floor(z))]).some(([a,b,c,d,e,f])=>x%1>=a&&x%1<d&&y%1>=b&&y%1<e&&z%1>=c&&z%1<f),cache=new Map();
function surfaces(hx,hz){const k=hx+','+hz;if(cache.has(k))return cache.get(k);const x=hx/2+.25,z=hz/2+.25,a=new Set();for(let y=108;y<=157;y++)for(const c of shape(data[ix(Math.floor(x),y,Math.floor(z))])){
 if(x%1<c[0]||x%1>=c[3]||z%1<c[2]||z%1>=c[5])continue;const h=y+c[4];if(!Number.isInteger(h*2))continue;let free=true;for(let yy=h+.125;yy<h+2;yy+=.25)if(solid(x,yy,z))free=false;if(free)a.add(h*2);
 }cache.set(k,[...a]);return cache.get(k);}
const seen=new Set(),queue=[[320,298,218]],pk=(x,z,h)=>`${x},${z},${h}`;for(let i=0;i<queue.length;i++){const [x,z,h]=queue[i],k=pk(x,z,h);if(seen.has(k)||!surfaces(x,z).includes(h))continue;seen.add(k);for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]])if(nx>=206&&nx<=434&&nz>=78&&nz<=300)for(const nh of surfaces(nx,nz))if(Math.abs(nh-h)<=1&&!seen.has(pk(nx,nz,nh)))queue.push([nx,nz,nh]);}
const targets=[['court',160,113,106],['palace hall',160,117,80],['west palace',133,117,82],['east palace',187,117,82],['gallery',140,129,60],['keep upper',163,153,48],['old gate guard',160,141,90],['old gate deck',160,149,96],['keep deck',160,157,45],['new gate floor',160,113,120],['new gate deck',160,133,116],['west tower room',125,113,112],['west tower deck',125,129,112],['east tower room',195,113,112],['east tower deck',195,129,112],['west front wall',140,129,116],['east front wall',180,129,116],['west side wall',108,125,63],['east side wall',212,125,63]];
const failed=targets.filter(([,x,h,z])=>!seen.has(pk(x*2,z*2,h*2)));if(failed.length)throw Error('Unreachable '+JSON.stringify(failed.map(t=>({target:t,surfaces:surfaces(t[1]*2,t[3]*2)}))));
let changed=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned voxel');changed++;}
const file=path.join(dir,'region/r.0.0.mca'),source=fs.readFileSync(file),sourceHash=crypto.createHash('sha256').update(source).digest('hex'),previewBlocks=[];for(const [i,position]of planned)if(m.definitions[data[i]].Name!=='minecraft:air'&&position[1]>=108)previewBlocks.push({position,state:m.definitions[data[i]]});
const audit={stage:'verified staged front and side defense',sourceHash,changedBlocks:changed,protectedOriginalColumns:protectedCols.size,sections,reachableTargets:targets,routeMethod:'half-block surfaces and 2-block point headroom; not full entity collision simulation',outsideChanges:0,palaceEnvelopeProtected:true,rearWallUnchanged:true,preview:{center:[160,124,84],distance:135,focuses:{west:{center:[119,124,84],distance:105},east:{center:[201,124,84],distance:105},gate:{center:[160,126,119],distance:60}}},blocks:previewBlocks};
fs.writeFileSync(path.join(out,'front-defense.json'),JSON.stringify(audit));
const html=fs.readFileSync(path.join(out,'bastions-3d.html'),'utf8').replace('ป้อมหน้าและหอประตู · ตรวจแบบ 3D','กำแพงหน้าและด้านข้าง · ตรวจแบบ 3D').replace('<script src="bastions-3d.js">','<script src="bastions-3d.js" data-model="front-defense.json">').replace('ข้อมูลบล็อกชุดเดียวกับแปลน','แบบกำแพงหน้า–ด้านข้างครบชุด · แนวหลังคงเดิม');fs.writeFileSync(path.join(out,'front-defense-3d.html'),html);
console.log({...audit,blocks:previewBlocks.length});
if(process.argv.includes('--apply')){
 const backup=path.join(root,'archive/map-edit-backups/before-front-defense/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(dir,backup,{recursive:true});
 const result=patch(m,data,old,source);if(crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')!==sourceHash)throw Error('World changed during staging');fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
 const saved=lib.load(path.basename(dir)),sb=Buffer.from(saved.blocks,'base64'),savedKeys=saved.definitions.map(key),expectedKeys=m.definitions.map(key);for(let i=0;i<data.length;i++)if(savedKeys[sb.readUInt16LE(i*2)]!==expectedKeys[data[i]])throw Error('Saved world mismatch '+i);
 const mf=path.join(dir,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf)),profile={stage:'front and side defense applied; original rear and palace retained',outerEntrance:[160,113,124],gateDeckY:132,bastionDeckY:128,sideDeckY:124,minimumWalkWidth:3,sections,verifiedTargets:targets,backup:path.relative(root,backup)};
 manifest.castle.frontDefense=profile;manifest.rooms=(manifest.rooms||[]).map(r=>r.id===manifest.castle.id?manifest.castle:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));const zf=path.join(dir,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.castle.id?{...z,frontDefense:profile}:z),null,2));
 audit.stage='applied to saved working map';audit.applied=true;fs.writeFileSync(path.join(out,'front-defense.json'),JSON.stringify(audit));fs.writeFileSync(path.join(out,'front-defense-3d.html'),html.replace('ยังไม่แก้แมพจริง','ชุดนี้บันทึกลงแมพเดิมแล้ว · ยังไม่ทดสอบในเกม'));
 fs.writeFileSync(report,JSON.stringify({...audit,blocks:undefined,changedChunks:result.changed,backup:path.relative(root,backup),inGameTested:false},null,2));console.log({applied:true,changedBlocks:changed,changedChunks:result.changed,backup});
}
