import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import {parse} from '@vue/compiler-sfc'
import {ref,reactive,computed} from 'vue'
const dir=new URL('../src/components/radiology/',import.meta.url)
const transpile=s=>ts.transpileModule(s,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText
const support=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('radiologyDraftSupport.ts',dir),'utf8'))).toString('base64'))
const doseSupport=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('personalDoseSupport.ts',dir),'utf8'))).toString('base64'))
function component(name,expose,{get=async()=>({data:{content:[],totalElements:0,last:true}}),confirm=async()=>{},props={}}={}){
 const calls=[],emits=[],warnings=[]
 const api={interceptors:{request:{use(){}}},get:async(...args)=>{calls.push(args[0]);return get(...args)}}
 const bindings={withDefaults:(props,defaults)=>({...defaults,...props}),ref,reactive,computed,watch(){},onMounted(){},onBeforeUnmount(){},defineProps:()=>({token:'test',username:'test',modality:'DR',...props}),defineEmits:()=>((...v)=>emits.push(v)),defineExpose(){},axios:{create:()=>api},ElMessage:{warning:m=>warnings.push(m),error:m=>warnings.push(m),success(){}},ElMessageBox:{confirm},...support,...doseSupport}
 const source=parse(fs.readFileSync(new URL(name,dir),'utf8')).descriptor.scriptSetup.content
 const ast=ts.createSourceFile('component.ts',source,ts.ScriptTarget.Latest,true)
 let stripped=source
 for(const node of [...ast.statements].reverse())if(ts.isImportDeclaration(node))stripped=stripped.slice(0,node.pos)+stripped.slice(node.end)
 stripped=stripped.replaceAll('import.meta.env.VITE_API_BASE_URL',"''")
 const result=new Function(...Object.keys(bindings),transpile(stripped)+`\nreturn {${expose}}`)(...Object.values(bindings))
 return {...result,calls,emits,warnings}
}
function radiology(confirm){
 const c=component('RadiologyWorkbench.vue','selectedCase,measurementValues,conditionValues,profileDraft,pointDrafts,syncCaseState,confirmLeave,selectQueryCase,openQueryResult,queryVisible',{confirm})
 c.selectedCase.value={id:1,modality:'DR',status:'DATA_ENTERED',measurements:[{itemCode:'ETD',fieldCode:'ETD1-1',fieldRole:'RAW',valueText:'10.12'}],profile:{},itemConditions:{},protectionPoints:[]}
 c.syncCaseState();return c
}
test('cancelled project and cross-module navigation preserve unsaved readings without requests',async()=>{
 const c=radiology(async()=>{throw 'cancel'});c.measurementValues['ETD::ETD1-1']='11.12';c.queryVisible.value=true
 await c.selectQueryCase({id:2,modality:'DR'});await c.selectQueryCase({id:3,modality:'CT'});await c.openQueryResult({id:4,modality:'DOSE'})
 assert.equal(c.selectedCase.value.id,1);assert.equal(c.measurementValues['ETD::ETD1-1'],'11.12');assert.equal(c.queryVisible.value,true);assert.deepEqual(c.calls,[]);assert.deepEqual(c.emits,[])
})
test('confirmed discard can open personal dose; clean values do not prompt',async()=>{
 let prompts=0;const c=radiology(async()=>{prompts++})
 assert.equal(await c.confirmLeave(),true);assert.equal(prompts,0)
 c.measurementValues['ETD::ETD1-1']='12.34';await c.openQueryResult({id:4,modality:'DOSE'})
 assert.equal(prompts,1);assert.deepEqual(c.emits,[['open-dose',4]])
})
test('conditions, report profile, and protection point edits all block cancelled navigation',async()=>{
 for(const edit of [c=>c.conditionValues.SID='100',c=>c.profileDraft.overallConclusion='changed',c=>c.pointDrafts.value.push({name:'new'})]){
 const c=radiology(async()=>{throw 'close'});edit(c);assert.equal(await c.confirmLeave(),false)
 }
})
test('refresh retries failed libraries and preserves current edited project',async()=>{
 let failed=true
 const c=component('PersonalDoseWorkbench.vue','refresh,standards,devices,form',{get:async path=>{
 if(path.includes('/standards')||path.includes('/devices')){if(failed)throw new Error('network');return {data:{content:[{id:9}],last:true}}}
 return {data:{content:[],totalElements:0}}
 }})
 c.form.value.receipt='UNSAVED';await c.refresh();assert.equal(c.warnings.length,2);assert.equal(c.standards.value.length,0)
 failed=false;await c.refresh();assert.equal(c.standards.value[0].id,9);assert.equal(c.devices.value[0].id,9);assert.equal(c.form.value.receipt,'UNSAVED')
})

