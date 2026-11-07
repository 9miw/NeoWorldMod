// Only the arrival terrace; do not place other city zones.
module.exports=function(t){
  const cx=160,cz=309,y=96,rx=26,rz=24,slab={type:'bottom',waterlogged:'false'},leaf={distance:'1',persistent:'true',waterlogged:'false'};
  const footprint=[];
  for(let z=cz-rz;z<=cz+rz;z++)for(let x=cx-rx;x<=cx+rx;x++){
    const d=((x-cx)/rx)**2+((z-cz)/rz)**2;if(d>1||t.heights[z*t.S+x]<0)continue;footprint.push([x,z]);
    // Foundation follows existing terrain; no columns are added outside the island.
    for(let yy=y-3;yy<y;yy++)t.put(x,yy,z,'stone_bricks');t.fill(x,y+1,z,x,y+18,z,'air');
    const ring=d>.88,axis=Math.abs(x-cx)<=3;
    t.put(x,y,z,ring?'polished_andesite':axis?'smooth_stone':t.hash(x,z)%11===0?'andesite':'stone_bricks');
    if(d>.9&&!(z<cz&&Math.abs(x-cx)<=8)){t.put(x,y+1,z,'stone_bricks');t.put(x,y+2,z,'stone_brick_slab',slab);}
    else if(d>.66&&d<.84&&!(z<cz&&Math.abs(x-cx)<=10)){
      t.put(x,y+1,z,'grass_block',{snowy:'false'});
      if(t.hash(x,z)%4===0)t.put(x,y+2,z,t.hash(x,z)%3?'oak_leaves':'poppy',t.hash(x,z)%3?leaf:undefined);
    }
  }
  // Flush, eight-point floor motif preserves a level player standing area.
  for(let dz=-8;dz<=8;dz++)for(let dx=-8;dx<=8;dx++){
    const r=Math.hypot(dx,dz);if(r>8)continue;
    t.put(cx+dx,y,cz+dz,r>7?'polished_andesite':r>5.5?'smooth_stone':Math.abs(dx)===Math.abs(dz)||Math.abs(dx)<=1||Math.abs(dz)<=1?'chiseled_stone_bricks':'polished_diorite');
  }
  for(const [dx,dz]of [[0,-7],[0,7],[-7,0],[7,0]])t.put(cx+dx,y,cz+dz,'sea_lantern');
  // Short north opening only; the rest of the city road is deliberately absent.
  t.pave(cx-6,281,cx+6,290,y);t.fill(cx-6,y+1,281,cx+6,y+3,291,'air');
  // Deep stone gate behind the spawn: layered plinths, columns and stepped pointed arch.
  for(const x of [153,167]){
    t.fill(x-1,y+1,326,x+1,y+2,330,'stone_bricks');t.fill(x,y+3,327,x,y+9,329,'chiseled_stone_bricks');
    t.fill(x-1,y+8,326,x+1,y+9,330,'stone_bricks');t.put(x,y+10,328,'stone_brick_slab',slab);
    t.put(x,y+3,325,'lantern',{hanging:'false',waterlogged:'false'});
  }
  for(let dx=-7;dx<=7;dx++){
    const h=y+10+Math.floor((7-Math.abs(dx))*.7);t.fill(cx+dx,h,327,cx+dx,h+1,329,'stone_bricks');t.put(cx+dx,h+2,328,'stone_brick_slab',slab);
  }
  t.put(cx,y+13,328,'lantern',{hanging:'true',waterlogged:'false'});
  // Four human-scale lanterns and banner poles leave the central approach free.
  for(const [x,z]of [[140,302],[180,302],[145,322],[175,322]])t.lamp(x,z,y);
  for(const x of [149,171]){
    t.fill(x,y+1,295,x,y+8,295,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});
    t.fill(x+1,y+4,295,x+2,y+7,295,'blue_wool');t.fill(x+1,y+4,295,x+1,y+6,295,'white_wool');t.put(x,y+9,295,'lantern',{hanging:'false',waterlogged:'false'});
  }
  for(const [x,z]of [[138,304],[182,304],[147,324],[173,324]])t.tree(x,z,y);
  for(const x of [141,179]){
    t.fill(x,y+1,309,x,y+1,314,'spruce_stairs',{facing:x<cx?'east':'west',half:'bottom',shape:'straight',waterlogged:'false'});
    for(const z of [308,315])t.put(x,y+1,z,'stone_brick_slab',slab);
  }
  t.spawn=[cx,97,310];
  // Verify real two-block air clearance from the spawn to every north-exit column.
  const seen=new Set([cx+','+310]),q=[[cx,310]];
  for(let i=0;i<q.length;i++)for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){
    const x=q[i][0]+dx,z=q[i][1]+dz,key=x+','+z;if(x<134||x>186||z<281||z>333||seen.has(key)||!t.get(x,y,z)||t.get(x,y+1,z)||t.get(x,y+2,z))continue;seen.add(key);q.push([x,z]);
  }
  for(let x=cx-6;x<=cx+6;x++)if(!seen.has(x+',281'))throw Error('Blocked spawn exit '+x);
  return {id:'02-spawn',name:'Spawn',bounds:[134,281,186,333],center:t.spawn,terrace:[53,49],exitWidth:13,exit:[160,97,281],footprint,reachableCells:seen.size};
};
