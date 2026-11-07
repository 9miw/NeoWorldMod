export function frustumPlanes(matrix,margin=1){
  const row=i=>[matrix[i],matrix[i+4],matrix[i+8],matrix[i+12]],w=row(3),planes=[];
  for(const axis of [0,1,2])for(const sign of [-1,1]){
    const r=row(axis),scale=axis===2?1:margin,p=w.map((v,i)=>v*scale+r[i]*sign),n=Math.hypot(p[0],p[1],p[2]);planes.push(p.map(v=>v/n));
  }
  return planes;
}
export function intersectsBox(planes,min,max){
  return planes.every(p=>p[0]*(p[0]>=0?max[0]:min[0])+p[1]*(p[1]>=0?max[1]:min[1])+p[2]*(p[2]>=0?max[2]:min[2])+p[3]>=-.001);
}
export function planChunks({size,floor,high,cut,focus,mobile,vp}){
  const radius=Math.min(mobile?144:192,size/2-16),wanted=[],retain=new Set(),preparePlanes=frustumPlanes(vp,1.18),retainPlanes=frustumPlanes(vp,1.45);
  for(let z=0;z<size;z+=16)for(let x=0;x<size;x+=16){
    const dx=x+8-focus[0],dz=z+8-focus[2],min=[x-1,floor-1,z-1],max=[Math.min(size,x+16)+1,Math.min(high-1,cut)+2,Math.min(size,z+16)+1],key=x+','+z;
    if(Math.abs(dx)>radius+32||Math.abs(dz)>radius+32||!intersectsBox(retainPlanes,min,max))continue;
    retain.add(key);
    if(Math.abs(dx)>radius||Math.abs(dz)>radius||!intersectsBox(preparePlanes,min,max))continue;
    wanted.push({x,z,key,min,max,lod:0,distance:dx*dx+dz*dz});
  }
  wanted.sort((a,b)=>a.distance-b.distance);return {wanted,retain};
}
