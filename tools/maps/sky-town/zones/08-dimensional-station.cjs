module.exports=function(t){
  const inside=(x,z)=>((x-224)/20)**2+((z-130)/22)**2<=1;
  for(let z=108;z<=152;z++)for(let x=204;x<=244;x++)if(inside(x,z)){
    const r=Math.hypot((x-224)/20,(z-130)/22),bottom=Math.floor(53+37*r);
    for(let y=bottom;y<=96;y++)t.put(x,y,z,y===96?'stone_bricks':t.hash(x,z,y)%13?'stone':'amethyst_block');
    if(!(x<=206&&z>=130&&z<=134)&&[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!inside(x+dx,z+dz)))t.put(x,97,z,'stone_bricks');
  }
  // Extend deck into both banks, with solid railings and full two-block clearance.
  t.pave(166,130,214,134);for(let x=169;x<=208;x++)for(const z of [129,135])t.fill(x,96,z,x,98,z,'stone_bricks');
  for(const x of [175,190,205])for(const z of [129,135])t.lamp(x,z,98);
  t.pave(214,119,234,141);
  for(const x of [217,231])t.fill(x,97,120,x,108,120,'chiseled_stone_bricks');
  for(let dx=-6;dx<=6;dx++)t.fill(224+dx,112-Math.floor(Math.abs(dx)/2),120,224+dx,113-Math.floor(Math.abs(dx)/2),120,'stone_bricks');
  t.fill(218,97,120,230,108,120,'cyan_stained_glass');for(const x of [217,231])t.put(x,100,121,'sea_lantern');
  t.tree(235,134);t.planter(214,138);t.lamp(214,142);t.lamp(233,142);
};
