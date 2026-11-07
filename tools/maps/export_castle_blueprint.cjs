// Read-only, deterministic construction baseline. Never writes to world/region files.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib');
const {createLibrary}=require('./viewer/map-library.cjs');
const root=path.resolve(__dirname,'../..'),map=path.join(root,'maps/starter_sky_land_spawn_v1'),out=path.join(root,'docs/maps/zone07-blueprint');
const region=path.join(map,'region/r.0.0.mca'),hash=()=>crypto.createHash('sha256').update(fs.readFileSync(region)).digest('hex'),beforeHash=hash();
const m=createLibrary(path.dirname(map)).load(path.basename(map)),bytes=Buffer.from(m.blocks,'base64'),S=m.size,ix=(x,y,z)=>(y-m.low)*S*S+z*S+x;
const data=new Uint16Array(bytes.length/2);for(let i=0;i<data.length;i++)data[i]=bytes.readUInt16LE(i*2);
// Preserve an independently readable baseline; all proposed changes remain in memory.
const baseline=data.slice(),stateKey=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort()),stateIds=new Map(m.definitions.map((d,i)=>[stateKey(d),i]));
const proposed=[];
function putDesign(x,y,z,name,properties){
 if(x<118||x>202||z<40||z>102||y<117||y>151)throw Error('Proposal outside facade bounds');
 const d={Name:'minecraft:'+name,...(properties?{Properties:properties}:{})},k=stateKey(d);if(!stateIds.has(k)){stateIds.set(k,m.definitions.length);m.definitions.push(d);}data[ix(x,y,z)]=stateIds.get(k);
}
const stair={facing:'north',half:'top',shape:'straight',waterlogged:'false'},lantern={hanging:'false',waterlogged:'false'};
// Projecting gate piers frame the opening without reducing its 11-block clearance.
for(const x0 of [149,169])for(let x=x0;x<=x0+2;x++)for(let y=117;y<=139;y++){
 putDesign(x,y,100,y<=121?'stone_bricks':y===128||y===139?'chiseled_stone_bricks':'smooth_quartz');
 if(y<=121)putDesign(x,y,101,'stone_bricks');
}
for(const x0 of [149,169])for(let x=x0;x<=x0+2;x++){
 putDesign(x,140,100,'stone_brick_stairs',stair);putDesign(x,141,100,'smooth_quartz');
 if(x===x0+1)putDesign(x,142,100,'lantern',lantern);
}
// Continuous lintel bands and a deliberately specified heraldic panel.
for(let x=149;x<=171;x++){putDesign(x,132,99,'chiseled_stone_bricks');putDesign(x,139,99,'smooth_quartz');}
for(let x=157;x<=163;x++)for(let y=133;y<=138;y++)putDesign(x,y,99,(x===157||x===163||y===133||y===138)?'smooth_quartz':x===160||y===135?'gold_block':'blue_wool');
// Side facades use deep three-wide piers, caps and paired blue-white banners.
for(const cx of [136,184])for(let x=cx-1;x<=cx+1;x++)for(let y=117;y<=136;y++)putDesign(x,y,95,y<=120?'stone_bricks':y===128||y===136?'chiseled_stone_bricks':'smooth_quartz');
for(const cx of [139,181])for(let y=122;y<=134;y++)for(let dx=0;dx<=1;dx++)putDesign(cx+dx,y,95,y===122||y===134?'gold_block':dx?'white_wool':'blue_wool');
// Tower emblems sit flush in the south face; they never obstruct tower doorways.
for(const cx of [125,195]){
 for(let y=133;y<=138;y++)putDesign(cx,y,99,y===133||y===138?'gold_block':'blue_wool');
 for(let y=145;y<=149;y++)putDesign(cx,y,54,y===145||y===149?'gold_block':'blue_wool');
}
for(let i=0;i<data.length;i++)if(data[i]!==baseline[i]){const y=Math.floor(i/(S*S))+m.low,z=Math.floor(i/S)%S,x=i%S;proposed.push({position:[x,y,z],before:m.definitions[baseline[i]],after:m.definitions[data[i]]});}
if(!proposed.length)throw Error('No design changes');
const source=fs.readFileSync(path.join(__dirname,'remodel_castle_fortress.cjs'),'utf8'),start=source.indexOf('const cache=new Map()'),end=source.indexOf('let count=0;',start);
if(start<0||end<0)throw Error('Route validator missing');
const routes=eval(source.slice(start,end)+'\ntargets;');
const origin=[103,96,30],size=[115,76,120],key=d=>d.Name+JSON.stringify(Object.entries(d.Properties||{}).sort());
const palette=[{Name:'minecraft:air'}],lookup=new Map([[key(palette[0]),0]]),dense=new Uint16Array(size.reduce((a,b)=>a*b)),counts=new Map(),runs=[];
const at=(x,y,z)=>((y-origin[1])*size[2]+z-origin[2])*size[0]+x-origin[0];
for(let y=96;y<=171;y++)for(let z=30;z<=149;z++)for(let x=103;x<=217;x++){
 const d=m.definitions[data[ix(x,y,z)]],k=key(d);if(!lookup.has(k)){lookup.set(k,palette.length);palette.push(d);}const id=lookup.get(k);dense[at(x,y,z)]=id;counts.set(id,(counts.get(id)||0)+1);
}
for(let i=0;i<dense.length;){let j=i+1;while(j<dense.length&&dense[j]===dense[i])j++;runs.push([j-i,dense[i]]);i=j;}
const areas=[
 ['01','ตัวปราสาท / core',[118,116,40,202,171,99],'กรอบหลัก 85×60; คิ้วและฐานยื่นวัดแยก ไม่ใช่พื้นที่ห้องทั้งหมด'],
 ['02','หอประตู',[149,116,83,171,151,101],'ตัวหอ23×17 ถึงZ99; ฐานเสาเสนอใหม่ยื่นถึงZ101 รวม23×19; ช่องซุ้ม X155–165; ห้องยามพื้น140; ชาน148'],
 ['03','โถงกลาง',[142,116,58,178,141,94],'37×37; พรมแกนกลาง X158–162 Z76–90; ช่วงหน้าซ้อนหอประตู'],
 ['04','ปีกบริการตะวันตก',[118,116,42,142,141,92],'25×51; พื้นชั้นบน128; ชาน139; หอคอยซ้อนหัวและท้าย'],
 ['05','ปีกบริการตะวันออก',[178,116,42,202,141,92],'25×51; พื้นชั้นบน128; ชาน139; หอคอยซ้อนหัวและท้าย'],
 ['06','หอหลัก',[149,116,43,171,171,65],'23×23; พื้น116/128/140/152; ชาน156; หอย่อย168; ธง171'],
 ['07','หอคอยหลังตะวันตก',[118,116,40,132,166,54],'ศูนย์125,47 รัศมีตัวหอ7; ฐานและคิ้วยื่นถึงราว8.5'],
 ['08','หอคอยหลังตะวันออก',[188,116,40,202,166,54],'ศูนย์195,47 รัศมีตัวหอ7; ฐานและคิ้วยื่นถึงราว8.5'],
 ['09','หอคอยหน้าตะวันตก',[118,116,85,132,158,99],'ศูนย์125,92 รัศมีตัวหอ7; ชั้นห้อง116/128'],
 ['10','หอคอยหน้าตะวันออก',[188,116,85,202,158,99],'ศูนย์195,92 รัศมีตัวหอ7; ชั้นห้อง116/128'],
 ['11','ลานและทางขึ้นประตู',[116,112,100,204,120,124],'พื้นลาน112; บันได X153–167: Z103/Y113 ถึง Z100/Y116'],
 ['12','กำแพงรอบนอก',[104,96,30,216,128,127],'ทรงโค้ง ไม่ใช่กล่องทึบ; ชานหน้า120 หลัง124; พิกัดจริงดู voxel'],
 ['13','ทางเชื่อมถนนหลัก',[153,108,125,167,113,149],'กว้าง15; ช่วงต่อถนนอยู่นอกกรอบโซน115×95 จึงระบุแยก'],
].map(([id,name,bounds,note])=>({id,name,bounds,dimensions:[bounds[3]-bounds[0]+1,bounds[4]-bounds[1]+1,bounds[5]-bounds[2]+1],note}));
const plan={format:'neoworld-block-blueprint-v1',status:'Construction proposal: saved castle layout plus deterministic mockup-inspired facade details; not applied to world',sourceMap:'starter_sky_land_spawn_v1',sourceRegionSha256:beforeHash,proposalChangedBlocks:proposed.length,origin,size,axes:{x:'east',y:'up',z:'south'},index:'((Y-96)*120+(Z-30))*115+(X-103)',encoding:'runs are [length,paletteId], X fastest then Z then Y; covers every coordinate including air',palette,runs,areas,levels:[112,116,120,124,128,139,140,141,148,152,156,168,171],routes:routes.map(([name,x,z,h])=>({name,position:[x,h,z],reachable:true})),notes:['Image-generated mockup is a visual reference, not exact geometry. This proposal retains functional massing and specifies additional facade details; it is not a 1:1 image reconstruction.','Area boxes are measurement envelopes and intentionally overlap where wings, towers, gatehouse, and keep join. Every voxel has exactly one final state.','Snapshot includes existing terrain and approach context. Do not paste the whole rectangular volume into the active world. No world writer is included.','Block-state inventory counts placed states in the snapshot, including terrain; it is not a survival crafting estimate.','No block entities are serialized; not an importable Minecraft structure file.','Slab/stair orientations are exact; route graph checks 0.5-block steps and two-block headroom, not full Minecraft collision simulation.']};
const decoded=[];for(const [n,id]of runs)for(let j=0;j<n;j++)decoded.push(id);if(decoded.length!==dense.length||decoded.some((id,i)=>id!==dense[i]))throw Error('RLE round trip failed');
for(const d of palette)if(d.Name.endsWith('_stairs')&&(!d.Properties?.facing||!d.Properties?.half||!d.Properties?.shape))throw Error('Stair state missing');
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'blocks.json'),JSON.stringify(plan));fs.writeFileSync(path.join(out,'blocks.json.gz'),zlib.gzipSync(JSON.stringify(plan)));
fs.writeFileSync(path.join(out,'proposal-changes.json'),JSON.stringify({sourceRegionSha256:beforeHash,changes:proposed}));
const quote=s=>'"'+String(s).replaceAll('"','""')+'"';fs.writeFileSync(path.join(out,'materials.csv'),'palette_id,block,properties,placed_count_including_context\n'+palette.map((d,i)=>[i,d.Name,quote(JSON.stringify(d.Properties||{})),counts.get(i)||0].join(',')).join('\n'));
const raw=Buffer.alloc(dense.length*2);for(let i=0;i<dense.length;i++)raw.writeUInt16LE(dense[i],i*2);
const html=fs.readFileSync(path.join(__dirname,'castle-blueprint-template.html'),'utf8').replace('/*BLUEPRINT_DATA*/',JSON.stringify({...plan,runs:undefined,blocks:raw.toString('base64')}));fs.writeFileSync(path.join(out,'index.html'),html);
const md='# แปลนบล็อกโซน 07\n\nแปลนฐานวัดจากปราสาทที่บันทึกจริง ไม่ใช่การออกแบบปราสาทใหม่ให้เหมือนภาพ AI ทุกส่วน และยังไม่แก้แมพหลัก ภาพ AI มีสัดส่วนที่ขัดกัน จึงใช้ขนาดและ XYZ ที่ตรวจจากบล็อกเป็นหลัก\n\n## ขนาดและการอ่าน\n\nกรอบหลัก X118–202 Z40–99 =85×60; กรอบโซน X103–217 Z30–124 =115×95; พื้น116 ถึงยอด171 ต่าง55ระดับ (ถ้านับบล็อกรวมพื้นและยอดคือ56ชั้น) ฐาน/คิ้วหอคอยยื่นจากกรอบหลักตามบล็อกเดิม จึงต้องอ่านกรอบส่วนยื่นแยก ไม่ถือว่าอาคารทั้งหมดอยู่ใน85×60\n\nSnapshot X103–217 Y96–171 Z30–149 ขนาด115×76×120 รวมทางเชื่อมถนน มี '+dense.length.toLocaleString('en-US')+' ตำแหน่งรวมอากาศ แต่ละตำแหน่งมีชนิดและสถานะบล็อกหนึ่งค่าแน่นอน ไม่มีการเดาพิกัดจากภาพ\n\n|รหัส|พื้นที่|XYZ ต่ำสุด → สูงสุด|กว้าง×ลึก|รายละเอียด|\n|---|---|---|---|---|\n'+areas.map(a=>'|'+a.id+'|'+a.name+'|'+a.bounds.slice(0,3).join(',')+' → '+a.bounds.slice(3).join(',')+'|'+a.dimensions[0]+'×'+a.dimensions[2]+'|'+a.note+'|').join('\n')+'\n\nพื้นที่ซ้อนกันเป็นส่วนเชื่อมอาคาร ไม่ใช่ห้องว่างสุทธิ ตัวอย่างหอหลักทับโถงด้านหลัง และหอคอยทับปีกอาคาร จึงห้ามรวมพื้นที่กล่องเพื่อคิดพื้นที่ใช้งานสุทธิ ผังแต่ละชั้นเป็นตัวตัดสินตำแหน่งจริง\n\n## ไฟล์\n\n- index.html: ผังทุกชั้น กดบล็อกดู XYZ/สถานะ และมุม3Dจากข้อมูลเดียวกัน ใช้งานออฟไลน์ได้\n- blocks.json: palette + RLE ครบทุกตำแหน่ง แกนXเดินเร็วสุด ตามด้วยZ แล้วY; พิกัดเริ่ม103,96,30\n- blocks.json.gz: สำเนาบีบอัดข้อมูลเดียวกัน\n- materials.csv: นับบล็อกที่วางแยกตามสถานะ รวมบริบทภูมิประเทศ ไม่ใช่จำนวนวัตถุดิบคราฟต์\n- validation.json: ตรวจ round trip ทุกตำแหน่ง เส้นทาง และ hash แมพไม่เปลี่ยน\n\n## ข้อจำกัด\n\nเป็น baseline ที่ตรวจสอบได้สำหรับปรับรายละเอียดต่อ ไม่กล่าวว่าเป็นแบบตรงภาพมอคอัพ1:1 และไม่ใช่ไฟล์นำเข้าเกม พรีวิว3Dแสดงบล็อกเต็มเพื่ออ่านมวลอาคาร บันได/ครึ่งบล็อกให้ดู Properties และผังชั้นเป็นหลัก ไม่มีข้อมูล block entities ไม่มีคำสั่งแก้แมพ ยังไม่ได้ทดสอบในเกมจริง\n';fs.writeFileSync(path.join(out,'BLUEPRINT.md'),md);
fs.appendFileSync(path.join(out,'BLUEPRINT.md'),'\n## แบบเสนอรายละเอียดหน้าปราสาท\n\nข้อมูล blocks.json และพรีวิวเป็นแบบเสนอ โดยยึดผังใช้งานเดิมแล้วเพิ่มรายละเอียดตามแนวมอคอัพ '+proposed.length+' ตำแหน่ง ยังไม่เขียนลงแมพจริง ข้อความ baseline ด้านบนหมายถึงที่มาของผังเดิม ไม่ใช่สถานะของไฟล์แบบเสนอ\n\n- เสาหอประตู X149–151 และ169–171 Z100 ฐานลึกถึงZ101 Y117–121; ลำเสาถึง139; คานบันไดคว่ำ140 คิ้ว141 โคม142\n- คิ้วประตู X149–171 Z99 Y132/139; ตราหินกรอบทองน้ำเงิน X157–163 Y133–138 Z99\n- เสาค้ำปีกอาคาร X135–137 /183–185 Z95 Y117–136; ธงคู่X139–140 /181–182 Z95 Y122–134\n- ตราหอคอยหน้า X125/195 Z99 Y133–138 และหอหลังZ54 Y145–149\n- proposal-changes.json ระบุ XYZ ชนิดก่อนและหลังครบทุกตำแหน่งที่เสนอให้แก้ เป็นข้อมูลตรวจงาน ไม่มีตัวเขียนแมพ\n- ตรวจเส้นทางทั้งหมดหลังเพิ่มรายละเอียดผ่าน ไม่อ้างว่าเป็นสำเนาภาพAI1:1\n');
const docPath=path.join(out,'BLUEPRINT.md');let document=fs.readFileSync(docPath,'utf8');document=document.replace('# แปลนบล็อกโซน 07','# แปลนเสนอปราสาทโซน 07').replace('แปลนฐานวัดจากปราสาทที่บันทึกจริง ไม่ใช่การออกแบบปราสาทใหม่ให้เหมือนภาพ AI ทุกส่วน และยังไม่แก้แมพหลัก ภาพ AI มีสัดส่วนที่ขัดกัน จึงใช้ขนาดและ XYZ ที่ตรวจจากบล็อกเป็นหลัก','แบบเสนอใช้ผังและมวลอาคารที่วัดจากปราสาทจริงเป็นฐาน เพิ่มรายละเอียดหน้าปราสาทตามแนวมอคอัพ '+proposed.length+' ตำแหน่ง และยังไม่แก้แมพหลัก ภาพ AI มีสัดส่วนที่ขัดกัน จึงยึดขนาดและ XYZ ในแปลนนี้เป็นหลัก ไม่กล่าวว่าตรงภาพทุกส่วน').replace('เป็น baseline ที่ตรวจสอบได้สำหรับปรับรายละเอียดต่อ','เป็นแบบเสนอที่ระบุบล็อกแน่นอนสำหรับตรวจและปรับรายละเอียดต่อ').replace('ข้อมูล blocks.json และพรีวิวเป็นแบบเสนอ โดยยึดผังใช้งานเดิมแล้วเพิ่มรายละเอียดตามแนวมอคอัพ '+proposed.length+' ตำแหน่ง ยังไม่เขียนลงแมพจริง ข้อความ baseline ด้านบนหมายถึงที่มาของผังเดิม ไม่ใช่สถานะของไฟล์แบบเสนอ','ข้อมูล blocks.json และพรีวิวใช้บล็อกชุดเดียวกัน รายการแก้ '+proposed.length+' ตำแหน่งยังไม่เขียนลงแมพจริง');fs.writeFileSync(docPath,document);
const afterHash=hash();if(beforeHash!==afterHash)throw Error('World changed while exporting');const validation={positions:dense.length,paletteStates:palette.length,rleRuns:runs.length,roundTrip:true,reachableRoutes:routes.length,proposalChangedBlocks:proposed.length,sourceRegionUnchanged:true,regionSha256:beforeHash,scope:'mockup-inspired facade proposal on measured functional layout; not applied',generatedAt:new Date().toISOString()};fs.writeFileSync(path.join(out,'validation.json'),JSON.stringify(validation,null,2));console.log(validation);
