module.exports=function(t){
  t.pave(84,178,108,202);
  for(let z=186;z<=194;z++)for(let x=92;x<=100;x++){const d=Math.hypot(x-96,z-190);if(d<4.5)t.put(x,96,z,d>3.3?'polished_andesite':d>2.4?'chiseled_stone_bricks':'smooth_stone');}
  for(const [x,z]of [[92,190],[100,190],[96,186],[96,194]])t.put(x,96,z,'sea_lantern');
  for(const x of [89,103]){t.fill(x,97,202,x,103,202,'stone_bricks');t.put(x,104,202,'chiseled_stone_bricks');}
  for(let dx=-6;dx<=6;dx++)t.put(96+dx,108-Math.abs(dx)/2|0,202,'stone_bricks');
  t.put(96,107,202,'lantern',{hanging:'true',waterlogged:'false'});
  for(const [x,z]of [[85,179],[107,179],[85,199],[107,199]])t.lamp(x,z);
  t.planter(84,195);t.planter(104,195);
  for(let x=83;x<=109;x++)t.put(x,97,204,'stone_bricks');
  for(let z=178;z<=204;z++)for(const x of [82,110])if(z<186||z>190)t.put(x,97,z,'stone_bricks');
  t.pave(78,186,83,190);t.pave(109,186,114,190);
};
