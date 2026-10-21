import test from 'node:test';
import assert from 'node:assert/strict';
test('system appearance follows OS changes, manual overrides persist, system mode restores automatic updates',async()=>{
 let listener:(e:{matches:boolean})=>void=()=>{};
 const classes=new Set<string>(),storage=new Map<string,string>();
 const media={matches:false,addEventListener:(_type:string,fn:typeof listener)=>{listener=fn;}};
 Object.assign(globalThis,{window:{matchMedia:()=>media},localStorage:{getItem:(k:string)=>storage.get(k)??null,setItem:(k:string,v:string)=>storage.set(k,v)},document:{createElement:()=>({}),documentElement:{classList:{toggle:(c:string,enabled:boolean)=>enabled?classes.add(c):classes.delete(c)},dataset:{}},querySelector:()=>({setAttribute:()=>{}})}});
 const theme=await import('../src/lib/theme.ts');
 assert.equal(theme.themeMode.value,'system');assert.equal(classes.has('dark'),false);
 listener({matches:true});assert.equal(classes.has('dark'),true);
 theme.setTheme('light');listener({matches:true});assert.equal(classes.has('dark'),false);assert.equal(storage.get('smartdiy-theme'),'light');
 theme.setTheme('system');assert.equal(classes.has('dark'),true);
 listener({matches:false});assert.equal(classes.has('dark'),false);
});
