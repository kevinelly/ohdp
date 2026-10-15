import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
const source=fs.readFileSync(new URL('../src/components/radiology/personalDoseSupport.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText
const {allLibraryPages,doseDraftKey}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
test('library loads beyond 100 entries even when the server limits each page',async()=>{
  const calls=[];const rows=await allLibraryPages(async page=>{calls.push(page);return {content:Array.from({length:page===5?3:20},(_,i)=>page*20+i),totalPages:6,last:page===5}})
  assert.equal(rows.length,103);assert.equal(rows[102],102);assert.deepEqual(calls,[0,1,2,3,4,5])
})
test('library failures are not mistaken for a complete first page',async()=>{
  await assert.rejects(allLibraryPages(async page=>{if(page===1)throw new Error('network');return {content:[1],totalPages:2}}),/network/)
})
test('persistent drafts are separated by account and case, without token-dependent keys',()=>{
  const storage=new Map();storage.set(doseDraftKey('alice'),'{"receipt":"DRAFT-1"}')
  assert.equal(storage.get(doseDraftKey('alice')),'{"receipt":"DRAFT-1"}')
  assert.notEqual(doseDraftKey('alice'),doseDraftKey('bob'));assert.notEqual(doseDraftKey('alice',1),doseDraftKey('alice',2))
})
