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
const scopeSupport=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('radiologyScopeSupport.ts',dir),'utf8'))).toString('base64'))
const systemLabels=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('../../systemDisplayLabels.ts',dir),'utf8'))).toString('base64'))
function component(name,expose,{get=async()=>({data:{content:[],totalElements:0,last:true}}),confirm=async()=>{},post=async()=>({data:{}}),put=async()=>({data:{}}),props={}}={}){
 const calls=[],emits=[],warnings=[]
 const api={post,put,interceptors:{request:{use(){}}},get:async(...args)=>{calls.push(args[0]);return get(...args)}}
 const bindings={withDefaults:(props,defaults)=>({...defaults,...props}),ref,reactive,computed,watch(){},onMounted(){},onBeforeUnmount(){},defineProps:()=>({token:'test',username:'test',modality:'DR',...props}),defineEmits:()=>((...v)=>emits.push(v)),defineExpose(){},axios:{create:()=>api},ElMessage:{warning:m=>warnings.push(m),error:m=>warnings.push(m),success(){}},ElMessageBox:{confirm},...support,...doseSupport,...scopeSupport}
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

test('TVD presentation reads legacy groups without changing their stored values or results',()=>{
 const values={'TVD::TVD2-0':'120','TVD::TVD-K2':'1.02'},conditions=reactive({'TVD.2':'第二组原条件'})
 const before=JSON.stringify({values,conditions})
 const c=component('TvdPptEntry.vue','rows,fieldKey,result,meanValue,deviationValue',{props:{values,conditions,disabled:false,results:[{fieldCode:'MEAN_2',output:'122.40 kV'},{fieldCode:'DISPLAY_2',output:'2.0%'}]}})
 assert.deepEqual(c.rows.value,[1,2,3]);assert.equal(c.fieldKey(2,0),'TVD::TVD2-0');assert.equal(c.fieldKey(2,4),'TVD::TVD-K2')
 assert.equal(c.meanValue(2),'122.40');assert.equal(c.meanValue(1),'');assert.equal(c.deviationValue(1),'');assert.equal(c.result(2,'MEAN'),'122.40 kV');assert.equal(c.result(2,'DISPLAY'),'2.0%');assert.equal(JSON.stringify({values,conditions}),before)
})

test('TVD adding a row migrates all three original groups and their independent conditions',()=>{
 const values={},conditions=reactive({'TVD.2':'第二组曝光参数'})
 for(let id=1;id<=3;id++){for(let col=0;col<4;col++)values[`TVD::TVD${id}-${col}`]=String(id*10+col);values[`TVD::TVD-K${id}`]=String(id)}
 const c=component('TvdPptEntry.vue','rows,addRow',{props:{values,conditions,disabled:false,results:[]}})
 c.addRow();assert.deepEqual(c.rows.value,[1,2,3,4]);assert.equal(conditions['TVD.R2'],'第二组曝光参数')
 for(let id=1;id<=3;id++){for(let col=0;col<4;col++)assert.equal(values[`TVD::R${id}__TVD1-${col}`],String(id*10+col));assert.equal(values[`TVD::R${id}__TVD-K1`],String(id))}
})

test('TVD sparse repeatable rows keep their result identity and delete only the last row',()=>{
 const values={'TVD::R1__TVD1-0':'80','TVD::R7__TVD1-0':'120'},conditions=reactive({'__rows.TVD':'[1,7]','TVD.R1':'保留条件','TVD.R7':'待删除条件'})
 const c=component('TvdPptEntry.vue','rows,result,addRow,deleteLastRow',{props:{values,conditions,disabled:false,results:[{fieldCode:'R7__DISPLAY_1',output:'1.5%'}]}})
 assert.equal(c.result(7,'DISPLAY'),'1.5%');c.deleteLastRow();assert.deepEqual(c.rows.value,[1]);assert.equal(values['TVD::R7__TVD1-0'],undefined);assert.equal(conditions['TVD.R7'],undefined);assert.equal(conditions['TVD.R1'],'保留条件');assert.equal(values['TVD::R1__TVD1-0'],'80')
 c.deleteLastRow();assert.deepEqual(c.rows.value,[1]);c.addRow();assert.deepEqual(c.rows.value,[1,2])
})

test('TVD readonly view never enables row migration or deletes measurements',()=>{
 const values={'TVD::TVD1-0':'80'},conditions={};const before=JSON.stringify({values,conditions})
 const c=component('TvdPptEntry.vue','addRow,deleteLastRow',{props:{values,conditions,disabled:true,results:[]}})
 c.addRow();c.deleteLastRow();assert.equal(JSON.stringify({values,conditions}),before)
})

test('page display names map legacy codes without changing stored identifiers',()=>{
 assert.equal(support.pptItemName('AIC','旧名称'),'AEC电离室之间一致性')
 assert.equal(support.pptItemName('LCD','低对比可探测能力'),'低对比分辨力')
 assert.equal(support.pptFieldName('ILA','非残影区D0','旧标签'),'非残影区ROI值D₂')
 assert.equal(support.pptFieldName('TVD','TVD2-0','TVD2-0'),'预设值')
 assert.equal(support.pptFieldName('RST','R3','R3'),'实测值3')
 assert.equal(support.pptFieldName('NEW','future','未来字段'),'未来字段')
 assert.equal(support.attachmentCategoryName('POINT_MAP'),'检测点位图')
})

test('applicable navigation skips unselected, pending and excluded items and leaves protection to its own page',()=>{
 const rows=[{itemCode:'TVD',selected:true,applicable:true},{itemCode:'ROQ',selected:true,applicable:false},{itemCode:'ARP',selected:true,applicable:false,pending:true},{itemCode:'STP',selected:false,applicable:true},{itemCode:'HVL',selected:true,applicable:true},{itemCode:'RPD',selected:true,applicable:true}]
 assert.deepEqual(scopeSupport.applicableQuality(rows).map(s=>s.itemCode),['TVD','HVL'])
 assert.equal(scopeSupport.adjacentQuality(rows,'TVD',1),'HVL');assert.equal(scopeSupport.adjacentQuality(rows,'HVL',-1),'TVD');assert.equal(scopeSupport.adjacentQuality(rows,'HVL',1),undefined)
})

test('scope status never labels excluded or pending items as saved or calculated',()=>{
 assert.equal(scopeSupport.qualityStatus({selected:true,applicable:false},true,true,true),'不开展')
 assert.equal(scopeSupport.qualityStatus({selected:true,applicable:false,pending:true},true,true,true),'待确认')
 assert.equal(scopeSupport.qualityStatus({selected:true,applicable:true},false,false,false),'待录入')
 assert.equal(scopeSupport.qualityStatus({selected:true,applicable:true},true,false,false),'已保存')
 assert.equal(scopeSupport.qualityStatus({selected:true,applicable:true},true,true,true),'已计算')
})

test('confirming one historical machine item preserves the other item choices and recorded exclusion reasons',()=>{
 const c=component('RadiologyScopeEditor.vue','choose',{props:{profile:{selectedGroups:['SPECIAL']},scopes:[{itemCode:'DDI',modelDependent:true,applicable:false,reason:'历史机型不提供DDI'},{itemCode:'STP',modelDependent:true,applicable:false,reason:'历史机型不提供图像'},{itemCode:'RUE',modelDependent:true,applicable:true,reason:''}]}})
 c.choose('DDI',true);const profile=c.emits[0][1];assert.equal(profile.itemApplicability.DDI,true);assert.equal(profile.itemApplicability.STP,false);assert.equal(profile.itemApplicability.RUE,true);assert.equal(profile.itemExclusionReasons.STP,'历史机型不提供图像')
})

test('new cases start with no confirmed scope while legacy and saved scope choices survive hydration',()=>{
 const c=component('RadiologyWorkbench.vue','blankCaseForm,selectedCase,profileDraft,syncCaseState')
 const fresh=c.blankCaseForm('DR');assert.deepEqual(fresh.profile.selectedGroups,[]);assert.equal(fresh.profile.scopeConfirmed,false);assert.equal(fresh.profile.itemApplicability.ARP,null)
 c.selectedCase.value={modality:'DR',measurements:[],protectionPoints:[],profile:{selectedGroups:['PROTECTION']}};c.syncCaseState();assert.deepEqual(c.profileDraft.selectedGroups,['PROTECTION']);assert.notEqual(c.profileDraft.scopeConfirmed,false)
})

test('scope-first next action opens applicable input and final item continues only into selected protection',async()=>{
 for(const protection of [false,true]){
  const c=component('RadiologyWorkbench.vue','selectedCase,syncCaseState,viewStage,workTab,entryStep,activeQuality,nextEntry,previousQuality')
  c.selectedCase.value={modality:'DR',measurements:[],protectionPoints:[],profile:{selectedGroups:protection?['GENERAL','PROTECTION']:['GENERAL'],scopeConfirmed:true},itemScope:[{itemCode:'TVD',group:'GENERAL',selected:true,applicable:true},{itemCode:'ROQ',group:'GENERAL',selected:true,applicable:false},{itemCode:'RPD',group:'PROTECTION',selected:protection,applicable:protection}]};c.syncCaseState();c.viewStage.value=2;c.workTab.value='data'
  await c.nextEntry();assert.equal(c.entryStep.value,'input');assert.equal(c.workTab.value,'data');await c.nextEntry();assert.equal(c.workTab.value,protection?'points':'attachments');assert.deepEqual(c.calls,[])
 }
})

test('only-protection scope continues directly to protection without a quality input step',async()=>{
 const c=component('RadiologyWorkbench.vue','selectedCase,syncCaseState,viewStage,workTab,nextEntry')
 c.selectedCase.value={modality:'DR',measurements:[],protectionPoints:[],profile:{selectedGroups:['PROTECTION'],scopeConfirmed:true},itemScope:[{itemCode:'TVD',group:'GENERAL',selected:false,applicable:false},{itemCode:'RPD',group:'PROTECTION',selected:true,applicable:true}]};c.syncCaseState();c.viewStage.value=2;c.workTab.value='data';await c.nextEntry();assert.equal(c.workTab.value,'points');assert.deepEqual(c.calls,[])
})

test('historical protection points remain stored but are omitted from this evaluation when protection is unselected',()=>{
 for(const selected of [true,false]){
  const c=component('RadiologyWorkbench.vue','selectedCase,evaluationRows')
  c.selectedCase.value={measurements:[],itemResults:[],itemScope:[{itemCode:'RPD',selected,applicable:selected}],protectionPoints:[{pointName:'历史点位',readings:[1,2,3,4,5],resultValue:1.2}]}
  assert.equal(c.evaluationRows.value.length,selected?1:0);assert.equal(c.selectedCase.value.protectionPoints.length,1)
 }
})

test('saving changed scope clears the last evaluation cache while retaining unsaved measurement edits',async()=>{
 const before={id:1,modality:'DR',status:'CALCULATED',profile:{selectedGroups:['GENERAL','SPECIAL']},protectionPoints:[],measurements:[],itemResults:[{itemCode:'HCR',complete:true,judgement:'QUALIFIED'}]}
 const after={...before,profile:{selectedGroups:['GENERAL'],scopeConfirmed:true},calculationFresh:false,itemResults:[]}
 const c=component('RadiologyWorkbench.vue','selectedCase,calculation,calculationItems,measurementValues,profileDraft,syncCaseState,saveReportProfile',{put:async()=>({data:after})})
 c.selectedCase.value=before;c.syncCaseState();c.calculation.value={items:before.itemResults};c.measurementValues['ROA::ROA1']='尚未保存的编辑';c.profileDraft.selectedGroups=['GENERAL']
 assert.equal(await c.saveReportProfile(),true);assert.equal(c.calculation.value,null);assert.deepEqual(c.calculationItems.value,[]);assert.equal(c.measurementValues['ROA::ROA1'],'尚未保存的编辑')
})
test('rule display omits null bounds and preserves zero and text requirements',()=>{
 assert.equal(support.ruleLimitText({lowerLimit:null,upperLimit:25,unit:'μSv/h'}),'≤25 μSv/h')
 assert.equal(support.ruleLimitText({lowerLimit:0,upperLimit:null}),'≥0')
 assert.equal(support.ruleLimitText({lowerLimit:0,upperLimit:5,lowerInclusive:false,upperInclusive:false}),'>0 <5')
 assert.equal(support.ruleLimitText({lowerLimit:null,upperLimit:null,expectedText:'否'}),'应为“否”')
 assert.equal(support.ruleLimitText({}),'未设置限值')
})
test('damaged seed labels are repaired while custom names and all grants remain unchanged',()=>{
 const row={id:5,roleCode:'RADIOLOGY_RULE_ADMIN',roleName:'æ”¾å°„',remark:'ç»´æŠ¤',status:'ACTIVE',permissions:[1,2]}
 const fixed=systemLabels.roleDisplay(row)
 assert.equal(fixed.roleName,'放射规则管理员');assert.equal(fixed.remark,'维护 DR/CT 版本化判定规则')
 assert.equal(fixed.id,row.id);assert.equal(fixed.permissions,row.permissions);assert.equal(row.roleName,'æ”¾å°„')
 assert.equal(systemLabels.roleDisplay({...row,roleName:'自定义管理员'}).roleName,'自定义管理员')
 assert.equal(systemLabels.permissionDisplay({permCode:'RADIOLOGY:REPORT:GENERATE',permName:'æŠ¥å‘Š'}).permName,'放射检测报告按钮')
 assert.equal(systemLabels.permissionDisplay({permCode:'CUSTOM',permName:'自定义权限'}).permName,'自定义权限')
})
test('file selection does not upload until confirmation and can clear pending selection',()=>{
 let posts=0
 const c=component('RadiologyWorkbench.vue','chooseAttachment,pendingAttachment',{post:async()=>{posts++;return {data:{}}}})
 const file={name:'点位图.png'},input={files:[file],value:'selected'}
 c.chooseAttachment({target:input});assert.deepEqual(c.pendingAttachment.value,file);assert.equal(input.value,'');assert.equal(posts,0)
 c.chooseAttachment({target:{files:[],value:''}});assert.equal(c.pendingAttachment.value,undefined)
})
test('fixed and dynamic reading labels agree; non-average projects do not show empty means',()=>{
 for(const [code,field] of [['TVD','TVD1-0'],['ETD','ETD1-0']]){
  const f={code:field,label:field};const rows=component('RadiologyMeasurementRows.vue','label',{props:{code,fields:[f],conditions:{},values:{}}})
  const fixed=component('MeasurementTable.vue','heading',{props:{code,fields:[f]}})
  assert.equal(rows.label(f),fixed.heading(f,0))
 }
 for(const code of ['ART','ILA','LBA','MTF','HCR','LCR','GTA','RST'])assert.equal(component('MeasurementTable.vue','showMean',{props:{code,fields:[]}}).showMean.value,false)
})
test('saved readings use server-normalized values while unsaved profile and points remain intact',()=>{
 const c=component('RadiologyWorkbench.vue','selectedCase,measurementValues,conditionValues,profileDraft,pointDrafts,syncSavedMeasurements,measurementsPending')
 c.measurementValues['ROA::ROA1']='10.00';c.measurementValues['ROA::removed']='old';c.conditionValues.OLD='old'
 c.profileDraft.overallConclusion='尚未保存的人工评价';c.pointDrafts.value=[{name:'尚未保存的点位'}]
 c.selectedCase.value={measurements:[{itemCode:'ROA',fieldCode:'ROA1',fieldRole:'RAW',valueText:'10.0'}],itemConditions:{LCR:'参考剂量=2.5μSv'}}
 c.syncSavedMeasurements()
 assert.equal(c.measurementValues['ROA::ROA1'],'10.0');assert.equal(c.measurementValues['ROA::removed'],undefined);assert.equal(c.conditionValues.OLD,undefined)
 assert.equal(c.measurementsPending.value,false);assert.equal(c.profileDraft.overallConclusion,'尚未保存的人工评价');assert.equal(c.pointDrafts.value[0].name,'尚未保存的点位')
})
test('cancelled project and cross-module navigation preserve unsaved readings without requests',async()=>{
 const c=radiology(async()=>{throw 'cancel'});c.measurementValues['ETD::ETD1-1']='11.12';c.queryVisible.value=true
 await c.selectQueryCase({id:2,modality:'DR'});await c.selectQueryCase({id:3,modality:'CT'});await c.openQueryResult({id:4,modality:'DOSE'})
 assert.equal(c.selectedCase.value.id,1);assert.equal(c.measurementValues['ETD::ETD1-1'],'11.12');assert.equal(c.queryVisible.value,true);assert.deepEqual(c.calls,[]);assert.deepEqual(c.emits,[])
})

test('saving measurements refreshes invalidated point results but retains unsaved point edits',()=>{
 for(const edited of [false,true]){
  const c=component('RadiologyWorkbench.vue','selectedCase,pointDrafts,syncSavedPointResults')
  const old=[{id:1,name:'测试点位',readings:[1,2,3],resultValue:2,averageValue:2}]
  c.pointDrafts.value=[{...old[0],name:edited?'尚未保存的点位':old[0].name,readingsText:'1, 2, 3'}]
  c.selectedCase.value={protectionPoints:[{...old[0],resultValue:null,averageValue:null}]}
  c.syncSavedPointResults(old)
  assert.equal(c.pointDrafts.value[0].resultValue,edited?2:null)
  assert.equal(c.pointDrafts.value[0].name,edited?'尚未保存的点位':'测试点位')
 }
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
  const c=component('MeasurementTable.vue','layout,referenceFields',{props:{code,fields}})
  const rendered=c.layout.value.rows.flatMap(r=>r.fields).concat(c.layout.value.extra,c.referenceFields.value).map(f=>f.code)
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


test('equipment unit edits preserve legacy notes, ranges and mAs',()=>{
 const text='额定电压 40～150 kV；最大电流 500 mA；100 mAs；备注：双焦点'
 assert.equal(support.equipmentParameter(text,'kV'),'40～150')
 assert.equal(support.equipmentParameter('100 mAs','mA'),'')
 const updated=support.updateEquipmentParameter(text,'mA','600')
 assert.equal(updated,'额定电压 40～150 kV；最大电流 600 mA；100 mAs；备注：双焦点')
 assert.equal(support.updateEquipmentParameter('旧设备备注','kV','120'),'旧设备备注；120 kV')
 assert.equal(support.updateEquipmentParameter('备注','kV',''),'备注')
})
test('dynamic results map by stable row id after deletion and preserve display precision',()=>{
 const c=component('RadiologyMeasurementRows.vue','resultColumns,rowResult',{props:{code:'TVD',fields:[],values:{},conditions:{'__rows.TVD':'[1,3]'},disabled:false,results:[{fieldCode:'R1__MEAN_1',fieldLabel:'第1行 校正均值',output:'80.00 kV'},{fieldCode:'R3__MEAN_1',fieldLabel:'第3行 校正均值',output:'100.0 kV'}]}})
 assert.equal(c.resultColumns.value.length,1);assert.equal(c.rowResult(3,'MEAN_1'),'100.0 kV');assert.equal(c.rowResult(2,'MEAN_1'),'—')
})

test('repeatable PPT rows retain their output columns before calculation and after cache invalidation',()=>{
 for(const [code,expected] of [['ROA',['MEAN','RESULT']],['ETD',['MEAN_1','DEVIATION_PERCENT_1']],['HCR',['RESULT']],['LCR',['RESULT']],['RST',['WWMIN','CTHM','MEAN','DEVIATION']]]){
  const c=component('RadiologyMeasurementRows.vue','columns',{props:{code,fields:[],values:{},conditions:{['__rows.'+code]:'[1,7]'},disabled:false,results:[]}})
  assert.deepEqual(c.columns.value.filter(v=>!v.field).map(v=>v.code).toSorted(),expected.toSorted())
 }
})

test('PPT repeatable view keeps ETD factor after the average and never adds unrelated intermediate outputs',()=>{
 const c=component('RadiologyMeasurementRows.vue','columns',{props:{code:'ETD',fields:[{code:'ETD1-0'},{code:'ETD1-1'},{code:'ETD1-2'},{code:'ETD1-3'},{code:'ETD-K1'}],values:{},conditions:{'__rows.ETD':'[1,7]'},disabled:false,results:[{fieldCode:'R7__MEAN_1',fieldLabel:'均值',output:'100.00 ms'},{fieldCode:'R7__DEVIATION_PERCENT_1',fieldLabel:'相对偏差',output:'2.0%'}]}})
 const codes=c.columns.value.map(v=>v.code);assert.ok(codes.indexOf('MEAN_1')<codes.indexOf('ETD-K1'));assert.equal(codes.includes('DEVIATION_PERCENT_1'),true);assert.equal(codes.includes('DEVIATION_MS_1'),false)
})

test('selected STP formula uses the existing fitted coefficient keys including power c=0',()=>{
 for(const [model,expected] of [['LINEAR','PV=2·K+3'],['LOG','PV=2·ln(K)+3'],['POWER','PV=2·K^3']]){
  const c=component('MeasurementTable.vue','selectedFormula',{props:{code:'STP',fields:[],results:[{fieldCode:'SELECTED_MODEL',output:model},{fieldCode:model+'_A',output:'2'},{fieldCode:model+(model==='POWER'?'_B':'_C'),output:'3'}]}})
  assert.equal(c.selectedFormula(),expected)
 }
})
test('AEC group and RUE region results use their own calculated fields',()=>{
 for(const [code,key,index] of [['ARS','DEVIATION_2',1],['AIC','LEFT_DEVIATION',1],['RUE','DOSE_4',3]]){
 const c=component('MeasurementTable.vue','calculated',{props:{code,fields:[],results:[{fieldCode:key,output:'-2.0'}]}})
 assert.equal(c.calculated({fields:[]},index,false),'-2.0')
 }
})

test('personal dose preview sends the exact comment once and clears it for the next reviewer',async()=>{
 const sent=[]
 const c=component('PersonalDoseWorkbench.vue','form,action,previewVisible,previewComplete,previewComment,selected',{post:async(path,body)=>{sent.push({path,body});return {data:{id:7,version:2,status:'PENDING_REVIEW',personnel:[],readings:[]}}}})
 c.form.value={id:7,version:1,status:'CALCULATED',personnel:[],readings:[]};c.previewVisible.value=true;c.previewComplete.value=true;c.previewComment.value='编写核对意见'
 await c.action('submit','编写核对意见')
 assert.equal(sent.length,1);assert.equal(sent[0].body.comment,'编写核对意见');assert.equal(c.selected.value.status,'PENDING_REVIEW');assert.equal(c.previewVisible.value,false);assert.equal(c.previewComment.value,'');assert.equal(c.previewComplete.value,false)
})

test('STP optional per-level conditions preserve all other saved conditions',()=>{
 const conditions=reactive({'STP':'SID=100cm','__params.STP':'{"voltage":"80"}'})
 const c=component('MeasurementTable.vue','setCondition',{props:{code:'STP',fields:[],conditions}})
 c.setCondition('voltage',2,'85');c.setCondition('ddi',2,'100.00')
 assert.equal(conditions['STP.voltage.3'],'85');assert.equal(conditions['STP.ddi.3'],'100.00');assert.equal(conditions.STP,'SID=100cm');assert.equal(conditions['__params.STP'],'{"voltage":"80"}')
})

test('CT uniformity displays the signed difference for each direction',()=>{
 const fields=['U','D','L','R'].map(d=>({code:'HUU-'+d+'1'}))
 const results=['U','D','L','R'].map((d,i)=>({fieldCode:'DEVIATION_'+d,output:String(i-2)})).concat({fieldCode:'RESULT',output:'2.0'})
 const c=component('MeasurementTable.vue','layout,calculated',{props:{code:'HUU',fields,results}})
 assert.deepEqual(c.layout.value.rows.map((r,i)=>c.calculated(r,i,false)),['-2','-1','0','1'])
})
test('CTDI part blocks retain each parameter exactly once and do not mix head and body',()=>{
 const codes=['HCD1','SCD1','层数n1','层厚T1','刻度系数k1','层数n2','层厚T2','刻度系数k2','CTDI-HEAD-MANUFACTURER','CTDI-HEAD-BASELINE','CTDI-BODY-MANUFACTURER','CTDI-BODY-BASELINE']
 const c=component('MeasurementTable.vue','ctdiParts,partParameters',{props:{code:'CTDI',fields:codes.map(code=>({code}))}})
 const parameters=c.ctdiParts.value.map(p=>c.partParameters(p).map(f=>f.code))
 assert.deepEqual(parameters[0],codes.filter(c=>c.endsWith('1')&&!c.startsWith('H')&&!c.startsWith('S')||c.startsWith('CTDI-HEAD')))
 assert.equal(new Set(parameters.flat()).size,10);assert.equal(parameters.flat().length,10)
})
test('LCD contrast rows preserve legacy input separately and match computed diameters',()=>{
 const codes=['LCD-COUNT-1','LCD-COUNT-05','LCD-COUNT-03','NUM1','mm1']
 const c=component('MeasurementTable.vue','layout',{props:{code:'LCD',fields:codes.map(code=>({code}))}})
 assert.deepEqual(c.layout.value.rows.map(r=>[r.label,r.fields[0].code]),[['1.0%','LCD-COUNT-1'],['0.5%','LCD-COUNT-05'],['0.3%','LCD-COUNT-03']])
 assert.deepEqual(c.layout.value.extra.map(f=>f.code),['NUM1','mm1'])
})

test('CTDI block edits isolate head and body and preserve shared legacy conditions',()=>{
 const conditions=reactive({'CTDI':'legacy shared','__params.CTDI':'{"voltage":"120"}'})
 const c=component('MeasurementTable.vue','setPartCondition',{props:{code:'CTDI',fields:[],conditions}})
 c.setPartCondition(['HEAD','头部','H','1'],'head 100kV');c.setPartCondition(['BODY','体部','S','2'],'body 120kV');c.setPartCondition(['HEAD','头部','H','1'],'{"voltage":"100"}',true)
 assert.equal(conditions['CTDI.1'],'head 100kV');assert.equal(conditions['CTDI.2'],'body 120kV');assert.equal(conditions['__params.CTDI.1'],'{"voltage":"100"}');assert.equal(conditions.CTDI,'legacy shared');assert.equal(conditions['__params.CTDI'],'{"voltage":"120"}')
})
test('RUE reference dose and CT scan parameters serialize immediately without losing legacy text',()=>{
 const c=component('RadiologyConditionEditor.vue','fields,update',{props:{itemCode:'RUE'}})
 Object.assign(c.fields,{sid:'100',voltage:'80',referenceDose:'2.5',extra:'原始条件'});c.update()
 assert.equal(JSON.parse(c.emits[0][1]).referenceDose,'2.5');assert.equal(c.emits[1][1],'SID=100cm，80kV，参考剂量=2.5μSv，原始条件')
 const ct=component('RadiologyConditionEditor.vue','fields,update',{props:{itemCode:'CTDI'}})
 Object.assign(ct.fields,{voltage:'120',current:'200',time:'1',extra:'头部'});ct.update()
 assert.equal(ct.emits[1][1],'120kV，200mA，1s，头部')
})

test('clearing all structured conditions clears the saved condition summary',()=>{
 const c=component('RadiologyConditionEditor.vue','fields,update',{props:{itemCode:'RUE',modelValue:'old 80kV'}})
 c.update();assert.deepEqual(c.emits[1],['update:modelValue',''])
})

test('simple-average projects show the computed average instead of an empty MEAN field',()=>{
 for(const code of ['HUA','HVL']){
  const c=component('MeasurementTable.vue','calculated',{props:{code,fields:[],results:[{fieldCode:'RESULT',output:'0.3 HU'}]}})
  assert.equal(c.calculated({fields:[]},0,true),'0.3 HU')
 }
})


test('ROA repeat rows retain mean and CV columns before recalculation',()=>{
 const c=component('RadiologyMeasurementRows.vue','columns',{props:{code:'ROA',fields:[{code:'ROA1',label:'读数K1'}],conditions:{},values:{},results:[]}})
 assert.deepEqual(c.columns.value.filter(column=>!column.field).map(column=>column.code),['MEAN','RESULT'])
 assert.equal(c.columns.value.find(column=>column.code==='MEAN').label,'平均值 K̄')
})

test('PPT K-bar labels cover fixed and repeated results without changing other quantities',()=>{
 for(const code of ['ROA','ROQ','ARP']){
  const c=component('MeasurementTable.vue','meanHeading',{props:{code,fields:[]}})
  assert.equal(c.meanHeading.value,'平均值 K̄')
 }
 assert.equal(support.pptResultName('RPD','MEAN','本底读数平均值'),'本底读数平均值 K̄')
 assert.equal(support.pptResultName('ROA','R7__MEAN','第7行 校正平均值'),'第7行 校正平均值 K̄')
 assert.equal(support.pptResultName('ROQ','MEAN_2','第二组校正平均值'),'第二组校正平均值 K̄')
 for(const code of ['TVD','ETD','HVL','ARS','AIC','RUE','CTDI','RST','LCD','HUL','HUA','HUU','NPS','DDI'])assert.equal(support.pptResultName(code,'MEAN','原有平均值'),'原有平均值')
 assert.equal(support.pptResultName('RPD','SD','标准差'),'标准差')
 assert.equal(support.pptResultName('ROA','MEAN','平均值 K̄'),'平均值 K̄')
 const c=component('RadiologyMeasurementRows.vue','columns,rowResult',{props:{code:'ROA',fields:[],conditions:{'__rows.ROA':'[7]'},values:{},results:[{fieldCode:'R7__MEAN',fieldLabel:'第7行 校正平均值',output:'1.2345 μGy'}]}})
 assert.equal(c.columns.value.find(column=>column.code==='MEAN').label,'平均值 K̄')
 assert.equal(c.rowResult(7,'MEAN'),'1.2345 μGy')
})


test('PPT project progression follows artefact, distance and lag order without mutating saved scopes',()=>{
 const codes=['DDI','STP','RUE','REA','ILA','ART','HCR','LCR']
 const original=codes.map(itemCode=>({itemCode,selected:true,applicable:true,pending:false}))
 const ordered=scopeSupport.pptOrderedScopes(original)
 assert.deepEqual(original.map(x=>x.itemCode),codes)
 assert.deepEqual(ordered.map(x=>x.itemCode),['DDI','STP','RUE','ART','REA','ILA','HCR','LCR'])
 assert.equal(scopeSupport.adjacentQuality(ordered,'RUE',1),'ART')
 assert.equal(scopeSupport.adjacentQuality(ordered,'REA',-1),'ART')
})

test('qualitative artefact and lag results display persisted text instead of an absent numeric result',()=>{
 for(const code of ['ART','ILA']){
  const c=component('MeasurementTable.vue','calculated',{props:{code,fields:[],results:[{fieldCode:'RESULT_TEXT',output:'无'}]}})
  assert.equal(c.calculated({fields:[]},0,false),'无')
 }
})

test('editing the LCR reference dose in the table preserves its shared conditions',()=>{
 const c=component('RadiologyConditionEditor.vue','fields,update',{props:{itemCode:'LCR',referenceOnly:true}})
 Object.assign(c.fields,{sid:'100',voltage:'80',referenceDose:'3.5',extra:'原始补充条件'});c.update()
 const saved=JSON.parse(c.emits[0][1]);assert.equal(saved.referenceDose,'3.5');assert.equal(saved.sid,'100');assert.equal(saved.voltage,'80');assert.equal(saved.extra,'原始补充条件')
})

test('ETD fixed and repeated E-r bind relative percent, leaving absolute milliseconds separate',()=>{
 const results=[{fieldCode:'MEAN_1',output:'210.00 ms'},{fieldCode:'DEVIATION_MS_1',output:'10.0 ms'},{fieldCode:'DEVIATION_PERCENT_1',output:'5.0 %'}]
 const c=component('MeasurementTable.vue','calculated,meanHeading,resultHeading',{props:{code:'ETD',fields:[],results}})
 assert.equal(c.calculated({fields:[]},0,true),'210.00 ms');assert.equal(c.calculated({fields:[]},0,false),'5.0 %')
 assert.equal(c.meanHeading.value,'平均值 T̄');assert.equal(c.resultHeading.value,'检测结果 Eᵣ')
 const repeat=component('RadiologyMeasurementRows.vue','rowResult,columns',{props:{code:'ETD',fields:[],values:{},conditions:{'__rows.ETD':'[7]'},results:results.map(r=>({...r,fieldCode:'R7__'+r.fieldCode,fieldLabel:r.fieldCode}))}})
 assert.equal(repeat.rowResult(7,'DEVIATION_PERCENT_1'),'5.0 %');assert.ok(!repeat.columns.value.some(c=>c.code==='DEVIATION_MS_1'))
})

test('HCR repeated baseline narration respects stable row id and hides retained legacy inputs',()=>{
 const values={'HCR::R7__厂家规定值/基线值':'3.2'}
 const c=component('RadiologyMeasurementRows.vue','rowResult,columns',{props:{code:'HCR',fields:[{code:'HCR1',label:'读数'},{code:'厂家规定值/基线值',label:'旧参考值'}],values,conditions:{'__rows.HCR':'[1,7]'},results:[{fieldCode:'R7__RESULT',fieldLabel:'第7行 分辨力基线',output:'2.6 lp/mm'},{fieldCode:'R1__RESULT',fieldLabel:'第1行 参考值保持率',output:'100.0 %'}]}})
 assert.equal(c.rowResult(7,'RESULT'),'建立基线值 2.6 lp/mm（即读数）');assert.equal(c.rowResult(1,'RESULT'),'100.0 %')
 assert.equal(c.columns.value.some(c=>c.code==='厂家规定值/基线值'),false);assert.equal(values['HCR::R7__厂家规定值/基线值'],'3.2')
})

test('dose display conversion retains physical value, zero, blanks and small decimal precision',()=>{
 for(const unit of ['nSv/h','μSv/h','mSv/h'])for(const to of ['nSv/h','μSv/h','mSv/h']){
  assert.equal(support.convertDoseRate('',unit,to),'');assert.equal(support.convertDoseRate(0,unit,to),'0')
  const converted=support.convertDoseRate('0.000123456789',unit,to)
  assert.ok(Math.abs(Number(support.convertDoseRate(converted,to,unit))-0.000123456789)<1e-18)
 }
 const props=reactive({modelValue:'1234.56789',canonicalUnit:'nSv/h',displayUnit:'μSv/h',label:'读数1'})
 const c=component('DoseRateInput.vue','displayed,update',{props})
 assert.equal(c.displayed.value,'1.23456789');assert.deepEqual(c.emits,[])
 c.update('0.000123456');assert.deepEqual(c.emits[0],['update:modelValue','0.123456'])
 c.update('');assert.deepEqual(c.emits[1],['update:modelValue',''])
})

test('clearing a point reading preserves later values and their unit positions',()=>{
 const c=component('RadiologyWorkbench.vue','pointRead,setPointRead,pointValues,pointAverage,selectedCase,conditionValues')
 const p={pointCode:'P1',readingsText:'100, 200, 300, 400, 500',readings:[100,200,300,400,500],averageValue:300}
 c.selectedCase.value={rpdUnit:'nSv/h'};c.setPointRead(p,1,'')
 assert.equal(c.pointRead(p,1),'');assert.equal(c.pointRead(p,2),'300');assert.equal(c.pointRead(p,4),'500')
 assert.match(c.pointAverage(p),/^待重算/);c.setPointRead(p,1,'200')
 for(let i=1;i<=5;i++)c.conditionValues['__rpd.pointUnit.P1.'+i]='μSv/h'
 assert.equal(c.pointAverage(p),'0.3 μSv/h')
 c.conditionValues['__rpd.pointUnit.P1.2']='mSv/h';assert.equal(c.pointAverage(p),'0.3 μSv/h')
 for(let i=1;i<=5;i++)c.conditionValues['__rpd.pointUnit.P1.'+i]='mSv/h'
 assert.equal(c.pointAverage(p),'0.0003 mSv/h')
})

test('math labels render semantic bars and subscripts instead of losing scientific symbols',()=>{
 const c=component('PptLabel.vue','parts',{props:{text:'平均值 K̄ / T̄ / Eᵣ / K₃ / fNyquist / CTDIW / α₁ / L₂ / R²'}})
 assert.deepEqual(c.parts.value.filter(p=>p.bar).map(p=>p.text),['K','T'])
 assert.deepEqual(c.parts.value.filter(p=>p.sub).map(p=>[p.text,p.sub]),[['E','r'],['K','3'],['f','Nyquist'],['CTDI','W'],['α','1'],['L','2']])
 assert.deepEqual(c.parts.value.find(p=>p.sup),{text:'R',sup:'2'})
})

test('ILA keeps independent percent result and full narrative; HCR baseline uses calculation text',()=>{
 const c=component('RadiologyWorkbench.vue','selectedCase,syncCaseState,entryResultText')
 c.selectedCase.value={id:1,status:'CALCULATED',calculationFresh:true,measurements:[{itemCode:'ILA',fieldCode:'RESULT',fieldRole:'FINAL',valueNum:10,displayValue:'10.0 %'}],profile:{},itemConditions:{},protectionPoints:[],itemResults:[{itemCode:'ILA',resultText:'存在残影，且像素值误差为10.0%'},{itemCode:'HCR',resultText:'建立基线值：2.6 lp/mm'}]};c.syncCaseState()
 assert.equal(c.entryResultText('ILA'),'存在残影，且像素值误差为10.0%');assert.equal(c.entryResultText('HCR'),'建立基线值 2.6 lp/mm（即读数）')
 c.selectedCase.value.calculationFresh=false;assert.equal(c.entryResultText('ILA'),'')
})

test('baseline help follows the actual project instead of reusing CT head/body instructions',()=>{
 for(const [code,expected] of [['HCR','radiology.hcr-baseline'],['LCR','radiology.lcr-baseline'],['DDI','radiology.ddi-baseline'],['CTDI','radiology.stability-baseline'],['NPS','radiology.project-baseline']]){
  const c=component('MeasurementTable.vue','baselineHelp',{props:{code,fields:[]}});assert.equal(c.baselineHelp(),expected)
 }
})

test('all quality projects have project-specific result help and DR baseline help avoids CT instructions',async()=>{
 const help=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('../../helpContent.ts',dir),'utf8'))).toString('base64'))
 for(const code of ['TVD','ROA','ROQ','HVL','ETD','ARP','ARS','AIC','UBD','LRD','DDI','STP','RUE','ART','REA','ILA','HCR','LCR','TPA','GTA','CTDI','LBA','RST','MTF','LCD','HUL','HUA','HUU','NPS'])assert.ok(help.findHelpAnchor('radiology.result.'+code)?.summary,code)
 assert.match(help.findHelpAnchor('radiology.result.HVL').summary,/≥2.3 mmAl/)
 for(const code of ['hcr','lcr','ddi'])assert.doesNotMatch(help.findHelpAnchor('radiology.'+code+'-baseline').summary,/头模|体模/)
})

test('LCR repeated rows retain baseline or signed change narrative independently',()=>{
 const c=component('RadiologyMeasurementRows.vue','rowResult',{props:{code:'LCR',fields:[],values:{},conditions:{},results:[{fieldCode:'R1__RESULT',fieldLabel:'第1行 可观察细节数基线',output:'10.00 个细节'},{fieldCode:'R1__CONTRAST',fieldLabel:'对比度',output:'0.300 %'},{fieldCode:'R7__RESULT',fieldLabel:'第7行 与基线的绝对差',output:'2.00'},{fieldCode:'R7__DETAIL_DELTA',fieldLabel:'细节数减基线值',output:'-2.00'},{fieldCode:'R7__CONTRAST',fieldLabel:'对比度',output:'0.300 %'}]}})
 assert.match(c.rowResult(1,'RESULT'),/建立基线值可观察影像细节数10个/)
 assert.match(c.rowResult(7,'RESULT'),/相差-2个细节变化/)
})
