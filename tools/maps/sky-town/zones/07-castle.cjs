module.exports=function(t){
  t.fill(62,94,22,130,107,64,'stone');t.pave(62,22,130,64,108);
  for(let z=65;z<=76;z++){const y=108-(z-65);t.fill(88,94,z,104,y,z,'stone_bricks');for(const x of [87,105])t.fill(x,96,z,x,y+1,z,'stone_bricks');}
  // Keep and accessible southern entrance.
  t.fill(78,109,31,114,124,52,'stone_bricks');t.fill(80,109,33,112,123,50,'air');t.pave(80,33,112,50,108);
  t.fill(93,109,51,99,114,52,'air');
  for(const x of [82,88,104,110])t.fill(x,116,52,x+1,119,52,'light_blue_stained_glass');
  for(let z=29;z<=54;z++){const rise=Math.min(z-29,54-z);t.fill(76,125+Math.floor(rise/2),z,116,125+Math.floor(rise/2),z,'blue_terracotta');}
  function tower(cx,cz,h){
    t.fill(cx-4,109,cz-4,cx+4,h,cz+4,'stone_bricks');t.fill(cx-2,109,cz-2,cx+2,h-1,cz+2,'air');
    for(let r=5;r>=0;r--)t.fill(cx-r,h+1+(5-r)*2,cz-r,cx+r,h+2+(5-r)*2,cz+r,'blue_terracotta');
    t.fill(cx,117,cz+4,cx,120,cz+4,'light_blue_stained_glass');
    t.fill(cx,h+13,cz,cx,h+15,cz,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});t.fill(cx+1,h+14,cz,cx+3,h+15,cz,'blue_wool');
  }
  tower(72,31,133);tower(120,31,133);tower(96,27,140);
  for(let x=62;x<=130;x++)for(const z of [22,64])if(!(z===64&&x>=88&&x<=104)){t.fill(x,109,z,x,110,z,'stone_bricks');if(x%3===0)t.put(x,111,z,'chiseled_stone_bricks');}
  for(let z=22;z<=64;z++)for(const x of [62,130])t.fill(x,109,z,x,110,z,'stone_bricks');
  for(const [x,z]of [[66,58],[126,58],[84,58],[108,58]])t.lamp(x,z,108);
  t.planter(67,54,108);t.planter(121,54,108);
};
