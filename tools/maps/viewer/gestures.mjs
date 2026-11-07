// Keep pointer transitions separate from the camera so lifting a finger cannot jump the view.
export function createGestures(){
  const points=new Map();
  function pair(){const [a,b]=points.values();return {x:(a.x+b.x)/2,y:(a.y+b.y)/2,distance:Math.hypot(a.x-b.x,a.y-b.y)};}
  return {
    start(id,x,y){points.set(id,{x,y});},
    move(id,x,y){
      const previous=points.get(id);if(!previous)return null;
      if(points.size===1){points.set(id,{x,y});return {kind:'rotate',dx:x-previous.x,dy:y-previous.y};}
      if(points.size!==2){points.set(id,{x,y});return null;}
      const before=pair();points.set(id,{x,y});const after=pair();
      return {kind:'transform',dx:after.x-before.x,dy:after.y-before.y,zoom:before.distance>=10&&after.distance>=10?before.distance/after.distance:1};
    },
    end(id){points.delete(id);},
    clear(){points.clear();}
  };
}
