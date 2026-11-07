import {createGestures} from './gestures.mjs';
import {cachedWindow,rebasePoint,clampCenter,needsWindow,overviewPoint} from './world-navigation.mjs';
import {frustumPlanes,intersectsBox,planChunks} from './view-chunks.mjs';
import {buildChunk,loadTextureArray,decodeCells} from './textured-mesh.mjs';
const $ = id => document.getElementById(id);
const canvas = $('viewport');
const gl = canvas.getContext('webgl2', {antialias:true,alpha:false});
let model,cells,program,textureArray,vertexCount=0,dirty=true,loading=true,sequence=0,meshGeneration=0;
let meshes=[],builtModel,builtCut,detailAnchor='',preparingKey='',detailTimer;const chunkBatches=new Map();let activeKeys=new Set(),attributeLayout=[];
const keys=new Set();
const windowCache=new Map(),textureCache=new Map();
let mapEntries=[],selectedMapId,mapInfo,overviewBackground,windowTimer,fetchController;
let mode='orbit', theta=.6, phi=.78, radius=245, target=[75,66,75], eye=[75,66.6,127], yaw=0, pitch=0;
const vertex=`#version 300 es
in vec3 aPosition;in vec3 aNormal;in vec2 aUv;in float aLayer;in vec3 aTint;uniform mat4 uVP;uniform vec3 uOffset;
out vec2 vUv;flat out float vLayer;out vec3 vTint;out float vLight;
void main(){gl_Position=uVP*vec4(aPosition+uOffset,1.);vUv=aUv;vLayer=aLayer;vTint=aTint;vLight=.62+.38*max(0.,dot(aNormal,normalize(vec3(-.4,.85,.3))));}`;
const fragment=`#version 300 es
precision highp float;precision highp sampler2DArray;in vec2 vUv;flat in float vLayer;in vec3 vTint;in float vLight;uniform sampler2DArray uTextures;out vec4 color;
void main(){vec4 texel=texture(uTextures,vec3((vec2(mod(vLayer,4.),floor(mod(vLayer,16.)/4.))+clamp(vUv,vec2(.0001),vec2(.9999)))/4.,floor(vLayer/16.)));if(texel.a<.1)discard;color=vec4(texel.rgb*vTint*vLight,texel.a);}`;
function fatal(message){$('fatal').hidden=false;$('fatal').textContent=message;}
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
if(!gl)fatal('เบราว์เซอร์นี้เปิด WebGL 2 ไม่ได้ กรุณาเปิดด้วย Chrome หรือ Edge ที่เปิดการเร่งกราฟิก');
else {
  try{program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));attributeLayout=[['aPosition',3,gl.FLOAT,false,0],['aNormal',3,gl.BYTE,true,18],['aUv',2,gl.UNSIGNED_SHORT,true,12],['aLayer',1,gl.UNSIGNED_SHORT,false,16],['aTint',3,gl.UNSIGNED_BYTE,true,21]].map(([name,...format])=>[gl.getAttribLocation(program,name),...format]);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.clearColor(.075,.105,.135,1);initializeStudio();}catch(e){fatal(e.message);}
}
function value(x,y,z){return !model||x<0||z<0||x>=model.size||z>=model.size||y<model.low||y>=model.high?0:cells[(y-model.low)*model.size*model.size+z*model.size+x];}
function visible(x,y,z){return !!value(x,y,z)&&y>=model.floor&&y<=Number($('height').value);}
function clearMeshes(){for(const m of meshes)gl.deleteBuffer(m.buffer);meshes=[];chunkBatches.clear();activeKeys.clear();builtModel=null;vertexCount=0;}
function detailFocus(){return mode==='walk'?eye:target;}
function focusKey(){const p=detailFocus(),angles=mode==='walk'?[yaw,pitch]:[theta,phi];return [Math.floor(p[0]/16),Math.floor(p[1]/16),Math.floor(p[2]/16),mode,...angles.map(a=>Math.round(a/.09)),Math.round(Math.log(radius)/.12),Math.round(canvas.clientWidth/Math.max(1,canvas.clientHeight)*20),mobileView.matches].join(',');}
function queueDetailRefresh(){
  if(!model||loading||!$('fatal').hidden||detailTimer||focusKey()===detailAnchor||focusKey()===preparingKey)return;
  detailTimer=setTimeout(()=>{detailTimer=null;if(!model||loading||focusKey()===detailAnchor||focusKey()===preparingKey)return;rebuild(sequence,true).catch(e=>fatal(e.message));},120);
}
async function rebuild(request=sequence,reuse=false){
  if(!model||!textureArray)return;const generation=++meshGeneration,current=model,currentCells=cells,cut=Number($('height').value),states=current.resources.states;
  if(!reuse||builtCut!==cut){clearMeshes();builtModel=current;builtCut=cut;}
  const buildFocusKey=focusKey(),plan=planChunks({size:current.size,floor:current.floor,high:current.high,cut,focus:detailFocus().slice(),mobile:mobileView.matches,vp:cameraView().vp});
  preparingKey=buildFocusKey;activeKeys=new Set(plan.wanted.map(c=>c.key));

  meshes=[...chunkBatches.values()].flatMap(c=>c.meshes);dirty=true;
  let completed=0;
  for(const entry of plan.wanted){
    if(generation!==meshGeneration||request!==sequence)return;
    const {x,z,lod,min,max}=entry,key=(x+current.origin[0])+','+(z+current.origin[1]),previous=chunkBatches.get(key);completed++;
    if(previous?.lod===lod)continue;
    if(previous)for(const batch of previous.meshes)gl.deleteBuffer(batch.buffer);
    const chunk=buildChunk(current,currentCells,states,x,z,cut),batches=[];
    for(const transparent of [false,true]){const data=transparent?chunk.transparent:chunk.solid;if(!data.length)continue;const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,data,gl.STATIC_DRAW);batches.push({origin:current.origin.slice(),texture:textureArray,key,buffer,count:data.length/24,bytes:data.byteLength,transparent,center:chunk.center,min,max});}
    chunkBatches.set(key,{lod,meshes:batches});meshes=[...chunkBatches.values()].flatMap(c=>c.meshes);dirty=true;
    if(completed%4===0){$('status').textContent='กำลังเตรียมพื้นที่ที่มอง '+Math.round(completed/Math.max(1,plan.wanted.length)*100)+'%';await new Promise(resolve=>{const channel=new MessageChannel();channel.port1.onmessage=()=>{channel.port1.close();channel.port2.close();resolve();};channel.port2.postMessage(0);});}
  }
  if(generation!==meshGeneration||request!==sequence)return;detailAnchor=buildFocusKey;preparingKey='';vertexCount=meshes.reduce((n,m)=>n+m.count,0);
  $('stats').dataset.cachedChunks=chunkBatches.size;$('stats').dataset.cachedBytes=meshes.reduce((n,m)=>n+m.bytes,0);
  $('status').textContent=current.name+' · เรนเดอร์เฉพาะพื้นที่ที่มอง';dirty=true;
}
async function loadMap(id,options={}){
  if(options.refresh)windowCache.clear();
  const request=++sequence;loading=true;selectedMapId=id;renderMapList();$('status').textContent=options.preserve?'กำลังโหลดพื้นที่ถัดไป…':'กำลังโหลดแมพ…';$('fatal').hidden=true;
  fetchController?.abort();const controller=new AbortController();fetchController=controller;
  try{
    let info=options.preserve&&mapInfo?.id===id?mapInfo:null;
    if(!info){const response=await fetch('/api/map-info?id='+encodeURIComponent(id),{signal:controller.signal});info=await response.json();if(!response.ok)throw Error(info.error);}
    if(request!==sequence)return;mapInfo=info;prepareOverview();
    const params=new URLSearchParams({id});if(info.fullWorld){params.set('size',String(mobileView.matches?384:512));if(options.center){params.set('x',options.center[0]);params.set('z',options.center[1]);}}
    const cacheKey=params.toString();let cached=windowCache.get(cacheKey);
    if(!cached&&options.center)cached=cachedWindow(windowCache.values(),id,options.center,mobileView.matches);
    let data,decoded;if(cached){({data,decoded}=cached);}else{const response=await fetch('/api/map?'+params,{signal:controller.signal});data=await response.json();if(!response.ok)throw Error(data.error||'โหลดแมพไม่สำเร็จ');if(request!==sequence)return;decoded=decodeCells(data.blocks);delete data.blocks;windowCache.set(cacheKey,{data,decoded});}
    if(!data.resources?.available)throw Error('ไม่พบทรัพยากร Minecraft 1.21.1');
    const textureKey=JSON.stringify(data.resources.textures),sameTextures=textureCache.has(textureKey);
    $('status').textContent='กำลังเตรียมพื้นที่…';const nextTexture=sameTextures?textureCache.get(textureKey):await loadTextureArray(gl,data.resources.textures);if(request!==sequence){if(!sameTextures)gl.deleteTexture(nextTexture);return;}
    const oldOrigin=model?.origin||[0,0],preserve=options.preserve&&model?.id===id,oldCut=Number($('height').value),wasTop=$('height').value===$('height').max;
    textureCache.set(textureKey,nextTexture);textureArray=nextTexture;
    const continuing=model?.id===id&&!options.refresh;
    if(!continuing){clearMeshes();for(const [key,c]of windowCache)if(c.data.id!==id)windowCache.delete(key);for(const [key,t]of textureCache)if(key!==textureKey){gl.deleteTexture(t);textureCache.delete(key);}}
    else for(const mesh of meshes){const shift=[oldOrigin[0]-data.origin[0],0,oldOrigin[1]-data.origin[1]];for(const field of ['min','max','center'])mesh[field]=mesh[field].map((v,i)=>v+shift[i]);}
    if(preserve){target=rebasePoint(target,oldOrigin,data.origin);eye=rebasePoint(eye,oldOrigin,data.origin);}
    model=data;cells=decoded;$('height').min=data.floor;$('height').max=data.high-1;$('height').value=preserve&&!wasTop?Math.max(data.floor,Math.min(data.high-1,oldCut)):data.high-1;$('height-value').value=$('height').value;createLabels();
    if(!preserve){$('home').click();if(options.center){target[0]=options.center[0]-data.origin[0];target[2]=options.center[1]-data.origin[1];}if(mobileView.matches)setMenu(false);}
    if(continuing&&wasTop)builtCut=Number($('height').value);
    await rebuild(request,continuing);if(request!==sequence)return;
    $('map-name').textContent=data.name;$('map-info').textContent=(data.worldFootprint||data.footprint).join(' × ')+' บล็อก · จุดเกิด '+(data.worldSpawn||data.spawn).join(' ');$('status').textContent=data.name+(data.stats.blocks?' · เรนเดอร์เฉพาะพื้นที่ที่มอง':' · จุดนี้ไม่มีบล็อกที่บันทึกไว้');
    $('render-note').textContent='Texture Vanilla 1.21.1 · แสงและสีไบโอมเป็นค่าพรีวิว'+(data.resources.fallbacks.length?' · บล็อกพิเศษ '+data.resources.fallbacks.length+' ชนิดใช้รูปทรงสำรอง':'')+(data.fullWorld?' · โหลดพื้นที่ตามจุดที่เลื่อนไป · เก็บพื้นที่ที่โหลดแล้วไว้':' · ส่วนที่พ้นมุมมองหยุดวาด');
    $('navigation-result').textContent='';drawOverview();
  }catch(e){if(request===sequence&&e.name!=='AbortError')fatal(e.message);}finally{if(request===sequence)loading=false;}
}
const names={'Treasure 1':'สมบัติ 1','Room 3 - chapel':'ห้อง 3 · วิหาร','Treasure 2 - library':'สมบัติ 2 · ห้องสมุด','Room 2 - prison':'ห้อง 2 · คุก','Boss arena':'ลานบอส','Room 4 - forge':'ห้อง 4 · โรงตีเหล็ก','Room 1 - barracks':'ห้อง 1 · ค่ายทหาร','Spawn':'จุดเกิด','Room 5 - knights hall':'ห้อง 5 · โถงอัศวิน'};
function createLabels(){const container=$('room-labels');container.replaceChildren();for(const r of model.rooms){const label=document.createElement('span');label.className='room-label'+(r.name==='Spawn'?' spawn':'');label.textContent=names[r.name]||r.name;label.dataset.x=r.labelExact?r.x:r.x+20;label.dataset.z=r.labelExact?r.z:r.z+20;label.dataset.y=r.y||78;container.append(label);}}
const sub=(a,b)=>a.map((v,i)=>v-b[i]);const dot=(a,b)=>a.reduce((n,v,i)=>n+v*b[i],0);const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];function norm(a){const n=Math.hypot(...a)||1;return a.map(v=>v/n);}
function lookAt(e,t){const z=norm(sub(e,t)),x=norm(cross([0,1,0],z)),y=cross(z,x);return [x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,e),-dot(y,e),-dot(z,e),1];}
function multiply(a,b){const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;}
function camera(){if(mode==='orbit')return [target[0]+Math.sin(theta)*Math.sin(phi)*radius,target[1]+Math.cos(phi)*radius,target[2]+Math.cos(theta)*Math.sin(phi)*radius];return eye;}
function cameraView(aspect=canvas.clientWidth/Math.max(1,canvas.clientHeight)){
  const f=1/Math.tan(Math.PI/7),near=mode==='orbit'?Math.max(.1,radius/2000):.1,far=Math.max(4096,radius*4);const projection=[f/aspect,0,0,0,0,f,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0];
  const e=camera(),t=mode==='orbit'?target:[e[0]+Math.sin(yaw)*Math.cos(pitch),e[1]+Math.sin(pitch),e[2]-Math.cos(yaw)*Math.cos(pitch)];

  return {vp:multiply(projection,lookAt(e,t)),e};
}
function draw(){
  const dpr=Math.min(window.devicePixelRatio||1,1.5),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  if(!w||!h)return;const {vp,e}=cameraView(w/h),planes=frustumPlanes(vp),visibleMeshes=meshes.filter(m=>intersectsBox(planes,m.min,m.max));
gl.viewport(0,0,w,h);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.useProgram(program);gl.uniformMatrix4fv(gl.getUniformLocation(program,'uVP'),false,vp);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D_ARRAY,textureArray);gl.uniform1i(gl.getUniformLocation(program,'uTextures'),0);
  function batch(mesh){gl.bindTexture(gl.TEXTURE_2D_ARRAY,mesh.texture);gl.uniform3f(gl.getUniformLocation(program,'uOffset'),mesh.origin[0]-model.origin[0],0,mesh.origin[1]-model.origin[1]);gl.bindBuffer(gl.ARRAY_BUFFER,mesh.buffer);for(const [loc,size,type,normalized,offset]of attributeLayout){gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,type,normalized,24,offset);}gl.drawArrays(gl.TRIANGLES,0,mesh.count);}
  gl.disable(gl.BLEND);gl.depthMask(true);for(const mesh of visibleMeshes)if(!mesh.transparent)batch(mesh);
  const translucent=visibleMeshes.filter(m=>m.transparent).sort((a,b)=>dot(sub(b.center,e),sub(b.center,e))-dot(sub(a.center,e),sub(a.center,e)));
  gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);for(const mesh of translucent)batch(mesh);gl.depthMask(true);gl.disable(gl.BLEND);
  for(const label of $('room-labels').children){const p=[Number(label.dataset.x),Math.min(Number(label.dataset.y),Number($('height').value)+3),Number(label.dataset.z),1];const q=[0,0,0,0];for(let r=0;r<4;r++)for(let c=0;c<4;c++)q[r]+=vp[c*4+r]*p[c];const x=q[0]/q[3],y=q[1]/q[3];label.hidden=!activeKeys.has((Math.floor(Number(label.dataset.x)/16)*16)+','+(Math.floor(Number(label.dataset.z)/16)*16))||!$('labels').checked||q[3]<=0||Math.abs(x)>1||Math.abs(y)>1;label.style.left=(x*.5+.5)*canvas.clientWidth+'px';label.style.top=(-y*.5+.5)*canvas.clientHeight+'px';}
  const count=visibleMeshes.reduce((n,m)=>n+m.count/3,0),chunks=new Set(visibleMeshes.map(m=>m.key)).size;$('stats').textContent=(model.worldFootprint||model.footprint).join(' × ')+' บล็อก · วาด '+chunks+' ชังก์ · '+count.toLocaleString()+' triangles';$('stats').dataset.drawnChunks=chunks;
  drawOverview();dirty=false;
}
let previous=performance.now();
function frame(now){const dt=Math.min((now-previous)/1000,.05);previous=now;if(mode==='walk'&&keys.size&&document.activeElement===canvas){const speed=(keys.has('shift')?48:10)*dt;for(const [key,angle]of [['w',0],['s',Math.PI],['a',-Math.PI/2],['d',Math.PI/2]])if(keys.has(key)){eye[0]+=Math.sin(yaw+angle)*speed;eye[2]-=Math.cos(yaw+angle)*speed;dirty=true;}if(keys.has('q')){eye[1]-=speed;dirty=true;}if(keys.has('e')){eye[1]+=speed;dirty=true;}}if(gl&&model&&dirty)draw();queueWindowRefresh();queueDetailRefresh();requestAnimationFrame(frame);}
requestAnimationFrame(frame);
let drag;const gestures=createGestures();
function rotateView(dx,dy){if(mode==='walk'){yaw+=dx*.005;pitch=Math.max(-1.45,Math.min(1.45,pitch-dy*.005));}else{theta-=dx*.006;phi=Math.max(.025,Math.min(1.52,phi+dy*.006));}dirty=true;}
function panView(dx,dy){
  const e=camera(),t=mode==='orbit'?target:[e[0]+Math.sin(yaw)*Math.cos(pitch),e[1]+Math.sin(pitch),e[2]-Math.cos(yaw)*Math.cos(pitch)];
  const backward=norm(sub(e,t)),right=norm(cross([0,1,0],backward)),up=cross(backward,right);
  const scale=2*(mode==='orbit'?radius:12)*Math.tan(Math.PI/7)/Math.max(1,canvas.clientHeight),destination=mode==='orbit'?target:eye;
  for(let i=0;i<3;i++)destination[i]+=(-dx*right[i]+dy*up[i])*scale;dirty=true;
}
function zoomView(ratio){if(mode==='orbit')radius=Math.max(8,Math.min(8192,radius*ratio));else{const amount=Math.max(-12,Math.min(12,12*Math.log(ratio)));eye[0]-=Math.sin(yaw)*Math.cos(pitch)*amount;eye[1]-=Math.sin(pitch)*amount;eye[2]+=Math.cos(yaw)*Math.cos(pitch)*amount;}dirty=true;}
canvas.addEventListener('pointerdown',event=>{canvas.focus();if(event.pointerType==='touch')gestures.start(event.pointerId,event.clientX,event.clientY);else drag={id:event.pointerId,x:event.clientX,y:event.clientY,button:event.button};canvas.setPointerCapture(event.pointerId);});
canvas.addEventListener('pointermove',event=>{
  if(event.pointerType==='touch'){const motion=gestures.move(event.pointerId,event.clientX,event.clientY);if(!motion)return;if(motion.kind==='rotate')rotateView(motion.dx,motion.dy);else{panView(motion.dx,motion.dy);zoomView(motion.zoom);}return;}
  if(!drag||drag.id!==event.pointerId)return;const dx=event.clientX-drag.x,dy=event.clientY-drag.y;drag.x=event.clientX;drag.y=event.clientY;if(drag.button===2)panView(dx,dy);else rotateView(dx,dy);
});
for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,e=>{gestures.end(e.pointerId);if(drag?.id===e.pointerId)drag=null;});
canvas.addEventListener('contextmenu',event=>event.preventDefault());canvas.addEventListener('wheel',event=>{event.preventDefault();if(mode==='orbit'){radius=Math.max(8,Math.min(8192,radius*Math.exp(event.deltaY*.001)));dirty=true;}},{passive:false});
canvas.addEventListener('keydown',event=>{if(['w','a','s','d','q','e','shift'].includes(event.key.toLowerCase())){event.preventDefault();keys.add(event.key.toLowerCase());}});window.addEventListener('keyup',event=>keys.delete(event.key.toLowerCase()));window.addEventListener('blur',()=>keys.clear());canvas.addEventListener('blur',()=>keys.clear());window.addEventListener('resize',()=>dirty=true);
const mobileView=window.matchMedia('(max-width: 850px)');
function setMode(next){mode=next;keys.clear();gestures.clear();drag=null;$('mode').textContent=next==='walk'?'มุมผู้เล่น':'มุมหมุน';$('controls').lastElementChild.textContent=mobileView.matches?(next==='walk'?'นิ้วเดียว: หันมอง · สองนิ้ว: เลื่อน · บีบ: เข้า/ออก':'นิ้วเดียว: หมุน · สองนิ้ว: เลื่อน · บีบ: ซูม'):(next==='walk'?'ลาก: หันมอง · WASD: เคลื่อน · Q/E: ลง/ขึ้น · Shift: เร็ว · ไม่มีการชนกำแพง':'ลากซ้าย: หมุน · ลากขวา: เลื่อน · ล้อเมาส์: ซูม');dirty=true;}
$('home').onclick=()=>{target=[model?model.size/2:75,model?model.spawn[1]+1:66,model?model.size/2:75];radius=model?Math.min(8192,Math.min(model.size,mobileView.matches?288:384)*1.65*Math.max(1,canvas.clientHeight/Math.max(1,canvas.clientWidth))):245;theta=.6;phi=.78;setMode('orbit');};$('top').onclick=()=>{target=[model?model.size/2:75,model?model.spawn[1]-1:64,model?model.size/2:75];radius=model?Math.min(8192,Math.min(model.size,mobileView.matches?288:384)*1.4*Math.max(1,canvas.clientHeight/Math.max(1,canvas.clientWidth))):205;theta=0;phi=.025;setMode('orbit');};$('player').onclick=()=>{eye=model?model.spawn.map((v,i)=>v+(i===1?1.6:.5)):[75.5,66.6,127.5];yaw=0;pitch=0;setMode('walk');canvas.focus();};
$('labels').onchange=()=>{dirty=true;};let cutTimer;$('height').oninput=()=>{$('height-value').value=$('height').value;clearTimeout(cutTimer);cutTimer=setTimeout(()=>{if(gl&&!loading)rebuild().catch(e=>fatal(e.message));},65);};
canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();fatal('การเชื่อมต่อกราฟิกหยุดทำงาน กรุณารีเฟรชหน้าเพื่อโหลดพรีวิวใหม่');});
function renderMapList(){
  $('map-list').replaceChildren();$('map-count').textContent=mapEntries.length+' แมพ';
  for(const entry of mapEntries){const button=document.createElement('button');button.className='map-item';button.setAttribute('aria-pressed',String(entry.id===selectedMapId));
    const name=document.createElement('strong');name.textContent=entry.name;const detail=document.createElement('span');detail.textContent=entry.footprint.join(' × ')+' · '+entry.id;button.append(name,detail);button.onclick=()=>loadMap(entry.id);$('map-list').append(button);}
}
async function initializeStudio(){
  $('refresh-maps').disabled=true;
  try{const response=await fetch('/api/maps');if(!response.ok)throw Error('โหลดรายการแมพไม่สำเร็จ');const data=await response.json();mapEntries=data.maps;renderMapList();$('list-message').textContent=mapEntries.length?'เลือกแมพเพื่อดู 3D':'ยังไม่มีแมพที่สร้างไว้';
    const next=mapEntries.find(m=>m.id===selectedMapId)||mapEntries[0];if(next)await loadMap(next.id,{refresh:true});else{++sequence;model=null;selectedMapId=undefined;++meshGeneration;if(gl)clearMeshes();$('map-name').textContent='ยังไม่มีแมพ';$('map-info').textContent='';$('room-labels').replaceChildren();$('stats').textContent='';$('status').textContent='ยังไม่มีแมพในรายการ';if(gl)gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);}
  }catch(e){fatal(e.message);}finally{$('refresh-maps').disabled=false;}
}
$('refresh-maps').onclick=initializeStudio;
function setMenu(open){
  const visible=open&&mobileView.matches;document.body.classList.toggle('menu-open',visible);$('menu-toggle').setAttribute('aria-expanded',String(visible));$('menu-toggle').setAttribute('aria-label',visible?'ปิดเครื่องมือ':'เปิดเครื่องมือ');$('menu-toggle').textContent=visible?'×':'☰';$('menu-backdrop').hidden=!visible;
  $('tools-panel').inert=mobileView.matches&&!visible;if(!visible&&$('tools-panel').contains(document.activeElement))$('menu-toggle').focus();gestures.clear();drag=null;
}
$('menu-toggle').onclick=()=>{setMenu(!document.body.classList.contains('menu-open'));drawOverview();};
$('menu-backdrop').onclick=()=>setMenu(false);
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&document.body.classList.contains('menu-open')){setMenu(false);$('menu-toggle').focus();}});
mobileView.addEventListener('change',()=>{setMenu(false);setMode(mode);queueDetailRefresh();});
window.addEventListener('blur',()=>{gestures.clear();drag=null;});
setMenu(false);setMode(mode);

