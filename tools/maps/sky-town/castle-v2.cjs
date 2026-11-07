module.exports=function(t,p,end){
  const x1=p(78),x2=end(114),z1=p(31),z2=end(52),cx=p(96);
  t.fill(p(62),94,p(22),end(130),107,end(64),'stone');t.pave(p(62),p(22),end(130),end(64),108);
  for(let z=p(65);z<=end(76);z++){const y=108-Math.floor((z-p(65))*12/(end(76)-p(65)+1));t.fill(p(88),94,z,end(104),y,z,'stone_bricks');for(const x of [p(87),end(105)])t.fill(x,96,z,x,y+1,z,'stone_bricks');}
  t.fill(x1,109,z1,x2,139,z2,'stone_bricks');t.fill(x1+2,109,z1+2,x2-2,138,z2-2,'air');
  for(const y of [108,122])t.fill(x1+2,y,z1+2,x2-2,y,z2-2,'spruce_planks');
  t.fill(cx-3,109,z2-1,cx+3,115,z2,'air');
  // Ground floor throne and reception, central approach stays unobstructed.
  t.fill(cx-10,109,z1+5,cx+10,109,z1+9,'polished_diorite');t.fill(cx-2,110,z1+6,cx+2,112,z1+7,'dark_oak_planks');
  t.fill(cx-2,109,z1+12,cx+2,109,z2-3,'blue_carpet');
  for(const x of [x1+5,x2-5])for(let z=z1+12;z<z2-7;z+=7)t.fill(x,109,z,x+2,109,z,'spruce_slab',{type:'bottom',waterlogged:'false'});
  // A real 14-rise staircase connects reception to the upper floor.
  for(let step=0;step<14;step++){const z=z2-5-step;t.fill(x1+3,109,z,x1+6,109+step,z,'stone_bricks');t.fill(x1+3,110+step,z,x1+6,114+step,z,'air');}
  for(let z=z2-18;z<=z2-5;z++)if(z>=z2-18&&z<=z2-14)t.fill(x1+3,122,z,x1+6,122,z,'air');
  for(const y of [114,128])for(let x=x1+7;x<x2-4;x+=9)t.fill(x,y,z2,x+2,y+4,z2,'light_blue_stained_glass');
  for(let x=x1;x<=x2;x+=8)t.fill(x,109,z2+1,x,138,z2+1,'chiseled_stone_bricks');
  for(let z=z1-2;z<=z2+2;z++){const rise=Math.min(z-(z1-2),z2+2-z);t.fill(x1-2,140+Math.floor(rise/3),z,x2+2,140+Math.floor(rise/3),z,'blue_terracotta');}
  const towerPoints=[];
  for(const [bx,bz,h]of [[72,31,158],[120,31,158],[96,27,166]]){
    const x=p(bx),z=p(bz),r=7;t.fill(x-r,109,z-r,x+r,h,z+r,'stone_bricks');t.fill(x-r+2,109,z-r+2,x+r-2,h-1,z+r-2,'air');t.fill(x-r+2,108,z-r+2,x+r-2,108,z+r-2,'spruce_planks');
    t.fill(x-2,109,z+r-1,x+2,113,z+r,'air');
    for(const y of [115,128,141])t.fill(x-1,y,z+r,x+1,y+3,z+r,'light_blue_stained_glass');
    for(let rr=9;rr>=0;rr--)t.fill(x-rr,h+1+(9-rr)*2,z-rr,x+rr,h+2+(9-rr)*2,z+rr,'blue_terracotta');
    towerPoints.push([x,109,z+r-3]);
  }
  // Open the central tower into reception without cutting support pillars.
  t.fill(cx-2,109,p(31),cx+2,113,p(31)+3,'air');
  for(let x=p(62);x<=end(130);x++)for(const z of [p(22),end(64)])if(!(z===end(64)&&x>=p(88)&&x<=end(104))){t.fill(x,109,z,x,111,z,'stone_bricks');if(x%4<2)t.put(x,112,z,'chiseled_stone_bricks');}
  for(let z=p(22);z<=end(64);z++)for(const x of [p(62),end(130)])t.fill(x,109,z,x,111,z,'stone_bricks');
  // Dedicated paths to the two freestanding tower doors.
  for(const bx of [72,120])t.pave(p(bx)-2,p(31)+8,p(bx)+2,end(62),108);
  for(const [x,z]of [[66,58],[126,58],[84,58],[108,58]])t.lamp(p(x),p(z),108);
  return {entrance:[cx,109,z2],clearWidth:7,interiorBounds:[x1+2,109,z1+2,x2-2,138,z2-2],usableFloorArea:(x2-x1-3)*(z2-z1-3)*2,checkpoints:[[cx+5,109,z2-4],[x1+7,123,z2-18],...towerPoints],upperFloorY:123};
};
