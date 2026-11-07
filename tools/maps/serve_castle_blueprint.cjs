const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root07=path.resolve(__dirname,'../../docs/maps/zone07-blueprint');
const root03=path.resolve(__dirname,'../../docs/maps/zone03-fountain-blueprint');

const files07={'/':'index.html','/index.html':'index.html','/blocks.json':'blocks.json','/blocks.json.gz':'blocks.json.gz','/materials.csv':'materials.csv','/BLUEPRINT.md':'BLUEPRINT.md','/validation.json':'validation.json','/preview.png':'preview.png','/layout.html':'layout.html','/layout.json':'layout.json','/layout-top.svg':'layout-top.svg','/layout-massing.svg':'layout-massing.svg'};
Object.assign(files07,{'/bastions.html':'bastions.html','/bastions.json':'bastions.json','/bastions-top.svg':'bastions-top.svg','/bastions-front.svg':'bastions-front.svg'});
Object.assign(files07,{'/bastions-3d.html':'bastions-3d.html','/bastions-3d.js':'bastions-3d.js'});
Object.assign(files07,{'/front-defense-3d.html':'front-defense-3d.html','/front-defense.json':'front-defense.json'});
Object.assign(files07,{'/castle-complete-3d.html':'castle-complete-3d.html','/castle-complete-3d.js':'castle-complete-3d.js','/castle-complete.json':'castle-complete.json'});

const files03={
  '/fountain':'fountain-blueprint.html',
  '/fountain/':'fountain-blueprint.html',
  '/fountain-blueprint.html':'fountain-blueprint.html',
  '/fountain-top.svg':'fountain-top.svg',
  '/fountain-front.svg':'fountain-front.svg',
  '/fountain-voxels.json':'fountain-voxels.json',
  '/fountain-blueprint-preview.jpg':'fountain-blueprint-preview.jpg'
};

http.createServer((req,res)=>{
  const urlPath=req.url?.split('?')[0]||'/';
  let targetFile=null;
  if(files03[urlPath]){
    targetFile=path.join(root03,files03[urlPath]);
  }else if(files07[urlPath]){
    targetFile=path.join(root07,files07[urlPath]);
  }

  if(req.method!=='GET'||!targetFile||!fs.existsSync(targetFile)){
    res.writeHead(404);return res.end('Not found');
  }

  res.setHeader('Cache-Control','no-store');
  res.setHeader('X-Content-Type-Options','nosniff');
  const ext=path.extname(targetFile).toLowerCase();
  const mime=ext==='.html'?'text/html; charset=utf-8':ext==='.js'?'text/javascript; charset=utf-8':ext==='.json'?'application/json':ext==='.svg'?'image/svg+xml':ext==='.png'?'image/png':ext==='.jpg'||ext==='.jpeg'?'image/jpeg':ext==='.gz'?'application/gzip':'text/plain; charset=utf-8';
  res.setHeader('Content-Type',mime);
  res.end(fs.readFileSync(targetFile));
}).listen(8766,'0.0.0.0',()=>console.log('Blueprint: http://localhost:8766/fountain'));