function prepareOverview(){
  $('world-navigation').hidden=!mapInfo?.fullWorld;if(!mapInfo?.fullWorld)return;
  const canvas=$('map-overview'),grid=document.createElement('canvas');grid.width=grid.height=mapInfo.grid;const ctx=grid.getContext('2d'),pixels=ctx.createImageData(grid.width,grid.height),coverage=atob(mapInfo.coverage);
  for(let i=0;i<coverage.length;i++){const present=coverage.charCodeAt(i)!==0;pixels.data.set(present?[83,143,117,255]:[22,33,42,255],i*4);}ctx.putImageData(pixels,0,0);
  overviewBackground=document.createElement('canvas');overviewBackground.width=canvas.width;overviewBackground.height=canvas.height;const base=overviewBackground.getContext('2d');base.fillStyle='#10181e';base.fillRect(0,0,canvas.width,canvas.height);
  const [width,depth]=mapInfo.footprint,scale=Math.min((canvas.width-12)/width,(canvas.height-12)/depth),rect={width:width*scale,height:depth*scale};rect.x=(canvas.width-rect.width)/2;rect.y=(canvas.height-rect.height)/2;mapInfo.rect=rect;
  base.imageSmoothingEnabled=false;base.drawImage(grid,rect.x,rect.y,rect.width,rect.height);base.strokeStyle='#638271';base.strokeRect(rect.x,rect.y,rect.width,rect.height);
  const [minX,minZ,maxX,maxZ]=mapInfo.bounds;$('world-summary').textContent=mapInfo.footprint.join(' × ')+' บล็อก · '+mapInfo.chunkCount.toLocaleString()+' ชังก์ที่บันทึก · X '+minX+' ถึง '+maxX+' · Z '+minZ+' ถึง '+maxZ;
  for(const [axis,min,max]of [['x',minX,maxX],['z',minZ,maxZ]]){$('world-'+axis).min=min;$('world-'+axis).max=max;}
  if(prepareOverview.lastId!==mapInfo.id){const spawn=mapInfo.spawn||[0,0,0];$('world-x').value=spawn[0];$('world-z').value=spawn[2];prepareOverview.lastId=mapInfo.id;}
  drawOverview();
}
function drawOverview(){
  if(!mapInfo?.fullWorld||!overviewBackground||mapInfo.id!==model?.id)return;
  const canvas=$('map-overview'),ctx=canvas.getContext('2d'),r=mapInfo.rect,b=mapInfo.bounds,w=b[2]-b[0]+1,d=b[3]-b[1]+1;ctx.drawImage(overviewBackground,0,0);
  const position=(x,z)=>[r.x+(x-b[0])/w*r.width,r.y+(z-b[1])/d*r.height],a=position(model.origin[0],model.origin[1]);
  ctx.strokeStyle='#f4d083';ctx.lineWidth=1;ctx.strokeRect(a[0],a[1],model.size/w*r.width,model.size/d*r.height);
  const p=detailFocus(),point=position(p[0]+model.origin[0],p[2]+model.origin[1]);ctx.fillStyle='#effff4';ctx.beginPath();ctx.arc(point[0],point[1],3,0,Math.PI*2);ctx.fill();
  $('stats').dataset.worldX=Math.round(p[0]+model.origin[0]);$('stats').dataset.worldZ=Math.round(p[2]+model.origin[1]);
}
function queueWindowRefresh(){
  if(!model?.fullWorld||loading||!$('fatal').hidden)return;
  const point=detailFocus(),world=clampCenter(model.worldBounds,point[0]+model.origin[0],point[2]+model.origin[1]);
  if(point[0]+model.origin[0]<model.worldBounds[0]||point[0]+model.origin[0]>model.worldBounds[2]||point[2]+model.origin[1]<model.worldBounds[1]||point[2]+model.origin[1]>model.worldBounds[3]){point[0]=world[0]-model.origin[0];point[2]=world[1]-model.origin[1];dirty=true;}
  if(windowTimer||!needsWindow(model,point,mobileView.matches))return;
  windowTimer=setTimeout(()=>{windowTimer=null;if(!model?.fullWorld||loading||!needsWindow(model,detailFocus(),mobileView.matches))return;const p=detailFocus(),center=clampCenter(model.worldBounds,p[0]+model.origin[0],p[2]+model.origin[1]);loadMap(model.id,{center,preserve:true});},120);
}
function jumpWorld(x,z){
  if(!mapInfo?.fullWorld||!Number.isFinite(x)||!Number.isFinite(z)){$('navigation-result').textContent='กรุณาระบุพิกัด X/Z';return;}
  const center=clampCenter(mapInfo.bounds,x,z);$('world-x').value=center[0];$('world-z').value=center[1];$('navigation-result').textContent='กำลังเปิดจุด '+center.join(' / ');loadMap(mapInfo.id,{center});
}
$('map-overview').onclick=event=>{if(!mapInfo?.fullWorld)return;const canvas=$('map-overview'),box=canvas.getBoundingClientRect(),r=mapInfo.rect,x=(event.clientX-box.left)/box.width*canvas.width,z=(event.clientY-box.top)/box.height*canvas.height;const point=overviewPoint(mapInfo.bounds,(x-r.x)/r.width,(z-r.y)/r.height);jumpWorld(...point);};
$('go-world').onclick=()=>{if(!$('world-x').reportValidity()||!$('world-z').reportValidity()||$('world-x').value===''||$('world-z').value==='')return;jumpWorld(Number($('world-x').value),Number($('world-z').value));};
$('go-spawn').onclick=()=>{const spawn=model?.worldSpawn||mapInfo?.spawn;if(spawn)jumpWorld(spawn[0],spawn[2]);};
