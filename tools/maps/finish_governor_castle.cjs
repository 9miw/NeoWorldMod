// Local castle completion. Stage first; --apply patches the saved city with a source-hash guard.
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {createLibrary}=require('./viewer/map-library.cjs'),{patch}=require('./smooth_active_land.cjs');
const root=path.resolve(__dirname,'../..'),dir=path.join(root,'maps/starter_sky_land_spawn_v1'),out=path.join(root,'docs/maps/zone07-blueprint');
const reportFile=path.join(dir,'castle-completion-report.json');
if(fs.existsSync(reportFile))throw Error('Castle completion already applied; inspect rather than replay');
const file=path.join(dir,'region/r.0.0.mca'),source=fs.readFileSync(file),sha=b=>crypto.createHash('sha256').update(b).digest('hex'),sourceHash=sha(source);
const lib=createLibrary(path.dirname(dir)),m=lib.load(path.basename(dir)),S=m.size,b=Buffer.from(m.blocks,'base64');
const old=new Uint16Array(b.length/2);for(let i=0;i<old.length;i++)old[i]=b.readUInt16LE(i*2);
const data=old.slice(),at=(x,y,z)=>(y-m.low)*S*S+z*S+x,key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
const ids=new Map(m.definitions.map((d,i)=>[key(d),i])),planned=new Set(),air={Name:'minecraft:air'};
const natural=new Set(['air','grass_block','dirt','stone','andesite','cobblestone','mossy_cobblestone'].map(n=>'minecraft:'+n));
const baseline=createLibrary(path.join(root,'archive/map-edit-backups/before-governor-castle')).load(path.basename(dir)),bb=Buffer.from(baseline.blocks,'base64'),protectedColumns=new Set();
for(let z=4;z<=102;z++)for(let x=100;x<=220;x++)for(let y=90;y<baseline.high;y++){
 const n=baseline.definitions[bb.readUInt16LE(((y-baseline.low)*S*S+z*S+x)*2)].Name;
 if(!natural.has(n)){protectedColumns.add(z*S+x);break;}
}
function put(x,y,z,n,p){
 if(!Number.isInteger(x+y+z)||x<100||x>220||z<4||z>102||y<24||y>171||y<m.low||y>=m.high)throw Error('Outside castle edit '+[x,y,z]);
 if(protectedColumns.has(z*S+x))throw Error('Protected earlier city '+[x,z]);
 // The recent front defense and its side walls south of Z40 remain byte-for-byte unchanged.
 if(z>=40&&(x<115||x>205)&&!(z===40&&y>=125&&y<=128&&(x>=111&&x<=113||x>=207&&x<=209)))throw Error('Protected side defense '+[x,y,z]);
 const d=typeof n==='object'?n:{Name:'minecraft:'+n,...(p?{Properties:p}:{})},k=key(d);
 if(!ids.has(k)){ids.set(k,m.definitions.length);m.definitions.push(d);}const i=at(x,y,z);data[i]=ids.get(k);planned.add(i);
}
const get=(x,y,z)=>m.definitions[data[at(x,y,z)]],original=(x,y,z)=>m.definitions[old[at(x,y,z)]];
const stairs=(f,half='bottom')=>({facing:f,half,shape:'straight',waterlogged:'false'}),slab={type:'bottom',waterlogged:'false'},lamp={hanging:'false',waterlogged:'false'};
function box(x0,y0,z0,x1,y1,z1,n,p){for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++)for(let y=y0;y<=y1;y++)put(x,y,z,n,p);}
function lampOn(x,y,z){put(x,y,z,'chiseled_stone_bricks');put(x,y+1,z,'lantern',lamp);}
function banner(x,z,y,h=8){for(let yy=y;yy<y+h;yy++)for(let dx=-1;dx<=1;dx++)put(x+dx,yy,z,dx===0&&yy>=y+2&&yy<=y+h-3?'gold_block':yy===y+3?'white_wool':'blue_wool');}
function crenellate(cells,y){for(const [x,z]of cells){put(x,y+1,z,'stone_bricks');if((x+z)%4<2){put(x,y+2,z,'chiseled_stone_bricks');put(x,y+3,z,'smooth_quartz_slab',slab);}else put(x,y+1,z,'smooth_quartz_slab',slab);}}
function rim(x0,z0,x1,z1,y){const a=[];for(let x=x0;x<=x1;x++)for(let z=z0;z<=z1;z++)if(x===x0||x===x1||z===z0||z===z1)a.push([x,z]);crenellate(a,y);}
function carveArch(x0,x1,z0,z1,floor,height){for(let x=x0;x<=x1;x++){
 const r=(x1-x0+1)/2,top=floor+height-Math.floor(3*Math.pow(Math.abs(x-(x0+x1)/2)/r,2));
 for(let z=z0;z<=z1;z++){box(x,floor+1,z,x,top,z,'air');put(x,top+1,z,'smooth_quartz');put(x,top,z,'quartz_stairs',stairs(x<(x0+x1)/2?'east':'west','top'));}
}}
// Remove superseded palace shells and the old rear curtain only inside their owned envelope.
box(115,113,14,205,171,102,'air');
for(let z=26;z<40;z++)for(let x=104;x<=216;x++)for(let y=112;y<=135;y++)if(!natural.has(get(x,y,z).Name))put(x,y,z,'air');
// Rounded northern land extension. Taper the rock underside into the existing sky island.
let addedLandColumns=0;
for(let z=4;z<=54;z++)for(let x=100;x<=220;x++){
 if(z>=40&&(x<115||x>205)||protectedColumns.has(z*S+x))continue;
 const q=Math.hypot(Math.max(119-x,0,x-201),Math.max(24-z,0,z-47));if(q>20)continue;
 const t=Math.max(0,(q-10)/10),smooth=t*t*(3-2*t),top=115-Math.round(8*smooth);
 let oldTop=-1;for(let y=m.low;y<=122;y++)if(natural.has(original(x,y,z).Name)&&original(x,y,z).Name!=='minecraft:air')oldTop=y;
 if(oldTop<0)addedLandColumns++;
 const bottom=Math.max(28,Math.round(42+q*1.8+5*Math.sin(x*.17+z*.11)));
 for(let y=bottom;y<=top;y++){
  const n=get(x,y,z).Name;if(!natural.has(n))continue;
  put(x,y,z,y===top?'grass_block':y>=top-3?'dirt':(x*13+y*7+z*11)%31<3?'andesite':(x+y+z)%23===0?'cobblestone':'stone',y===top?{snowy:'false'}:undefined);
 }
}
// Level the usable palace terrace; court is intentionally four blocks below the interior.
for(let z=14;z<=102;z++)for(let x=115;x<=205;x++){
 const floor=z>=85?112:116;
 for(let y=108;y<=floor;y++)put(x,y,z,y===floor?'grass_block':'stone',y===floor?{snowy:'false'}:undefined);
 if(floor<116)box(x,floor+1,z,x,116,z,'air');
}
const rooms=[];
function shell(name,x0,z0,x1,z1,floor,deck){
 rooms.push({name,bounds:[x0,z0,x1,z1],floorY:floor,deckY:deck,clearInterior:[x1-x0-3,z1-z0-3]});
 for(let z=z0;z<=z1;z++)for(let x=x0;x<=x1;x++){
  for(let y=Math.min(112,floor);y<=floor;y++)put(x,y,z,y===floor?'smooth_stone':'stone_bricks');
  const edge=x<=x0+1||x>=x1-1||z<=z0+1||z>=z1-1;
  if(edge)for(let y=floor+1;y<=deck;y++)put(x,y,z,y<=floor+3||y===floor+12||y>=deck-1?'stone_bricks':'calcite');
  put(x,deck,z,'stone_bricks');
 }
 rim(x0,z0,x1,z1,deck);
 // Layered plinth stairs, external piers, deep window surrounds and inverted corbels.
 for(let x=x0;x<=x1;x++)for(const z of [z0,z1]){
  put(x,floor+1,z,'stone_brick_stairs',stairs(z===z0?'south':'north'));
  if((x-x0)%5===0)put(x,deck-2,z,'stone_brick_stairs',stairs(z===z0?'south':'north','top'));
 }
 for(let z=z0;z<=z1;z++)for(const x of [x0,x1])put(x,floor+1,z,'stone_brick_stairs',stairs(x===x0?'east':'west'));
 for(const z of [z0,z1])for(let x=x0+3;x<=x1-3;x+=6){
  for(let y=floor+2;y<=deck-3;y++)put(x,y,z,'smooth_quartz');
  put(x,deck-2,z,'quartz_stairs',stairs(z===z0?'south':'north','top'));
 }
 function window(x,z,side,y){const height=Math.min(5,deck-3-y);for(let yy=y;yy<=y+height;yy++)for(let d=-1;d<=1;d++){
  const xx=x+(side?0:d),zz=z+(side?d:0),n=Math.abs(d)===1||yy===y||yy===y+height?'smooth_quartz':'light_blue_stained_glass';
  put(xx,yy,zz,n);if(n==='light_blue_stained_glass')put(xx+(side?(x===x0?1:-1):0),yy,zz+(side?0:(z===z0?1:-1)),'air');
 }put(x,y-1,z,'quartz_stairs',stairs(side?(x===x0?'east':'west'):(z===z0?'south':'north')));}
 for(const y of [floor+5,floor+15,floor+27])if(y+3<=deck-3){
  for(let x=x0+6;x<=x1-5;x+=6)for(const z of [z0,z1])window(x,z,false,y);
  for(let z=z0+6;z<=z1-5;z+=6)for(const x of [x0,x1])window(x,z,true,y);
 }
 for(const x of [x0-1,x1+1])for(let z=z0+3;z<=z1-3;z+=7){
  box(x,floor+1,z,x,deck-3,z+1,'stone_bricks');box(x,floor+4,z,x,deck-4,z+1,'smooth_quartz');
  for(let dz=0;dz<=1;dz++){put(x,floor+1,z+dz,'stone_brick_stairs',stairs(x<x0?'east':'west'));put(x,deck-2,z+dz,'quartz_stairs',stairs(x<x0?'east':'west','top'));}
  put(x,deck-1,z,'smooth_quartz_slab',slab);put(x,floor+8,z,'lantern',lamp);
 }
}
// Side wings have a stepped outline with recessed links, not long rectangular house roofs.
for(const left of [true,false]){
 const a=left?119:180,c=left?140:201,mid0=left?121:182,mid1=left?138:199;
 shell(left?'West archive and services':'East guard and residence',a,52,c,68,116,140);
 shell(left?'West recessed link':'East recessed link',mid0,67,mid1,86,116,136);
 shell(left?'West reception wing':'East service wing',a,84,c,100,116,138);
 box(a+2,128,54,c-2,128,98,'spruce_planks');
 // Wide openings unite the three overlapping masses on both floors.
 for(const y of [117,129])for(const z of [67,68,84,85,86])box(mid0+2,y,z,mid1-2,y+5,z,'air');
}
shell('Great hall',143,50,177,80,116,142);
// Rear connector and the four-level central keep.
shell('Rear connecting gallery',149,37,171,51,116,138);
box(151,128,38,169,128,51,'spruce_planks');
shell('Central keep',149,16,171,38,116,156);
for(const y of [128,140,152])box(151,y,18,169,y,36,'spruce_planks');
// Broad upper nave balconies leave a double-height middle, with carved supporting columns.
box(145,128,52,149,128,78,'spruce_planks');box(171,128,52,175,128,78,'spruce_planks');box(145,128,52,175,128,56,'spruce_planks');
for(const x of [149,171])for(let z=57;z<=77;z++)put(x,129,z,'stone_brick_wall',{up:'true',north:'low',south:'low',east:'none',west:'none',waterlogged:'false'});
for(const x of [146,174])for(const z of [58,68,77]){
 box(x,117,z,x,127,z,'smooth_quartz');put(x,126,z+1,'quartz_stairs',stairs('north','top'));put(x,126,z-1,'quartz_stairs',stairs('south','top'));
 lampOn(x,117,z+2);
}
// Two storey covered galleries link hall and service wings.
for(const left of [true,false]){
 const x0=left?138:175,x1=left?145:182;
 for(const y of [116,128]){box(x0,y,60,x1,y,66,'smooth_stone');box(x0,y+1,61,x1,y+5,65,'air');}
 for(const z of [60,66])for(const x of [x0,x1])box(x,117,z,x,136,z,'smooth_quartz');
 for(const z of [60,66]){box(x0,135,z,x1,136,z,'smooth_quartz');for(let x=x0;x<=x1;x++)put(x,134,z,'quartz_stairs',stairs(z===60?'south':'north','top'));}
 box(x0,137,60,x1,137,66,'stone_bricks');
}
for(const y of [116,128]){
 carveArch(155,165,37,39,y,7);carveArch(155,165,49,51,y,7);
}
carveArch(155,165,79,80,116,12);
// Ornamented south portal, balcony, banner field and central stepped approach.
for(const x of [152,167])box(x,117,81,x+1,132,82,'smooth_quartz');
box(153,129,81,167,129,83,'stone_bricks');rim(153,81,167,83,129);box(157,130,80,163,134,83,'air');
banner(160,81,133,7);for(const x of [151,169])lampOn(x,119,82);
for(let i=0;i<4;i++)for(let x=153;x<=167;x++){
 const z=84-i,y=113+i;box(x,y+1,z,x,120,z,'air');box(x,112,z,x,y-1,z,'stone_bricks');put(x,y,z,'stone_brick_stairs',stairs('north'));
}
for(let z=85;z<=102;z++)for(let x=141;x<=179;x++)put(x,112,z,Math.abs(x-160)<=2?'smooth_stone':(x+z)%7===0?'polished_andesite':'stone_bricks');
// Side flights reach wing ground floor without crossing the main approach or round towers.
for(const x0 of [141,173])for(let i=0;i<4;i++)for(let x=x0;x<x0+7;x++){
 const z=96-i,y=113+i;box(x,y+1,z,x,120,z,'air');box(x,112,z,x,y-1,z,'stone_bricks');put(x,y,z,'stone_brick_stairs',stairs('north'));
}for(const x0 of [141,173])box(x0,116,88,x0+6,116,92,'smooth_stone');
// Main keep stairs: 3 full flights, broad landings, then a final flight to the crown.
function flight(x0,x1,z0,dir,floor,rise){
 for(let i=0;i<rise;i++){const z=z0+(dir==='north'?-i:i),y=floor+1+i;
  box(x0,y,z,x1,Math.min(171,y+3),z,'air');for(let x=x0;x<=x1;x++)put(x,y,z,'stone_brick_stairs',stairs(dir));
 }
}
// Open the wells through every intermediate floor before placing the individual flights.
box(151,117,20,154,155,31,'air');box(166,129,20,169,143,31,'air');
flight(151,154,31,'north',116,12);box(151,128,18,169,128,19,'spruce_planks');
flight(166,169,20,'south',128,12);box(151,140,32,169,140,34,'spruce_planks');
flight(151,154,31,'north',140,12);box(151,152,18,169,152,19,'spruce_planks');
box(158,153,22,162,160,25,'air');flight(158,162,25,'north',152,4);
// Wing flights continue to their roof walks through two 12-block rises.
for(const x0 of [130,186]){
 box(x0,117,70,x0+3,143,81,'air');flight(x0,x0+3,81,'north',116,12);
 box(x0-3,128,68,x0+3,128,69,'spruce_planks');
 const next=x0-5;box(next,129,70,next+3,143,81,'air');flight(next,next+3,70,'south',128,8);
 box(next,136,78,next+3,136,83,'stone_bricks');
 // Walk along the recessed deck, then climb to the higher north terrace.
 box(next,137,77,next+3,140,81,'air');
 for(let i=0;i<4;i++)for(let x=next;x<=next+3;x++)put(x,137+i,72-i,'stone_brick_stairs',stairs('north'));
 box(next,140,67,next+3,140,68,'stone_bricks');box(next,141,67,next+3,144,69,'air');
}
// Round blue-roof towers: flared bases, vertical ribs, crown corbels and pointed stepped roofs.
const towers=[];
function roundTower(cx,cz,deck,tip,r=7){
 towers.push({center:[cx,116,cz],radius:r,deckY:deck,tipY:tip});
 for(let z=cz-r-1;z<=cz+r+1;z++)for(let x=cx-r-1;x<=cx+r+1;x++){
  const d=Math.hypot(x-cx,z-cz);if(d>r+.6)continue;
  put(x,116,z,'stone_bricks');
  for(let y=117;y<=deck;y++){
   const edge=d>=r-1.5;let n=edge?(y<=119||y===128||y===140||y>=deck-1?'stone_bricks':'calcite'):'air';
   if(edge&&(x===cx||z===cz)&&y>120&&((y-121)%12)<5)n='light_blue_stained_glass';
   if(edge&&Math.abs(x-cx)>2&&Math.abs(z-cz)>2&&Math.abs(Math.abs(x-cx)-Math.abs(z-cz))<=1)n='smooth_quartz';
   put(x,y,z,n);
  }
  for(const y of [128,140,deck])if(y<=deck&&d<r-1.5)put(x,y,z,'spruce_planks');
  if(d>r-.6){put(x,119,z,'stone_brick_stairs',stairs(Math.abs(x-cx)>Math.abs(z-cz)?x<cx?'east':'west':z<cz?'south':'north'));put(x,deck-1,z,'quartz_stairs',stairs(Math.abs(x-cx)>Math.abs(z-cz)?x<cx?'east':'west':z<cz?'south':'north','top'));}
 }
 for(let y=deck+1;y<=tip-1;y++){
  const rr=(tip-y)*r/(tip-deck);for(let x=cx-r-1;x<=cx+r+1;x++)for(let z=cz-r-1;z<=cz+r+1;z++){
   const d=Math.hypot(x-cx,z-cz);if(d>rr+.6)continue;
   put(x,y,z,y===deck+1?'smooth_quartz':'blue_concrete');
   // Vanilla prismarine stairs are green; retain blue concrete on the pointed roof.
  }
 }
 put(cx,tip,cz,'gold_block');
 // Lower circular rooms connect to the wing on their inner edge, at both floor levels.
 for(const y of [116,128]){const z0=cz<60?cz+4:cz-8,z1=cz<60?cz+8:cz-4;carveArch(cx-2,cx+2,z0,z1,y,5);}
 lampOn(cx+3,117,cz);lampOn(cx-3,129,cz);
}
for(const [x,z,d,t]of [[125,45,150,166],[195,45,150,166],[125,93,142,158],[195,93,142,158]])roundTower(x,z,d,t);
for(const t of towers){const [cx,,cz]=t.center;for(let y=t.tipY+1;y<=t.tipY+5;y++)put(cx,y,cz,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});
 for(let dx=1;dx<=4;dx++)for(let y=t.tipY+3;y<=t.tipY+4;y++)put(cx+dx,y,cz,(dx+y)%3===0?'white_wool':'blue_wool');}
