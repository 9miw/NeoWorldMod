const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const map=path.resolve(__dirname,'../../maps/starter_sky_land_spawn_v1'),m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
function put(x,y,z,n){const d={Name:'minecraft:'+n},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);}
const towers=[[125,47,150,165],[195,47,150,165],[125,92,142,157],[195,92,142,157]],towerVoxel=(x,y,z)=>towers.some(([cx,cz,top,tip])=>y<=top?Math.hypot(x-cx,z-cz)<=7.2:y<=tip&&Math.hypot(x-cx,z-cz)<=(tip-y)*7/(tip-top)+.6),smallRoof=(x,y,z)=>[130,190].some(cx=>Math.abs(x-cx)<=7&&z>=59&&z<=73&&y===141+Math.floor((7-Math.max(Math.abs(x-cx),Math.abs(z-66)))*.7));
// Old pitched-roof fragments had been inside the original keep/tower protection masks.
for(let x=117;x<=203;x++)for(let z=41;z<=95;z++)for(let y=139;y<=156;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:blue_concrete'&&!towerVoxel(x,y,z)&&!smallRoof(x,y,z))put(x,y,z,'air');
for(const [x0,x1,z0,z1,lo]of [[118,142,42,92,142],[178,202,42,92,142]])for(const z of [z0,z1])for(let x=x0;x<=x1;x++)for(let y=lo;y<=150;y++){
 if(towerVoxel(x,y,z))continue;const n=m.definitions[data[ix(x,y,z)]].Name;if(['minecraft:calcite','minecraft:smooth_quartz','minecraft:stone_bricks'].includes(n))put(x,y,z,'air');
}
for(let x=142;x<=178;x++)for(let y=142;y<=153;y++){
 if(x===149||x===171||y===152)continue;const n=m.definitions[data[ix(x,y,58)]].Name;if(['minecraft:calcite','minecraft:smooth_quartz','minecraft:stone_bricks'].includes(n))put(x,y,58,'air');
 if(x>=149&&x<=171)continue;const nn=m.definitions[data[ix(x,y,94)]].Name;if(['minecraft:calcite','minecraft:smooth_quartz'].includes(nn))put(x,y,94,'air');
}
// Deep-framed arrow windows and bands give the gatehouse its own stone articulation.
for(const cx of [154,160,166])for(let x=cx-1;x<=cx+1;x++)for(let y=142;y<=147;y++)put(x,y,99,x===cx&&y>=143&&y<=146?'light_blue_stained_glass':'smooth_quartz');
for(let x=149;x<=171;x++)for(const y of [140,148])put(x,y,99,'chiseled_stone_bricks');
const file=path.join(map,'region/r.0.0.mca'),r=patch(m,data,old,fs.readFileSync(file));fs.writeFileSync(file+'.pending',r.bytes);fs.renameSync(file+'.pending',file);let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;console.log({changedBlocks:count,changedChunks:r.changed});
