const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const zlib=require('node:zlib');

class GameAssets{
  constructor(jar=process.env.MAP_VIEWER_CLIENT_JAR||path.join(os.homedir(),'.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')){
    this.bytes=fs.readFileSync(jar);this.entries=new Map();this.cache=new Map();
    const b=this.bytes;let end=b.length-22;while(end>=Math.max(0,b.length-65557)&&b.readUInt32LE(end)!==0x06054b50)end--;
    if(end<0||b.readUInt32LE(end)!==0x06054b50)throw Error('Invalid Minecraft client archive');
    const count=b.readUInt16LE(end+10);let offset=b.readUInt32LE(end+16);
    for(let i=0;i<count;i++){
      if(b.readUInt32LE(offset)!==0x02014b50)throw Error('Invalid ZIP directory');
      const length=b.readUInt16LE(offset+28),extra=b.readUInt16LE(offset+30),comment=b.readUInt16LE(offset+32),name=b.toString('utf8',offset+46,offset+46+length);
      this.entries.set(name,{method:b.readUInt16LE(offset+10),size:b.readUInt32LE(offset+20),offset:b.readUInt32LE(offset+42)});offset+=46+length+extra+comment;
    }
    if(this.json('version.json')?.id!=='1.21.1')throw Error('ต้องใช้ทรัพยากร Minecraft 1.21.1');
  }
  read(name){
    const entry=this.entries.get(name);if(!entry)return null;
    const b=this.bytes,start=entry.offset;if(b.readUInt32LE(start)!==0x04034b50)throw Error('Invalid ZIP entry');
    const from=start+30+b.readUInt16LE(start+26)+b.readUInt16LE(start+28),compressed=b.subarray(from,from+entry.size);
    if(entry.method===0)return compressed;
    if(entry.method===8)return zlib.inflateRawSync(compressed,{maxOutputLength:16*1024*1024});
    throw Error('Unsupported asset compression');
  }
  json(name){if(!this.cache.has(name)){const bytes=this.read(name);this.cache.set(name,bytes?JSON.parse(bytes.toString('utf8')):null);}return this.cache.get(name);}
  resource(kind,id){
    const [namespace,name]=id.includes(':')?id.split(':'):['minecraft',id];
    if(namespace!=='minecraft'||!name||name.split('/').some(p=>p==='..'||p==='')||!/^[a-z0-9_./-]+$/.test(name))return null;
    return `assets/minecraft/${kind}/${name}`;
  }
  texture(id){const name=this.resource('textures',id);return name?this.read(name+'.png'):null;}
}
module.exports={GameAssets};