// Small keep corner turrets have solid supports above the inhabited floors.
for(const [cx,cz]of [[151,18],[169,18],[151,36],[169,36]]){
 for(let y=153;y<=160;y++)for(let x=cx-2;x<=cx+2;x++)for(let z=cz-2;z<=cz+2;z++)if(Math.hypot(x-cx,z-cz)<=2.4)put(x,y,z,y===156?'chiseled_stone_bricks':'smooth_quartz');
 for(let y=161;y<=167;y++)for(let x=cx-3;x<=cx+3;x++)for(let z=cz-3;z<=cz+3;z++)if(Math.hypot(x-cx,z-cz)<=(168-y)*.43+.25)put(x,y,z,'blue_concrete');
 put(cx,168,cz,'gold_block');
}
// Highest central lantern spire, distinct from the four corner turrets.
for(let y=157;y<=162;y++)for(let x=157;x<=163;x++)for(let z=26;z<=32;z++)if(Math.hypot(x-160,z-29)<=3.2)put(x,y,z,y<161?'smooth_quartz':'light_blue_stained_glass');
for(let y=163;y<=168;y++)for(let x=156;x<=164;x++)for(let z=25;z<=33;z++)if(Math.hypot(x-160,z-29)<=(169-y)*.55)put(x,y,z,'blue_concrete');
put(160,169,29,'gold_block');put(160,170,29,'oak_fence',{north:'false',south:'false',east:'false',west:'false',waterlogged:'false'});put(160,171,29,'gold_block');
for(let z=30;z<=34;z++)for(let y=169;y<=170;y++)put(160,y,z,(z+y)%3===0?'white_wool':'blue_wool');
banner(160,39,141,11);for(const x of [147,173]){box(x,117,32,x,151,33,'smooth_quartz');put(x,152,32,'quartz_stairs',stairs(x<160?'east':'west','top'));}
// Rear curtain follows a chamfered outline and joins the existing side walks at Z40.
const wall=new Map(),walk=new Set(),points=[[112,40],[110,24],[114,17],[124,10],[196,10],[206,17],[210,24],[208,40]];
for(let j=0;j<points.length-1;j++){
 const [ax,az]=points[j],[bx,bz]=points[j+1],n=Math.max(Math.abs(ax-bx),Math.abs(az-bz));
 for(let t=0;t<=n;t++){
  const cx=Math.round(ax+(bx-ax)*t/n),cz=Math.round(az+(bz-az)*t/n);if(cz>=40)continue;
  for(let dx=-2;dx<=2;dx++)for(let dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=6&&cz+dz<40&&cz+dz>=6)wall.set((cz+dz)*S+cx+dx,[cx+dx,cz+dz]);
  for(let dx=-1;dx<=1;dx++)for(let dz=-1;dz<=1;dz++)if(cz+dz<40&&cz+dz>=6)walk.add((cz+dz)*S+cx+dx);
 }
}
for(const [k,[x,z]]of wall){
 let ground=-1;for(let y=116;y>=90;y--)if(natural.has(get(x,y,z).Name)&&get(x,y,z).Name!=='minecraft:air'){ground=y;break;}
 if(ground<0)throw Error('Rear wall unsupported '+[x,z]);box(x,ground+1,z,x,124,z,'stone_bricks');box(x,125,z,x,128,z,'air');
 if(!walk.has(k))crenellate([[x,z]],124);
}
// Open only the three-wide former end caps; preserve the remaining side/front defense.
for(const x0 of [111,207])for(let x=x0;x<=x0+2;x++)box(x,125,39,x,128,40,'air');
for(const x of [128,140,152,168,180,192]){
 box(x,113,7,x+1,125,7,'stone_bricks');put(x,126,7,'smooth_quartz_slab',slab);put(x,127,7,'lantern',lamp);
 for(let dx=0;dx<=1;dx++){put(x+dx,112,6,'stone_brick_stairs',stairs('south'));put(x+dx,123,7,'stone_brick_stairs',stairs('south','top'));}
}
for(const x of [134,146,174,186]){
 box(x,117,8,x,120,8,'air');box(x,117,9,x,120,9,'iron_bars',{north:'false',south:'false',east:'true',west:'true',waterlogged:'false'});
 banner(x,7,114,7);
}
// Reception, council, residence and library furnishings; keep the principal seven-wide circulation free.
for(let z=58;z<=78;z++)for(let x=158;x<=162;x++)put(x,117,z,'blue_carpet');
box(154,117,54,166,118,57,'polished_andesite');for(const x of [158,160,162])put(x,119,55,'spruce_stairs',stairs('south'));
box(159,120,54,161,123,54,'blue_wool');put(160,124,54,'gold_block');
for(const x of [147,173])for(const z of [60,71]){box(x,137,z,x,139,z,'chain',{axis:'y',waterlogged:'false'});put(x,136,z,'lantern',{...lamp,hanging:'true'});}
for(const left of [true,false]){
 const a=left?121:182,c=left?138:199;
 // North wing: archive left, equipment workshop right.
 for(let z=55;z<=65;z+=3){box(a,117,z,a+1,120,z,left?'bookshelf':'stripped_spruce_wood',left?undefined:{axis:'y'});box(c-1,117,z,c,120,z,left?'bookshelf':'stripped_spruce_wood',left?undefined:{axis:'y'});}
 box(a+4,117,58,c-4,117,60,'spruce_planks');for(const x of [a+4,c-4])put(x,118,59,'lantern',lamp);
 put(a+3,117,64,left?'crafting_table':'smithing_table');
 // South wing reception / guard counters; no block entities required.
 box(a+2,117,86,c-2,117,87,'dark_oak_planks');for(const x of [a+3,c-3])lampOn(x,118,86);
 for(const z of [89,96])for(const x of [a+2,c-2])put(x,117,z,'spruce_stairs',stairs(x<160?'east':'west'));
 // Upper level sleeping quarters with wool mattresses and carved wood headboards.
 for(const z of [56,62,88,95])for(const x of [a+1,c-2]){
  box(x,129,z,x+1,129,z+2,'white_wool');box(x,129,z-1,x+1,130,z-1,'spruce_planks');put(x,129,z+3,'spruce_slab',slab);
 }
}
// Keep: administrative ground floor, council on 2F, residence on 3F, library on 4F.
for(const y of [117,129,141,153])for(const x of [156,164])lampOn(x,y,35);
box(157,117,22,163,117,23,'dark_oak_planks');put(160,118,22,'lantern',lamp);
box(157,129,25,163,129,29,'spruce_planks');for(const z of [25,27,29])for(const x of [156,164])put(x,129,z,'spruce_stairs',stairs(x<160?'east':'west'));
box(157,141,26,159,141,29,'white_wool');box(157,141,25,159,142,25,'spruce_planks');box(161,141,30,164,141,31,'dark_oak_planks');
for(const x of [156,164])box(x,153,27,x,155,34,'bookshelf');box(158,153,31,162,153,32,'spruce_planks');put(160,154,31,'lantern',lamp);
// Court fountain and paired gardens remain off the public axis around the basin.
for(let x=157;x<=163;x++)for(let z=91;z<=97;z++)if(Math.hypot(x-160,z-94)<=3.3){
 const inner=Math.hypot(x-160,z-94)<2.5;put(x,113,z,inner?'water':'stone_bricks',inner?{level:'0'}:undefined);
 if(!inner)put(x,114,z,'stone_brick_slab',slab);
}
box(160,113,94,160,116,94,'smooth_quartz');put(160,117,94,'water',{level:'0'});
for(const x0 of [148,168])for(const z0 of [88,99])for(let x=x0;x<x0+5;x++)for(let z=z0;z<z0+3;z++){
 const edge=x===x0||x===x0+4||z===z0||z===z0+2;put(x,113,z,edge?'stone_brick_slab':'moss_block',edge?slab:undefined);if(!edge)put(x,114,z,'azure_bluet');
}
for(const x of [150,170])for(const z of [86,98])lampOn(x,113,z);
for(const x of [136,184]){banner(x,101,125,8);lampOn(x,117,101);}
// Court planters, roof lanterns and small stone pinnacles complete the castle silhouette.
for(const x of [116,204])for(let z=55;z<=80;z++)if(get(x,116,z).Name==='minecraft:grass_block'&&get(x,117,z).Name==='minecraft:air'){
 put(x,117,z,'oak_leaves',{persistent:'true',distance:'7',waterlogged:'false'});if(z%5===0)put(x,118,z,'oak_leaves',{persistent:'true',distance:'7',waterlogged:'false'});
}
// Formal rear gardens occupy the usable terrace on either side of the keep.
for(const cx of [133,187]){
 for(let x=cx-7;x<=cx+7;x++)for(let z=19;z<=32;z++){
  const path=Math.abs(x-cx)<=1||z===25||z===26;
  put(x,116,z,path?'polished_andesite':'grass_block',path?undefined:{snowy:'false'});
  const edge=x===cx-7||x===cx+7||z===19||z===32;
  if(edge&&!path&&get(x,117,z).Name==='minecraft:air')put(x,117,z,'oak_leaves',{persistent:'true',distance:'7',waterlogged:'false'});
 }
 for(const x of [cx-4,cx+4])for(const z of [22,29]){put(x,117,z,'moss_block');put(x,118,z,'white_tulip');}
 for(let x=cx-3;x<=cx+3;x++)put(x,117,31,'spruce_stairs',stairs('north'));
 lampOn(cx-6,117,20);lampOn(cx+6,117,20);
 // Join the rear garden paths to the main terrace beside the rear connector.
 for(let z=33;z<=49;z++)for(let x=cx-1;x<=cx+1;x++)if(get(x,117,z).Name==='minecraft:air')put(x,116,z,'polished_andesite');
}
for(const [x,y,z]of [[143,142,50],[177,142,50],[143,142,80],[177,142,80],[119,140,60],[201,140,60],[119,138,84],[201,138,84]]){
 box(x,y+1,z,x,y+3,z,'smooth_quartz');put(x,y+4,z,'gold_block');put(x,y+5,z,'lantern',lamp);
}
for(const [x,y,z]of [[145,143,72],[175,143,72],[139,139,88],[181,139,88],[155,157,21],[165,157,21]])lampOn(x,y,z);
// Split reception counters around the circulation opening into the south wing.
for(const cx of [129,191])box(cx-2,117,84,cx+2,122,88,'air');
// Clear all stair headrooms again after adjacent furnishings/roofs; never clear actual treads.
// Validation below uses state shapes and includes the preserved outer defense.
const pass=n=>n==='minecraft:air'||n.endsWith('_carpet')||n.includes('tulip')||n.endsWith(':azure_bluet')||n.endsWith(':water'),shapeCache=new Map();
function shape(id){if(shapeCache.has(id))return shapeCache.get(id);const d=m.definitions[id],n=d.Name,p=d.Properties||{};let a;
 if(pass(n))a=[];else if(n.endsWith('_stairs')){const top=p.half==='top',lo=top?0:.5,hi=top?.5:1;a=[top?[0,.5,0,1,1,1]:[0,0,0,1,.5,1],p.facing==='north'?[0,lo,0,1,hi,.5]:p.facing==='south'?[0,lo,.5,1,hi,1]:p.facing==='east'?[.5,lo,0,1,hi,1]:[0,lo,0,.5,hi,1]];}
 else if(n.endsWith('_slab'))a=p.type==='top'?[[0,.5,0,1,1,1]]:p.type==='double'?[[0,0,0,1,1,1]]:[[0,0,0,1,.5,1]];else a=[[0,0,0,1,1,1]];
 shapeCache.set(id,a);return a;}
