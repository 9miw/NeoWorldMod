// Standalone spawn draft. Uses the existing Anvil exporter; never overwrites a map.
const fs=require('node:fs');
const path=require('node:path');
const {exportMap}=require('./create_ruined_castle.cjs');
function buildSpawn(){
  const S=64,LOW=60,HIGH=84,FLOOR=64,blocks=new Uint16Array(S*S*(HIGH-LOW)),definitions=[{Name:'minecraft:air'}],ids=new Map();
  function put(x,y,z,name,properties){
    if(x<0||z<0||x>=S||z>=S||y<LOW||y>=HIGH)throw Error('Block outside map');
    const key=JSON.stringify([name,properties]);if(!ids.has(key)){ids.set(key,definitions.length);definitions.push({Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});}
    blocks[(y-LOW)*S*S+z*S+x]=ids.get(key);
  }
  function fill(x1,y1,z1,x2,y2,z2,n,p){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,n,p);}
  const hash=(x,z)=>(Math.imul(x+7,374761393)^Math.imul(z+17,668265263))>>>0;
  fill(0,60,0,63,60,63,'bedrock');fill(0,61,0,63,62,63,'stone');fill(0,63,0,63,63,63,'dirt');fill(0,64,0,63,64,63,'grass_block',{snowy:'false'});
  // Plaza x20..44,z25..49 = 25x25; forward is north (-Z).
  const paved=(x,z)=>(x>=20&&x<=44&&z>=25&&z<=49)||(x>=28&&x<=36&&z>=2&&z<25)||(z>=35&&z<=39&&x>=2&&x<=61);
  for(let z=2;z<62;z++)for(let x=2;x<62;x++)if(paved(x,z)){
    const edge=!paved(x-1,z)||!paved(x+1,z)||!paved(x,z-1)||!paved(x,z+1);
    put(x,64,z,edge?'smooth_stone':hash(x,z)%11===0?'mossy_stone_bricks':hash(x,z)%5===0?'cobblestone':'stone_bricks');
  }
  // Inlaid spawn medallion, flush with the walking surface.
  for(let z=38;z<=46;z++)for(let x=28;x<=36;x++){const d=Math.hypot(x-32,z-42);if(d<=4.3)put(x,64,z,d>3.2?'polished_andesite':d>2.3?'chiseled_stone_bricks':'smooth_stone');}
  for(const [x,z]of [[28,42],[36,42],[32,38],[32,46]])put(x,64,z,'shroomlight');
  const slab={type:'bottom',waterlogged:'false'};
  // Low edges leave all three exits clear.
  for(let z=25;z<=49;z++)if(z<35||z>39)for(const x of [19,45])put(x,65,z,'stone_brick_slab',slab);
  for(let x=20;x<=44;x++)if(x<28||x>36)put(x,65,24,'stone_brick_slab',slab);
  for(let x=19;x<=45;x++)put(x,65,50,'stone_brick_slab',slab);
  function lamp(x,z){put(x,65,z,'chiseled_stone_bricks');fill(x,66,z,x,67,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,68,z,'lantern',{hanging:'false',waterlogged:'false'});}
  for(const p of [[20,25],[44,25],[20,34],[44,34],[20,40],[44,40],[23,48],[41,48],[26,8],[38,8],[26,19],[38,19],[8,33],[8,41],[55,33],[55,41]])lamp(...p);
  function planter(x,z){fill(x,65,z,x+5,65,z+2,'oak_planks');fill(x+1,65,z+1,x+4,65,z+1,'dirt');for(let i=1;i<=4;i++)put(x+i,66,z+1,i%2?'poppy':'fern');}
  for(const p of [[21,26],[38,26],[21,45],[38,45]])planter(...p);
  // Backdrop arch south of spawn. It is scenery, not a fourth travel route.
  for(const x of [28,36]){fill(x-1,65,49,x+1,66,51,'stone_bricks');fill(x,67,50,x,71,50,'stone_bricks');put(x,72,50,'stone_brick_slab',slab);}
  for(let dx=-3;dx<=3;dx++){const y=73-Math.abs(dx);put(32+dx,y,50,'stone_bricks');put(32+dx,y+1,50,'stone_brick_slab',slab);}
  put(32,72,50,'lantern',{hanging:'true',waterlogged:'false'});
  function tree(x,z){fill(x,65,z,x,70,z,'oak_log',{axis:'y'});for(let y=69;y<=73;y++)for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(Math.abs(dx)+Math.abs(dz)+(y===73?2:0)<=4&&!(dx===0&&dz===0&&y<=70))put(x+dx,y,z+dz,'oak_leaves',{distance:'1',persistent:'true',waterlogged:'false'});}
  for(const p of [[14,24],[50,24],[14,48],[50,48],[24,55],[40,55]])tree(...p);
  for(let z=5;z<60;z++)for(let x=5;x<60;x++)if(!paved(x,z)&&!(x>=17&&x<=47&&z>=22&&z<=53)&&hash(x,z)%37===0&&blocks[(65-LOW)*S*S+z*S+x]===0)put(x,65,z,'short_grass');
  const get=(x,y,z)=>blocks[(y-LOW)*S*S+z*S+x];
  const visited=new Set(['32,42']),queue=[[32,42]];
  for(let i=0;i<queue.length;i++)for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const x=queue[i][0]+dx,z=queue[i][1]+dz,key=x+','+z;if(x<0||z<0||x>=S||z>=S||visited.has(key)||get(x,65,z)||get(x,66,z)||!get(x,64,z))continue;visited.add(key);queue.push([x,z]);}
  for(const [x,z]of [[32,2],[2,37],[61,37]])if(!visited.has(x+','+z))throw Error('Disconnected exit');
  for(let z=2;z<=42;z++)for(let x=28;x<=36;x++)if(get(x,65,z)||get(x,66,z))throw Error('Main route obstructed');
  return {name:'NeoWorld - Spawn Plaza v1',S,LOW,HIGH,FLOOR,blocks,definitions,tiles:[],spawn:[32,65,42],rooms:[{name:'Spawn',bounds:[20,25,44,49],center:[32,65,42]}],reachableFloorCells:visited.size};
}
function generate(destination='maps/spawn_plaza_v1'){
  const result=exportMap(buildSpawn(),destination),folder=path.resolve(destination),file=path.join(folder,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(manifest,{name:'Spawn Plaza · Draft 1',description:'ลานเกิด 25 × 25 · ทางหลักด้านหน้า 9 บล็อก · ทางข้าง 5 บล็อก',gameplay:'Architecture draft only. No NPCs or scripted gameplay.',design:{plaza:[25,25],mainPathWidth:9,sidePathWidth:5,forward:'north (-Z)',source:'output/mockups/spawn-plaza-concept.png'}});
  fs.writeFileSync(file,JSON.stringify(manifest,null,2));fs.writeFileSync(path.join(folder,'README.md'),'# Spawn Plaza · Draft 1\n\nJava 1.21.1. 64 × 64 including scenery. Spawn 32 65 42, facing north. Plaza 25 × 25, main path 9 blocks, side paths 5 blocks. Standalone draft; original maps unchanged.\n');
  return result;
}
module.exports={buildSpawn,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
