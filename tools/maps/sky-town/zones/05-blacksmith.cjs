module.exports=function(t){
  t.pave(33,65,74,92);t.house(36,66,25,14,{h:8,wall:'stripped_spruce_wood'});t.pave(47,80,74,86);
  t.fill(38,97,68,41,113,71,'stone_bricks');t.fill(39,99,69,40,113,70,'air');
  // Decorative cold forge: no spreading fire or flowing lava.
  t.fill(40,97,72,43,97,75,'shroomlight');t.fill(40,98,72,43,98,75,'orange_stained_glass');
  t.put(52,97,76,'anvil',{facing:'north'});t.put(57,97,76,'smithing_table');
  t.put(63,97,78,'barrel',{facing:'up',open:'false'});t.put(65,97,78,'barrel',{facing:'up',open:'false'});
  // Vanilla marker for the project's upgrade table; actual mod block installed later.
  t.put(65,97,84,'enchanting_table');t.put(66,97,84,'sea_lantern');
  for(const [x,z]of [[34,88],[72,88]])t.lamp(x,z);t.planter(34,90);
};
