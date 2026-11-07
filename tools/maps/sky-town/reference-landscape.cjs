const {lift}=require('./reference-terrain.cjs');
function landscape(t){
  const grass=(x,z)=>t.definitions[t.get(x,96+lift(x,z),z)].Name==='minecraft:grass_block';
  function bed(x,z){const y=96+lift(x,z);t.put(x,y+1,z,'oak_leaves',{distance:'1',persistent:'true',waterlogged:'false'});if(t.hash(x,z)%3===0)t.put(x,y+2,z,'poppy');}
  // Dense landscape borders around every zone replace the vacant green oval.
  for(let z=38;z<340;z+=3)for(let x=30;x<310;x+=3)if(grass(x,z)&&!t.get(x,97+lift(x,z),z)){
    let near=false;for(const [dx,dz]of [[4,0],[-4,0],[0,4],[0,-4]]){const id=t.get(x+dx,96+lift(x+dx,z+dz),z+dz),name=t.definitions[id].Name;if(id&&name!=='minecraft:grass_block'&&name!=='minecraft:dirt')near=true;}
    if(near||t.hash(x,z)%13===0)bed(x,z);
  }
  // Mature trees in clustered gardens, leaving all existing paved routes untouched.
  for(let z=43;z<336;z+=17)for(let x=35;x<308;x+=17)if(grass(x,z)&&t.hash(x,z)%3!==0){
    const y=96+lift(x,z);let free=true;for(let dz=-5;dz<=5;dz++)for(let dx=-5;dx<=5;dx++)if(!grass(x+dx,z+dz)||lift(x+dx,z+dz)!==lift(x,z)||t.get(x+dx,y+4,z+dz))free=false;
    if(free)t.tree(x,z,y);
  }
  // Lantern-lined main avenue and banners leading toward the castle.
  for(let z=143;z<=290;z+=18)for(const x of [148,176]){
    const y=96+lift(x,z);if(!t.get(x,y,z))continue;t.lamp(x,z,y);
    if(z%36===17){t.fill(x,y+5,z,x,y+8,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});t.fill(x+1,y+5,z,x+2,y+8,z,'blue_wool');}
  }
  // A round front arrival terrace rather than a square plate with isolated objects.
  const cx=161,cz=313;
  for(let dz=-23;dz<=23;dz++)for(let dx=-27;dx<=27;dx++){
    const d=(dx/27)**2+(dz/23)**2;if(d>1||!t.get(cx+dx,96,cz+dz))continue;
    if(d>.82){t.put(cx+dx,96,cz+dz,'smooth_stone');if(d>.94&&!(dz<-17&&Math.abs(dx)<9))t.put(cx+dx,97,cz+dz,'stone_brick_slab',{type:'bottom',waterlogged:'false'});}
  }
  for(const [x,z]of [[142,304],[180,304],[143,325],[179,325]]){t.tree(x,z);t.lamp(x+3,z-3);}
  // Hanging roots along exposed cliff columns; each root is physical wood and foliage.
  for(let z=42;z<340;z+=11)for(let x=28;x<310;x+=11)if(t.get(x,96+lift(x,z),z)&&!t.get(x+3,96+lift(x,z),z)){
    const top=94+lift(x,z),length=12+t.hash(x,z)%25;
    for(let y=Math.max(25,top-length);y<=top;y++)if(!t.get(x+1,y,z))t.put(x+1,y,z,'oak_log',{axis:'y'});
    for(let y=Math.max(26,top-length+2);y<top;y+=3)if(!t.get(x+2,y,z))t.put(x+2,y,z,'oak_leaves',{distance:'1',persistent:'true',waterlogged:'false'});
  }
}
module.exports={landscape};
