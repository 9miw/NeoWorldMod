// Typed NBT round-trip for editing existing chunks while retaining unrelated tags.
const zlib=require('node:zlib');
function decode(buffer){
  let pos=0;const take=n=>{if(n<0||pos+n>buffer.length)throw Error('Truncated NBT');const o=pos;pos+=n;return o;};
  const text=()=>{const n=buffer.readUInt16BE(take(2));return buffer.toString('utf8',take(n),pos);};
  function payload(type,depth=0){
    if(depth>100)throw Error('NBT too deep');
    switch(type){
      case 1:return buffer.readInt8(take(1));case 2:return buffer.readInt16BE(take(2));case 3:return buffer.readInt32BE(take(4));case 4:return buffer.readBigInt64BE(take(8));case 5:return buffer.readFloatBE(take(4));case 6:return buffer.readDoubleBE(take(8));
      case 8:return text();
      case 10:{const value={};while(true){const t=buffer.readUInt8(take(1));if(!t)break;const name=text();value[name]={type:t,value:payload(t,depth+1)};}return value;}
      case 7:case 9:case 11:case 12:{const child=type===9?buffer.readUInt8(take(1)):null,n=buffer.readInt32BE(take(4));if(n<0||n>16000000)throw Error('Invalid NBT array');if(type===7)return buffer.subarray(take(n),pos);if(type===9)return {type:child,values:Array.from({length:n},()=>payload(child,depth+1))};return Array.from({length:n},()=>type===11?buffer.readInt32BE(take(4)):buffer.readBigInt64BE(take(8)));}
      default:throw Error('Unsupported NBT '+type);
    }
  }
  const type=buffer.readUInt8(take(1));if(type!==10)throw Error('Expected compound');const name=text();return {type,name,value:payload(type)};
}
function encode(root){
  const str=s=>{const b=Buffer.from(s),n=Buffer.alloc(2);n.writeUInt16BE(b.length);return Buffer.concat([n,b]);};
  const num=(size,fn,value)=>{const b=Buffer.alloc(size);b[fn](value);return b;};
  function payload(type,v){
    switch(type){
      case 1:return num(1,'writeInt8',v);case 2:return num(2,'writeInt16BE',v);case 3:return num(4,'writeInt32BE',v);case 4:return num(8,'writeBigInt64BE',BigInt.asIntN(64,v));case 5:return num(4,'writeFloatBE',v);case 6:return num(8,'writeDoubleBE',v);case 8:return str(v);
      case 10:return Buffer.concat([...Object.entries(v).map(([name,n])=>Buffer.concat([Buffer.from([n.type]),str(name),payload(n.type,n.value)])),Buffer.from([0])]);
      case 7:return Buffer.concat([num(4,'writeInt32BE',v.length),v]);
      case 9:return Buffer.concat([Buffer.from([v.type]),num(4,'writeInt32BE',v.values.length),...v.values.map(x=>payload(v.type,x))]);
      case 11:case 12:return Buffer.concat([num(4,'writeInt32BE',v.length),...v.map(x=>payload(type===11?3:4,x))]);
      default:throw Error('Unsupported NBT '+type);
    }
  }
  return Buffer.concat([Buffer.from([10]),str(root.name||''),payload(10,root.value)]);
}
function patchRegion(original,edit){
  const header=Buffer.from(original.subarray(0,8192)),bodies=[];let sector=2,changed=0;
  for(let slot=0;slot<1024;slot++){
    const loc=original.readUInt32BE(slot*4);if(!loc)continue;
    const start=(loc>>>8)*4096,length=original.readUInt32BE(start),compression=original[start+4];
    if(compression!==2)throw Error('Only zlib chunks supported');
    const old=original.subarray(start,start+(loc&255)*4096),tree=decode(zlib.inflateSync(original.subarray(start+5,start+4+length)));
    let body=old;
    if(edit(tree,slot)){
      const compressed=zlib.deflateSync(encode(tree));body=Buffer.alloc(Math.ceil((compressed.length+5)/4096)*4096);body.writeUInt32BE(compressed.length+1);body[4]=2;compressed.copy(body,5);changed++;header.writeUInt32BE(Math.floor(Date.now()/1000),4096+slot*4);
    }
    if(body.length/4096>255)throw Error('Chunk too large');header.writeUInt32BE((sector<<8)|(body.length/4096),slot*4);sector+=body.length/4096;bodies.push(body);
  }
  return {bytes:Buffer.concat([header,...bodies]),changed};
}
module.exports={decode,encode,patchRegion};
