const fs=require('node:fs'),path=require('node:path'),{createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),rf=path.join(map,'blacksmith-gable-report.json');if(fs.existsSync(rf))throw Error('Already detailed');
const m=createLibrary(path.dirname(map)).load(path.basename(map)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);const data=old.slice(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i])),ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
function put(x,y,z,n,p){const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);}
// Raised gable window frames and horizontal beams break up the broad timber face.
for(const z of [137,149]){
 for(let x=75;x<=87;x++)put(x,118,z,'dark_oak_log',{axis:'x'});
 for(let x=79;x<=83;x++)for(let y=119;y<=122;y++)put(x,y,z,x===79||x===83||y===119||y===122?'dark_oak_planks':'light_blue_stained_glass');
}
for(let x=70;x<=74;x++)for(let y=116;y<=118;y++)put(x,y,161,x===70||x===74||y===116||y===118?'dark_oak_planks':'light_blue_stained_glass');
for(const [x,f]of [[81,'west'],[93,'east']])put(x,112,160,'dark_oak_stairs',{facing:f,half:'top',shape:'straight',waterlogged:'false'});
let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;const file=path.join(map,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file));fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);fs.writeFileSync(rf,JSON.stringify({changedBlocks:count,changedChunks:result.changed,backup:'archive/map-edit-backups/before-blacksmith/starter_sky_land_spawn_v1'},null,2));console.log({changedBlocks:count,changedChunks:result.changed});
