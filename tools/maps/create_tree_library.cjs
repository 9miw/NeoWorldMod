const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const {createTown}=require('./sky-town/core.cjs'),{exportMap}=require('./create_ruined_castle.cjs'),{encode}=require('./nbt-edit.cjs');
const designs=[
 ['T01','ไม้พุ่มทรงกลม',44,224,'shrub'],['T02','ต้นโอ๊กเล็ก',100,224,'small'],['T03','ต้นอ่อนทรงโปร่ง',156,224,'airy'],['T04','ไม้ทรงเรียว',212,224,'column'],
 ['T05','ไม้ดอกชมพู',44,181,'cherry'],['T06','โอ๊กพุ่มกว้าง',100,181,'oak'],['T07','เบิร์ชสูงโปร่ง',156,181,'birch'],['T08','สนทรงกรวย',212,181,'spruce'],
 ['T09','สนสูงทรงแคบ',48,127,'pine'],['T10','ไม้กิ่งแผ่ไม่สมมาตร',128,127,'crooked'],['T11','ไม้กิ่งห้อย',208,127,'weeping'],
 ['T12','ต้นไม้ประจำเมือง',48,54,'town'],['T13','ไม้โบราณมีโพรง',128,54,'ancient'],['T14','ไม้แฟนตาซีเรืองแสง',208,54,'fantasy']
];
function buildLibrary(){
 const t=createTown({size:256,high:144}),trees=[];
 t.fill(0,60,0,255,62,255,'stone');t.fill(0,63,0,255,63,255,'dirt');t.fill(0,64,0,255,64,255,'smooth_stone');
 const font={T:['111','010','010','010','010'],0:['111','101','101','101','111'],1:['010','110','010','010','111'],2:['111','001','111','100','111'],3:['111','001','111','001','111'],4:['101','101','111','001','001'],5:['111','100','111','001','111'],6:['111','100','111','101','111'],7:['111','001','010','010','010'],8:['111','101','111','101','111'],9:['111','101','111','001','111']};
 for(const [id,name,cx,cz,kind]of designs){
  const plot=Number(id.slice(1))<=4?25:Number(id.slice(1))<=8?35:Number(id.slice(1))<=11?51:65,half=(plot-1)/2;
  for(let z=cz-half;z<=cz+half;z++)for(let x=cx-half;x<=cx+half;x++)t.put(x,64,z,Math.max(Math.abs(x-cx),Math.abs(z-cz))===half?'quartz_block':'grass_block',Math.max(Math.abs(x-cx),Math.abs(z-cz))===half?undefined:{snowy:'false'});
  const records=new Map(),base=65,seed=Number(id.slice(1));
  function put(x,y,z,n,p){x=Math.round(x);y=Math.round(y);z=Math.round(z);if(Math.abs(x)>half-2||Math.abs(z)>half-6||y<0)throw Error('Tree outside copy-safe plot '+id);t.put(cx+x,base+y,cz+z,n,p);const key=[x,y,z].join(',');if(n==='air')records.delete(key);else records.set(key,{x,y,z,name:n,properties:p});}
  const wood=kind==='birch'?'birch_log':kind==='cherry'?'cherry_log':['pine','spruce'].includes(kind)?'spruce_log':kind==='fantasy'?'dark_oak_log':'oak_log';
  function ball(x,y,z,rx,ry,rz,mat='oak_leaves'){
   for(let dz=-Math.ceil(rz);dz<=rz;dz++)for(let dy=-Math.ceil(ry);dy<=ry;dy++)for(let dx=-Math.ceil(rx);dx<=rx;dx++){
    const d=(dx/rx)**2+(dy/ry)**2+(dz/rz)**2,h=t.hash(Math.round(x+dx),Math.round(z+dz),Math.round(y+dy)+seed)%100;
    if(d>1+(h-50)*.003||y+dy<0)continue;
    const xx=Math.round(x+dx),yy=Math.round(y+dy),zz=Math.round(z+dz);if(records.has([xx,yy,zz].join(',')))continue;
    const n=Array.isArray(mat)?mat[h%mat.length]:mat;put(xx,yy,zz,n,n.endsWith('_leaves')?{distance:'7',persistent:'true',waterlogged:'false'}:undefined);
   }
  }
  function branch(a,b,r=0){const delta=b.map((v,i)=>v-a[i]),steps=Math.ceil(Math.hypot(...delta)*2);for(let i=0;i<=steps;i++){
   const q=a.map((v,k)=>v+delta[k]*i/steps),rr=Math.max(0,r*(1-i/steps*.65));
   for(let dz=-Math.ceil(rr);dz<=rr;dz++)for(let dx=-Math.ceil(rr);dx<=rr;dx++)if(dx*dx+dz*dz<=rr*rr+.2)put(q[0]+dx,q[1],q[2]+dz,wood,{axis:Math.abs(delta[0])>Math.abs(delta[1])?'x':Math.abs(delta[2])>Math.abs(delta[1])?'z':'y'});
  }}
  const canopy=kind==='cherry'?'cherry_leaves':['pine','spruce'].includes(kind)?'spruce_leaves':kind==='birch'?'birch_leaves':kind==='fantasy'?['warped_wart_block','purple_wool','warped_wart_block','magenta_wool']:'oak_leaves';
  if(kind==='shrub'){branch([0,0,0],[0,1,0]);ball(0,1,0,2,1.6,2);}
  if(kind==='small'){branch([0,0,0],[0,3,0]);ball(0,4,0,2.5,2,2.5);}
  if(kind==='airy'){branch([0,0,0],[0,7,0]);for(const p of [[-2,4,0],[2,5,1],[0,7,-1]]){branch([0,2,0],p);ball(...p,1.6,1.3,1.6);}}
  if(kind==='column'){branch([0,0,0],[0,10,0]);for(let y=4;y<12;y+=2)ball(0,y,0,y>9?1:2,2,2);}
  if(['cherry','oak','birch'].includes(kind)){
   const h=kind==='birch'?12:kind==='oak'?8:6;branch([0,0,0],[0,h,0],kind==='oak'?1:0);
   for(const [dx,dz]of [[-1,0],[1,0],[0,-1],[0,1]]){const end=[dx*(kind==='birch'?3:4),h-1,dz*4];branch([0,3,0],end);ball(...end,kind==='birch'?2:3,kind==='birch'?3:2.4,3,canopy);}ball(0,h+1,0,3,kind==='birch'?3:2.5,3,canopy);
  }
  if(['spruce','pine'].includes(kind)){
   const h=kind==='pine'?24:18;branch([0,0,0],[0,h-1,0]);
   for(let y=kind==='pine'?8:3;y<h;y+=3){const radius=Math.max(1,Math.round((h-y)/(kind==='pine'?5:4)));branch([0,y,0],[radius,y-1,0]);branch([0,y,0],[-radius,y-1,0]);ball(0,y,0,radius,1.8,radius,canopy);}ball(0,h-1,0,1,1,1,canopy);
  }
  if(kind==='crooked'){
   branch([0,0,0],[-2,7,0],1.4);branch([-2,7,0],[1,11,0],1);for(const e of [[-8,11,2],[7,14,-2],[-6,16,-3],[5,8,4]]){branch([-1,6,0],e,1);ball(...e,4,2,3);}ball(0,16,0,4,2,3);
  }
  if(kind==='weeping'){
   branch([0,0,0],[0,13,0],1.2);
   for(let i=0;i<8;i++){const a=i*Math.PI/4,e=[Math.round(Math.cos(a)*6),14+i%2,Math.round(Math.sin(a)*6)];branch([0,8,0],e,1);ball(...e,4,3,4);for(let j=0;j<3;j++){const xx=e[0]+j-1,zz=e[2]+(i%2);for(let y=6+i%3;y<e[1];y++)put(xx,y,zz,'oak_leaves',{distance:'7',persistent:'true',waterlogged:'false'});}}
  }
  if(['town','ancient','fantasy'].includes(kind)){
   const ancient=kind==='ancient',fantasy=kind==='fantasy',h=fantasy?44:ancient?38:30,thick=ancient?3.4:fantasy?2.8:2.2;
   branch([0,0,0],[fantasy?3:0,Math.floor(h*.55),0],thick);
   for(let i=0;i<8;i++){
    const a=i*Math.PI/4,reach=ancient?11:fantasy?12:8,e=[Math.round(Math.cos(a)*reach),h-8+i%3*2,Math.round(Math.sin(a)*reach*.8)];
    branch([0,Math.floor(h*.35),0],e,1.8);ball(...e,fantasy?5:ancient?5:4,4,fantasy?5:4,canopy);
    branch([0,3,0],[Math.round(Math.cos(a)*8),0,Math.round(Math.sin(a)*8)],1.2);
    if(ancient)for(let y=10;y<e[1]-2;y++)put(e[0],y,e[2],'vine',{east:'false',west:'false',north:'true',south:'false',up:'false'});
    if(fantasy){put(e[0],e[1]-3,e[2],'sea_lantern');put(e[0],e[1]-4,e[2],'lantern',{hanging:'true',waterlogged:'false'});}
   }
   ball(fantasy?3:0,h-4,0,ancient?7:6,4,6,canopy);
   if(ancient)for(let y=0;y<=6;y++)for(let z=-1;z<=4;z++)for(let x=-1;x<=1;x++)put(x,y,z,'air');
  }
  const entries=[...records.values()],min=[Math.min(...entries.map(b=>b.x)),Math.min(...entries.map(b=>b.y)),Math.min(...entries.map(b=>b.z))],max=[Math.max(...entries.map(b=>b.x)),Math.max(...entries.map(b=>b.y)),Math.max(...entries.map(b=>b.z))];
  const tree={id,name,kind,base:[cx,base,cz],plotBounds:[cx-half,cz-half,cx+half,cz+half],copyBounds:[cx+min[0],base+min[1],cz+min[2],cx+max[0],base+max[1],cz+max[2]],size:max.map((v,i)=>v-min[i]+1),anchor:min.map(v=>-v),blocks:entries,min};trees.push(tree);
  const labelZ=cz+half-5;t.fill(cx-6,64,labelZ-1,cx+6,64,labelZ+5,'quartz_block');
  [...id].forEach((c,i)=>font[c].forEach((row,dz)=>[...row].forEach((v,dx)=>{if(v==='1')t.put(cx-5+i*4+dx,64,labelZ+dz,'black_concrete');})));
 }
 return {...t,name:'NeoWorld - Tree Library',tiles:[],rooms:trees.map(b=>({name:b.id,bounds:b.plotBounds,center:b.base})),spawn:[128,65,246],reachableFloorCells:0,trees};
}
function structure(tree){
 const n=(type,value)=>({type,value}),palette=[],ids=new Map(),blocks=[];
 for(const b of tree.blocks){const key=b.name+JSON.stringify(b.properties||{});if(!ids.has(key)){ids.set(key,palette.length);palette.push({Name:n(8,'minecraft:'+b.name),...(b.properties?{Properties:n(10,Object.fromEntries(Object.entries(b.properties).map(([k,v])=>[k,n(8,v)])))}:{})});}
  blocks.push({state:n(3,ids.get(key)),pos:n(9,{type:3,values:[b.x-tree.min[0],b.y-tree.min[1],b.z-tree.min[2]]})});
 }
 return zlib.gzipSync(encode({name:'',value:{DataVersion:n(3,3955),size:n(9,{type:3,values:tree.size}),palette:n(9,{type:10,values:palette}),blocks:n(9,{type:10,values:blocks}),entities:n(9,{type:10,values:[]})}}));
}
function generate(destination='maps/tree_library'){
 if(fs.existsSync(path.resolve(destination)))throw Error('Output already exists: '+destination);
 const t=buildLibrary(),result=exportMap(t,destination),manifestFile=path.join(destination,'map-manifest.json'),m=JSON.parse(fs.readFileSync(manifestFile,'utf8'));fs.mkdirSync(path.join(destination,'structures'));
 const catalog=t.trees.map(({blocks,min,...tree})=>({...tree,blockCount:blocks.length,structure:'structures/'+tree.id+'.nbt'}));
 for(const tree of t.trees)fs.writeFileSync(path.join(destination,'structures',tree.id+'.nbt'),structure(tree));
 Object.assign(m,{name:'NeoWorld · Tree Library',description:'คลังต้นไม้ T01–T14 · แปลงสี่เหลี่ยม · ไฟล์โครงสร้างแยกต้น',trees:catalog,gameplay:'Reusable architecture assets. Structures contain only tree blocks, not plot ground, labels or neighboring trees.'});fs.writeFileSync(manifestFile,JSON.stringify(m,null,2));fs.writeFileSync(path.join(destination,'catalog.json'),JSON.stringify(catalog,null,2));fs.writeFileSync(path.join(destination,'zones.json'),JSON.stringify(catalog.map(b=>({id:b.id,bounds:b.plotBounds,center:b.base})),null,2));
 const md='# คลังต้นไม้ NeoWorld\n\nแมพทรัพยากรแยกจากเมืองหลัก ใช้โฟลเดอร์ `maps/tree_library` คงที่ แก้ภายในแมพนี้ ไม่สร้างชุดใหม่ทุกครั้ง\n\nเรียงต้นเล็กด้านหน้า (+Z) ไปต้นใหญ่ด้านหลัง (-Z) ป้ายรหัสปูบนพื้นหน้าแต่ละแปลง\n\n| รหัส | แบบ | ขนาดจริง กว้าง × สูง × ลึก | จุดฐาน X Y Z | จุดฐานภายในไฟล์โครงสร้าง |\n|---|---|---|---|---|\n'+catalog.map(b=>`| ${b.id} | ${b.name} | ${b.size.join(' × ')} | ${b.base.join(' ')} | ${b.anchor.join(' ')} |`).join('\n')+'\n\nไฟล์ `structures/T01.nbt` ถึง `T14.nbt` เป็น Minecraft Structure NBT ไม่รวมพื้นแปลง ป้ายหรือสิ่งรอบข้าง จุด anchor ใช้ชดเชยให้ฐานลำต้นตรงตำแหน่งวางที่ต้องการ ขอบเขตคัดลอกจริงอยู่ใน catalog.json\n\nใบไม้ใช้ persistent=true เพื่อไม่สลาย ต้นแฟนตาซีใช้บล็อกสีและไฟ Vanilla ไม่ต้องติดตั้งทรัพยากรเพิ่ม ต้นมีโพรงส่งออกเฉพาะบล็อกต้นไม้ ควรวางในพื้นที่ว่างเพื่อรักษาช่องโพรง\n\nภาพมอคอัพเป็นเป้าหมายรูปทรง ไม่ใช่หลักฐานว่าบล็อกตรงภาพทุกจุด ยังต้องตรวจพรีวิวและการวางในเกมจริง\n';
 fs.writeFileSync(path.join(destination,'MAP.md'),md);fs.writeFileSync(path.join(destination,'README.md'),md);return {...result,trees:catalog.length};
}
module.exports={buildLibrary,structure,generate};
if(require.main===module)console.log(JSON.stringify(generate(process.argv[2])));
