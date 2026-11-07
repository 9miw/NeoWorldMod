const ns='http://www.w3.org/2000/svg',$=id=>document.getElementById(id);
function node(tag,attrs,parent){const n=document.createElementNS(ns,tag);for(const [k,v]of Object.entries(attrs))n.setAttribute(k,v);parent.append(n);return n;}
const bounds=points=>{const xs=points.map(p=>p[0]),zs=points.map(p=>p[1]);return [Math.min(...xs),Math.min(...zs),Math.max(...xs),Math.max(...zs)];};
const points=a=>a.map(p=>p.join(',')).join(' ');
let selected;function select(id){selected=selected===id?undefined:id;for(const n of document.querySelectorAll('[data-zone]')){n.classList.toggle('muted',!!selected&&n.dataset.zone!==selected);n.classList.toggle('selected',n.dataset.zone===selected);}const z=data.zones.find(z=>z.id===selected);$('selection').textContent=z?`${z.id} ${z.name} · จุดเชื่อม X ${z.entry[0]}, Z ${z.entry[1]} · พื้น Y ${z.floor}`:'แสดงทุกโซน';}
let data;
try{
 data=await(await fetch('/reference-plan/plan.json')).json();const old=await(await fetch('/reference-plan/old-zones.json')).json(),plan=$('plan'),traces=$('traces');
 const defs=node('defs',{},plan),pattern=node('pattern',{id:'grid20',width:20,height:20,patternUnits:'userSpaceOnUse'},defs);node('path',{d:'M 20 0 L 0 0 0 20',fill:'none',stroke:'#617184','stroke-opacity':'.3','stroke-width':'.5'},pattern);
 node('rect',{width:420,height:375,fill:'url(#grid20)',id:'grid-layer'},plan);node('polygon',{points:points(data.island),fill:'#31433c',stroke:'#83a692','stroke-width':1.5},plan);
 node('polygon',{points:points(data.islandImage),fill:'none',stroke:'#edf5ed','stroke-width':3,'stroke-dasharray':'7 5'},traces);
 const oldLayer=node('g',{id:'old-layer',display:'none'},plan);
 for(const z of old.filter(z=>!z.id.startsWith('01'))){const [x,z0,x2,z2]=z.islandBounds||z.bounds;node('rect',{x,y:z0,width:x2-x+1,height:z2-z0+1,fill:'none',stroke:'#c9cdd1','stroke-width':1,'stroke-dasharray':'4 3'},oldLayer);}
 node('polyline',{points:'278,219 343,219',fill:'none',stroke:'#c9cdd1','stroke-width':1,'stroke-dasharray':'4 3'},oldLayer);
 node('polyline',{points:points(data.road),fill:'none',stroke:'#e2c976','stroke-width':12,'stroke-linejoin':'round'},plan);
 for(const line of data.branches)node('polyline',{points:points(line),fill:'none',stroke:'#d0bd80','stroke-width':7,'stroke-linejoin':'round'},plan);
 const buildingLayer=node('g',{id:'building-layer'},plan);
 for(const z of data.zones){
   for(const [parent,poly,width]of [[traces,z.image,3],[plan,z.polygon,1.5]]){const p=node('polygon',{points:points(poly),fill:z.color,'fill-opacity':'.12',stroke:z.color,'stroke-width':width,'data-zone':z.id},parent);if(parent===traces)p.addEventListener('click',()=>select(z.id));}
   for(const b of z.buildings){const [x,z0,x2,z2]=b.bounds;node('rect',{x,y:z0,width:x2-x+1,height:z2-z0+1,fill:z.color,'fill-opacity':'.55',stroke:z.color,'stroke-width':1,'data-zone':z.id},buildingLayer);}
   const label=node('text',{x:z.entry[0],y:z.entry[1],fill:z.color,'font-size':10,'text-anchor':'middle','data-zone':z.id},plan);label.textContent=z.id;
   const b=bounds(z.polygon),before=old.find(o=>o.id.startsWith(z.id)),ob=before.islandBounds||before.bounds,row=document.createElement('tr');row.dataset.zone=z.id;
   const info=[`${z.id} ${z.name}`,`${b[2]-b[0]+1} × ${b[3]-b[1]+1}${z.id==='03'?' (เฉพาะลาน ถนนแยก)':''}`,`${ob[2]-ob[0]+1} × ${ob[3]-ob[1]+1}${z.id==='03'?' (รวมถนน)':''}`,z.buildings.map(b=>`${b.name}: ${b.bounds[2]-b.bounds[0]+1} × ${b.bounds[3]-b.bounds[1]+1} / ภายใน ≥ ${b.usableMin.join(' × ')}`).join(' • ')||'ลานและทางเดิน ไม่มีอาคารหลัก'];
   for(const value of info){const td=document.createElement('td');td.textContent=value;row.append(td);}row.addEventListener('click',()=>select(z.id));$('rows').append(row);
 }
 // Keep footprint fills behind building masses and labels.
 plan.append(buildingLayer);for(const text of [...plan.querySelectorAll('text')])plan.append(text);
 node('circle',{cx:165,cy:312,r:2,fill:'white'},plan);node('circle',{cx:167,cy:196,r:5,fill:'#75d6ef',stroke:'#d2f5ff','stroke-width':1},plan);
 const islandLabel=node('text',{x:64,y:269,fill:'#a7bcb0','font-size':10},plan);islandLabel.textContent='01';
 const north=node('text',{x:405,y:19,fill:'#d9e8ec','font-size':10,'text-anchor':'end'},plan);north.textContent='N ↑';
 plan.addEventListener('click',event=>{const id=event.target.closest('[data-zone]')?.dataset.zone;if(id)select(id);});
 for(const [control,target]of [['trace',traces],['old',oldLayer],['buildings',buildingLayer],['grid',$('grid-layer')]])$(control).onchange=()=>target.setAttribute('display',$(control).checked?'inline':'none');
}catch(e){$('selection').textContent='เปิดผังไม่สำเร็จ: '+e.message;}
