// Detailed preview geometry only. No world writer or import command is provided.
const fs=require('node:fs'),path=require('node:path');
const {vox,spec,out,hash,before}=require('./design_front_bastions.cjs'),K=(x,y,z)=>`${x},${y},${z}`,get=(x,y,z)=>vox.get(K(x,y,z))||'air';
const put=(x,y,z,n)=>{if(x<116||x>204||z<103||z>125||y<108||y>135)throw Error('Detail outside bounds');vox.set(K(x,y,z),n);};
const stair=(f,h='bottom')=>`stairs:${f}:${h}`,mirror=n=>n.replace(/east|west/g,f=>f==='east'?'west':'east'),toward=(dx,dz)=>Math.abs(dx)>Math.abs(dz)?dx>0?'west':'east':dz>0?'north':'south';
// Sloping stone footings and inverted stair corbels follow the actual round footprint.
for(const cx of [125,195])for(let z=103;z<=121;z++)for(let x=cx-9;x<=cx+9;x++){
 const dx=x-cx,dz=z-112,r=Math.hypot(dx,dz),f=toward(dx,dz);
 for(const [y,lo,hi]of [[112,8.1,9.1],[114,7.1,8.1]])if(r>=lo&&r<=hi)put(x,y,z,stair(f));
 if(r>=6.8&&r<=8.1)put(x,126,z,stair(f,'top'));
 if(r>=8.1&&r<=9.1)put(x,127,z,'slab:bottom');
 if(r>=7.2&&r<=9.1&&get(x,131,z)==='stone')put(x,131,z,'slab:bottom');
 // Facade stone variation is material-based, not a baked texture.
 for(let y=115;y<=125;y++)if(get(x,y,z)==='stone'&&(Math.abs(x-160)+y+z)%13===0)put(x,y,z,'chiseled');
}
for(const cx of [125,195]){
 for(const dx of [-1,1])for(let y=118;y<=121;y++)put(cx+dx,y,119,'pier');
 put(cx,117,119,stair('north'));put(cx,122,119,stair('north','top'));
 for(const dx of [-3,3]){put(cx+dx,113,120,stair('north'));put(cx+dx,124,120,stair('north','top'));put(cx+dx,125,120,'slab:bottom');}
 for(const dx of [-4,4]){for(let y=117;y<=122;y++)put(cx+dx,y,119,y===119?'white':'blue');put(cx+dx,123,119,'gold');}
 for(const dx of [-5,5]){put(cx+dx,129,118,'chiseled');put(cx+dx,130,118,'lantern');}
}
// Keep the full eleven-block opening: shaped arch stones are ABOVE its original crown.
for(let x=155;x<=165;x++){const crown=122+Math.floor(4*(1-(Math.abs(x-160)/6)**2));put(x,crown+1,124,x===160?'pier':stair(x<160?'east':'west','top'));}
for(const x of [151,169]){put(x,113,125,stair('north'));put(x,130,125,stair('north','top'));put(x,131,125,'lantern');}
for(let x=149;x<=171;x++){
 if(x<155||x>165)put(x,116,124,'chiseled');
 if(get(x,135,124)==='stone')put(x,135,124,'slab:bottom');
 put(x,132,124,'slab:top');
}
for(const x of [152,168])for(let y=118;y<=125;y++)put(x,y,124,y===118||y===125?'gold':y===121?'white':'blue');
for(let y=129;y<=131;y++)for(let x=158;x<=162;x++)put(x,y,124,x===160||y===130?'gold':'blue');
// A stair landing is cut through the relevant deck instead of being buried by it.
function step(x,y,z,f){for(let yy=y+1;yy<=Math.min(135,y+3);yy++)put(x,yy,z,'air');put(x,y,z,stair(f));}
for(const cx of [125,195]){
 const m=cx===125?1:-1,X=d=>cx+m*d,F=f=>m===1?f:mirror(f);
 for(let j=0;j<4;j++)for(const z of [109,110])step(X(-3+j),113+j,z,F('east'));
 for(let x=1;x<=2;x++)for(const z of [109,110])put(X(x),116,z,'deck');
 for(let j=0;j<4;j++)for(const x of [2,3])step(X(x),117+j,111+j,'south');
 for(const x of [2,3])for(const z of [115,116])put(X(x),120,z,'deck');
 for(let j=0;j<4;j++)for(const z of [115,116])step(X(1-j),121+j,z,F('west'));
 for(const x of [-4,-3])for(const z of [115,116])put(X(x),124,z,'deck');
 for(let j=0;j<4;j++)for(const x of [-4,-3])step(X(x),125+j,114-j,'north');
}
// Paired, two-wide service stairs leave X155..165 clear all the way through.
for(let j=0;j<12;j++)for(const x of [151,152,168,169])step(x,113+j,121-j,'north');
for(const x of [151,152,153,154,166,167,168,169])for(const z of [108,109])put(x,124,z,'deck');
for(const x of [151,152,153,154,166,167,168,169])for(const z of [108,109])for(let y=125;y<=127;y++)put(x,y,z,'air');
for(let j=0;j<8;j++)for(const x of [153,154,166,167])step(x,125+j,110+j,'south');
// Restore jambs only outside the opening where upper service stairs meet the deck.
const names={base:'stone_bricks',stone:'calcite',cornice:'smooth_quartz',deck:'smooth_stone',pier:'smooth_quartz',slit:'iron_bars',blue:'blue_wool',white:'white_wool',gold:'gold_block',chiseled:'chiseled_stone_bricks',air:'air',lantern:'lantern'};
function state(n){if(n.startsWith('stairs:')){const [,f,h]=n.split(':');return {Name:'minecraft:stone_brick_stairs',Properties:{facing:f,half:h,shape:'straight',waterlogged:'false'}};}if(n.startsWith('slab:'))return {Name:'minecraft:smooth_quartz_slab',Properties:{type:n.split(':')[1],waterlogged:'false'}};
 return {Name:'minecraft:'+names[n],...(n==='lantern'?{Properties:{hanging:'false',waterlogged:'false'}}:n==='slit'?{Properties:{north:'false',south:'false',east:'true',west:'true',waterlogged:'false'}}:{})};}
