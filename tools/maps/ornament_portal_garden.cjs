// Visual refinement after inspecting the saved portal: nested frame, fluted piers and richer beds.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/starter_sky_land_spawn_v1'),file=path.join(dir,'region/r.0.0.mca'),backup=path.join(root,'archive/map-edit-backups/portal-garden-before-ornament');assert(process.argv.includes('--apply'));assert(!fs.existsSync(backup));
const source=fs.readFileSync(file),m=createLibrary(path.join(root,'maps')).load('starter_sky_land_spawn_v1'),b=Buffer.from(m.blocks,'base64'),S=m.size,data=new Uint16Array(b.length/2),key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),ids=new Map(m.definitions.map((d,i)=>[key(d),i]));for(let i=0;i<data.length;i++)data[i]=b.readUInt16LE(i*2);const original=data.slice(),at=(x,y,z)=>(y-m.low)*S*S+z*S+x;
const id=(n,p)=>{const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}return ids.get(k);},get=(x,y,z)=>data[at(x,y,z)],name=(x,y,z)=>m.definitions[get(x,y,z)].Name.slice(10);
const changed=new Set();function put(x,y,z,n,p){assert(x>=325&&x<=371&&z>=192&&z<=242&&y>=108&&y<=132);assert(!(x>=336&&x<=339&&z>=218&&z<=221&&y<=112),'NPC reserve');const i=at(x,y,z);assert(m.definitions[original[i]].Name==='minecraft:flowering_azalea_leaves'||!/_(log|wood|leaves)$/.test(m.definitions[original[i]].Name),'Existing tree');const v=id(n,p);if(data[i]!==v){data[i]=v;changed.add(i);}}
const stairs=(f,half='bottom')=>({facing:f,half,shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'};
for(let dy=1;dy<=19;dy++){
 const outer=dy<=10?9:19-dy+1,inner=outer-2;
 for(let dz=-outer;dz<=outer;dz++)if(Math.abs(dz)>inner)for(const x of [347,348])put(x,110+dy,216+dz,(dy===4||dy===8||dy===12)?'polished_andesite':'stone_bricks');
 if(dy>=11)for(const side of [-1,1])put(346,110+dy,216+side*outer,'stone_brick_stairs',stairs(side<0?'south':'north','top'));
}
for(const z of [207,225]){
 for(let x=346;x<=348;x++)for(let zz=z-1;zz<=z+1;zz++)for(let y=108;y<=111;y++)put(x,y,zz,y===111?'chiseled_stone_bricks':'stone_bricks');
 for(let y=112;y<=122;y++){put(346,y,z,'polished_andesite');put(347,y,z,y===116||y===120?'chiseled_stone_bricks':'polished_andesite');}
 for(let x=345;x<=348;x++)put(x,123,z,'stone_brick_stairs',stairs(x<347?'east':'west','top'));
 put(346,124,z,'chiseled_stone_bricks');put(346,125,z,'stone_brick_slab',slab);
}
// Restrained blue/gold crest and paired lantern brackets on actual stone supports.
put(347,130,216,'chiseled_stone_bricks');put(347,131,216,'gold_block');for(const z of [211,221]){put(346,118,z,'chiseled_stone_bricks');put(345,119,z,'stone_brick_slab',slab);put(345,118,z,'lantern',{hanging:'true',waterlogged:'false'});}
let flowers=0,shrubs=0;for(const [cx,cz,rx,rz]of [[334,201,7,4],[335,233,7,4],[367,211,4,4],[366,225,4,4]])for(let z=cz-rz;z<=cz+rz;z++)for(let x=cx-rx;x<=cx+rx;x++){
 const d=Math.hypot((x-cx)/rx,(z-cz)/rz);if(d>1||name(x,108,z)!=='grass_block'||name(x,109,z)!=='air')continue;
 if((x+z)%7===0&&d>.6){put(x,109,z,'flowering_azalea_leaves',{persistent:'true',distance:'7',waterlogged:'false'});shrubs++;}else if((x*3+z)%3!==0){put(x,109,z,['allium','cornflower','oxeye_daisy','lily_of_the_valley'][(x+z)%4]);flowers++;}
}
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),out=patch(m,data,original,source);assert.equal(hash(fs.readFileSync(file)),hash(source));fs.mkdirSync(backup,{recursive:true});fs.writeFileSync(path.join(backup,'r.0.0.mca'),source);fs.writeFileSync(file+'.pending',out.bytes);fs.renameSync(file+'.pending',file);
const saved=createLibrary(path.join(root,'maps')).load('starter_sky_land_spawn_v1'),sb=Buffer.from(saved.blocks,'base64');for(let i=0;i<data.length;i++)assert.equal(key(saved.definitions[sb.readUInt16LE(i*2)]),key(m.definitions[data[i]]));
const base=createLibrary(path.join(root,'archive/map-edit-backups/before-zone08-garden-redesign')).load('starter_sky_land_spawn_v1'),ob=Buffer.from(base.blocks,'base64'),sk=saved.definitions.map(key),ok=base.definitions.map(key);let net=0;for(let i=0;i<data.length;i++)if(sk[sb.readUInt16LE(i*2)]!==ok[ob.readUInt16LE(i*2)])net++;
const reportFile=path.join(dir,'portal-garden-report.json'),r=JSON.parse(fs.readFileSync(reportFile));r.netChangedBlocks=net;r.resultHash=hash(out.bytes);r.ornament={changedBlocks:changed.size,changedChunks:out.changed,extraFlowers:flowers,extraShrubs:shrubs,frontArchX:[346,348],frontArchPeakY:129,crestY:131,backup:path.relative(root,backup)};fs.writeFileSync(reportFile,JSON.stringify(r,null,2));const mf=path.join(dir,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.blockStates=saved.definitions;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));console.log(JSON.stringify(r.ornament));
