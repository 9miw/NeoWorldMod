module.exports=function(t){
  t.pave(92,76,100,177);t.pave(77,82,115,122);
  t.pave(74,82,91,86);t.pave(73,118,91,122);t.pave(101,98,132,102);t.pave(142,130,170,134);
  // Keep the central avenue open: fountain offset west, large tree east.
  for(let dz=-5;dz<=5;dz++)for(let dx=-5;dx<=5;dx++)if(dx*dx+dz*dz<=25)t.put(84+dx,97,100+dz,'stone_bricks');
  for(let dz=-3;dz<=3;dz++)for(let dx=-3;dx<=3;dx++)if(dx*dx+dz*dz<=9)t.put(84+dx,98,100+dz,'light_blue_stained_glass');
  t.fill(84,98,100,84,102,100,'sea_lantern');t.put(84,103,100,'light_blue_stained_glass');t.tree(109,109);
  for(let z=132;z<=168;z+=18)for(const x of [89,103])t.lamp(x,z);
  for(const [x,z]of [[78,83],[114,83],[78,120],[114,120]])t.lamp(x,z);
  t.planter(78,127);t.planter(110,127);
};
