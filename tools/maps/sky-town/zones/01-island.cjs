module.exports=function(t){
  // Organic edge, with broad town shoulders and a narrower arrival peninsula.
  t.island=(x,z)=>{const v=(z-112)/100;if(Math.abs(v)>1)return false;const r=86*Math.sqrt(Math.max(0,1-v*v))*(z>150?1-(z-150)*.002:1);return Math.abs(x-100)<=r+Math.sin(z*.17)*2;};
  for(let z=12;z<=212;z++)for(let x=12;x<=188;x++)if(t.island(x,z)){
    const radial=Math.min(1,Math.hypot((x-100)/90,(z-112)/105)),bottom=Math.max(25,Math.floor(88-56*(1-radial)+Math.sin(x*.23+z*.12)*4));
    for(let y=bottom;y<=96;y++)t.put(x,y,z,y===96?'grass_block':y>92?'dirt':y<50?'deepslate':t.hash(x,z,y)%23?'stone':'andesite',y===96?{snowy:'false'}:undefined);
    if(t.hash(x,z)%241===0){const y=bottom+2;t.put(x,y,z,'sea_lantern');if(bottom>27)t.put(x,bottom-1,z,'amethyst_block');}
    // Block-height perimeter rail guards exposed edges without covering the bridge approach.
    if(z>20&&!(x>162&&z>=129&&z<=135)&&[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>!t.island(x+dx,z+dz)))t.put(x,97,z,'stone_bricks');
  }
  for(const [x,z]of [[32,152],[54,164],[140,160],[159,157],[61,184],[130,184],[151,61]])if(t.island(x,z))t.tree(x,z);
};
