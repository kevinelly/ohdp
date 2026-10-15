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

test('stage navigation does not issue requests or discard measurements',()=>{
 const c=component('RadiologyWorkbench.vue','goStage,viewStage,measurementValues,conditionValues')
 c.measurementValues['TVD::TVD1-1']='80.25';c.conditionValues.TVD='SID=100cm'
 for(const stage of [2,3,4,5,1]){c.goStage(stage);assert.equal(c.viewStage.value,stage)}
 assert.equal(c.measurementValues['TVD::TVD1-1'],'80.25');assert.equal(c.conditionValues.TVD,'SID=100cm');assert.deepEqual(c.calls,[])
})

test('personal dose stage navigation retains people, readings and conclusion',()=>{
 const c=component('PersonalDoseWorkbench.vue','goStage,viewStage,form,tab')
 c.form.value.personnel=[{name:'测试甲'}];c.form.value.readings=[{values:['1.00']}];c.form.value.conclusion='人工评价'
 for(const stage of [3,4,5,2])c.goStage(stage)
 assert.equal(c.viewStage.value,2);assert.equal(c.tab.value,'base');assert.equal(c.form.value.conclusion,'人工评价');assert.equal(c.form.value.readings[0].values[0],'1.00');assert.equal(c.form.value.personnel[0].name,'测试甲');assert.deepEqual(c.calls,[])
})

test('enabling dynamic measurement rows preserves old readings and conditions',()=>{
 const fields=[{code:'TVD1-0',label:'预设值'},{code:'TVD1-1',label:'读数1'},{code:'TVD-K1',label:'校准因子'}]
 const values=reactive({'TVD::TVD1-0':'60','TVD::TVD1-1':'60.10','TVD::TVD2-1':'80.20','TVD::TVD3-1':'100.30'}),conditions=reactive({'TVD.1':'条件一'})
 const c=component('RadiologyMeasurementRows.vue','addRow,rows',{props:{code:'TVD',fields,values,conditions,disabled:false}})
 c.addRow();assert.deepEqual(c.rows.value,[1,2,3,4]);assert.equal(values['TVD::R1__TVD1-1'],'60.10');assert.equal(values['TVD::R2__TVD1-1'],'80.20');assert.equal(values['TVD::R3__TVD1-1'],'100.30');assert.equal(conditions['TVD.R1'],'条件一')
})

test('material, position and signal tables include every input once',()=>{
 for(const [code,codes] of [['HUL',['Air-HU1','Air-HU2','Air-HU3','Air-REF','LDPE-HU1']],['CTDI',['HCD1','HCD2','HCD3','HUD1','层数n1','CTDI-HEAD-MANUFACTURER']],['STP',['STP-K1','STP-R1','STP-K2','STP-R2','STP-MODEL']],['TVD',['TVD1-0','TVD1-1','TVD2-0','TVD2-1','TVD-K1','TVD-K2']]]){
  const fields=codes.map(code=>({code,label:code,unit:'',required:true,role:'RAW',sortNo:1}))
  const c=component('MeasurementTable.vue','layout',{props:{code,fields}})
  const rendered=c.layout.value.rows.flatMap(r=>r.fields).concat(c.layout.value.extra).map(f=>f.code)
  assert.deepEqual(rendered.toSorted(),codes.toSorted());assert.equal(new Set(rendered).size,codes.length)
 }
})

test('legacy and partial calculated groups retain the complete aggregate result',()=>{
 for(const measurements of [[],[{itemCode:'TVD',fieldCode:'DISPLAY_1',fieldRole:'INTERMEDIATE',displayValue:'1.0',unit:'kV'}]]){
  const c=component('RadiologyWorkbench.vue','selectedCase,evaluationRows')
  c.selectedCase.value={id:1,modality:'DR',status:'REPORTED',measurements,protectionPoints:[],itemResults:[{itemCode:'TVD',itemName:'管电压指示的偏离',complete:true,resultText:'第1组1.0 kV；第2组2.0 kV；第3组3.0%',judgement:'QUALIFIED',ruleDescription:'原判定依据'}]}
  assert.equal(c.evaluationRows.value.length,1);assert.equal(c.evaluationRows.value[0].itemName,'管电压指示的偏离');assert.match(c.evaluationRows.value[0].result,/第3组/)
 }
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

