// Reference-driven terraces, in real block coordinates. Keep building plots level.
function lift(x,z){
  if(x>=350)return 0;
  if(x>=275&&z>=214&&z<=226)return Math.max(0,6-Math.floor((x-275)*6/75));
  if(x>=330)return 0;
  if(x>=200&&x<=285&&z>=130&&z<=240)return 6;
  if(x>=50&&x<=135&&z>=164&&z<=245)return 4;
  if(x>=55&&x<=132&&z>=104&&z<=156)return 12;
  if(x>=125&&x<=169&&z>=196&&z<=205)return 4;
  if(x>=125&&x<=162&&z>=139&&z<=148)return 12+Math.floor((x-125)*3/37);
  if(x>=168&&x<=220&&z>=164&&z<=175)return 10-Math.floor((x-168)*4/52);
  if(x>=146&&x<=178&&z>=130&&z<=225)return Math.max(0,Math.min(18,Math.floor((220-z)/5)));
  return z<130?18:z<170?12:z<220?6:0;
}
function terrain(t,old){
  for(let z=0;z<t.S;z++)for(let x=0;x<t.S;x++){
    const h=lift(x,z),ground=old.get(x,96,z);
    if(!ground)continue;
    const main=x<320;
    if(main){
      const r=Math.min(1,Math.hypot((x-168)/146,(z-187)/163)),bottom=Math.max(24,Math.floor(24+r*32+Math.sin(x*.23+z*.11)*8+Math.sin(x*.07-z*.19)*9));
      for(let y=bottom;y<96+h;y++)t.put(x,y,z,y>92+h?'dirt':t.hash(x,z,y)%31===0?'mossy_cobblestone':t.hash(x,z,y)%7?'stone':'andesite');
    }else for(let y=old.LOW;y<96;y++){const id=old.get(x,y,z);if(id){const d=old.definitions[id];t.put(x,y,z,d.Name.slice(10),d.Properties);}}
    for(let y=96;y<old.HIGH;y++){const id=old.get(x,y,z);if(id){const d=old.definitions[id];t.put(x,y+h,z,d.Name==='minecraft:blue_terracotta'?'blue_concrete':d.Name.slice(10),d.Properties);}}
  }
  // Exposed terrace edges have retaining stone and grass overhangs, not smooth dirt slabs.
  for(let z=30;z<345;z++)for(let x=26;x<312;x++)if(old.get(x,96,z)){
    const h=lift(x,z),edge=[[1,0],[-1,0],[0,1],[0,-1]].some(([dx,dz])=>old.get(x+dx,96,z+dz)&&lift(x+dx,z+dz)<h-1);
    if(edge)for(let y=96;y<96+h;y++)t.put(x,y,z,t.hash(x,z,y)%5?'stone_bricks':'mossy_stone_bricks');
  }
}
module.exports={lift,terrain};
