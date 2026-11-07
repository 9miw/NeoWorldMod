const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const zlib=require('node:zlib');
const os=require('node:os');
const {createLibrary}=require('./map-library.cjs');
const {GameAssets}=require('./game-assets.cjs');
const {makeBlockModels}=require('./block-models.cjs');
const ROOT=path.resolve(__dirname,'../../..');
const localIPv4=()=>Object.values(os.networkInterfaces()).flat().filter(a=>a.family==='IPv4'&&!a.internal).map(a=>a.address);
function createViewerServer(options={}){
  const library=createLibrary(options.mapsRoot||path.join(ROOT,'maps'));
  const addresses=new Set(['localhost','127.0.0.1',...localIPv4()]);
  let assets,models;
  function resources(){if(!assets){assets=new GameAssets(options.clientJar);models=makeBlockModels(assets);}return models;}
  const server=http.createServer((req,res)=>{
    function json(status,value){
      const raw=Buffer.from(JSON.stringify(value));res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
      if(/\bgzip\b/.test(req.headers['accept-encoding']||'')){res.setHeader('Content-Encoding','gzip');res.setHeader('Vary','Accept-Encoding');res.writeHead(status);res.end(zlib.gzipSync(raw));}else{res.writeHead(status);res.end(raw);}
    }
    try{
      const host=req.headers.host,port=server.address()?.port;
      if(![...addresses].some(address=>host===`${address}:${port}`))return json(403,{error:'Unknown host'});
      if(req.headers.origin&&req.headers.origin!==`http://${host}`)return json(403,{error:'Origin refused'});
      if(req.method!=='GET'){res.setHeader('Allow','GET');return json(405,{error:'เครื่องมือนี้ใช้ดูแมพเท่านั้น'});}
      const url=new URL(req.url,`http://${host}`);
      if(url.pathname==='/api/maps')return json(200,{maps:library.list()});
      if(url.pathname==='/api/map-info')return json(200,library.info(url.searchParams.get('id')));
      if(url.pathname==='/api/map'){
        const options={},params=url.searchParams;
        if(params.has('x')||params.has('z')){if(!params.has('x')||!params.has('z')||!params.get('x')||!params.get('z'))return json(400,{error:'Invalid center'});options.center=[Number(params.get('x')),Number(params.get('z'))];}
        if(params.has('size')){options.size=Number(params.get('size'));if(![320,384,512].includes(options.size))return json(400,{error:'Invalid window size'});}
        const map=library.load(params.get('id'),options);map.resources=resources().compile(map.definitions);return json(200,map);
      }
      if(url.pathname==='/api/texture'){
        resources();const bytes=assets.texture(url.searchParams.get('id')||'');if(!bytes)return json(404,{error:'Texture not found'});
        res.setHeader('Content-Type','image/png');res.setHeader('Cache-Control','public, max-age=86400');res.setHeader('X-Content-Type-Options','nosniff');return res.end(bytes);
      }
      const files={'/':'index.html','/app.js':'app.js','/gestures.mjs':'gestures.mjs','/textured-mesh.mjs':'textured-mesh.mjs','/view-chunks.mjs':'view-chunks.mjs','/world-navigation.mjs':'world-navigation.mjs','/style.css':'style.css',
        '/reference-plan':'reference-plan/index.html','/reference-plan/':'reference-plan/index.html','/reference-plan/app.js':'reference-plan/app.js','/reference-plan/style.css':'reference-plan/style.css','/reference-plan/plan.json':'reference-plan/plan.json','/reference-plan/old-zones.json':'reference-plan/old-zones.json','/reference-plan/reference.jpg':'reference-plan/reference.jpg'},file=files[url.pathname];
      if(!file)return json(404,{error:'Not found'});
      res.setHeader('Content-Type',file.endsWith('.html')?'text/html; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':file.endsWith('.json')?'application/json; charset=utf-8':file.endsWith('.jpg')?'image/jpeg':'text/javascript; charset=utf-8');
      res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
      res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'");
      res.end(fs.readFileSync(path.join(__dirname,file)));
    }catch(error){json(error.status||500,{error:error.message});}
  });
  return server;
}
module.exports={createViewerServer};
if(require.main===module){
  const server=createViewerServer();let port=Number(process.env.MAP_VIEWER_PORT||8765);
  server.on('error',error=>{if(error.code==='EADDRINUSE'&&port<8775)server.listen(++port,'0.0.0.0');else{console.error(error);process.exitCode=1;}});
  server.on('listening',()=>console.log(`NeoWorld Map Review: http://127.0.0.1:${server.address().port}\n${localIPv4().map(ip=>`LAN: http://${ip}:${server.address().port}`).join('\n')}\nRead-only map library. Stop with Ctrl+C.`));server.listen(port,'0.0.0.0');
}
