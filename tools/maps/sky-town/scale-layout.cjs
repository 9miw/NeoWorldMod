// Scale the layout in X/Z; rebuild doors, roofs, lamps and trees in real block units.
// One preview block remains one Minecraft block. This is not a scaled viewer camera.
function layout(t){
  const p=n=>8+Math.floor(n*1.6),end=n=>p(n+1)-1,a={...t};
  a.put=(x,y,z,n,props)=>t.fill(p(x),y,p(z),end(x),y,end(z),n,props);
  a.fill=(x,y,z,x2,y2,z2,n,props)=>t.fill(p(x),y,p(z),end(x2),y2,end(z2),n,props);
  a.get=(x,y,z)=>t.get(p(x),y,p(z));
  a.pave=(x,z,x2,z2,y=96)=>t.pave(p(x),p(z),end(x2),end(z2),y);
  a.lamp=(x,z,y=96)=>t.lamp(p(x),p(z),y);
  a.tree=(x,z,y=96)=>{
    const cx=p(x),cz=p(z);t.fill(cx,y+1,cz,cx+1,y+11,cz+1,'oak_log',{axis:'y'});
    for(let dy=8;dy<=16;dy++)for(let dz=-5;dz<=5;dz++)for(let dx=-5;dx<=5;dx++)if(dx*dx+dz*dz+(dy-11)**2*.7<30&&!(dx>=0&&dx<=1&&dz>=0&&dz<=1&&dy<=11))t.put(cx+dx,y+dy,cz+dz,'oak_leaves',{distance:'1',persistent:'true',waterlogged:'false'});
  };
  a.planter=(x,z,y=96)=>t.planter(p(x),p(z),y);
  a.house=(x,z,w,d,options={})=>{
    const xx=p(x),zz=p(z),ww=p(x+w)-xx,dd=p(z+d)-zz,h=Math.max(12,Math.round((options.h||7)*1.6)),y=options.y||96;
    const result=t.house(xx,zz,ww,dd,{...options,h});
    for(let dx=0;dx<ww;dx++){const rise=Math.min(dx+1,ww-dx);for(const face of [zz,zz+dd-1])t.fill(xx+dx,y+h+1,face,xx+dx,y+h+rise,face,'stripped_oak_wood',{axis:'y'});}
    for(let dx=5;dx<ww-3;dx+=7){t.fill(xx+dx,y+1,zz+dd,xx+dx,y+h,zz+dd,'spruce_log',{axis:'y'});if(Math.abs(dx-Math.floor(ww/2))>4)t.fill(xx+dx+1,y+4,zz+dd-1,xx+dx+3,y+7,zz+dd-1,'light_blue_stained_glass');}
    // Roof overhang, visible foundation and a front lintel above the clear doorway.
    t.fill(xx,y+h,zz+dd,xx+ww-1,y+h,zz+dd,'spruce_log',{axis:'x'});
    const cx=xx+Math.floor(ww/2);t.fill(cx-2,y+1,zz+dd-1,cx+2,y+4,zz+dd,'air');t.fill(cx-3,y+5,zz+dd,cx+3,y+5,zz+dd,'spruce_log',{axis:'x'});
    return result;
  };
  return {a,p,end};
}
module.exports={layout};
