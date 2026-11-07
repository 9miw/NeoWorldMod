const S=256,LOW=24,HIGH=160,FLOOR=24;
function createTown({size=S,high=HIGH}={}){
  const S=size,HIGH=high;
  const blocks=new Uint16Array(S*S*(HIGH-LOW)),definitions=[{Name:'minecraft:air'}],ids=new Map([['air',0]]),tiles=new Map(),zones=[];
  const index=(x,y,z)=>(y-LOW)*S*S+z*S+x;
  function get(x,y,z){return x<0||z<0||x>=S||z>=S||y<LOW||y>=HIGH?0:blocks[index(x,y,z)];}
  function put(x,y,z,name,properties){
    if(![x,y,z].every(Number.isInteger)||x<0||z<0||x>=S||z>=S||y<LOW||y>=HIGH)throw Error(`Outside map: ${x},${y},${z}`);
    const key=name+(properties?JSON.stringify(properties):'');if(!ids.has(key)){ids.set(key,definitions.length);definitions.push({Name:'minecraft:'+name,...(properties?{Properties:properties}:{})});}
    blocks[index(x,y,z)]=ids.get(key);const tileKey=x+','+y+','+z;tiles.delete(tileKey);
    if(['chest','barrel','furnace'].includes(name))tiles.set(tileKey,{id:'minecraft:'+name,x,y,z});
  }
  function fill(x1,y1,z1,x2,y2,z2,n,p){for(let y=y1;y<=y2;y++)for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,n,p);}
  const hash=(x,z,s=0)=>(Math.imul(x+31*s,374761393)^Math.imul(z+17*s,668265263))>>>0;
  function pave(x1,z1,x2,z2,y=96){for(let z=z1;z<=z2;z++)for(let x=x1;x<=x2;x++)put(x,y,z,x===x1||x===x2||z===z1||z===z2?'smooth_stone':hash(x,z)%7?'stone_bricks':'andesite');}
  function lamp(x,z,y=96){put(x,y+1,z,'chiseled_stone_bricks');fill(x,y+2,z,x,y+3,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(x,y+4,z,'lantern',{hanging:'false',waterlogged:'false'});}
  function tree(x,z,y=96){fill(x,y+1,z,x,y+7,z,'oak_log',{axis:'y'});for(let dy=5;dy<=10;dy++)for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)if(Math.abs(dx)+Math.abs(dz)+(dy===10?2:0)<=5&&!(dx===0&&dz===0&&dy<=7))put(x+dx,y+dy,z+dz,'oak_leaves',{distance:'1',persistent:'true',waterlogged:'false'});}
  function planter(x,z,y=96){fill(x,y+1,z,x+4,y+1,z+2,'spruce_planks');fill(x+1,y+1,z+1,x+3,y+1,z+1,'dirt');for(let dx=1;dx<=3;dx++)put(x+dx,y+2,z+1,dx%2?'poppy':'fern');}
  function house(x,z,w,d,{y=96,h=7,roof='dark_oak_planks',wall='stripped_oak_wood'}={}){
    pave(x-1,z-1,x+w,z+d,y);fill(x,y+1,z,x+w-1,y+h,z+d-1,wall,{axis:'y'});fill(x+1,y+1,z+1,x+w-2,y+h,z+d-2,'air');
    fill(x+1,y,z+1,x+w-2,y,z+d-2,'oak_planks');
    for(const xx of [x,x+w-1])for(const zz of [z,z+d-1])fill(xx,y+1,zz,xx,y+h,zz,'oak_log',{axis:'y'});
    const cx=x+Math.floor(w/2);fill(cx-1,y+1,z+d-1,cx+1,y+3,z+d-1,'air');
    for(const xx of [x,x+w-1])for(let zz=z+3;zz<z+d-2;zz+=5)fill(xx,y+3,zz,xx,y+4,zz+1,'light_blue_stained_glass');
    for(let xx=x-1;xx<=x+w;xx++){const rise=Math.min(xx-(x-1),x+w-xx);fill(xx,y+h+1+rise,z-1,xx,y+h+1+rise,z+d,roof);}
    fill(cx,y+1,z+2,cx+2,y+1,z+2,'spruce_planks');put(cx,y+3,z+2,'lantern',{hanging:'false',waterlogged:'false'});
    return {entry:[cx,y+1,z+d]};
  }
  return {S,LOW,HIGH,FLOOR,blocks,definitions,tiles,zones,get,put,fill,hash,pave,lamp,tree,planter,house};
}
module.exports={createTown};
