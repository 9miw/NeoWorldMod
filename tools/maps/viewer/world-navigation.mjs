export function rebasePoint(point,oldOrigin,newOrigin){return [point[0]+oldOrigin[0]-newOrigin[0],point[1],point[2]+oldOrigin[1]-newOrigin[1]];}
export function clampCenter(bounds,x,z){return [Math.max(bounds[0],Math.min(bounds[2],Math.round(x))),Math.max(bounds[1],Math.min(bounds[3],Math.round(z)))];}
export function needsWindow(model,point,mobile){
  if(!model?.fullWorld)return false;
  const radius=Math.min(mobile?144:192,model.size/2-16),margin=Math.max(16,model.size/2-radius-24);
  return Math.abs(point[0]-model.size/2)>margin||Math.abs(point[2]-model.size/2)>margin;
}
export function overviewPoint(bounds,u,v){return clampCenter(bounds,bounds[0]+Math.max(0,Math.min(1,u))*(bounds[2]-bounds[0]),bounds[1]+Math.max(0,Math.min(1,v))*(bounds[3]-bounds[1]));}

export function cachedWindow(windows,id,center,mobile){return [...windows].find(c=>c.data.id===id&&!needsWindow(c.data,[center[0]-c.data.origin[0],0,center[1]-c.data.origin[1]],mobile));}
