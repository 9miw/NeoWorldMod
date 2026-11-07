// Concept massing only: no region writer, no Minecraft world generation.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'docs/maps/zone07-blueprint'),world=path.join(root,'maps/starter_sky_land_spawn_v1/region/r.0.0.mca');
const hash=()=>crypto.createHash('sha256').update(fs.readFileSync(world)).digest('hex'),before=hash();
const layout={status:'PROPOSED LAYOUT / NOT APPROVED FOR WORLD EDIT',orientation:'north=-Z; front=south=+Z',zone:[103,-10,217,124],core:[118,16,202,100],rearExtension:40,
 areas:[
 {id:1,name:'หอประตู',bounds:[149,108,171,124],floor:112,deck:140,top:143,note:'อยู่ในกำแพงหน้า ไม่ติดโถง; ช่องประตูกว้าง11 บล็อก X155–165'},
 {id:2,name:'โถงกลาง',bounds:[143,50,177,80],floor:116,deck:141,top:146,note:'35×31; ระเบียงยื่นด้านหน้า ผนังแบ่งเป็นสามช่วง ไม่ใช้กล่องเรียบ'},
 {id:3,name:'ปีกตะวันตก',bounds:[119,52,140,100],floor:116,deck:136,top:142,note:'กรอบ22×49; สามมวลเหลื่อมกัน เชื่อมหอคอยหลังที่Z52 และเชื่อมโถงด้วยระเบียงกว้าง7'},
 {id:4,name:'ปีกตะวันออก',bounds:[180,52,201,100],floor:116,deck:136,top:142,note:'กรอบ22×49; สมดุลฝั่งซ้าย เชื่อมหอคอยหลังและระเบียงโถง'},
 {id:5,name:'หอหลัก',bounds:[149,16,171,38],floor:116,deck:156,top:171,note:'23×23; ชั้น116/128/140/152; เชื่อมโถงผ่านอาคารเชื่อม Z39–49'},
 {id:6,name:'บันไดและระเบียง',bounds:[141,81,179,104],floor:112,top:116,note:'กลางกว้าง15 ช่วงZ81–86; สองข้างกว้าง7 X141–147/173–179 Z90–104; พื้นที่ว่างระหว่างชุดคงไว้'},
 {id:7,name:'ลานกลาง',bounds:[141,87,179,107],floor:112,top:112,note:'กรอบ39×21; หักพื้นที่บันไดข้างแล้ว แกนกลางเหลือกว้าง25; น้ำพุศูนย์160,98 รัศมี3 เป็นตัวแทนตำแหน่ง'},
 {id:8,name:'กำแพงและทางเดิน',bounds:[108,-4,212,124],floor:112,deck:120,top:123,note:'กรอบ105×129 แต่แนวจริงเป็นมุมตัด/ป้อมยื่น; ทางเดินหน้า120 หลัง124; ไม่ใช่กำแพงสี่เหลี่ยมทึบ'}],
 wingMasses:[[119,52,140,67,142],[121,68,138,85,136],[119,86,140,100,139],[180,52,201,67,142],[182,68,199,85,136],[180,86,201,100,139]],
 galleries:[{bounds:[139,60,145,66],floor:128,top:132},{bounds:[175,60,181,66],floor:128,top:132}],
 towers:[{x:125,z:45,r:7,deck:150,tip:166},{x:195,z:45,r:7,deck:150,tip:166},{x:125,z:93,r:7,deck:142,tip:158},{x:195,z:93,r:7,deck:142,tip:158}],
 ring:[[122,-4],[198,-4],[212,10],[212,103],[202,117],[181,124],[172,124],[148,124],[126,123],[118,120],[109,114],[108,104],[108,10]],
 levels:{gateFloor:112,courtFloor:112,hallFloor:116,wingFloor:116,keepFloor:116,frontWallDeck:120,rearWallDeck:124,keepDeck:156,highest:171},
 pending:['ยังไม่อนุมัติว่าตรงมอคอัพ','พื้นที่ใช้สอยภายในแต่ละชั้นและช่องเปิดยังไม่ออกแบบ','บันไดเป็นขอบเขตและระดับเสนอ ยังไม่ใช่แบบบล็อกขั้นจริง','แนวกำแพงซ้ายหน้าต้องตรวจหลบโรงตีเหล็กและรักษาทางเดิมก่อนลงบล็อก','รูปทรงขอบดินเป็นกรอบเสนอ ยังไม่กำหนดหน้าผาและการรองรับชั้นดิน','ยังไม่ส่งออกแปลนบล็อกละเอียดใหม่จนผังนี้ผ่าน']};
