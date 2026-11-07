const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'castle-finishing-report.json');if(fs.existsSync(rf))throw Error('Already finished');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x,ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
function put(x,y,z,n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);}
for(const [x0,x1,z0,z1,eave]of [[118,142,42,92,139],[178,202,42,92,139],[142,178,58,94,141]])for(const z of [z0,z1])for(let x=x0;x<=x1;x++){
 const cx=(x0+x1)/2,hw=(x1-x0)/2,roof=eave+Math.floor((hw+1-Math.abs(x-cx))*.7);
 for(let y=eave;y<roof;y++)if(m.definitions[data[ix(x,y,z)]].Name==='minecraft:air')put(x,y,z,x===cx?'smooth_quartz':y%4===0?'stone_bricks':'calcite');
}
// Properly support the meeting furniture and link its platform to both upper galleries.
for(let x=149;x<=171;x++)for(let z=74;z<=84;z++)put(x,128,z,'spruce_planks');
for(let x=149;x<=171;x++)for(let z=90;z<=95;z++)put(x,128,z,'spruce_planks');
for(let x=157;x<=163;x++)for(let z=94;z<=95;z++)for(let y=129;y<=133;y++)put(x,y,z,'air');
for(const z of [74,84])for(let x=150;x<=170;x++)put(x,129,z,'stone_brick_wall',{up:'true',north:'none',south:'none',east:'low',west:'low',waterlogged:'false'});
// Access to the top of the curtain wall from the courtyard.
for(let i=0;i<4;i++)for(let z=109;z<=112;z++){
 const x=115-i,y=113+i;for(let yy=112;yy<y;yy++)put(x,yy,z,'stone_bricks');put(x,y,z,'stone_brick_stairs',{facing:'west',half:'bottom',shape:'straight',waterlogged:'false'});
}
for(let x=109;x<=111;x++)for(let z=109;z<=112;z++)put(x,116,z,'stone_bricks');
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;const file=path.join(map,'region/r.0.0.mca'),r=patch(m,data,old,fs.readFileSync(file));fs.writeFileSync(file+'.pending',r.bytes);fs.renameSync(file+'.pending',file);fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:r.changed,backup:'archive/map-edit-backups/before-governor-castle/starter_sky_land_spawn_v1'},null,2));console.log({changedBlocks:count,changedChunks:r.changed,modelHigh:m.high});
