function furnish(t,{x,z,w,d,y=96,kind='shop'}){
  // Every entrance is three full blocks wide. Reserve a central aisle at least five wide.
  const cx=x+Math.floor(w/2),front=z+d-1;
  t.fill(cx-2,y+1,front-1,cx+2,y+4,front,'air');
  for(let zz=z+3;zz<front-3;zz+=6){
    for(const xx of [x+2,x+w-3])t.fill(xx,y+1,zz,xx,y+3,zz+2,kind==='hall'?'bookshelf':'barrel',kind==='hall'?undefined:{facing:'south',open:'false'});
    t.put(cx,y+6,zz,'lantern',{hanging:'true',waterlogged:'false'});
  }
  // Counters leave a front customer zone and a five-block aisle behind them.
  for(const xx of [x+5,x+w-8]){t.fill(xx,y+1,z+5,xx+3,y+1,z+5,'spruce_planks');t.put(xx+1,y+2,z+5,'lantern',{hanging:'false',waterlogged:'false'});}
  if(kind==='smith'){t.put(x+5,y+1,z+9,'anvil',{facing:'north'});t.put(x+w-6,y+1,z+9,'smithing_table');}
  // Layout counters from the first draft must not block the new circulation axis.
  t.fill(cx-2,y+1,z+2,cx+2,y+4,front,'air');
  const points=[[cx,y+1,front+1],[cx,y+1,front-3],[cx,y+1,z+8]];
  return {entrance:[cx,y+1,front],clearWidth:5,interiorBounds:[x+1,y+1,z+1,x+w-2,y+10,z+d-2],usableFloorArea:(w-2)*(d-2),checkpoints:points};
}
module.exports={furnish};
