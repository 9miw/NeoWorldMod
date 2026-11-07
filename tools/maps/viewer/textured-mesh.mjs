export function decodeCells(base64){
  const length=base64.length*3/4-(base64.endsWith('==')?2:base64.endsWith('=')?1:0);
  if(!Number.isInteger(length)||length%2)throw Error('Invalid voxel byte count');
  const cells=new Uint16Array(length/2),bytes=new Uint8Array(cells.buffer);let position=0;
  for(let start=0;start<base64.length;start+=65536){const part=atob(base64.slice(start,start+65536));for(let i=0;i<part.length;i++)bytes[position++]=part.charCodeAt(i);}
  if(new Uint8Array(new Uint16Array([1]).buffer)[0]!==1)for(let i=0;i<bytes.length;i+=2){const a=bytes[i];bytes[i]=bytes[i+1];bytes[i+1]=a;}
  return cells;
}

class Vertices{
  constructor(){this.data=new Uint8Array(16384);this.length=0;this.views();}
  views(){this.floats=new Float32Array(this.data.buffer);this.shorts=new Uint16Array(this.data.buffer);this.signed=new Int8Array(this.data.buffer);}
  vertex(p,n,uv,layer,tint){
    if(this.length+24>this.data.length){const next=new Uint8Array(this.data.length*2);next.set(this.data);this.data=next;this.views();}
    const o=this.length,f=o/4,s=o/2;this.floats[f]=p[0];this.floats[f+1]=p[1];this.floats[f+2]=p[2];
    this.shorts[s+6]=Math.round(Math.max(0,Math.min(1,uv[0]))*65535);this.shorts[s+7]=Math.round(Math.max(0,Math.min(1,uv[1]))*65535);this.shorts[s+8]=layer;
    for(let i=0;i<3;i++){this.signed[o+18+i]=Math.round(n[i]*127);this.data[o+21+i]=Math.round(tint[i]*255);}this.length+=24;
  }
  finish(){return this.data.slice(0,this.length);}
}
export function buildChunk(model,cells,states,cx,cz,cut){
  const solid=new Vertices(),transparent=new Vertices(),S=model.size,min=model.floor,max=Math.min(model.high-1,cut);
  const get=(x,y,z)=>x<0||z<0||x>=S||z>=S||y<min||y>max?0:cells[(y-model.low)*S*S+z*S+x];
  for(let y=min;y<=max;y++)for(let z=cz;z<Math.min(S,cz+16);z++)for(let x=cx;x<Math.min(S,cx+16);x++){
    const id=get(x,y,z),state=states[id];if(!id||!state?.faces.length)continue;
    // Fully enclosed blocks cannot contribute visible faces, even for complex models.
    if([[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]].every(d=>states[get(x+d[0],y+d[1],z+d[2])]?.solid))continue;
    const output=state.transparent?transparent:solid;
    for(const face of state.faces){
      if(face.cull){const [dx,dy,dz]=face.cull,neighbor=states[get(x+dx,y+dy,z+dz)];if(neighbor?.solid||neighbor&&neighbor.group===state.group&&(state.transparent||state.fluid||state.group.endsWith('_leaves')))continue;}
      for(const index of [0,1,2,0,2,3]){
        const p=face.positions[index],normal=face.shade?face.normal:[0,1,0];
        const py=state.fluid&&states[get(x,y+1,z)]?.group===state.group?p[1]/state.fluidHeight:p[1];
        output.vertex([x+p[0],y+py,z+p[2]],normal,face.uv[index],face.texture,face.tint);
      }
    }
  }
  return {solid:solid.finish(),transparent:transparent.finish(),center:[cx+8,(min+max)/2,cz+8]};
}

export async function loadTextureArray(gl,ids){
  if(Math.ceil(ids.length/16)>gl.getParameter(gl.MAX_ARRAY_TEXTURE_LAYERS))throw Error('อุปกรณ์นี้รองรับจำนวน Texture ไม่พอ');
  const texture=gl.createTexture(),size=64,canvas=document.createElement('canvas');canvas.width=canvas.height=size;
  const context=canvas.getContext('2d');context.imageSmoothingEnabled=false;
  gl.bindTexture(gl.TEXTURE_2D_ARRAY,texture);gl.texStorage3D(gl.TEXTURE_2D_ARRAY,9,gl.RGBA8,size*4,size*4,Math.max(1,Math.ceil(ids.length/16)));
  gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_MIN_FILTER,gl.NEAREST_MIPMAP_LINEAR);
  gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_MAX_LEVEL,6);
  try{
    for(let start=0;start<ids.length;start+=8){
      const bitmaps=await Promise.all(ids.slice(start,start+8).map(async id=>{if(id==='__missing__')return null;const response=await fetch('/api/texture?id='+encodeURIComponent(id));if(!response.ok)throw Error('โหลด Texture ไม่สำเร็จ: '+id);return createImageBitmap(await response.blob());}));
      for(let j=0;j<bitmaps.length;j++){
        context.clearRect(0,0,size,size);const bitmap=bitmaps[j];
        if(bitmap){context.drawImage(bitmap,0,0,bitmap.width,Math.min(bitmap.width,bitmap.height),0,0,size,size);bitmap.close();}
        else{for(let y=0;y<8;y++)for(let x=0;x<8;x++){context.fillStyle=(x+y)%2?'#ff00ff':'#181818';context.fillRect(x*8,y*8,8,8);}}
        const index=start+j;gl.bindTexture(gl.TEXTURE_2D_ARRAY,texture);gl.texSubImage3D(gl.TEXTURE_2D_ARRAY,0,(index%4)*size,Math.floor((index%16)/4)*size,Math.floor(index/16),size,size,1,gl.RGBA,gl.UNSIGNED_BYTE,context.getImageData(0,0,size,size).data);
      }
    }
    gl.bindTexture(gl.TEXTURE_2D_ARRAY,texture);gl.generateMipmap(gl.TEXTURE_2D_ARRAY);return texture;
  }catch(error){gl.deleteTexture(texture);throw error;}
}
