// Approved Zone 03 paving, external curbs, double lanterns and flush lighting.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),DIR=path.join(ROOT,'maps/starter_sky_land_spawn_v1'),FILE=path.join(DIR,'region/r.0.0.mca'),BACKUP=path.join(ROOT,'archive/map-edit-backups/before-zone03-royal-avenue/starter_sky_land_spawn_v1');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());assert(process.argv.includes('--apply'));assert(!fs.existsSync(BACKUP),'Already redesigned');
const source=fs.readFileSync(FILE),m=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),S=m.size,b=Buffer.from(m.blocks,'base64'),data=new Uint16Array(b.length/2);for(let i=0;i<data.length;i++)data[i]=b.readUInt16LE(i*2);const original=data.slice(),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));
const zonesFile=path.join(DIR,'zones.json'),zones=JSON.parse(fs.readFileSync(zonesFile)),zone=zones.find(z=>String(z.id).startsWith('03')),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
const get=(x,y,z)=>data[at(x,y,z)],def=(x,y,z)=>m.definitions[get(x,y,z)],name=(x,y,z)=>def(x,y,z).Name.slice(10),orig=(x,y,z)=>m.definitions[original[at(x,y,z)]];
const flags=[...(zone.avenueLandscaping?.newFlags||[]).map(f=>f.base),...(zone.details.flags||[])];
const fountain=(x,y,z)=>x>=153&&x<=167&&z>=208&&z<=222&&y>=102&&y<=117;
const protectedAt=(x,y,z)=>fountain(x,y,z)||/_(log|wood|leaves)$/.test(orig(x,y,z).Name)||flags.some(([fx,,fz])=>Math.abs(x-fx)<=3&&Math.abs(z-fz)<=1&&y>=104&&y<=115&&orig(x,y,z).Name!=='minecraft:air');
function id(n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);}
const changed=new Map();function put(x,y,z,n,p,r){assert(x>=112&&x<=208&&z>=150&&z<=301&&y>=96&&y<=116,'Outside Zone03');const v=typeof n==='number'?n:id(n,p),i=at(x,y,z);assert(!protectedAt(x,y,z)||v===original[i],'Protected fountain/tree/flag '+[x,y,z]);if(data[i]!==v){data[i]=v;changed.set(i,r);}}
const stone=new Set(['stone_bricks','smooth_stone','polished_andesite','andesite','cobblestone','mossy_cobblestone','chiseled_stone_bricks']);
const levels=new Map((zone.details.profile||[]).map(p=>[p.z,p]));
for(let z=279;z<=301;z++){let y=108;while(y>95&&!stone.has(name(160,y,z))&&!name(160,y,z).endsWith('_stairs'))y--;levels.set(z,{z,y,stairs:name(160,y,z).endsWith('_stairs')});}
const half=z=>z>=197&&z<=233?6+Math.round(5*Math.sqrt(Math.max(0,1-Math.abs(z-215)/18))):6;
const paving=[],lights=[],curbs=[],lanterns=[],oldRemoved=[],skippedLamps=[];
function pave(x,y,z,edge=false){if(protectedAt(x,y,z)||!stone.has(name(x,y,z))||name(x,y+1,z)!=='air')return;const dx=Math.abs(x-160),d=Math.max(dx,Math.abs(z-215))+.414*Math.min(dx,Math.abs(z-215));let n='smooth_stone';
 if(z>=197&&z<=233){if(d>=10.3&&d<11.5||d>=7.4&&d<8.3)n='polished_andesite';else if((x+z)%9===0)n='stone_bricks';}
 else {const phase=((z-170)%12+12)%12;if(dx<=2&&dx+Math.abs(phase-6)<=2)n='polished_andesite';else if(edge)n='stone_bricks';else if(dx===4)n='polished_andesite';}
 put(x,y,z,n,undefined,'paving');paving.push([x,y,z]);
}
for(let z=150;z<=301;z++){
 const p=levels.get(z);if(!p||p.stairs)continue;const hw=half(z);for(let x=160-hw;x<=160+hw;x++)pave(x,p.y,z,Math.abs(x-160)>=hw-1);
 // Lights replace full-height pavers only. Slab/stair surface heights remain exactly unchanged.
 if(z%6===0)for(const x of [160-hw+1,160+hw-1])if(!protectedAt(x,p.y,z)&&stone.has(name(x,p.y,z))&&name(x,p.y+1,z)==='air'){put(x,p.y,z,'sea_lantern',undefined,'flush-light');lights.push([x,p.y,z]);}
}
for(let z=212;z<=218;z++)for(let x=112;x<=208;x++)if(Math.abs(x-160)>half(z)&&!fountain(x,103,z)){pave(x,103,z,z===212||z===218);if(x%8===0&&(z===213||z===217)&&stone.has(name(x,103,z))&&name(x,104,z)==='air'){put(x,103,z,'sea_lantern',undefined,'flush-light');lights.push([x,103,z]);}}
// Low external curbs, open at junctions, stair flights and bench recesses.
const slab={type:'bottom',waterlogged:'false'};
for(let z=150;z<=278;z++){
 const p=levels.get(z);if(!p||p.stairs||z>=210&&z<=220||z>=231&&z<=237||z===150)continue;const hw=half(z);
 for(const x of [159-hw,161+hw]){if(protectedAt(x,p.y+1,z)||name(x,p.y+1,z)!=='air'||!['grass_block','dirt',...stone].includes(name(x,p.y,z)))continue;put(x,p.y+1,z,'stone_brick_slab',slab,'curb');curbs.push([x,p.y+1,z]);}
}
// Branch curbs stop before the market guard's path near X113 Z219.
for(const z of [211,219])for(let x=118;x<=202;x++)if(x<=146||x>=174){if(protectedAt(x,104,z)||name(x,104,z)!=='air'||!['grass_block','dirt',...stone].includes(name(x,103,z)))continue;put(x,104,z,'stone_brick_slab',slab,'curb');curbs.push([x,104,z]);}
// Stage each old-lamp replacement independently; retain a fixture when its replacement cannot fit.
for(const [x,floor,oldZ]of zone.details.lamps){
 const z=oldZ===235?241:oldZ,plan=[],clear=[];
 for(let y=floor+1;y<=floor+5;y++){const n=name(x,y,oldZ);if(['chiseled_stone_bricks','oak_fence','lantern'].includes(n))clear.push([x,y,oldZ]);}
 for(let dx=-1;dx<=1;dx++)for(let dz=-1;dz<=1;dz++)plan.push([x+dx,floor,z+dz,'stone_bricks']);
 plan.push([x,floor+1,z,'chiseled_stone_bricks']);for(let y=floor+2;y<=floor+4;y++)plan.push([x,y,z,'polished_andesite']);plan.push([x,floor+5,z,'lapis_block'],[x,floor+6,z,'stone_brick_slab',slab]);
 for(const dz of [-1,1])plan.push([x,floor+5,z+dz,'stone_bricks'],[x,floor+6,z+dz,'stone_brick_slab',slab],[x,floor+4,z+dz,'lantern',{hanging:'true',waterlogged:'false'}]);
 const clearKeys=new Set(clear.map(p=>p.join(',')));let valid=true;
 for(const [px,py,pz]of plan){if(protectedAt(px,py,pz)){valid=false;break;}const n=name(px,py,pz);if(py>floor&&n!=='air'&&!clearKeys.has([px,py,pz].join(','))){valid=false;break;}if(py===floor&&!['grass_block','dirt',...stone].includes(n)){valid=false;break;}}
 if(!valid){skippedLamps.push([x,oldZ]);continue;}for(const [px,py,pz]of clear)put(px,py,pz,'air',undefined,'remove-old-lamp');oldRemoved.push([x,oldZ]);for(const [px,py,pz,n,p]of plan)put(px,py,pz,n,p,'double-lamp');lanterns.push({x,z,floorY:floor,oldZ,topY:floor+6.5,lanterns:[[x,floor+4,z-1],[x,floor+4,z+1]]});
}
// Supported flower pockets on verges, keeping road entrances and existing decorations untouched.
let flowers=0;for(let z=173;z<=276;z++)for(const x of [151,152,168,169]){
 if(z>=210&&z<=220||z%3!==0||protectedAt(x,104,z)||name(x,103,z)!=='grass_block'||name(x,104,z)!=='air')continue;put(x,104,z,(x+z)%2?'cornflower':'oxeye_daisy',undefined,'flower-verge');flowers++;
}
assert(lanterns.length>=10,'Too many replacement fixtures conflict with protected objects');assert(lights.length>=30);
let net=0;const counts={};for(const [i,r]of changed)if(data[i]!==original[i]){net++;counts[r]=(counts[r]||0)+1;const k=i%(S*S),x=k%S,z=Math.floor(k/S),y=m.low+Math.floor(i/(S*S));assert(!protectedAt(x,y,z));}
const beforeHash=hash(source);assert.equal(hash(fs.readFileSync(FILE)),beforeHash);const out=patch(m,data,original,source);fs.mkdirSync(path.dirname(BACKUP),{recursive:true});fs.cpSync(DIR,BACKUP,{recursive:true});fs.writeFileSync(FILE+'.pending',out.bytes);assert.equal(hash(fs.readFileSync(FILE)),beforeHash);fs.renameSync(FILE+'.pending',FILE);
const saved=createLibrary(path.join(ROOT,'maps')).load('starter_sky_land_spawn_v1'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]));
const report={zone:'03-streets-square',paving,lights,curbs,lanterns,oldRemoved,skippedLamps,flowers,netChangedBlocks:net,counts,changedChunks:out.changed,sourceHash:beforeHash,resultHash:hash(out.bytes),backup:path.relative(ROOT,BACKUP),verification:{savedSemanticComparison:true,fountainUnchanged:true,treesAndFlagsUnchanged:true,inGame:false,deployed:false}};
fs.writeFileSync(path.join(DIR,'royal-avenue-report.json'),JSON.stringify(report,null,2));zone.royalAvenue={pavingStyle:'light-stone-andesite-diamonds',mainClearWidth:13,curbsOutsideRoad:true,lights:lights.length,doubleLampPosts:lanterns.length,updated:'2026-10-27',deployed:false};fs.writeFileSync(zonesFile,JSON.stringify(zones,null,2));const mf=path.join(DIR,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.streetsSquare={...manifest.streetsSquare,royalAvenue:zone.royalAvenue};manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify({...report,paving:paving.length,lights:lights.length,curbs:curbs.length,lanterns:lanterns.length},null,2));
