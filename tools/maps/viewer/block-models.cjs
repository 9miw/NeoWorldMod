const directions={down:[0,-1,0],up:[0,1,0],north:[0,0,-1],south:[0,0,1],west:[-1,0,0],east:[1,0,0]};
const invisible=new Set(['air','cave_air','void_air','barrier','light','structure_void']);
function matches(condition,properties){
  if(!condition)return true;
  return Object.entries(condition).every(([key,value])=>key==='OR'?value.some(c=>matches(c,properties)):key==='AND'?value.every(c=>matches(c,properties)):String(value).split('|').includes(properties[key]));
}
function turn(p,axis,degrees,origin=[.5,.5,.5],rescale=false){
  if(!degrees)return [...p];const a=degrees*Math.PI/180,c=Math.cos(a),s=Math.sin(a),v=p.map((x,i)=>x-origin[i]);
  const indexes=axis==='x'?[1,2]:axis==='y'?[2,0]:[0,1],i=indexes[0],j=indexes[1],first=v[i],second=v[j];v[i]=first*c-second*s;v[j]=first*s+second*c;
  if(rescale){const scale=1/Math.cos(a);v[i]*=scale;v[j]*=scale;}return v.map((x,k)=>x+origin[k]);
}
function variantTurn(p,variant,vector=false){const origin=vector?[0,0,0]:[.5,.5,.5];return turn(turn(p,'x',-(variant.x||0),origin),'y',-(variant.y||0),origin);}
function corners(name,from,to){
  const [x0,y0,z0]=from,[x1,y1,z1]=to;
  return {down:[[x0,y0,z1],[x0,y0,z0],[x1,y0,z0],[x1,y0,z1]],up:[[x0,y1,z0],[x0,y1,z1],[x1,y1,z1],[x1,y1,z0]],north:[[x1,y1,z0],[x1,y0,z0],[x0,y0,z0],[x0,y1,z0]],south:[[x0,y1,z1],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1]],west:[[x0,y1,z0],[x0,y0,z0],[x0,y0,z1],[x0,y1,z1]],east:[[x1,y1,z1],[x1,y0,z1],[x1,y0,z0],[x1,y1,z0]]}[name];
}
function defaultUV(name,a,b){return {down:[a[0],16-b[2],b[0],16-a[2]],up:[a[0],a[2],b[0],b[2]],north:[16-b[0],16-b[1],16-a[0],16-a[1]],south:[a[0],16-b[1],b[0],16-a[1]],west:[a[2],16-b[1],b[2],16-a[1]],east:[16-b[2],16-b[1],16-a[2],16-a[1]]}[name];}
function specialModel(name,properties){
  const faceBox=(from,to,texture,u=0,v=0,sheet=[64,64])=>{
    const [w,h,d]=to.map((n,i)=>n-from[i]),uv={west:[u,v+d,u+d,v+d+h],north:[u+d,v+d,u+d+w,v+d+h],east:[u+d+w,v+d,u+d+w+d,v+d+h],south:[u+d+w+d,v+d,u+d+w+d+w,v+d+h],up:[u+d,v,u+d+w,v+d],down:[u+d+w,v,u+d+w+w,v+d]};
    return {from,to,faces:Object.fromEntries(Object.entries(uv).map(([side,rect])=>[side,{texture,uv:rect.map((n,i)=>n/sheet[i%2]*16)}]))};
  };
  const facing={north:0,east:90,south:180,west:270}[properties.facing]||0;
  if(['chest','trapped_chest','ender_chest'].includes(name))return {y:facing,inline:{elements:[faceBox([1,0,1],[15,10,15],'minecraft:entity/chest/'+(name==='ender_chest'?'ender':name==='trapped_chest'?'trapped':'normal'),0,19),faceBox([1,9,1],[15,14,15],'minecraft:entity/chest/'+(name==='ender_chest'?'ender':name==='trapped_chest'?'trapped':'normal')),faceBox([7,7,0],[9,11,1],'minecraft:entity/chest/'+(name==='ender_chest'?'ender':name==='trapped_chest'?'trapped':'normal'))]}};
  if(name.endsWith('_wall_sign')||name.endsWith('_sign')){
    const wood=name.replace(/_wall_sign$|_sign$/,''),wall=name.endsWith('_wall_sign');
    return {y:wall?facing:Number(properties.rotation||0)*22.5,inline:{elements:[faceBox(wall?[0,4,14]:[0,8,7],wall?[16,12,16]:[16,16,9],'minecraft:entity/signs/'+wood,0,0,[64,32]),...(!wall?[faceBox([7,0,7],[9,8,9],'minecraft:entity/signs/'+wood,0,14,[64,32])]:[])]}};
  }
  if(name.endsWith('_bed')){
    const id='minecraft:entity/bed/'+name.slice(0,-4),head=properties.part==='head';
    return {y:facing,inline:{elements:[faceBox([0,3,0],[16,9,16],id,0,head?0:22),faceBox([0,0,head?0:14],[2,3,head?2:16],id,50,0),faceBox([14,0,head?0:14],[16,3,head?2:16],id,50,0)]}};
  }
  if(name.endsWith('shulker_box')){
    const id='minecraft:entity/shulker/shulker'+(name==='shulker_box'?'':'_'+name.replace('_shulker_box',''));
    return {inline:{elements:[faceBox([0,0,0],[16,8,16],id,0,28),faceBox([0,8,0],[16,16,16],id)]}};
  }
  return null;
}
function makeBlockModels(assets){
  const cache=new Map();
  function resolve(id,depth=0){
    if(depth>24)throw Error('Model parent cycle');if(cache.has(id))return cache.get(id);
    const file=assets.resource('models',id),own=file?assets.json(file+'.json'):null;
    if(!own)return null;const parent=own.parent&&!own.parent.startsWith('builtin/')?resolve(own.parent,depth+1):null;
    const result={...parent,...own,textures:{...parent?.textures,...own.textures},elements:own.elements||parent?.elements||[]};cache.set(id,result);return result;
  }
  function compile(definitions){
    const textures=[],textureIndex=new Map(),fallbacks=new Set(),missingTextures=new Set();
    const texture=id=>{if(!textureIndex.has(id)){textureIndex.set(id,textures.length);textures.push(id);}return textureIndex.get(id);};
    const states=definitions.map(definition=>{
      const name=definition.Name.split(':')[1],properties=definition.Properties||{};
      if(definition.Name.startsWith('minecraft:')&&invisible.has(name))return {faces:[],solid:false,group:definition.Name};
      const statePath=assets.resource('blockstates',definition.Name),state=statePath?assets.json(statePath+'.json'):null;
      let applications=[];
      if(state?.variants){const match=Object.entries(state.variants).find(([key])=>!key||key.split(',').every(pair=>{const [k,v]=pair.split('=');return properties[k]===v;}));if(match)applications.push(Array.isArray(match[1])?match[1][0]:match[1]);}
      for(const part of state?.multipart||[])if(matches(part.when,properties))applications.push(Array.isArray(part.apply)?part.apply[0]:part.apply);
      const special=specialModel(name,properties);if(special){applications=[special];fallbacks.add(definition.Name);}
      const faces=[];let fullCube=false;
      const fluid=name==='water'||name==='lava';
      if(fluid)applications=[{inline:{textures:{all:'minecraft:block/'+name+'_still'},elements:[{from:[0,0,0],to:[16,properties.level==='0'?16*8/9:Math.max(2,16-(Number(properties.level)%8)*2),16],faces:Object.fromEntries(Object.keys(directions).map(d=>[d,{texture:'#all',cullface:d,tintindex:name==='water'?0:undefined}]))}]}}];
      for(const application of applications){
        const m=application.inline||resolve(application.model);if(!m?.elements?.length)continue;
        for(const element of m.elements){
          const from=element.from.map(v=>v/16),to=element.to.map(v=>v/16);
          if(!element.rotation&&from.every(v=>v===0)&&to.every(v=>v===1)&&Object.keys(element.faces||{}).length===6)fullCube=true;
          for(const [side,face]of Object.entries(element.faces||{})){
            let id=face.texture;const seen=new Set();while(id?.startsWith('#')&&!seen.has(id)){seen.add(id);id=m.textures[id.slice(1)];}
            if(!id||id.startsWith('#'))id='__missing__';if(!id.includes(':'))id='minecraft:'+id;
            if(!assets.texture(id)){missingTextures.add(id);id='__missing__';}
            let positions=corners(side,from,to);
            if(element.rotation){const r=element.rotation;positions=positions.map(p=>turn(p,r.axis,r.angle,r.origin.map(v=>v/16),r.rescale));}
            positions=positions.map(p=>variantTurn(p,application));
            const a=positions[0],b=positions[1],c=positions[2],u=b.map((v,i)=>v-a[i]),v=c.map((n,i)=>n-a[i]);
            let normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]],length=Math.hypot(...normal)||1;
            normal=normal.map(n=>n/length);const expected=variantTurn(directions[side],application,true);if(normal.reduce((sum,n,i)=>sum+n*expected[i],0)<0)normal=normal.map(n=>-n);
            const uv=face.uv||defaultUV(side,element.from,element.to),base=[[uv[0],uv[1]],[uv[0],uv[3]],[uv[2],uv[3]],[uv[2],uv[1]]];
            let shift=(face.rotation||0)/90;
            if(application.uvlock){
              const old=corners(side,[0,0,0],[1,1,1]),rotated=old.map(p=>variantTurn(p,application)),newSide=Object.keys(directions).find(d=>directions[d].every((n,i)=>Math.abs(n-expected[i])<.01));
              if(newSide){const canonical=corners(newSide,[0,0,0],[1,1,1]);const index=canonical.findIndex(p=>p.every((n,i)=>Math.abs(n-rotated[0][i])<.01));if(index>=0)shift+=index;}
            }
            const tinted=face.tintindex!==undefined;let tint=[1,1,1];
            if(tinted){const colour=['cherry_leaves','azalea_leaves','flowering_azalea_leaves'].includes(name)?'ffffff':fluid?'3f76e4':name.includes('spruce')?'619961':name.includes('birch')?'80a755':name.includes('leaves')||name==='vine'?'77ab2f':'91bd59';tint=[0,2,4].map(i=>parseInt(colour.slice(i,i+2),16)/255);}
            faces.push({positions,uv:base.map((_,i)=>base[(i+shift)%4].map(n=>n/16)),normal,texture:texture(id),tint,cull:face.cullface?variantTurn(directions[face.cullface],application,true).map(Math.round):null,shade:element.shade!==false});
          }
        }
      }
      if(!faces.length){
        fallbacks.add(definition.Name);
        const m=resolve('minecraft:block/'+name),particle=m?.textures?.particle;
        let id=particle&&!particle.startsWith('#')?particle:'minecraft:block/'+name;if(!assets.texture(id))id='__missing__';
        for(const side of Object.keys(directions))faces.push({positions:corners(side,[0,0,0],[1,1,1]),uv:[[0,0],[0,1],[1,1],[1,0]],normal:directions[side],texture:texture(id),tint:[1,1,1],cull:directions[side],shade:true});
        fullCube=true;
      }
      const transparent=fluid&&name==='water'||name.includes('stained_glass')||name==='glass'||name==='glass_pane'||name==='ice'||name==='nether_portal';
      const cutout=/leaves|grass|fern|flower|coral|vine|sapling|roots|fungus|mushroom|wheat|fire|lily_pad/.test(name);
      return {faces,solid:fullCube&&!transparent&&!cutout,transparent,cutout,fluid,fluidHeight:fluid?Math.max(...faces.flatMap(f=>f.positions.map(p=>p[1]))):1,group:definition.Name};
    });
    return {states,textures,fallbacks:[...fallbacks],missingTextures:[...missingTextures],version:'1.21.1',available:true};
  }
  return {compile,resolve};
}
module.exports={makeBlockModels,matches,turn,variantTurn,corners};
