// Terrain-only study. Hand-shaped coast; no buildings, paths, trees or service markers.
const fs=require('node:fs'),path=require('node:path');
const {createTown}=require('./sky-town/core.cjs'),{exportMap}=require('./create_ruined_castle.cjs');
const outline=[[123,24],[181,24],[218,37],[246,59],[254,88],[242,108],[264,119],[287,144],[303,178],[301,207],[285,231],[260,247],[233,257],[222,279],[218,303],[203,326],[182,339],[160,343],[139,334],[126,317],[115,291],[99,269],[73,258],[46,242],[29,218],[24,188],[28,162],[42,145],[56,136],[51,121],[45,105],[50,84],[72,64],[96,51],[104,35]];
function inside(x,z){
  let hit=false;for(let i=0,j=outline.length-1;i<outline.length;j=i++){
    const [a,b]=outline[i],[c,d]=outline[j];
    if((x-a)*(d-b)===(z-b)*(c-a)&&x>=Math.min(a,c)&&x<=Math.max(a,c)&&z>=Math.min(b,d)&&z<=Math.max(b,d))return true;
    if((b>z)!==(d>z)&&x<(c-a)*(z-b)/(d-b)+a)hit=!hit;
  }return hit;
}
const smooth=n=>{n=Math.max(0,Math.min(1,n));return n*n*(3-2*n);};
const bump=(x,z,cx,cz,rx,rz)=>smooth(1-Math.hypot((x-cx)/rx,(z-cz)/rz));
function buildLand(){
  const t=createTown({size:384,high:144}),S=t.S,mask=new Uint8Array(S*S),distance=new Uint16Array(S*S),heights=new Int16Array(S*S).fill(-1),bottoms=new Int16Array(S*S).fill(-1),queue=[];
  for(let z=24;z<=343;z++)for(let x=24;x<=303;x++)if(inside(x,z))mask[z*S+x]=1;
  // Distance to the actual coastline controls tapering, not a circular island equation.
  for(let z=24;z<=343;z++)for(let x=24;x<=303;x++)if(mask[z*S+x]&&[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!mask[(z+dz)*S+x+dx])){distance[z*S+x]=1;queue.push([x,z]);}
  for(let i=0;i<queue.length;i++){const [x,z]=queue[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz,k=nz*S+nx;if(mask[k]&&!distance[k]){distance[k]=distance[z*S+x]+1;queue.push([nx,nz]);}}}
  const peaks=[[80,170,33,88],[156,294,28,88],[247,194,35,92],[164,86,45,100],[121,219,34,98],[205,142,29,94]];
  for(let z=24;z<=343;z++)for(let x=24;x<=303;x++)if(mask[z*S+x]){
    const d=distance[z*S+x],hill=6*smooth((295-z)/230)+20*bump(x,z,164,79,103,108)+8*bump(x,z,73,128,50,65)+6*bump(x,z,255,184,52,68);
    const edgeDrop=Math.max(0,5-d)*.65;
    const top=Math.max(96,Math.min(124,96+2*Math.round((hill-edgeDrop)/2)));
    let depth=Math.min(65,14+d*.9);
    for(const [cx,cz,r,h]of peaks)depth=Math.max(depth,h*Math.max(0,1-Math.hypot(x-cx,z-cz)/r));
    const ridge=Math.sin(x*.21+z*.09)*2+Math.sin(z*.31-x*.07)*2;
    const bottom=Math.max(24,Math.round(top-depth+ridge));heights[z*S+x]=top;bottoms[z*S+x]=bottom;
    for(let y=bottom;y<=top;y++){
      const soilDepth=d<4?2:3,rock=t.hash(Math.floor(x/3),Math.floor(z/3),Math.floor(y/4));
      const name=y===top?'grass_block':y>top-soilDepth?'dirt':rock%29===0?'mossy_cobblestone':rock%7===0?'andesite':rock%13===0?'cobblestone':'stone';
      t.put(x,y,z,name,name==='grass_block'?{snowy:'false'}:undefined);
    }
  }
  const spawn=[160,heights[310*S+160]+1,310];
  const visited=new Set([spawn[2]*S+spawn[0]]),todo=[[spawn[0],spawn[2]]];
  for(let i=0;i<todo.length;i++){const [x,z]=todo[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const k=(z+dz)*S+x+dx;if(mask[k]&&!visited.has(k)){visited.add(k);todo.push([x+dx,z+dz]);}}}
  const area=mask.reduce((n,v)=>n+v,0);if(visited.size!==area)throw Error('Disconnected land mass');
  const active=[...heights].filter(y=>y>=0),lower=[...bottoms].filter(y=>y>=0);
  const stats={width:280,depth:320,landColumns:area,minSurfaceY:Math.min(...active),maxSurfaceY:Math.max(...active),lowestRockY:Math.min(...lower),deepestColumn:Math.max(...active.map((y,i)=>y-lower[i]+1))};
  return {...t,name:'NeoWorld - Sky Land Study v1',spawn,tiles:[],rooms:[],reachableFloorCells:0,heights,bottoms,terrainStats:stats,outline,colours:{grass_block:'#66874a',dirt:'#826247',andesite:'#8c8d8b',cobblestone:'#777c78'}};
}
function generate(destination='maps/starter_sky_land_v1'){
  if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
  const t=buildLand(),result=exportMap(t,destination),file=path.join(destination,'map-manifest.json'),m=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(m,{name:'Sky Land · Terrain Only 1',description:'แผ่นดินเท่านั้น · 280 × 320 · ขอบอิสระ เนินด้านหลัง และปลายหินหลายยอด',zones:[{id:'01-island',name:'Mainland only',bounds:[24,24,303,343],outline:t.outline}],terrain:t.terrainStats,gameplay:'Terrain study only. No buildings, roads, vegetation decorations, secondary islands or services. Footprint connectivity is checked; traversable roads are not designed yet.',reference:'User-confirmed terrain proportions, not a certified 1:1 image reconstruction.'});
  fs.writeFileSync(file,JSON.stringify(m,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(m.zones,null,2));
  fs.writeFileSync(path.join(destination,'terrain-profile.json'),JSON.stringify({size:t.S,empty:-1,heights:[...t.heights],bottoms:[...t.bottoms]}));
  fs.writeFileSync(path.join(destination,'README.md'),'# Sky Land — Terrain Only 1\n\nMainland footprint 280 × 320 blocks within a 384 × 384 save. No buildings, roads, trees or satellite islands.\n\nHand-shaped polygon coastline, rear hill, two left shoulders, a right shoulder, a rounded front peninsula and several tapered rock tips. Dimensions are an agreed terrain study, not measurements certified from the perspective image.\n\nSource: tools/maps/create_sky_land.cjs. Profile heights and rock bottoms: terrain-profile.json. Spawn is an inspection position on grass; no arrival plaza has been built. Horizontal land connectivity checked; walkable city routes pending.\n');
  return {...result,terrain:t.terrainStats};
}
module.exports={buildLand,generate,inside,outline};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
