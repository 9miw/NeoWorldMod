const {lift}=require('./reference-terrain.cjs');
function buildings(t){
  const stone='stone_bricks',slab={type:'bottom',waterlogged:'false'};
  function banner(x,y,z,c='blue'){t.fill(x,y,z,x+2,y+7,z,c+'_wool');t.fill(x+1,y,z,x+1,y+6,z,'white_wool');t.fill(x-1,y+8,z,x+3,y+8,z,'spruce_log',{axis:'x'});}
  function turret(x,z,r,base,top){
    for(let y=base;y<=top;y++)for(let dz=-r;dz<=r;dz++)for(let dx=-r;dx<=r;dx++)if(dx*dx+dz*dz<=r*r+1&&(dx*dx+dz*dz>=(r-2)**2||y===base))t.put(x+dx,y,z+dz,y===base||y%9===0?'chiseled_stone_bricks':stone);
    for(let rr=r+2;rr>=0;rr--)for(let dz=-rr;dz<=rr;dz++)for(let dx=-rr;dx<=rr;dx++)if(dx*dx+dz*dz<=rr*rr+1)t.fill(x+dx,top+1+(r+2-rr)*2,z+dz,x+dx,top+2+(r+2-rr)*2,z+dz,'blue_concrete');
    for(let y=base+7;y<top-4;y+=9)t.fill(x,y,z+r,x,y+3,z+r,'light_blue_stained_glass');
    t.fill(x,top+(r+3)*2,z,x,top+(r+3)*2+3,z,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});t.fill(x+1,top+(r+3)*2+1,z,x+3,top+(r+3)*2+3,z,'blue_wool');
  }
  // Six-part castle skyline: central keep, existing high rear towers and front turrets.
  const front=91;
  turret(137,front,5,127,164);turret(185,front,5,127,164);
  turret(161,front-8,4,157,178);
  for(const x of [141,150,171,180]){
    t.fill(x,127,front+1,x+2,155,front+2,'chiseled_stone_bricks');
    for(const y of [135,147]){t.fill(x-1,y,front+3,x+3,y+5,front+3,'smooth_stone');t.fill(x,y+1,front+4,x+2,y+4,front+4,'light_blue_stained_glass');t.put(x+1,y+5,front+4,'light_blue_stained_glass');}
  }
  // Recessed gateway preserves the original seven-block passage at castle floor Y126.
  t.fill(157,127,front,165,135,front+6,'air');
  for(const x of [155,167])t.fill(x,127,front+4,x,136,front+4,'chiseled_stone_bricks');
  for(let dx=-6;dx<=6;dx++)t.put(161+dx,140-Math.floor(Math.abs(dx)/2),front+4,'stone_bricks');
  banner(144,140,front+4);banner(176,140,front+4);
  for(const z of [107,109,111])for(const x of [132,190])t.put(x,145,z,'stone_brick_slab',slab);
  // Crenellations and corbels on the castle's outer retaining wall.
  for(let x=108;x<213;x+=4)if(x<148||x>175){t.fill(x,127,111,x+1,130,111,'stone_bricks');t.put(x,125,112,'stone_brick_stairs',{facing:'north',half:'top',shape:'straight',waterlogged:'false'});}
  // Hall central projecting gable and porch: not a plain rectangular barn.
  const hx=239,hz=170,hy=96+6;
  t.fill(hx-9,hy,hz,hx+9,hy,hz+9,'stone_bricks');
  for(const x of [hx-9,hx+9])t.fill(x,hy+1,hz+7,x,hy+10,hz+7,'spruce_log',{axis:'y'});
  for(let dx=-11;dx<=11;dx++){const rise=11-Math.abs(dx);t.fill(hx+dx,hy+11+rise,hz-2,hx+dx,hy+11+rise,hz+10,'dark_oak_planks');if(Math.abs(dx)<=9)t.fill(hx+dx,hy+11,hz+8,hx+dx,hy+11+rise,hz+8,'stripped_oak_wood',{axis:'y'});}
  t.fill(hx-3,hy+1,hz-2,hx+3,hy+5,hz+1,'air');banner(hx-7,hy+3,hz+9,'purple');banner(hx+5,hy+3,hz+9,'purple');
  // Merchant awnings, shutters, balconies and roof dormers in block-scale detail.
  for(const b of [{x:62,z:168,w:21,d:20},{x:94,z:168,w:24,d:20},{x:65,z:113,w:40,d:23}]){
    const y=96+lift(b.x,b.z),cx=b.x+Math.floor(b.w/2),f=b.z+b.d;
    for(const x of [b.x+2,b.x+b.w-3]){t.fill(x,y+1,f+3,x,y+4,f+3,'spruce_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});t.put(x,y+5,f+3,'lantern',{hanging:'false',waterlogged:'false'});}
    for(let dx=-5;dx<=5;dx++)t.fill(cx+dx,y+6,f,cx+dx,y+6,f+4,dx%2?'white_wool':'orange_wool');
    for(let xx=b.x+3;xx<b.x+b.w-3;xx+=7){t.fill(xx,y+7,f-1,xx+2,y+10,f-1,'light_blue_stained_glass');t.fill(xx-1,y+7,f,xx-1,y+10,f,'spruce_trapdoor',{facing:'east',half:'bottom',open:'true',powered:'false',waterlogged:'false'});}
    t.fill(cx-2,y+1,f-1,cx+2,y+4,f+1,'air');
  }
  // Portal is a tall sculpted gateway, with a clear waiting terrace in front.
  const px=366,pz=200;
  for(const x of [px-12,px+12]){t.fill(x-1,97,pz-1,x+1,116,pz+1,'chiseled_stone_bricks');t.fill(x-2,97,pz-2,x+2,100,pz+2,'stone_bricks');}
  for(let dx=-11;dx<=11;dx++)t.fill(px+dx,124-Math.floor(Math.abs(dx)*.6),pz,px+dx,126-Math.floor(Math.abs(dx)*.6),pz+1,'stone_bricks');
  t.fill(px-10,97,pz,px+10,116,pz,'cyan_stained_glass');
  for(const x of [px-14,px+14]){t.fill(x,97,pz+4,x,100,pz+4,'amethyst_block');t.put(x,101,pz+4,'sea_lantern');}
}
module.exports={buildings};
