'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../assets/app.js'),'utf8').split('function show(page)')[0];
function harness(){
 let now=0,next=0;const timers=new Map();
 const context={AbortController,document:{},setTimeout:(fn,ms)=>{timers.set(++next,{fn,at:now+ms});return next;},clearTimeout:id=>timers.delete(id)};
 vm.createContext(context);vm.runInContext(source,context);
 return {context,timers,advance(ms){now+=ms;for(const [id,t] of timers)if(t.at<=now){timers.delete(id);t.fn();}},api:path=>vm.runInContext(`api(${JSON.stringify(path)},{})`,context)};
}
for(const path of ['/api/login','/api/credentials'])test(path+' accepts a response after the previous 15-second limit',async()=>{
 const h=harness();let respond,signal;
 h.context.fetch=(_,options)=>{signal=options.signal;return new Promise(resolve=>{respond=resolve;});};
 const pending=h.api(path);h.advance(18108);assert.equal(signal.aborted,false);
 respond({ok:true,json:async()=>({ok:true})});assert.equal((await pending).ok,true);assert.equal(h.timers.size,0);
});
test('credential timeout explains possible persistence; normal requests retain 15 seconds',async()=>{
 for(const [path,limit,pattern] of [['/api/credentials',120000,/podem já ter sido guardados/],['/api/state',15000,/Verifica a ligação/]]){
  const h=harness();h.context.fetch=(_,options)=>new Promise((_,reject)=>options.signal.addEventListener('abort',()=>reject(new Error('aborted'))));
  const pending=h.api(path);const check=assert.rejects(pending,pattern);h.advance(limit);await check;assert.equal(h.timers.size,0);
 }
});
