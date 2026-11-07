module.exports=function(t){
  t.pave(120,80,165,142);t.house(130,81,29,20,{h:10,roof:'dark_oak_planks'});t.pave(128,101,154,124);
  for(const x of [132,155]){t.fill(x,97,103,x,103,103,'stone_bricks');t.fill(x,100,104,x+1,103,104,'blue_wool');}
  for(const x of [122,158])t.fill(x,97,113,x+5,97,113,'spruce_slab',{type:'bottom',waterlogged:'false'});
  // Three service counters: dungeon, party and PvP; connection to gameplay comes later.
  for(const x of [134,143,152]){t.fill(x,97,86,x+3,97,86,'spruce_planks');t.put(x+1,98,86,'lantern',{hanging:'false',waterlogged:'false'});}
  t.pave(142,120,146,134);
  for(const [x,z]of [[122,104],[163,104],[122,139],[163,139]])t.lamp(x,z);
  t.planter(121,127);t.tree(160,123);
};
