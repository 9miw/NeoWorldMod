const fs=require('node:fs'),path=require('node:path');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const ROOT=path.resolve(__dirname,'../..'),MAP=path.join(ROOT,'maps/starter_sky_land_spawn_v1');
function run(){
 const report=path.join(MAP,'market-layout-report.json');if(fs.existsSync(report))throw Error('Layout already exists; inspect before editing');
 const m=createLibrary(path.dirname(MAP)).load(path.basename(MAP)),b=Buffer.from(m.blocks,'base64'),old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);
 const data=old.slice(),ids=new Map(m.definitions.map((d,i)=>[JSON.stringify(d),i]));
 const ix=(x,y,z)=>(y-m.low)*m.size*m.size+z*m.size+x;
 const get=(x,y,z)=>m.definitions[old[ix(x,y,z)]].Name;
 function put(x,y,z,n,p){if(x<47||x>127||z<172||z>255||y<90||y>123)throw Error('Out of bounds');const d={Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=JSON.stringify(d);if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=ids.get(k);}
 const natural=new Set(['minecraft:air','minecraft:grass_block','minecraft:dirt','minecraft:stone','minecraft:andesite','minecraft:cobblestone']);
 // Rounded terrain bank surrounds the market without altering the island outline or built columns.
 for(let z=172;z<=255;z++)for(let x=47;x<=127;x++){
  if(get(x,90,z)==='minecraft:air')continue;let top=90,built=false;for(let y=90;y<m.high;y++){const n=get(x,y,z);if(!natural.has(n)){built=true;break;}if(n!=='minecraft:air')top=y;}if(built)continue;
  const dist=Math.hypot(Math.max(65-x,0,x-111),Math.max(190-z,0,z-237));if(dist>18)continue;
  const t=Math.max(0,1-dist/18),target=Math.round(top+(103-top)*t*t*(3-2*t));
  for(let y=Math.min(top,target)-2;y<=Math.max(top,target);y++)put(x,y,z,y>target?'air':y===target?'grass_block':y>=target-2?'dirt':'stone',y===target?{snowy:'false'}:undefined);
 }
 // Rounded paving and a seven-block connection to the existing western branch.
 for(let z=190;z<=237;z++)for(let x=65;x<=111;x++){
  const corner=Math.hypot(Math.max(70-x,0,x-106),Math.max(195-z,0,z-232));if(corner>5)continue;
  put(x,103,z,'stone_bricks');
 }
 for(let x=106;x<=112;x++)for(let z=212;z<=218;z++)put(x,103,z,'smooth_stone');
 // Reserved north route for the smith, and three open stall plots (no awnings yet).
 for(let z=181;z<=190;z++)for(let x=98;x<=104;x++){for(let y=96;y<103;y++)if(get(x,y,z)==='minecraft:air')put(x,y,z,y>=101?'dirt':'stone');put(x,103,z,'smooth_stone');}
 const stalls=[[69,220,77,226],[92,221,100,227],[81,230,89,235]];
 for(const [x0,z0,x1,z1]of stalls)for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)if(x===x0||x===x1||z===z0||z===z1)put(x,103,z,'orange_terracotta');
 const shops=[{id:'general',bounds:[69,193,87,211]},{id:'food-materials',bounds:[94,191,110,205]}];
 for(const shop of shops){const [x0,z0,x1,z1]=shop.bounds,cx=(x0+x1)/2;
  for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)put(x,103,z,'spruce_planks');
  for(const x of [x0,x1])for(const z of [z0,Math.floor((z0+z1)/2),z1])for(let y=104;y<=110;y++)put(x,y,z,'dark_oak_log',{axis:'y'});
  for(let z=z0;z<=z1;z++)for(const x of [x0,x1])put(x,110,z,'dark_oak_log',{axis:'z'});
  for(const z of [z0,z1])for(let x=x0;x<=x1;x++){put(x,110,z,'dark_oak_log',{axis:'x'});put(x,111+Math.floor((Math.min(x-x0,x1-x))/2),z,'spruce_planks');}
  const ridge=111+Math.floor((x1-x0)/4);for(let z=z0;z<=z1;z++)put(cx,ridge,z,'dark_oak_log',{axis:'z'});
  shop.floorY=103;shop.entrance=[cx,104,z1];shop.roofRidgeY=ridge;shop.stage='open timber frame; roof outline';
 }
 let count=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i])count++;
 const file=path.join(MAP,'region/r.0.0.mca'),result=patch(m,data,old,fs.readFileSync(file));
 const backup=path.join(ROOT,'archive/map-edit-backups/before-market-layout/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(MAP,backup,{recursive:true});
 fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
 const layout={id:'04-market',name:'Market',bounds:[65,181,112,237],floorY:103,shops,stallPlots:stalls,smithRoute:[101,104,181],stage:'structural layout only'};
 const mf=path.join(MAP,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.market=layout;manifest.rooms=(manifest.rooms||[]).filter(r=>r.id!==layout.id);manifest.rooms.push(layout);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const zf=path.join(MAP,'zones.json'),zones=JSON.parse(fs.readFileSync(zf)),zi=zones.findIndex(z=>z.id===layout.id);if(zi>=0)zones[zi]={...zones[zi],...layout};else zones.push(layout);fs.writeFileSync(zf,JSON.stringify(zones,null,2));
 fs.writeFileSync(report,JSON.stringify({layout,changedBlocks:count,changedChunks:result.changed,backup:path.relative(ROOT,backup)},null,2));console.log(JSON.stringify({changedBlocks:count,changedChunks:result.changed,layout}));
}
if(require.main===module)run();
