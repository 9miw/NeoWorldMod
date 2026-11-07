const assert=require('node:assert/strict');
// Read the saved stair/slab geometry along cardinal route centerlines at 0.1-block intervals.
// This is a point-foot/headroom model, not the Minecraft 0.6-wide player collision engine.
module.exports=function verify(m,layout){
 const b=Buffer.from(m.blocks,'base64'),S=m.size,def=(x,y,z)=>m.definitions[b.readUInt16LE(((y-m.low)*S*S+z*S+x)*2)];
 function surface(wx,wz,expected){const x=Math.floor(wx),z=Math.floor(wz),fx=wx-x,fz=wz-z;for(let y=Math.min(m.high-1,Math.floor(expected));y>=m.low;y--){const d=def(x,y,z),n=d.Name,p=d.Properties||{};if(n==='minecraft:air')continue;
  let h=y+1;if(n.endsWith('_slab')&&p.type==='bottom')h=y+.5;
  if(n.endsWith('_stairs')){assert.equal(p.half,'bottom');assert.equal(p.shape,'straight');const high=p.facing==='north'?fz<.5:p.facing==='south'?fz>=.5:p.facing==='west'?fx<.5:fx>=.5;h=y+(high?1:.5);}
  for(let yy=y+1;yy<=y+3&&yy<m.high;yy++)assert.equal(def(x,yy,z).Name,'minecraft:air','Headroom blocked');return h;
 }throw Error('Missing support');}
 const expectedNodes=new Map(layout.walkNodes.map(n=>[n.x+','+n.z,n.feet]));
 let samples=0,maxDelta=0;for(const line of layout.walkRoutes){let prev;for(let i=1;i<line.length;i++){const a=line[i-1],c=line[i];assert.equal(Math.abs(c[0]-a[0])+Math.abs(c[1]-a[1]),1);for(let j=0;j<=10;j++){const t=j/10,wx=a[0]+.5+(c[0]-a[0])*t,wz=a[1]+.5+(c[1]-a[1])*t,expected=expectedNodes.get(Math.floor(wx)+','+Math.floor(wz)),h=surface(wx,wz,expected);if(prev!==undefined){const delta=Math.abs(h-prev);maxDelta=Math.max(maxDelta,delta);assert(delta<=.5001,`Full step on route [${a}] -> [${c}] at sample ${j}: ${prev} -> ${h}`);}prev=h;samples++;}}}
 const nodes=new Map(layout.walkNodes.map(n=>[n.x+','+n.z,n])),seen=new Set(['193,337']),queue=[[193,337]];
 for(let i=0;i<queue.length;i++){const [x,z]=queue[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const k=(x+dx)+','+(z+dz);if(nodes.has(k)&&!seen.has(k)&&Math.abs(nodes.get(k).feet-nodes.get(x+','+z).feet)<=.5){seen.add(k);queue.push([x+dx,z+dz]);}}}
 for(const p of layout.buildingReserves)assert(seen.has(p.center[0]+','+p.center[2]),'Disconnected building reserve');assert(seen.has('126,73'),'Disconnected chapel approach');assert.equal(seen.size,nodes.size);
 return {surfaceSamples:samples,maxAdjacentSampleStep:maxDelta,connectedWalkNodes:seen.size,allSixBuildingReservesConnected:true,limitation:'Point surface sampling only; player width, runtime stair shape updates and Minecraft movement still require in-game testing.'};
};