function cubes(n){if(n==='air')return [];if(n.startsWith('stairs:')){const [,f,h]=n.split(':'),b=h==='bottom'?[0,0,0,1,.5,1]:[0,.5,0,1,1,1],lo=h==='bottom'?.5:0,hi=h==='bottom'?1:.5;return [b,f==='north'?[0,lo,0,1,hi,.5]:f==='south'?[0,lo,.5,1,hi,1]:f==='east'?[.5,lo,0,1,hi,1]:[0,lo,0,.5,hi,1]];}if(n.startsWith('slab:'))return n==='slab:top'?[[0,.5,0,1,1,1]]:[[0,0,0,1,.5,1]];if(n==='lantern')return [[.25,0,.25,.75,.5,.75],[.375,.5,.375,.625,.75,.625]];if(n==='slit')return [[0,0,.4375,1,1,.5625]];return [[0,0,0,1,1,1]];}
// Quarter-block raster samples the actual cuboids; no full-cube substitution for stairs.
const sample=(x,y,z)=>{const xx=Math.floor(x),yy=Math.floor(y),zz=Math.floor(z),n=get(xx,yy,zz);return cubes(n).some(([a,b,c,d,e,f])=>x-xx>=a&&x-xx<d&&y-yy>=b&&y-yy<e&&z-zz>=c&&z-zz<f)?n:'air';};
for(let z=108;z<=124;z++)for(let x=155;x<=165;x++){const crown=122+Math.floor(4*(1-(Math.abs(x-160)/6)**2));for(let y=113;y<=crown;y++)if(get(x,y,z)!=='air')throw Error('Arch blocked '+K(x,y,z));}
for(const [k,v]of vox){const [x,y,z]=k.split(',').map(Number);if(get(320-x,y,z)!==mirror(v))throw Error('Not symmetric '+k);}
for(const f of ['north','south','east','west'])for(const h of ['top','bottom']){let count=0;for(const x of [.25,.75])for(const y of [.25,.75])for(const z of [.25,.75])if(cubes(stair(f,h)).some(([a,b,c,d,e,ff])=>x>=a&&x<d&&y>=b&&y<e&&z>=c&&z<ff))count++;if(count!==6)throw Error('Stair volume');}
// Half-block surface graph; checks actual stair cuboids and two-block headroom.
function reachable(bounds,start,target){
 const cache=new Map(),key=(x,z,h)=>`${x},${z},${h}`,surfaces=(hx,hz)=>{const k=hx+','+hz;if(cache.has(k))return cache.get(k);const x=hx/2+.25,z=hz/2+.25,a=[];
  for(let y=112;y<=132;y++)for(const c of cubes(get(Math.floor(x),y,Math.floor(z)))){if(x%1<c[0]||x%1>=c[3]||z%1<c[2]||z%1>=c[5])continue;const h=y+c[4];if(!Number.isInteger(h*2))continue;let free=true;for(let yy=h+.125;yy<h+2;yy+=.25)if(sample(x,yy,z)!=='air')free=false;if(free)a.push(h*2);}cache.set(k,[...new Set(a)]);return cache.get(k);};
 const q=[[start[0]*2,start[2]*2,start[1]*2]],seen=new Set();for(let i=0;i<q.length;i++){const [x,z,h]=q[i],k=key(x,z,h);if(seen.has(k)||!surfaces(x,z).includes(h))continue;seen.add(k);for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]]){if(nx<bounds[0]*2||nx>bounds[2]*2+1||nz<bounds[1]*2||nz>bounds[3]*2+1)continue;for(const nh of surfaces(nx,nz))if(Math.abs(nh-h)<=1&&!seen.has(key(nx,nz,nh)))q.push([nx,nz,nh]);}}
 if(!seen.has(key(target[0]*2,target[2]*2,target[1]*2)))throw Error('Service stair unreachable '+JSON.stringify({start,target}));return seen.size;
}
const routes=[reachable([116,103,134,121],[125,113,112],[125,129,112]),reachable([186,103,204,121],[195,113,112],[195,129,112]),reachable([149,108,171,124],[160,113,120],[160,133,116])];
const colors={base:'#92958c',stone:'#c4c7bc',cornice:'#e1ddca',deck:'#acb4ad',pier:'#d4d8cc',slit:'#344850',blue:'#285b9a',white:'#ece8d9',gold:'#cda557',chiseled:'#9caca4',lantern:'#ffd17c'},color=n=>n.startsWith('stairs:')?'#a8b3a7':n.startsWith('slab:')?'#dddcca':colors[n];
const start=(w,h,t)=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" shape-rendering="crispEdges"><rect width="100%" height="100%" fill="#10212b"/><style>text{font-family:Arial,sans-serif;fill:#eee8d6;shape-rendering:auto}</style><text x="35" y="36" font-size="22">${t}</text>`;
const sx=x=>75+(x-114)*11,sy=y=>495-(y-108)*13,sz=z=>125+(z-101)*11;
let front=start(1200,600,'FRONT ELEVATION / REAL STAIR & SLAB SHAPES'),top=start(1200,530,'TOP PLAN / SAME DETAILED BLOCK STATES');
for(let y=108;y<136;y+=.25)for(let x=116;x<205;x+=.25){let n='air';for(let z=125.875;z>=103;z-=.25)if((n=sample(x+.125,y+.125,z))!=='air')break;if(n!=='air')front+=`<rect x="${sx(x)}" y="${sy(y+.25)+13}" width="2.75" height="3.25" fill="${color(n)}"/>`;}
for(let z=103;z<126;z+=.25)for(let x=116;x<205;x+=.25){let n='air';for(let y=135.875;y>=108;y-=.25)if((n=sample(x+.125,y,z+.125))!=='air')break;if(n!=='air')top+=`<rect x="${sx(x)}" y="${sz(z)}" width="2.75" height="2.75" fill="${color(n)}"/>`;}
for(let y=108;y<=135;y++)front+=`<path d="M 90 ${sy(y)+13} H 1080" stroke="#18292b" opacity=".13"/>`;
for(const y of [112,128,132,135])front+=`<text x="1120" y="${sy(y)+10}" font-size="14">Y${y}</text>`;
front+='<text x="210" y="92" font-size="18">A · OUTER BASTION</text><text x="580" y="92" text-anchor="middle" font-size="18">11-BLOCK CLEAR GATE</text><text x="980" y="92" text-anchor="middle" font-size="18">B · OUTER BASTION</text><text x="35" y="550" font-size="15">Sloped bases · inverted corbels · recessed slit frames · banners · lanterns · shaped arch</text><text x="35" y="580" font-size="14">Stairs and slabs use directional cuboids. No world changes.</text></svg>';
for(const [x,z,dx,dz]of [[132,112,12,4],[188,112,-12,4],[125,105,0,-5],[195,105,0,-5]])top+=`<path d="M ${sx(x)+5.5} ${sz(z)+5.5} l ${dx*11} ${dz*11}" stroke="#e59766" stroke-width="2" stroke-dasharray="7 5"/>`;
top+='<text x="35" y="80" font-size="16">Flat battlements / no blue roofs / wall alignment still pending</text><text x="35" y="485" font-size="15">Body Ø15 / base and crown Ø19 / centre spacing 70 blocks</text><text x="35" y="510" font-size="14">Orange dashed lines show wall attachment directions only.</text></svg>';
const inventory={},blocks=[];for(const [k,n]of vox){if(n==='air')continue;const s=state(n),key=s.Name+JSON.stringify(s.Properties||{});inventory[key]=(inventory[key]||0)+1;blocks.push({position:k.split(',').map(Number),state:s});}
spec.pending=spec.pending.map(t=>t==='บันไดขึ้นห้องป้อม/ชานประตูและเฟอร์นิเจอร์ยังไม่ออกแบบ'?'เพิ่มบันไดบริการขึ้นชานป้อมทั้งสองและชานประตูแล้ว ตรวจกราฟครึ่งบล็อกพร้อมช่องเหนือหัว2บล็อกผ่าน ยังไม่จำลองการชนตัวผู้เล่นจริง และยังไม่มีเฟอร์นิเจอร์':t);
const old=fs.readFileSync(path.join(out,'bastions.html'),'utf8'),from=old.indexOf('<svg ',old.indexOf('<section id="front"')),to=old.indexOf('</svg>',from)+6,first=old.slice(0,from)+front+old.slice(to),tf=first.indexOf('<svg ',first.indexOf('<section id="top"')),tt=first.indexOf('</svg>',tf)+6;
let html=first.slice(0,tf)+top+first.slice(tt);html=html.replace('แบบเสนอเฉพาะทรงป้อม','แบบเสนอรายละเอียดป้อมและซุ้มประตู').replace('ฐานบาน → ตัวป้อมสอบเข้า → คิ้วหัวป้อมยื่น → ชานเปิดและเชิงเทิน ภาพด้านหน้ากับด้านบนอ่านจากรูปทรงบล็อกชุดเดียวกัน','เพิ่มบันไดฐาน บันไดคว่ำรองหัวป้อม ครึ่งบล็อกเชิงเทิน กรอบช่องยิงธนู ธง โคม ตราประตู และบันไดบริการภายใน พรีวิวแสดงรูปทรงบันไดจริง').replace('บันไดขึ้นห้องป้อม/ชานประตูและเฟอร์นิเจอร์ยังไม่ออกแบบ','เพิ่มบันไดบริการภายในแล้ว แต่ยังต้องตรวจเส้นทางเดินและช่องเหนือหัวก่อนนำเข้าเกม; ยังไม่มีเฟอร์นิเจอร์');
if(hash()!==before)throw Error('World changed');
html=html.replace('<nav>','<nav><a href="bastions-3d.html" style="padding:9px 13px;border:1px solid #8295a0;border-radius:5px;text-decoration:none">เปิดตรวจ 3D</a>');
fs.copyFileSync(path.join(__dirname,'bastions-3d-template.html'),path.join(out,'bastions-3d.html'));fs.copyFileSync(path.join(__dirname,'bastions-3d.js'),path.join(out,'bastions-3d.js'));
const verification={worldUnchanged:true,mirrorSymmetry:true,fullArchProfileClear:true,stairCuboidsVerified:true,blocks:blocks.length,stairs:blocks.filter(b=>b.state.Name.endsWith('_stairs')).length,slabs:blocks.filter(b=>b.state.Name.endsWith('_slab')).length,inGameTested:false,internalRouteVerified:true,routeMethod:'half-block support graph with two-block point headroom; not full entity collision simulation',reachableSurfaces:routes};
fs.writeFileSync(path.join(out,'bastions.html'),html);fs.writeFileSync(path.join(out,'bastions-front.svg'),front);fs.writeFileSync(path.join(out,'bastions-top.svg'),top);fs.writeFileSync(path.join(out,'bastions.json'),JSON.stringify({...spec,stage:'DETAILED PREVIEW / NOT APPLIED',encoding:'absolute XYZ, omitted coordinates are air inside bounds; preview data only',bounds:[116,108,103,204,135,125],blocks,inventory,verification}));console.log(verification);