const solid=(x,y,z)=>shape(data[at(Math.floor(x),Math.floor(y),Math.floor(z))]).some(([a,b,c,d,e,f])=>x%1>=a&&x%1<d&&y%1>=b&&y%1<e&&z%1>=c&&z%1<f),cache=new Map();
function surfaces(hx,hz){const k=hx+','+hz;if(cache.has(k))return cache.get(k);const x=hx/2+.25,z=hz/2+.25,a=new Set();for(let y=108;y<=157;y++)for(const c of shape(data[at(Math.floor(x),y,Math.floor(z))])){
 if(x%1<c[0]||x%1>=c[3]||z%1<c[2]||z%1>=c[5])continue;const h=y+c[4];if(!Number.isInteger(h*2))continue;let free=true;for(let yy=h+.125;yy<h+2;yy+=.25)if(solid(x,yy,z))free=false;if(free)a.add(h*2);
 }cache.set(k,[...a]);return cache.get(k);}
const seen=new Set(),queue=[[320,298,218]],pk=(x,z,h)=>`${x},${z},${h}`;
for(let i=0;i<queue.length;i++){const [x,z,h]=queue[i],k=pk(x,z,h);if(seen.has(k)||!surfaces(x,z).includes(h))continue;seen.add(k);
 for(const [nx,nz]of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]])if(nx>=208&&nx<=432&&nz>=12&&nz<=300)for(const nh of surfaces(nx,nz))if(Math.abs(nh-h)<=1&&!seen.has(pk(nx,nz,nh)))queue.push([nx,nz,nh]);
}
const targets=[['court',160,113,100],['hall',160,117,74],['west wing',136,117,88],['east wing',183,117,88],['west upper',136,129,75],['east upper',194,129,75],['hall gallery',148,129,64],['keep admin',160,117,33],['keep council',160,129,33],['keep residence',163,141,33],['keep library',163,153,33],['keep crown',163,157,24],['west roof',136,137,75],['east roof',194,137,75],['west front tower',125,117,93],['east front tower',195,117,93],['west rear tower',125,117,45],['east rear tower',195,117,45],['west front tower upper',125,129,93],['east front tower upper',195,129,93],['west rear tower upper',125,129,45],['east rear tower upper',195,129,45],['preserved gate',160,133,116],['preserved west bastion',125,129,112],['preserved east bastion',195,129,112],['west rear wall',113,125,22],['east rear wall',207,125,22],['north wall',160,125,10]];
const failures=targets.filter(([,x,h,z])=>!seen.has(pk(x*2,z*2,h*2))).map(t=>({target:t,surfaces:surfaces(t[1]*2,t[3]*2)}));
let changedBlocks=0;for(let i=0;i<data.length;i++)if(data[i]!==old[i]){if(!planned.has(i))throw Error('Unplanned change');changedBlocks++;}
const bounds=[100,24,4,220,171,102],previewBlocks=[];
// Preview the actual whole castle including preserved defenses, not merely a proposal shell.
for(let z=4;z<=128;z++)for(let x=100;x<=220;x++)for(let y=108;y<=171;y++){
 const d=get(x,y,z);if(d.Name==='minecraft:air')continue;
 const exposed=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].some(([dx,dy,dz])=>{const n=get(x+dx,y+dy,z+dz).Name;return n==='minecraft:air'||n.includes('glass')||n.endsWith('_stairs')||n.endsWith('_slab');});
 if(exposed||d.Name.endsWith('_stairs')||d.Name.endsWith('_slab'))previewBlocks.push({position:[x,y,z],state:d});
}
const audit={stage:'staged castle completion',applied:false,sourceHash,bounds,changedBlocks,addedLandColumns,protectedEarlierColumns:protectedColumns.size,frontDefensePreserved:true,sideJoinChanges:'only three-wide end caps at Z40 opened',rooms,towers,routeTargets:targets,routeFailures:failures,reachableSurfaces:seen.size,routeMethod:'half-block surfaces with 2-block point headroom, not full player collision',inGameTested:false,preview:{center:[160,131,65],distance:195,focuses:{west:{center:[128,134,70],distance:105},east:{center:[192,134,70],distance:105},gate:{center:[160,140,43],distance:125}}},blocks:previewBlocks};
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'castle-complete.json'),JSON.stringify(audit));
let html=fs.readFileSync(path.join(__dirname,'bastions-3d-template.html'),'utf8').replaceAll('ป้อมหน้าและหอประตู','ปราสาทโซน 07 ทั้งชุด').replace('ป้อมซ้าย','ปีกตะวันตก').replace('ป้อมขวา','ปีกตะวันออก').replace('หอประตู</option>','หอหลักและโถง</option>').replaceAll('136','172').replace('min="113"','min="113"').replace('bastions-3d.js','castle-complete-3d.js').replace('<script src="castle-complete-3d.js">','<script src="castle-complete-3d.js" data-model="castle-complete.json">');
fs.writeFileSync(path.join(out,'castle-complete-3d.html'),html);
let js=fs.readFileSync(path.join(__dirname,'bastions-3d.js'),'utf8').replaceAll('cut=136','cut=172').replaceAll('value=136','value=172').replaceAll('textContent=136','textContent=172');
js=js.replace("const n=s.Name;if(n.includes('blue_wool'))", "const n=s.Name;if(n.includes('blue_concrete')||n.includes('prismarine'))return [.12,.26,.62];if(n.includes('glass'))return [.36,.66,.76];if(n.endsWith(':water'))return [.17,.51,.76];if(n.includes('grass')||n.includes('moss')||n.includes('leaves'))return [.29,.46,.22];if(n.includes('spruce')||n.includes('oak')||n.includes('bookshelf'))return [.40,.27,.15];if(n.includes('blue_wool'))");
fs.writeFileSync(path.join(out,'castle-complete-3d.js'),js);
console.log(JSON.stringify({...audit,blocks:previewBlocks.length,routeTargets:targets.length,rooms:rooms.length,towers:towers.length}));
if(failures.length)throw Error('Routes failed: '+failures.map(f=>f.target[0]).join(', '));
if(process.argv.includes('--apply')){
 const backup=path.join(root,'archive/map-edit-backups/before-castle-completion/starter_sky_land_spawn_v1');if(fs.existsSync(backup))throw Error('Backup exists');
 if(sha(fs.readFileSync(file))!==sourceHash)throw Error('Concurrent world change: stage again using latest source');
 fs.mkdirSync(path.dirname(backup),{recursive:true});fs.cpSync(dir,backup,{recursive:true});
 const result=patch(m,data,old,source);if(sha(fs.readFileSync(file))!==sourceHash)throw Error('World changed while patching');fs.writeFileSync(file+'.pending',result.bytes);fs.renameSync(file+'.pending',file);
 const saved=lib.load(path.basename(dir)),sb=Buffer.from(saved.blocks,'base64'),sk=saved.definitions.map(key),ek=m.definitions.map(key);
 for(let i=0;i<data.length;i++)if(sk[sb.readUInt16LE(i*2)]!==ek[data[i]])throw Error('Saved world mismatch at '+i);
 const profile={bounds:[103,4,217,124],buildingBounds:[118,16,202,100],buildingSize:[85,85],floorY:116,courtFloorY:112,highestY:171,heightAboveFloor:55,entrance:[160,117,80],rooms,towers,stage:'completed architecture and furnishings; no gameplay/NPC integration',completion:{bounds,sourceHash,backup:path.relative(root,backup),changedBlocks,changedChunks:result.changed,addedLandColumns,verifiedTargets:targets,inGameTested:false}};
 const mf=path.join(dir,'map-manifest.json'),manifest=JSON.parse(fs.readFileSync(mf));manifest.castle={...manifest.castle,...profile};delete manifest.castle.fortress;
 manifest.castle.frontDefense={...manifest.castle.frontDefense,stage:'front and side defense retained; north end caps opened to completed rear curtain'};
 manifest.rooms=manifest.rooms.map(r=>r.id===manifest.castle.id?manifest.castle:r);fs.writeFileSync(mf,JSON.stringify(manifest,null,2));
 const zf=path.join(dir,'zones.json'),zones=JSON.parse(fs.readFileSync(zf));fs.writeFileSync(zf,JSON.stringify(zones.map(z=>z.id===manifest.castle.id?manifest.castle:z),null,2));
 audit.applied=true;audit.stage='saved working city castle completed';fs.writeFileSync(path.join(out,'castle-complete.json'),JSON.stringify(audit));fs.writeFileSync(path.join(out,'castle-complete-3d.html'),html.replace('ยังไม่แก้แมพจริง','บันทึกลงแมพเดิมแล้ว · ยังไม่ทดสอบในเกม'));
 fs.writeFileSync(reportFile,JSON.stringify({...audit,blocks:undefined,changedChunks:result.changed,backup:path.relative(root,backup),savedVoxelComparison:true,outsidePlannedChanges:0},null,2));console.log(JSON.stringify({applied:true,changedBlocks,changedChunks:result.changed,backup}));
}
