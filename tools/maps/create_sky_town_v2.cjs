const fs=require('node:fs'),path=require('node:path');
const {createTown}=require('./sky-town/core.cjs'),{layout}=require('./sky-town/scale-layout.cjs'),{furnish}=require('./sky-town/interiors.cjs'),{exportMap}=require('./create_ruined_castle.cjs');
const ids=['01-island','02-spawn','03-roads-square','04-market','05-blacksmith','06-adventurers-hall','07-castle','08-dimensional-station'];
function navigation(t,targets){
  // Search actual player air cells, not a heightmap that would confuse roofs with floors.
  const stand=(x,y,z)=>{const base=t.definitions[t.get(x,y-1,z)].Name;return t.get(x,y-1,z)&&!base.endsWith('_carpet')&&!t.get(x,y,z)&&!t.get(x,y+1,z);};
  const key=(x,y,z)=>(y-t.LOW)*t.S*t.S+z*t.S+x,seen=new Set(),q=[t.spawn];seen.add(key(...t.spawn));
  for(let i=0;i<q.length;i++){const [x,y,z]=q[i];for(const [dx,dz]of [[1,0],[-1,0],[0,1],[0,-1]]){const nx=x+dx,nz=z+dz;if(nx<0||nz<0||nx>=t.S||nz>=t.S)continue;for(const dy of [0,1,-1]){const yy=y+dy;if(yy<97||yy>t.HIGH-2)continue;const k=key(nx,yy,nz);if(seen.has(k))break;if(stand(nx,yy,nz)&&!(dy===1&&t.get(x,y+2,z))){seen.add(k);q.push([nx,yy,nz]);break;}}}}
  for(const {name,point}of targets)if(!seen.has(key(...point)))throw Error('Unreachable usable area: '+name+' '+point);
  return {reachableFloorCells:seen.size,checkedTargets:targets};
}
function buildLargeTown(){
  const t=createTown({size:416,high:192}),{a,p,end}=layout(t),old=JSON.parse(fs.readFileSync(path.resolve('archive/maps-before-single-world/starter_sky_town_v1/zones.json'),'utf8')),services=[];
  for(const id of ids){if(id==='07-castle'){const c=require('./sky-town/castle-v2.cjs')(t,p,end);services.push({id:'castle',name:'Castle reception and upper hall',...c});}else require('./sky-town/zones/'+id+'.cjs')(a);}
  for(const b of [
    {id:'npc-shop',name:'NPC Shop',x:34,z:100,w:13,d:13,kind:'shop'},
    {id:'player-market',name:'Market Office',x:54,z:100,w:15,d:13,kind:'shop'},
    {id:'blacksmith',name:'Blacksmith',x:36,z:66,w:25,d:14,kind:'smith'},
    {id:'adventurers',name:'Adventurers Hall',x:130,z:81,w:29,d:20,kind:'hall'}
  ]){services.push({id:b.id,name:b.name,...furnish(t,{x:p(b.x),z:p(b.z),w:p(b.x+b.w)-p(b.x),d:p(b.z+b.d)-p(b.z),kind:b.kind})});}
  // Human-scale street furniture and gardens, on grass rather than the reserved avenue.
  for(const [bx,bz]of [[70,157],[120,157],[63,173],[128,174],[38,154],[157,160],[51,54],[145,61]]){
    const x=p(bx),z=p(bz);if(!t.get(x,96,z))continue;t.pave(x-6,z-4,x+6,z+4);t.planter(x-4,z-3);t.lamp(x+5,z+3);t.fill(x-3,97,z+2,x+2,97,z+2,'spruce_slab',{type:'bottom',waterlogged:'false'});
  }
  // Fine-grained rock ridges give the underside depth instead of one smooth slab.
  for(const [bx,bz,depth]of [[40,105,35],[62,151,40],[87,183,46],[129,167,35],[160,110,30],[123,53,38]]){
    const x=p(bx),z=p(bz);for(let y=depth;y<90;y++){const r=Math.min(8,1+Math.floor((y-depth)/7));for(let dz=-r;dz<=r;dz++)for(let dx=-r;dx<=r;dx++)if(dx*dx+dz*dz<=r*r)t.put(x+dx,y,z+dz,(y+dx+dz)%17?'stone':'andesite');}t.put(x,depth,z,'sea_lantern');
  }
  t.spawn=[p(96),97,p(190)];
  const zones=old.map(z=>({...z,bounds:[p(z.bounds[0]),p(z.bounds[1]),end(z.bounds[2]),end(z.bounds[3])],center:[p(z.center[0]),z.center[1],p(z.center[2])],source:'tools/maps/sky-town/zones/'+z.id+'.cjs',layoutScale:1.6}));
  const targets=[{name:'square',point:[p(96),97,p(100)]},{name:'portal',point:[p(224),97,p(132)]},...services.flatMap(s=>s.checkpoints.map((point,i)=>({name:s.id+'-'+i,point})))];
  const checks=navigation(t,targets);
  return {...t,...checks,name:'NeoWorld - Starter Sky Town v2',zones,services,tiles:[...t.tiles.values()],rooms:zones.filter(z=>z.id!=='01-island')};
}
function generate(destination='maps/starter_sky_town_v2'){
  if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
  const t=buildLargeTown(),result=exportMap(t,destination),file=path.join(destination,'map-manifest.json'),m=JSON.parse(fs.readFileSync(file,'utf8'));
  Object.assign(m,{name:'Starter Sky Town · V2 Expanded',description:'เกาะหลักประมาณ 283 × 322 · อาคารเข้าใช้งานได้ · ผังขยายตามคอนเซปต์',zones:t.zones,services:t.services,navigationChecks:t.checkedTargets,design:{units:'1 Minecraft block',mainIslandApprox:[283,322],saveFootprint:[416,416],layoutScaleFromV1:1.6,mainAvenueWidth:[14,15],sidePathWidth:8,reference:'Concept image has no measured scale; dimensions are design targets, not a certified 1:1 image reconstruction.'},gameplay:'Usable architectural interiors. NPC actions, portal travel and upgrade-table marker still need production setup.'});
  fs.writeFileSync(file,JSON.stringify(m,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(t.zones,null,2));
  fs.writeFileSync(path.join(destination,'README.md'),'# Starter Sky Town V2 Expanded\n\n416 × 416 save footprint. Main island approximately 283 × 322 blocks. These are real block dimensions, not camera scaling. Concept has no measured scale.\n\nOpen shop, market, blacksmith and adventurer interiors. Castle reception, upstairs hall and ground-level tower rooms have path checks. Services and portal are architectural markers only. Editable zones: tools/maps/sky-town/zones; expanded layout: scale-layout.cjs; interiors.cjs; castle-v2.cjs.\n');
  return {...result,services:t.services.length,checkedTargets:t.checkedTargets.length};
}
module.exports={buildLargeTown,navigation,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