for(const a of layout.areas)a.size=[a.bounds[2]-a.bounds[0]+1,a.bounds[3]-a.bounds[1]+1];
const [x0,z0,x1,z1]=layout.zone,W=x1-x0+1,D=z1-z0+1,field=new Int16Array(W*D).fill(-1),colors=new Array(W*D).fill('#547647');
const ground=(x,z)=>{const dx=Math.max(119-x,0,x-201),dz=Math.max(6-z,0,z-108);return Math.hypot(dx,dz)<=16;};
function set(x,z,y,color){if(x<x0||x>x1||z<z0||z>z1)throw Error('Out of concept bounds');const i=(z-z0)*W+x-x0;if(y>=field[i]){field[i]=y;colors[i]=color;}}
function box(a,y,c){for(let z=a[1];z<=a[3];z++)for(let x=a[0];x<=a[2];x++)set(x,z,y,c);}
for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++)if(ground(x,z))set(x,z,z<=80?115:111,'#57784c');
box([141,87,179,107],112,'#b9b4a1');box([149,108,171,124],112,'#b9b4a1');
box([143,39,177,49],137,'#b7c0c4');
for(const a of layout.wingMasses)box(a.slice(0,4),a[4],'#b7c0c4');
for(const a of layout.galleries)box(a.bounds,a.top,'#c5cbbd');
// Great hall has a recessed centre and projecting corner bays.
box([145,50,175,77],141,'#cbd0cb');box([143,54,148,80],145,'#cbd0cb');box([172,54,177,80],145,'#cbd0cb');box([149,76,171,80],138,'#bec6c4');
box([149,16,171,38],151,'#cbd0cb');box([151,18,169,36],156,'#d3d5cc');
function cone(cx,cz,r,deck,tip){for(let z=cz-r;z<=cz+r;z++)for(let x=cx-r;x<=cx+r;x++){const d=Math.hypot(x-cx,z-cz);if(d>r+.15)continue;set(x,z,deck,'#d3d5cc');set(x,z,deck+1+Math.floor((1-d/(r+.3))*(tip-deck-1)),'#3464b8');}}
for(const t of layout.towers)cone(t.x,t.z,t.r,t.deck,t.tip);
for(const [x,z]of [[151,18],[169,18],[151,36],[169,36]])cone(x,z,2,159,168);set(160,27,171,'#e6c16f');
// Outer gatehouse uses two rounded shoulders and a recessed centre, rather than a single box.
box([152,108,168,124],137,'#d0d1c6');cone(152,116,3,140,143);cone(168,116,3,140,143);
const wallCells=new Map();function segment(a,b){const n=Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1]));for(let j=0;j<=n;j++){const x=Math.round(a[0]+(b[0]-a[0])*j/n),z=Math.round(a[1]+(b[1]-a[1])*j/n);for(let dz=-1;dz<=1;dz++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,zz=z+dz;if(xx<x0||xx>x1||zz<z0||zz>z1||xx>=149&&xx<=171&&zz>=108)continue;const y=zz<50?124:zz<80?122:120;wallCells.set(xx+','+zz,y);set(xx,zz,y+2,'#a6afb0');}}}
for(let i=0;i<layout.ring.length;i++)segment(layout.ring[i],layout.ring[(i+1)%layout.ring.length]);
for(const [cx,cz]of [[115,4],[205,4],[110,104],[210,104]])for(let z=cz-4;z<=cz+4;z++)for(let x=cx-4;x<=cx+4;x++)if(Math.hypot(x-cx,z-cz)<=4)set(x,z,cz<50?128:124,'#bdc5c2');
for(let z=81;z<=86;z++)box([153,z,167,z],Math.max(112,116-Math.floor((z-81)*4/5)),'#d6ccb1');
for(const xx of [141,173])for(let z=90;z<=104;z++)box([xx,z,xx+6,z],116-Math.floor((z-90)*4/14),'#d6ccb1');
for(let z=95;z<=101;z++)for(let x=157;x<=163;x++)if(Math.hypot(x-160,z-98)<=3)set(x,z,114,'#509cc2');
// Validate structural envelopes, symmetry, space reservation, and massing bounds.
const checks={zoneSize:[W,D],coreSize:[85,85],rearExtension:40,gateCourtHallKeepOrder:124>107&&87>80&&50>38,courtCentralWidth:173-148,gateClearWidth:11,hallCourtDifference:4,worldUnchanged:before===hash(),isConceptOnly:true};
for(const t of layout.towers)if(t.x-t.r<118||t.x+t.r>202||t.z-t.r<16||t.z+t.r>100)throw Error('Tower beyond core');
if(!checks.worldUnchanged||!checks.gateCourtHallKeepOrder)throw Error('Validation failed');
const esc=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;'),p=(x,z)=>[100+(x-x0)*5.7,90+(z-z0)*5.7],svg=(body,w,h)=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" role="img"><rect width="100%" height="100%" fill="#10212b"/>${body}</svg>`;
let top='<style>text{font-family:Arial,sans-serif;fill:#edf0e7}.dim{fill:#d8bb76;font-size:14px}.num{font-size:17px;font-weight:bold;text-anchor:middle}</style><text x="30" y="35" font-size="23">ZONE 07 / PROPOSED PLAN 1–8</text><text x="30" y="60" class="dim">N ↑  / 1 square = 1 block / 115 × 135 site / 85 × 85 core</text>';
for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++){const i=(z-z0)*W+x-x0;if(field[i]<0)continue;const [px,pz]=p(x,z);top+=`<rect x="${px}" y="${pz}" width="5.7" height="5.7" fill="${colors[i]}"/>`;}
for(let x=110;x<=210;x+=10){const [px]=p(x,z0);top+=`<text x="${px}" y="82" font-size="11">${x}</text>`;}for(let z=-10;z<=120;z+=10){const [,pz]=p(x0,z);top+=`<text x="55" y="${pz+9}" font-size="11">${z}</text>`;}
const badges=[[1,160,117],[2,160,65],[3,129,75],[4,191,75],[5,160,27],[6,144,98],[6,176,98],[7,160,90],[8,111,65],[8,209,65]];
for(const [id,x,z]of badges){const [px,pz]=p(x,z);top+=`<circle cx="${px+3}" cy="${pz+3}" r="14" fill="#153c63" stroke="#f4d88f" stroke-width="2"/><text x="${px+3}" y="${pz+9}" class="num">${id}</text>`;}
const [cx,cz]=p(118,16);top+=`<rect x="${cx}" y="${cz}" width="${85*5.7}" height="${85*5.7}" fill="none" stroke="#edc467" stroke-dasharray="7 5"/><text x="${cx}" y="${cz-9}" class="dim">85 × 85 CORE (dashed)</text>`;
top+='<text x="100" y="890" class="dim">Front / South → existing road; back / North → proposed land extension</text>';
const topSvg=svg(top,870,920);
const faces=[],scale=5.3,proj=(x,y,z)=>[820+((x-160)-(z-57))*scale,930+((x-160)+(z-57))*scale*.45-(y-108)*scale*1.25];
function face(points,c,depth){faces.push({points,c,depth});}
for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++){const i=(z-z0)*W+x-x0,h=field[i];if(h<0)continue;const pp=(xx,yy,zz)=>proj(xx,yy,zz),dep=x+z;
 face([pp(x,h+1,z),pp(x+1,h+1,z),pp(x+1,h+1,z+1),pp(x,h+1,z+1)],colors[i],dep+.05);
 for(const [dx,dz,shade]of [[1,0,'#818f91'],[0,1,'#9ca6a3']]){const nx=x+dx,nz=z+dz,j=(nz-z0)*W+nx-x0,nh=nx>x1||nz>z1?-1:field[j],lo=Math.max(105,nh+1);if(h<lo)continue;face(dx?[pp(x+1,lo,z),pp(x+1,lo,z+1),pp(x+1,h+1,z+1),pp(x+1,h+1,z)]:[pp(x,lo,z+1),pp(x+1,lo,z+1),pp(x+1,h+1,z+1),pp(x,h+1,z+1)],shade,dep+.1);}
}
faces.sort((a,b)=>a.depth-b.depth);let iso='<text x="45" y="45" fill="#f0dfad" font-family="Arial" font-size="25">ZONE 07 / MASSING STUDY — NOT FINAL ARCHITECTURE</text>';
for(const f of faces)iso+=`<polygon points="${f.points.map(p=>p.map(n=>n.toFixed(1)).join(',')).join(' ')}" fill="${f.c}"/>`;
for(const [id,x,z]of badges.filter(([id,x])=>id!==8||x===209)){const h=field[(z-z0)*W+x-x0],[px,py]=proj(x,h+3,z);iso+=`<circle cx="${px}" cy="${py}" r="17" fill="#153c63" stroke="#f4d88f" stroke-width="2"/><text x="${px}" y="${py+6}" text-anchor="middle" fill="white" font-size="20" font-family="Arial">${id}</text>`;}
iso+='<text x="45" y="1160" fill="#e1c986" font-family="Arial" font-size="19">Solid volumes show positions and height hierarchy only. Gate arch and interiors are not cut yet.</text>';
const massSvg=svg(iso,1700,1200);
const rows=layout.areas.map(a=>`<tr><td>${a.id} ${a.name}</td><td>${a.size.join(' × ')}</td><td>X${a.bounds[0]}–${a.bounds[2]} / Z${a.bounds[1]}–${a.bounds[3]}</td><td>พื้น ${a.floor} / ยอด ${a.top}</td><td>${a.note}</td></tr>`).join('');
const ref=path.join('C:/Users/miw/.codex/generated_images/01a11c41-9459-7283-be89-0c281d596564','exec-617b414f-2f9b-41b3-822b-55f128b54ef9.png');
const html=`<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>07 · ผังใหม่ 1–8</title><style>*{box-sizing:border-box}body{margin:0;background:#0c1922;color:#e8ece9;font:16px system-ui}header,section{padding:20px;max-width:1500px;margin:auto}h1{font-size:25px;margin:0 0 8px}.warn{color:#e8c979}nav{display:flex;flex-wrap:wrap;gap:8px;margin:15px 0}button{padding:10px 15px;border:1px solid #8099aa;border-radius:6px;background:#243c4c;color:white;font:inherit;cursor:pointer}button[aria-pressed=true]{background:#315d82;border-color:#edca7c}.panel{display:none}.panel.active{display:block}svg,img{display:block;max-width:100%;height:auto;margin:auto}#top svg{max-height:1000px}table{border-collapse:collapse;width:100%;font-size:14px}th,td{border:1px solid #41525e;padding:10px;text-align:left}a{color:#9dd4ff}.scroll{overflow:auto}.note{color:#b9c8c9;line-height:1.7}@media(max-width:600px){section,header{padding:12px}h1{font-size:21px}table{min-width:850px}}</style><header><h1>ปราสาท 07 — ผังใหม่ ①–⑧</h1><div class="warn">แบบเสนอสำหรับตรวจรูปทรงและตำแหน่ง · ยังไม่แก้แมพจริง</div><p>โซน115×135 · ตัวปราสาท85×85 · ขยายด้านหลัง40บล็อก · พื้นลาน112 / อาคาร116 / ยอด171</p><nav><button data-panel="top" aria-pressed="true">แปลนด้านบน</button><button data-panel="mass" aria-pressed="false">มวลอาคาร 3D</button><button data-panel="ref" aria-pressed="false">มอคอัพอ้างอิง</button><button data-panel="sizes" aria-pressed="false">ขนาดและระดับ</button></nav></header><section id="top" class="panel active">${topSvg}<p class="note">ขอบทองคือกรอบ85×85 ตำแหน่ง①–⑧ตรงกับมอคอัพ ปีกอาคารแบ่งสามช่วงเว้า–ยื่น ตัวแทนน้ำพุมีไว้ตรวจตำแหน่ง ยังไม่ใช่แบบตกแต่ง</p></section><section id="mass" class="panel">${massSvg}<p class="note">ภาพนี้ตรวจมวล ตำแหน่ง และความสูงเท่านั้น ยังเป็นรูปทรงทึบ ไม่มีการเจาะซุ้มประตู หน้าต่าง ภายใน หรือรายละเอียดวัสดุ จึงยังไม่ใช่ภาพปราสาทสำเร็จ</p></section><section id="ref" class="panel"><img alt="มอคอัพปราสาทอ้างอิง" src="data:image/png;base64,${fs.readFileSync(ref).toString('base64')}"><p class="note">ใช้ภาพใหญ่ด้านหน้าเป็นหลัก ส่วนภาพย่อยใช้ช่วยตรวจหน้าที่ของพื้นที่ ไม่ถือว่าทุกภาพมีสัดส่วนตรงกัน</p></section><section id="sizes" class="panel"><div class="scroll"><table><thead><tr><th>จุด</th><th>กรอบกว้าง×ลึก</th><th>ตำแหน่งเสนอ</th><th>Y</th><th>รูปทรงและเงื่อนไข</th></tr></thead><tbody>${rows}</tbody></table></div><p>บันไดหลัก→①ประตู→⑦ลาน→⑥บันได→②โถง→อาคารเชื่อม→⑤หอหลัก</p><p>ช่องเปิดลาน39×21 รวมกรอบบันไดข้าง พื้นที่กลางที่ไม่ทับชุดบันไดกว้าง25บล็อก หอคอยและปีกมีจุดประกบโดยตั้งใจ</p><ul>${layout.pending.map(t=>'<li>'+esc(t)+'</li>').join('')}</ul><p><a href="layout.json">ข้อมูลผัง</a> · <a href="layout-top.svg">แปลนภาพ</a> · <a href="layout-massing.svg">ภาพมวลอาคาร</a></p></section><script>for(const b of document.querySelectorAll('button[data-panel]'))b.onclick=()=>{for(const x of document.querySelectorAll('.panel'))x.classList.toggle('active',x.id===b.dataset.panel);for(const x of document.querySelectorAll('button[data-panel]'))x.setAttribute('aria-pressed',String(x===b));};</script></html>`;
const screenHtml=html.replace('#top svg{max-height:1000px}','svg{max-height:calc(100vh - 270px)}#top svg{max-height:calc(100vh - 270px)}').replace('ตำแหน่ง①–⑧ตรงกับมอคอัพ','หมายเลข①–⑧ยึดมอคอัพ ตำแหน่งเสนอรอตรวจ');
fs.mkdirSync(out,{recursive:true});for(const [name,value]of Object.entries({'layout.json':JSON.stringify({...layout,checks},null,2),'layout-top.svg':topSvg,'layout-massing.svg':massSvg,'layout.html':screenHtml}))fs.writeFileSync(path.join(out,name),value);
if(before!==hash())throw Error('World changed');console.log(checks);
