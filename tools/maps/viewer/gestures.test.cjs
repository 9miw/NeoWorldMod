const {test}=require('node:test');
const assert=require('node:assert/strict');
test('one finger rotates; two fingers pan and pinch without jumps across transitions',async()=>{
  const {createGestures}=await import('./gestures.mjs'),g=createGestures();
  g.start(1,100,100);assert.deepEqual(g.move(1,120,110),{kind:'rotate',dx:20,dy:10});
  g.start(2,220,110);
  let result=g.move(1,110,110);assert.equal(result.kind,'transform');assert.equal(result.dx,-5);assert.equal(result.dy,0);assert.ok(result.zoom<1);
  result=g.move(2,230,110);assert.equal(result.kind,'transform');assert.equal(result.dx,5);assert.ok(result.zoom<1);
  // A translated pair pans by its centroid and does not change the total zoom.
  const pair=createGestures();pair.start(1,0,0);pair.start(2,100,0);const a=pair.move(1,10,20),b=pair.move(2,110,20);
  assert.equal(a.dx+b.dx,10);assert.equal(a.dy+b.dy,20);assert.ok(Math.abs(a.zoom*b.zoom-1)<1e-12);
  g.end(1);assert.deepEqual(g.move(2,235,112),{kind:'rotate',dx:5,dy:2});
  g.end(2);assert.equal(g.move(2,500,500),null);
  g.start(1,10,10);g.start(2,10,10);assert.equal(g.move(2,11,10).zoom,1);
  g.start(3,15,15);assert.equal(g.move(3,16,16),null);g.end(3);g.clear();assert.equal(g.move(1,20,20),null);
});
