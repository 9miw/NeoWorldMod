// Explicitly authorized design restart. Stages outside maps; archives the former island.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {exportMap}=require('./create_ruined_castle.cjs');
const ROOT=path.resolve(__dirname,'../..'),target=path.join(ROOT,'maps/leveling_island_01');
const DESIGN='forest-basin-terrain-01',S=384,LOW=8,HIGH=168,FLOOR=8;
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
function build(){
 const blocks=new Uint16Array(S*S*(HIGH-LOW)),definitions=[{Name:'minecraft:air'}],ids=new Map([['air{}',0]]);
 function id(n,p){const k=n+JSON.stringify(p||{});if(!ids.has(k)){ids.set(k,definitions.length);definitions.push({Name:'minecraft:'+n,...(p?{Properties:p}:{})});}return ids.get(k);}
 const at=(x,y,z)=>(y-LOW)*S*S+z*S+x;
 function put(x,y,z,n,p){assert(x>=0&&z>=0&&x<S&&z<S&&y>=LOW&&y<HIGH,'Out of bounds');blocks[at(x,y,z)]=id(n,p);}
 const top=new Int16Array(S*S).fill(-1),bottom=new Int16Array(S*S).fill(-1),route=new Uint8Array(S*S);
 const control=[[121,24],[167,32],[182,52],[218,46],[240,67],[276,74],[295,104],[281,129],[321,151],[316,180],[337,199],[322,228],[299,233],[310,261],[285,293],[251,305],[232,328],[194,321],[167,309],[142,315],[120,296],[83,296],[57,275],[72,249],[43,229],[49,206],[83,194],[71,163],[89,143],[81,112],[95,86],[82,60],[96,35]];
 // Catmull-Rom interpolated silhouette avoids an ellipse and angular polygon corners.
 const outline=[];for(let i=0;i<control.length;i++)for(let j=0;j<8;j++){
  const t=j/8,a=control[(i+control.length-1)%control.length],b=control[i],c=control[(i+1)%control.length],d=control[(i+2)%control.length];
  outline.push([0,1].map(k=>0.5*((2*b[k])+(-a[k]+c[k])*t+(2*a[k]-5*b[k]+4*c[k]-d[k])*t*t+(-a[k]+3*b[k]-3*c[k]+d[k])*t*t*t)));
 }
 function inside(x,z){let yes=false;for(let i=0,j=outline.length-1;i<outline.length;j=i++){const a=outline[i],b=outline[j];if((a[1]>z)!==(b[1]>z)&&x<(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])yes=!yes;}return yes;}
 const gauss=(x,z,cx,cz,rx,rz)=>Math.exp(-(((x-cx)/rx)**2+((z-cz)/rz)**2));
 function terrain(x,z){return 89+53*gauss(x,z,125,65,54,62)+30*gauss(x,z,267,148,52,86)+14*gauss(x,z,91,215,40,50)+5*Math.sin(x/28)*Math.sin(z/34)-6*gauss(x,z,197,254,51,42);}
 for(let z=16;z<338;z++)for(let x=32;x<348;x++)if(inside(x,z)){
  let edge=999;for(const [ox,oz] of outline)edge=Math.min(edge,Math.hypot(x-ox,z-oz));
  const hi=Math.round(terrain(x,z)),ridge=Math.pow(Math.abs(Math.sin(x/16+Math.sin(z/23))*Math.cos(z/19)),1.7);
  const depth=8+Math.min(1,edge/22)*(24+35*ridge)+Math.min(12,edge/5),lo=Math.max(12,Math.round(hi-depth));
  top[z*S+x]=hi;bottom[z*S+x]=lo;
 }
 // Floating entry island, physically separate from the mainland.
 for(let z=335;z<=371;z++)for(let x=175;x<=211;x++){const r=Math.hypot((x-193)/17,(z-353)/18);if(r<=1){top[z*S+x]=88;bottom[z*S+x]=Math.round(44+36*r*r);}}
 function column(x,z,hi){const k=z*S+x;assert(top[k]>=0,'Path leaves land at '+x+','+z);top[k]=hi;}
 const pads=[[169,264,16,13],[210,271,18,13],[230,244,16,14],[189,228,17,13],[155,242,15,12],[222,214,16,12]];
 for(const [cx,cz,w,d] of pads){const level=Math.round(terrain(cx,cz));for(let z=cz-d;z<=cz+d;z++)for(let x=cx-w;x<=cx+w;x++){const r=Math.max(Math.abs(x-cx)/w,Math.abs(z-cz)/d),k=z*S+x;if(top[k]<0)continue;const blend=Math.min(1,Math.max(0,(r-.62)/.38));top[k]=Math.round(level*(1-blend)+top[k]*blend);}}
 const paths=[[[193,319],[196,300],[196,280],[191,258],[202,241],[207,218],[188,196],[174,177],[180,153],[167,131],[144,112],[126,96],[126,73]],[[202,241],[239,237],[258,217],[270,194],[280,179],[295,175]],[[270,194],[247,177],[219,180],[188,196]]];
 const centerlines=[];
 for(const points of paths){const samples=[];for(let j=1;j<points.length;j++){const a=points[j-1],b=points[j],steps=Math.ceil(Math.hypot(a[0]-b[0],a[1]-b[1])*2);for(let i=0;i<=steps;i++){const t=i/steps,x=Math.round(a[0]+(b[0]-a[0])*t),z=Math.round(a[1]+(b[1]-a[1])*t);samples.push([x,z]);}}
  for(const [x,z] of samples){assert(top[z*S+x]>=0,'Disconnected planned route');for(let dz=-2;dz<=2;dz++)for(let dx=-2;dx<=2;dx++)if(dx*dx+dz*dz<=5&&top[(z+dz)*S+x+dx]>=0)route[(z+dz)*S+x+dx]=1;}centerlines.push(samples);
 }
 // Chapel reserve on the high northwest ridge: flat center, blended shoulders, no building yet.
 const chapelY=Math.round(terrain(126,66));for(let z=49;z<=82;z++)for(let x=113;x<=139;x++)if(top[z*S+x]>=0){const r=Math.max(Math.abs(x-126)/13,Math.abs(z-66)/17),t=Math.min(1,Math.max(0,(r-.65)/.35));top[z*S+x]=Math.round(chapelY*(1-t)+top[z*S+x]*t);}
 for(let z=0;z<S;z++)for(let x=0;x<S;x++){const k=z*S+x;if(top[k]<0)continue;for(let y=bottom[k];y<=top[k];y++)put(x,y,z,y===top[k]?(route[k]?'coarse_dirt':'grass_block'):y>top[k]-4?'dirt':y<bottom[k]+3?'deepslate':(x*17+z*13+y)%23===0?'andesite':'stone',y===top[k]&&!route[k]?{snowy:'false'}:undefined);}
 // Level wooden connection at the southern landing, with stairs into the mainland if needed.
 for(let z=319;z<=337;z++)for(let x=191;x<=195;x++){const y=88;put(x,y,z,'spruce_planks');for(let yy=y+1;yy<=Math.max(y+3,top[z*S+x]);yy++)put(x,yy,z,'air');top[z*S+x]=y;route[z*S+x]=1;}
 // Entrance landing must blend with the road rather than collide with hillside.
 for(let z=312;z<=318;z++)for(let x=191;x<=195;x++){const y=88+Math.floor((318-z)/2);for(let yy=bottom[z*S+x];yy<=y;yy++)if(yy>=LOW)put(x,yy,z,yy===y?'coarse_dirt':'dirt');for(let yy=y+1;yy<=Math.max(top[z*S+x]+2,y+3);yy++)put(x,yy,z,'air');top[z*S+x]=y;}
 const rooms=[{name:'01 Arrival reserve',bounds:[175,335,211,371],center:[193,89,353]},{name:'02 Village basin · six reserves',bounds:[137,199,251,289],center:[193,top[258*S+193]+1,258]},{name:'03 Eastern forest ridge reserve',bounds:[224,105,312,217],center:[267,top[148*S+267]+1,148]},{name:'04 Chapel ridge reserve',bounds:[105,42,148,92],center:[126,chapelY+1,66]},{name:'05 Cave approach reserve',bounds:[280,161,312,191],center:[295,top[175*S+295]+1,175]}];
 return {S,LOW,HIGH,FLOOR,blocks,definitions,tiles:[],rooms,spawn:[193,89,353],name:'Island 01 · Forest Basin · Terrain',colours:{grass_block:'#668845',coarse_dirt:'#846846'},top,bottom,pads,paths,centerlines,outline,chapelY};
}
function verify(model){const {S,LOW,HIGH,blocks,spawn,top,centerlines}=model;const get=(x,y,z)=>blocks[(y-LOW)*S*S+z*S+x];assert(get(spawn[0],spawn[1]-1,spawn[2]));assert(!get(spawn[0],spawn[1],spawn[2])&&!get(spawn[0],spawn[1]+1,spawn[2]));for(const line of centerlines)for(const [x,z] of line)assert(top[z*S+x]>=0);for(const y of [89,90])assert(!get(193,y,329));return {spawnClear:true,plannedRoutesOnLand:true,bridgeClear:true,inGame:false};}
function run(){assert(process.argv.includes('--apply'),'Explicit --apply required');const oldManifest=JSON.parse(fs.readFileSync(path.join(target,'map-manifest.json')));assert(oldManifest.designId!==DESIGN,'Restart already applied; edit this world in place from now on');const source=sha(fs.readFileSync(path.join(target,'region/r.0.0.mca'))),stamp=Date.now(),stage=path.join(ROOT,'archive/map-edit-staging','forest-basin-'+stamp),backup=path.join(ROOT,'archive/map-edit-backups','before-forest-basin-restart-'+stamp,'leveling_island_01');
 const model=build(),validation=verify(model);exportMap(model,stage);
 const mf=path.join(stage,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));Object.assign(manifest,{name:model.name,designId:DESIGN,description:'New design · terrain blockout only · buildings and trees pending',floorY:LOW,gameplay:'Terrain blockout only; no NPCs, monsters, warp or checkpoint logic.'});delete manifest.reachableFloorCells;fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const layout={designId:DESIGN,orientation:'north = -Z',footprint:[S,S],outline:model.outline,paths:model.paths,buildingReserves:model.pads.map(([x,z,rx,rz])=>({center:[x,model.top[z*S+x],z],bounds:[x-rx,z-rz,x+rx,z+rz]})),zones:model.rooms,validation};fs.writeFileSync(path.join(stage,'layout.json'),JSON.stringify(layout,null,2));fs.writeFileSync(path.join(stage,'zones.json'),JSON.stringify(model.rooms,null,2));
 fs.writeFileSync(path.join(stage,'README.md'),'# Island 01 · Forest Basin\n\nMinecraft Java 1.21.1 · 384×384 · spawn 193 89 353.\n\nTerrain blockout only. Buildings, forest assets, cave interior, portal and gameplay are pending. Old eight-zone tools do not apply. Read MAP.md before edits.\n');
 fs.writeFileSync(path.join(stage,'MAP.md'),`# เกาะ 01 ใหม่ — หมู่บ้านร้างกลางป่าและเนินเขา\n\nผู้ใช้ยืนยันเริ่มนับ 1 ใหม่ เลิกผัง 8 โซนเดิม และยืนยันมอคอัพใหม่\n\n- designId: ${DESIGN}\n- แมพทำงานเดิม: maps/leveling_island_01; ต่อจากนี้แก้ในโลกนี้ ไม่รันตัวเริ่มใหม่ซ้ำ\n- กรอบบันทึก 384×384 เป็นขนาดตั้งต้นที่ผู้พัฒนากำหนด ไม่ใช่มาตราส่วน 1:1 จากรูป\n- Java 1.21.1 / DataVersion 3955 / 576 chunks / จุดเกิด 193 89 353 / พื้นทางเข้า Y88\n- มอคอัพ: docs/maps/island-01-new-terrain-concept.png\n- สำรองงานเก่า: ${path.relative(ROOT,backup)}\n- sourceRegionSha256: ${source}\n\n## ขั้นที่ 1 — แผ่นดินและผัง\n\nเกาะหลักทรงยาวเหนือ–ใต้ ชายฝั่งเว้าแหว่งด้านตะวันตก/ตะวันออก ฐานหินหลายยอด; แอ่งหมู่บ้านทางใต้ สันเขาป่าทางตะวันออก ยอดโบสถ์ทางตะวันตกเฉียงเหนือ\n\n${model.rooms.map(r=>'- '+r.name+': X/Z bounds '+r.bounds.join(',')+' / center '+r.center.join(',')).join('\n')}\n\nพื้นที่อาคาร 6 หลังและแนวทางเดินอยู่ใน layout.json ยังเป็นพื้นที่สำรอง ไม่ใช่อาคารจริง; ยังไม่มีต้นไม้ โบสถ์ ถ้ำภายใน ประตูมิติ หรือมอนสเตอร์\n\n## ตรวจสอบ\n\nตรวจจุดเกิด ทางเชื่อม และแนวเส้นทางอยู่บนแผ่นดินก่อนบันทึกแล้ว; ผล native/compile และภาพ 3D จะบันทึกต่อท้ายหลังตรวจจริง ไม่อ้างผลตัวตรวจ 8 โซนเก่า\n`);
 assert.equal(sha(fs.readFileSync(path.join(target,'region/r.0.0.mca'))),source,'Map changed concurrently');fs.mkdirSync(path.dirname(backup),{recursive:true});
 // Both literal paths are checked within the project before moving a directory.
 for(const p of [target,backup,stage])assert(path.resolve(p).startsWith(ROOT+path.sep));fs.renameSync(target,backup);try{fs.renameSync(stage,target);}catch(e){fs.renameSync(backup,target);throw e;}
 console.log(JSON.stringify({target,backup,designId:DESIGN,validation,chapelY:model.chapelY}));
}
module.exports={build,verify};if(require.main===module)run();
