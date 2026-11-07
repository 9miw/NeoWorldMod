module.exports=function(t){
  t.pave(32,99,74,143);t.house(34,100,13,13);t.house(54,100,15,13,{roof:'spruce_planks'});
  const colours=['red','blue','yellow','cyan'];
  [[36,119],[57,119],[36,133],[57,133]].forEach(([x,z],i)=>{
    for(const dx of [0,8])for(const dz of [0,6])t.fill(x+dx,97,z+dz,x+dx,101,z+dz,'oak_log',{axis:'y'});
    for(let dx=-1;dx<=9;dx++)t.fill(x+dx,102,z-1,x+dx,102,z+7,dx%2?'white_wool':colours[i]+'_wool');
    t.fill(x+1,97,z+4,x+7,97,z+4,'spruce_planks');t.put(x+1,98,z+5,'barrel',{facing:'up',open:'false'});
  });
  for(const [x,z]of [[33,116],[72,116],[33,144],[72,144]])t.lamp(x,z);
  t.planter(48,140);t.tree(27,124);
};
